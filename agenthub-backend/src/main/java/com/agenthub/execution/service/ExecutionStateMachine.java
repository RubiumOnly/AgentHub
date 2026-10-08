package com.agenthub.execution.service;

import com.agenthub.execution.domain.model.StepRunStatus;
import com.agenthub.execution.domain.model.WorkflowRunStatus;
import com.agenthub.execution.infrastructure.entity.RunEventEntity;
import com.agenthub.execution.infrastructure.entity.StepRunEntity;
import com.agenthub.execution.infrastructure.entity.WorkflowRunEntity;
import com.agenthub.execution.infrastructure.repository.RunEventRepository;
import com.agenthub.execution.infrastructure.repository.StepRunRepository;
import com.agenthub.execution.infrastructure.repository.WorkflowRunRepository;
import com.agenthub.shared.context.RequestContext;
import com.agenthub.shared.exception.BusinessException;
import com.agenthub.shared.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Strict state machine for WorkflowRun and StepRun.
 * Validates legal transitions, intercepts illegal moves, and persists state change audit logs.
 */
@Component
public class ExecutionStateMachine {

    private static final Logger log = LoggerFactory.getLogger(ExecutionStateMachine.class);

    private final WorkflowRunRepository workflowRunRepository;
    private final StepRunRepository stepRunRepository;
    private final RunEventRepository runEventRepository;
    private final RunEventBroadcaster broadcaster;
    private final ObjectMapper objectMapper;
    private final Map<String, ReentrantLock> runLocks = new ConcurrentHashMap<>();

    public ExecutionStateMachine(WorkflowRunRepository workflowRunRepository,
                                 StepRunRepository stepRunRepository,
                                 RunEventRepository runEventRepository,
                                 RunEventBroadcaster broadcaster,
                                 ObjectMapper objectMapper) {
        this.workflowRunRepository = workflowRunRepository;
        this.stepRunRepository = stepRunRepository;
        this.runEventRepository = runEventRepository;
        this.broadcaster = broadcaster;
        this.objectMapper = objectMapper;
    }

    private ReentrantLock getLockForRun(String runId) {
        return runLocks.computeIfAbsent(runId, k -> new ReentrantLock());
    }

    /**
     * Transition a WorkflowRun to target status with strict validation and audit logging.
     */
    @Transactional
    public WorkflowRunEntity transitionRun(String runId, WorkflowRunStatus targetStatus, String reason) {
        String actor = RequestContext.get().getUserId();
        if (actor == null || actor.isBlank()) {
            actor = "system";
        }
        String correlationId = RequestContext.get().getCorrelationId();
        return transitionRun(runId, targetStatus, actor, reason, correlationId);
    }

    /**
     * Transition a WorkflowRun with explicit actor, reason, and correlationId.
     */
    @Transactional
    public WorkflowRunEntity transitionRun(String runId, WorkflowRunStatus targetStatus, String actor, String reason, String correlationId) {
        ReentrantLock lock = getLockForRun(runId);
        lock.lock();
        try {
            WorkflowRunEntity run = workflowRunRepository.findById(runId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.RUN_NOT_FOUND, "Run not found: " + runId));

            WorkflowRunStatus currentStatus = WorkflowRunStatus.fromString(run.getStatus());

            if (currentStatus == targetStatus) {
                log.debug("Workflow run [{}] is already in status [{}], transition is idempotent.", runId, targetStatus);
                return run;
            }

            if (!currentStatus.canTransitionTo(targetStatus)) {
                String errorMsg = String.format("Illegal state transition for run [%s]: Cannot move from [%s] to [%s]. Reason provided: %s",
                        runId, currentStatus, targetStatus, reason);
                log.error(errorMsg);
                throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, errorMsg);
            }

            LocalDateTime now = LocalDateTime.now();
            run.setStatus(targetStatus.name());
            run.setUpdatedAt(now);
            if (correlationId != null) {
                run.setCorrelationId(correlationId);
            }

            if (targetStatus == WorkflowRunStatus.RUNNING) {
                if (run.getStartedAt() == null) {
                    run.setStartedAt(now);
                }
                if (currentStatus == WorkflowRunStatus.FAILED || currentStatus == WorkflowRunStatus.TIMED_OUT) {
                    run.setFinishedAt(null);
                    run.setCancelledAt(null);
                    run.setCancelReason(null);
                }
            } else if (targetStatus.isTerminal()) {
                if (run.getFinishedAt() == null) {
                    run.setFinishedAt(now);
                }
                if (targetStatus == WorkflowRunStatus.CANCELLED) {
                    run.setCancelledAt(now);
                    run.setCancelReason(reason != null ? reason : "Cancelled");
                } else if (targetStatus == WorkflowRunStatus.TIMED_OUT) {
                    run.setCancelledAt(now);
                    run.setCancelReason(reason != null ? reason : "Timed out");
                }
            }

            WorkflowRunEntity saved = workflowRunRepository.save(run);

            // Publish transition audit event through broadcaster with monotonic sequence and SSE push
            try {
                Map<String, Object> payloadMap = Map.of(
                        "runId", runId,
                        "fromStatus", currentStatus.name(),
                        "toStatus", targetStatus.name(),
                        "actor", actor != null ? actor : "system",
                        "reason", reason != null ? reason : "",
                        "correlationId", correlationId != null ? correlationId : "",
                        "timestamp", now.toString()
                );
                String payloadJson = objectMapper.writeValueAsString(payloadMap);
                broadcaster.publishEvent(runId, "RUN_STATE_CHANGED", payloadJson);
            } catch (Exception e) {
                log.warn("Failed to serialize or record state transition event for run {}: {}", runId, e.getMessage());
            }

            log.info("Workflow run [{}] transitioned: [{}] -> [{}] (actor: {}, reason: {})",
                    runId, currentStatus, targetStatus, actor, reason);

            return saved;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Transition a StepRun to target status with strict validation and audit logging.
     */
    @Transactional
    public StepRunEntity transitionStep(String stepRunId, StepRunStatus targetStatus, String reason) {
        String actor = RequestContext.get().getUserId();
        if (actor == null || actor.isBlank()) {
            actor = "system";
        }
        String correlationId = RequestContext.get().getCorrelationId();
        return transitionStep(stepRunId, targetStatus, actor, reason, correlationId);
    }

    /**
     * Transition a StepRun with explicit actor, reason, and correlationId.
     */
    @Transactional
    public StepRunEntity transitionStep(String stepRunId, StepRunStatus targetStatus, String actor, String reason, String correlationId) {
        StepRunEntity step = stepRunRepository.findById(stepRunId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STEP_RUN_NOT_FOUND, "Step run not found: " + stepRunId));

        ReentrantLock lock = getLockForRun(step.getRunId());
        lock.lock();
        try {
            StepRunStatus currentStatus = StepRunStatus.fromString(step.getStatus());

            if (currentStatus == targetStatus) {
                log.debug("Step run [{}] is already in status [{}], transition is idempotent.", stepRunId, targetStatus);
                return step;
            }

            if (!currentStatus.canTransitionTo(targetStatus)) {
                String errorMsg = String.format("Illegal state transition for step [%s]: Cannot move from [%s] to [%s]. Reason provided: %s",
                        stepRunId, currentStatus, targetStatus, reason);
                log.error(errorMsg);
                throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, errorMsg);
            }

            LocalDateTime now = LocalDateTime.now();
            step.setStatus(targetStatus.name());
            step.setUpdatedAt(now);
            if (correlationId != null) {
                step.setCorrelationId(correlationId);
            }

            if (targetStatus == StepRunStatus.RUNNING && step.getStartedAt() == null) {
                step.setStartedAt(now);
            } else if (targetStatus.isTerminal()) {
                step.setFinishedAt(now);
                if (step.getStartedAt() != null) {
                    step.setDurationMs(java.time.Duration.between(step.getStartedAt(), now).toMillis());
                }
                if (targetStatus == StepRunStatus.FAILED && reason != null) {
                    step.setErrorMessage(reason);
                }
            } else if (targetStatus == StepRunStatus.PENDING && (currentStatus == StepRunStatus.FAILED || currentStatus == StepRunStatus.TIMED_OUT)) {
                // Retry attempt
                step.setAttempt(step.getAttempt() + 1);
                step.setErrorMessage(null);
                step.setStartedAt(null);
                step.setFinishedAt(null);
                step.setDurationMs(null);
            }

            StepRunEntity saved = stepRunRepository.save(step);

            // Publish transition audit event through broadcaster with monotonic sequence and SSE push
            try {
                Map<String, Object> payloadMap = Map.of(
                        "runId", step.getRunId(),
                        "stepRunId", stepRunId,
                        "nodeId", step.getNodeId(),
                        "fromStatus", currentStatus.name(),
                        "toStatus", targetStatus.name(),
                        "attempt", step.getAttempt(),
                        "actor", actor != null ? actor : "system",
                        "reason", reason != null ? reason : "",
                        "correlationId", correlationId != null ? correlationId : "",
                        "timestamp", now.toString()
                );
                String payloadJson = objectMapper.writeValueAsString(payloadMap);
                broadcaster.publishEvent(step.getRunId(), "STEP_STATE_CHANGED", payloadJson);
            } catch (Exception e) {
                log.warn("Failed to serialize or record state transition event for step {}: {}", stepRunId, e.getMessage());
            }

            log.info("Step run [{}] for run [{}] transitioned: [{}] -> [{}] (attempt: {}, actor: {}, reason: {})",
                    stepRunId, step.getRunId(), currentStatus, targetStatus, step.getAttempt(), actor, reason);

            return saved;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Clean up internal lock state for completed run to avoid memory leak.
     */
    public void cleanupRun(String runId) {
        if (runId != null) {
            runLocks.remove(runId);
        }
    }
}
