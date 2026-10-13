package com.agenthub.orchestration.scheduler;

import com.agenthub.execution.domain.model.CancelToken;
import com.agenthub.execution.domain.model.WorkflowRunStatus;
import com.agenthub.execution.infrastructure.entity.StepRunEntity;
import com.agenthub.execution.infrastructure.entity.WorkflowRunEntity;
import com.agenthub.execution.infrastructure.repository.StepRunRepository;
import com.agenthub.execution.infrastructure.repository.WorkflowRunRepository;
import com.agenthub.execution.service.ExecutionStateMachine;
import com.agenthub.infrastructure.concurrency.WorkspaceLockManager;
import com.agenthub.orchestration.domain.dsl.WorkflowDsl;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Internal DAG seam responsible for workspace lock acquisition/release, DB run & step lease
 * ownership/heartbeats, timeout watchdog scheduling, and bounded execution thread pool management.
 */
@Component
public class DagLeaseExecutor {

    private static final Logger log = LoggerFactory.getLogger(DagLeaseExecutor.class);

    private final WorkspaceLockManager lockManager;
    private final WorkflowRunRepository workflowRunRepository;
    private final StepRunRepository stepRunRepository;
    private final ExecutionStateMachine stateMachine;
    private final DagEventRecorder eventRecorder;
    private final ObjectMapper objectMapper;

    private final ScheduledExecutorService watchdogScheduler = Executors.newScheduledThreadPool(
            2,
            new CustomizableThreadFactory("dag-watchdog-")
    );

    private final ExecutorService executionPool = new ThreadPoolExecutor(
            8,
            64,
            60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(1000),
            new CustomizableThreadFactory("dag-exec-"),
            new ThreadPoolExecutor.CallerRunsPolicy()
    );

    public DagLeaseExecutor(WorkspaceLockManager lockManager,
                            WorkflowRunRepository workflowRunRepository,
                            StepRunRepository stepRunRepository,
                            ExecutionStateMachine stateMachine,
                            DagEventRecorder eventRecorder,
                            ObjectMapper objectMapper) {
        this.lockManager = lockManager;
        this.workflowRunRepository = workflowRunRepository;
        this.stepRunRepository = stepRunRepository;
        this.stateMachine = stateMachine;
        this.eventRecorder = eventRecorder;
        this.objectMapper = objectMapper;
    }

    public ExecutorService getExecutionPool() {
        return executionPool;
    }

    public ScheduledFuture<?> scheduleTimeoutWatchdog(String runId, long effectiveTimeout, CancelToken cancelToken) {
        return watchdogScheduler.schedule(() -> {
            log.warn("DAG watchdog timeout triggered for run [{}] after {} seconds", runId, effectiveTimeout);
            cancelToken.timeout("Execution exceeded timeout limit of " + effectiveTimeout + "s");
            try {
                stateMachine.transitionRun(runId, WorkflowRunStatus.TIMED_OUT, "watchdog", "Timeout after " + effectiveTimeout + "s", null);
                eventRecorder.publishRunTimedOut(runId, effectiveTimeout);
            } catch (Exception e) {
                log.debug("Run [{}] already in terminal state on timeout watchdog: {}", runId, e.getMessage());
            }
        }, effectiveTimeout, TimeUnit.SECONDS);
    }

    public boolean tryAcquireWorkspaceLock(String workspacePath, String runId, long effectiveTimeout) {
        if (workspacePath == null || workspacePath.isBlank()) {
            return true;
        }
        long lockTimeoutMs = 5000;
        long leaseTtlMs = Math.max(60000L, effectiveTimeout * 1000L + 30000L);
        return lockManager.tryAcquireLock(workspacePath, runId, lockTimeoutMs, leaseTtlMs);
    }

    public void releaseWorkspaceLock(String workspacePath, String runId) {
        if (workspacePath != null && !workspacePath.isBlank()) {
            lockManager.releaseLock(workspacePath, runId);
        }
    }

    public boolean claimRunLeaseAndPersistSnapshot(String runId, String workerId, long effectiveTimeout, WorkflowDsl dsl) {
        WorkflowRunEntity runEntity = workflowRunRepository.findById(runId).orElse(null);
        if (runEntity == null) {
            return true;
        }
        if (runEntity.getLeaseOwner() != null && !workerId.equals(runEntity.getLeaseOwner())
                && runEntity.getLeaseUntil() != null && runEntity.getLeaseUntil().isAfter(LocalDateTime.now())) {
            log.warn("Run [{}] active lease held by [{}] until [{}], aborting concurrent execution on [{}]",
                    runId, runEntity.getLeaseOwner(), runEntity.getLeaseUntil(), workerId);
            return false;
        }
        runEntity.setLeaseOwner(workerId);
        runEntity.setLeaseUntil(LocalDateTime.now().plusSeconds(effectiveTimeout + 30));
        runEntity.setHeartbeatAt(LocalDateTime.now());
        if (runEntity.getDslSnapshot() == null || runEntity.getDslSnapshot().isBlank()) {
            try {
                runEntity.setDslSnapshot(objectMapper.writeValueAsString(dsl));
            } catch (Exception ignored) {}
        }
        workflowRunRepository.save(runEntity);
        return true;
    }

    public void releaseRunLease(String runId, String workerId) {
        try {
            workflowRunRepository.releaseRunLease(runId, workerId, LocalDateTime.now());
        } catch (Exception ignored) {}
    }

    public void claimStepLease(StepRunEntity stepEntity, String workerId, Integer attempt, Map<String, Object> resolvedInputs) {
        try {
            StepRunEntity current = stepRunRepository.findById(stepEntity.getId()).orElse(stepEntity);
            if (attempt != null) {
                current.setAttempt(attempt);
            }
            current.setLeaseOwner(workerId);
            current.setLeaseUntil(LocalDateTime.now().plusSeconds(120));
            current.setHeartbeatAt(LocalDateTime.now());
            if (resolvedInputs != null) {
                current.setInputsJson(objectMapper.writeValueAsString(resolvedInputs));
            }
            stepRunRepository.save(current);
        } catch (Exception ignored) {}
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down DagLeaseExecutor thread pools...");
        watchdogScheduler.shutdownNow();
        executionPool.shutdown();
        try {
            if (!executionPool.awaitTermination(5, TimeUnit.SECONDS)) {
                executionPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            executionPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
