package com.agenthub.orchestration.scheduler;

import com.agenthub.execution.domain.model.CancelToken;
import com.agenthub.execution.domain.model.StepRunStatus;
import com.agenthub.execution.domain.model.WorkflowRunStatus;
import com.agenthub.execution.infrastructure.entity.StepRunEntity;
import com.agenthub.execution.infrastructure.entity.WorkflowRunEntity;
import com.agenthub.execution.infrastructure.repository.StepRunRepository;
import com.agenthub.execution.infrastructure.repository.WorkflowRunRepository;
import com.agenthub.execution.service.ExecutionStateMachine;
import com.agenthub.orchestration.domain.approval.ApprovalDecision;
import com.agenthub.orchestration.domain.approval.ApprovalDecisionResult;
import com.agenthub.orchestration.domain.approval.ApprovalSignalRegistry;
import com.agenthub.orchestration.domain.dsl.WorkflowNodeDsl;
import com.agenthub.orchestration.domain.evaluation.WorkflowExecutionContext;
import com.agenthub.orchestration.infrastructure.entity.ApprovalEntity;
import com.agenthub.orchestration.infrastructure.repository.ApprovalRepository;
import com.agenthub.shared.context.RequestContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Internal DAG seam responsible for step/run state transitions, pause barrier waiting,
 * human-in-the-loop approval suspension & resumption, and sibling node abort propagation.
 */
@Component
public class DagStateTransitioner {

    private static final Logger log = LoggerFactory.getLogger(DagStateTransitioner.class);

    private final ExecutionStateMachine stateMachine;
    private final StepRunRepository stepRunRepository;
    private final WorkflowRunRepository workflowRunRepository;
    private final ApprovalRepository approvalRepository;
    private final ApprovalSignalRegistry approvalSignalRegistry;
    private final DagEventRecorder eventRecorder;
    private final ObjectMapper objectMapper;

    public DagStateTransitioner(ExecutionStateMachine stateMachine,
                                StepRunRepository stepRunRepository,
                                WorkflowRunRepository workflowRunRepository,
                                ApprovalRepository approvalRepository,
                                ApprovalSignalRegistry approvalSignalRegistry,
                                DagEventRecorder eventRecorder,
                                ObjectMapper objectMapper) {
        this.stateMachine = stateMachine;
        this.stepRunRepository = stepRunRepository;
        this.workflowRunRepository = workflowRunRepository;
        this.approvalRepository = approvalRepository;
        this.approvalSignalRegistry = approvalSignalRegistry;
        this.eventRecorder = eventRecorder;
        this.objectMapper = objectMapper;
    }

    public void transitionRun(String runId, WorkflowRunStatus target, String actor, String reason) {
        stateMachine.transitionRun(runId, target, actor, reason, null);
    }

    public void transitionStep(String stepRunId, StepRunStatus target, String actor, String reason) {
        stateMachine.transitionStep(stepRunId, target, actor, reason, null);
    }

    public void cleanupRun(String runId) {
        stateMachine.cleanupRun(runId);
    }

    public void markStepSucceeded(StepRunEntity stepEntity, WorkflowNodeDsl node, String output, WorkflowExecutionContext context) {
        stateMachine.transitionStep(stepEntity.getId(), StepRunStatus.SUCCEEDED, "scheduler", "Completed successfully", null);
        try {
            StepRunEntity current = stepRunRepository.findById(stepEntity.getId()).orElse(stepEntity);
            if (output != null && output.length() > 255) {
                current.setOutputRef(output.substring(0, 252) + "...");
            } else {
                current.setOutputRef(output);
            }
            current.setLeaseOwner(null);
            current.setLeaseUntil(null);
            current.setOutputsJson(objectMapper.writeValueAsString(context.getNodeState(node.getId()).getOutputs()));
            stepRunRepository.save(current);
        } catch (Exception ignored) {}
    }

    public void markStepSkipped(StepRunEntity stepEntity, WorkflowNodeDsl node, String reason, WorkflowExecutionContext context) {
        context.recordNodeSkipped(node.getId(), reason);
        stateMachine.transitionStep(stepEntity.getId(), StepRunStatus.SKIPPED, "scheduler", reason, null);
        try {
            StepRunEntity current = stepRunRepository.findById(stepEntity.getId()).orElse(stepEntity);
            current.setLeaseOwner(null);
            current.setLeaseUntil(null);
            current.setOutputsJson(objectMapper.writeValueAsString(context.getNodeState(node.getId()).getOutputs()));
            stepRunRepository.save(current);
        } catch (Exception ignored) {}
        eventRecorder.publishStepSkipped(stepEntity.getRunId(), stepEntity.getId(), node.getId(), reason);
    }

    public void markStepCancelled(String stepRunId, String reason) {
        try {
            stateMachine.transitionStep(stepRunId, StepRunStatus.CANCELLED, "scheduler", reason, null);
            StepRunEntity current = stepRunRepository.findById(stepRunId).orElse(null);
            if (current != null) {
                current.setLeaseOwner(null);
                current.setLeaseUntil(null);
                stepRunRepository.save(current);
            }
        } catch (Exception ignored) {}
    }

    public void abortRemainingNodes(Map<String, StepRunEntity> stepEntityMap,
                                    ConcurrentMap<String, StepRunStatus> nodeStatuses,
                                    CountDownLatch completionLatch,
                                    String reason) {
        if (stepEntityMap == null) return;
        for (Map.Entry<String, StepRunEntity> entry : stepEntityMap.entrySet()) {
            String nid = entry.getKey();
            StepRunStatus prev = nodeStatuses.putIfAbsent(nid, StepRunStatus.CANCELLED);
            if (prev == null || prev == StepRunStatus.PENDING) {
                if (prev == StepRunStatus.PENDING) {
                    nodeStatuses.put(nid, StepRunStatus.CANCELLED);
                }
                markStepCancelled(entry.getValue().getId(), reason);
                completionLatch.countDown();
            }
        }
    }

    public void checkPauseWait(String runId, CancelToken cancelToken) throws InterruptedException {
        WorkflowRunEntity currentRun = workflowRunRepository.findById(runId).orElse(null);
        while (currentRun != null && "PAUSED".equalsIgnoreCase(currentRun.getStatus())) {
            if (cancelToken.isCancelled()) break;
            Thread.sleep(500);
            currentRun = workflowRunRepository.findById(runId).orElse(null);
        }
    }

    public boolean handleNodeApproval(String runId,
                                      StepRunEntity stepEntity,
                                      WorkflowNodeDsl node,
                                      CancelToken cancelToken,
                                      WorkflowExecutionContext context) {
        String approvalId = "appr-" + UUID.randomUUID().toString().substring(0, 8);
        String requestedBy = context.getInitiatorUserId();
        if (requestedBy == null || requestedBy.isBlank()) {
            requestedBy = RequestContext.get().getUserId();
        }
        if (requestedBy == null || requestedBy.isBlank()) {
            requestedBy = "orchestrator-gate";
        }
        ApprovalEntity approval = new ApprovalEntity(
                approvalId, runId, stepEntity.getId(), "PENDING", requestedBy
        );
        approvalRepository.save(approval);

        stateMachine.transitionStep(stepEntity.getId(), StepRunStatus.WAITING_APPROVAL, "scheduler", "Waiting for approval", null);
        stateMachine.transitionRun(runId, WorkflowRunStatus.WAITING_APPROVAL, "scheduler", "Waiting for approval on node " + node.getId(), null);

        eventRecorder.publishApprovalRequested(runId, approvalId, stepEntity.getId(), node.getId(), node.getName());

        CompletableFuture<ApprovalDecisionResult> signal = approvalSignalRegistry.register(approvalId);

        try {
            while (true) {
                if (cancelToken.isCancelled()) {
                    approvalSignalRegistry.remove(approvalId);
                    return false;
                }
                try {
                    ApprovalDecisionResult decisionResult = signal.get(1, TimeUnit.SECONDS);
                    approvalSignalRegistry.remove(approvalId);
                    if (decisionResult.getDecision() == ApprovalDecision.APPROVED) {
                        stateMachine.transitionRun(runId, WorkflowRunStatus.RUNNING, "scheduler", "Approval approved by " + decisionResult.getReviewer(), null);
                        stateMachine.transitionStep(stepEntity.getId(), StepRunStatus.RUNNING, "scheduler", "Approval approved by " + decisionResult.getReviewer(), null);
                        return true;
                    } else {
                        stateMachine.transitionStep(stepEntity.getId(), StepRunStatus.FAILED, "scheduler", "Approval rejected: " + decisionResult.getReason(), null);
                        stateMachine.transitionRun(runId, WorkflowRunStatus.FAILED, "scheduler", "Approval rejected: " + decisionResult.getReason(), null);
                        return false;
                    }
                } catch (TimeoutException ignored) {
                    ApprovalEntity current = approvalRepository.findById(approvalId).orElse(null);
                    if (current != null && !"PENDING".equalsIgnoreCase(current.getStatus())) {
                        approvalSignalRegistry.remove(approvalId);
                        boolean isApproved = "APPROVED".equalsIgnoreCase(current.getStatus());
                        ApprovalDecision decision = isApproved ? ApprovalDecision.APPROVED : ApprovalDecision.REJECTED;
                        ApprovalDecisionResult fallbackResult = new ApprovalDecisionResult(decision, current.getDecisionReason(), current.getReviewedBy());
                        signal.complete(fallbackResult);
                        if (isApproved) {
                            stateMachine.transitionRun(runId, WorkflowRunStatus.RUNNING, "scheduler", "Approval approved by " + fallbackResult.getReviewer(), null);
                            stateMachine.transitionStep(stepEntity.getId(), StepRunStatus.RUNNING, "scheduler", "Approval approved by " + fallbackResult.getReviewer(), null);
                            return true;
                        } else {
                            stateMachine.transitionStep(stepEntity.getId(), StepRunStatus.FAILED, "scheduler", "Approval rejected: " + fallbackResult.getReason(), null);
                            stateMachine.transitionRun(runId, WorkflowRunStatus.FAILED, "scheduler", "Approval rejected: " + fallbackResult.getReason(), null);
                            return false;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Approval wait error for node " + node.getId(), e);
            approvalSignalRegistry.remove(approvalId);
            return false;
        }
    }
}
