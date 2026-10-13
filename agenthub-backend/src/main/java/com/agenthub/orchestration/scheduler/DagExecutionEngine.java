package com.agenthub.orchestration.scheduler;

import com.agenthub.execution.domain.model.CancelToken;
import com.agenthub.execution.domain.model.CancelTokenRegistry;
import com.agenthub.execution.domain.model.StepRunStatus;
import com.agenthub.execution.domain.model.WorkflowRunStatus;
import com.agenthub.execution.infrastructure.entity.StepRunEntity;
import com.agenthub.execution.infrastructure.entity.WorkflowRunEntity;
import com.agenthub.execution.infrastructure.repository.StepRunRepository;
import com.agenthub.execution.infrastructure.repository.WorkflowRunRepository;
import com.agenthub.execution.service.ExecutionStateMachine;
import com.agenthub.execution.service.RunEventBroadcaster;
import com.agenthub.infrastructure.concurrency.WorkspaceLockManager;
import com.agenthub.orchestration.domain.approval.ApprovalSignalRegistry;
import com.agenthub.orchestration.domain.dsl.TopologicalSortResult;
import com.agenthub.orchestration.domain.dsl.WorkflowDsl;
import com.agenthub.orchestration.domain.dsl.WorkflowEdgeDsl;
import com.agenthub.orchestration.domain.dsl.WorkflowNodeDsl;
import com.agenthub.orchestration.domain.evaluation.WorkflowExecutionContext;
import com.agenthub.orchestration.infrastructure.repository.ApprovalRepository;
import com.agenthub.runtime.AgentRuntimeRegistry;
import com.agenthub.shared.exception.BusinessException;
import com.agenthub.shared.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Enterprise DAG Parallel and Topological Execution Engine.
 * Orchestrates 5 internal seams behind a single entry point:
 * {@link DagTopologyPlanner}, {@link DagLeaseExecutor}, {@link DagStateTransitioner},
 * {@link DagAgentInvoker}, and {@link DagEventRecorder}.
 */
@Component
public class DagExecutionEngine {

    private static final Logger log = LoggerFactory.getLogger(DagExecutionEngine.class);
    public static final String INSTANCE_WORKER_ID = "worker-" + UUID.randomUUID().toString().substring(0, 8);

    private final CancelTokenRegistry cancelTokenRegistry;
    private final StepRunRepository stepRunRepository;
    private final WorkflowRunRepository workflowRunRepository;
    private final ObjectMapper objectMapper;
    private final DagTopologyPlanner topologyPlanner;
    private final DagLeaseExecutor leaseExecutor;
    private final DagStateTransitioner stateTransitioner;
    private final DagAgentInvoker agentInvoker;
    private final DagEventRecorder eventRecorder;

    @Autowired
    public DagExecutionEngine(CancelTokenRegistry cancelTokenRegistry,
                              StepRunRepository stepRunRepository,
                              WorkflowRunRepository workflowRunRepository,
                              ObjectMapper objectMapper,
                              DagTopologyPlanner topologyPlanner,
                              DagLeaseExecutor leaseExecutor,
                              DagStateTransitioner stateTransitioner,
                              DagAgentInvoker agentInvoker,
                              DagEventRecorder eventRecorder) {
        this.cancelTokenRegistry = cancelTokenRegistry;
        this.stepRunRepository = stepRunRepository;
        this.workflowRunRepository = workflowRunRepository;
        this.objectMapper = objectMapper;
        this.topologyPlanner = topologyPlanner;
        this.leaseExecutor = leaseExecutor;
        this.stateTransitioner = stateTransitioner;
        this.agentInvoker = agentInvoker;
        this.eventRecorder = eventRecorder;
    }

    public DagExecutionEngine(ExecutionStateMachine stateMachine,
                              CancelTokenRegistry cancelTokenRegistry,
                              AgentRuntimeRegistry runtimeRegistry,
                              RunEventBroadcaster broadcaster,
                              StepRunRepository stepRunRepository,
                              WorkflowRunRepository workflowRunRepository,
                              WorkspaceLockManager lockManager,
                              ApprovalRepository approvalRepository,
                              ApprovalSignalRegistry approvalSignalRegistry,
                              ObjectMapper objectMapper) {
        this.cancelTokenRegistry = cancelTokenRegistry;
        this.stepRunRepository = stepRunRepository;
        this.workflowRunRepository = workflowRunRepository;
        this.objectMapper = objectMapper;
        this.topologyPlanner = new DagTopologyPlanner();
        this.eventRecorder = new DagEventRecorder(broadcaster, objectMapper);
        this.leaseExecutor = new DagLeaseExecutor(
                lockManager, workflowRunRepository, stepRunRepository, stateMachine, this.eventRecorder, objectMapper);
        this.stateTransitioner = new DagStateTransitioner(
                stateMachine, stepRunRepository, workflowRunRepository, approvalRepository,
                approvalSignalRegistry, this.eventRecorder, objectMapper);
        this.agentInvoker = new DagAgentInvoker(
                runtimeRegistry, this.leaseExecutor, this.stateTransitioner, this.eventRecorder);
    }

    public CompletableFuture<WorkflowRunStatus> executeDag(String runId,
                                                           WorkflowDsl dsl,
                                                           String workspacePath,
                                                           Map<String, Object> initialInputs,
                                                           long timeoutSeconds) {
        return executeDag(runId, dsl, workspacePath, initialInputs, timeoutSeconds, null);
    }

    public CompletableFuture<WorkflowRunStatus> executeDag(String runId,
                                                           WorkflowDsl dsl,
                                                           String workspacePath,
                                                           Map<String, Object> initialInputs,
                                                           long timeoutSeconds,
                                                           String initiatorUserId) {
        TopologicalSortResult topology = topologyPlanner.validateAndPlan(dsl);
        CancelToken cancelToken = cancelTokenRegistry.getOrCreate(runId);
        long effectiveTimeout = timeoutSeconds > 0
                ? timeoutSeconds
                : (dsl.getTimeoutSeconds() != null && dsl.getTimeoutSeconds() > 0 ? dsl.getTimeoutSeconds() : 300);

        ScheduledFuture<?> timeoutWatchdog = leaseExecutor.scheduleTimeoutWatchdog(runId, effectiveTimeout, cancelToken);

        return CompletableFuture.supplyAsync(() -> {
            boolean lockAcquired = false;
            try {
                if (workspacePath != null && !workspacePath.isBlank()) {
                    lockAcquired = leaseExecutor.tryAcquireWorkspaceLock(workspacePath, runId, effectiveTimeout);
                    if (!lockAcquired) {
                        log.warn("Failed to acquire workspace lock for run [{}] on [{}]", runId, workspacePath);
                        stateTransitioner.transitionRun(runId, WorkflowRunStatus.FAILED, "scheduler",
                                "Failed to acquire workspace lock due to concurrent execution: " + workspacePath);
                        eventRecorder.publishRunFailed(runId, "Workspace lock contention");
                        return WorkflowRunStatus.FAILED;
                    }
                }

                stateTransitioner.transitionRun(runId, WorkflowRunStatus.RUNNING, "scheduler", "DAG execution started");
                eventRecorder.publishDagStarted(runId, dsl.getNodes().size());

                if (!leaseExecutor.claimRunLeaseAndPersistSnapshot(runId, INSTANCE_WORKER_ID, effectiveTimeout, dsl)) {
                    return WorkflowRunStatus.FAILED;
                }

                WorkflowExecutionContext context = new WorkflowExecutionContext(initialInputs, initiatorUserId);

                List<StepRunEntity> existingSteps = stepRunRepository.findByRunIdOrderByCreatedAtAsc(runId);
                Map<String, StepRunEntity> existingStepByNodeId = new HashMap<>();
                for (StepRunEntity se : existingSteps) {
                    existingStepByNodeId.put(se.getNodeId(), se);
                }

                Map<String, WorkflowNodeDsl> nodeMap = new HashMap<>();
                Map<String, StepRunEntity> stepEntityMap = new HashMap<>();
                for (WorkflowNodeDsl node : dsl.getNodes()) {
                    nodeMap.put(node.getId(), node);
                    StepRunEntity stepEntity = existingStepByNodeId.get(node.getId());
                    if (stepEntity == null) {
                        String stepRunId = "step-" + UUID.randomUUID().toString().substring(0, 8);
                        stepEntity = new StepRunEntity(stepRunId, runId, node.getId(), "PENDING");
                        stepEntity.setInputRef(node.getName() != null ? node.getName() : node.getId());
                        stepEntity.setRequiresApproval(node.isRequiresApproval() || "APPROVAL".equalsIgnoreCase(node.getType()));
                        stepRunRepository.save(stepEntity);
                    }
                    stepEntityMap.put(node.getId(), stepEntity);
                }

                Map<String, List<WorkflowEdgeDsl>> incomingEdges = topologyPlanner.buildIncomingEdgeMap(dsl);

                ConcurrentMap<String, StepRunStatus> nodeStatuses = new ConcurrentHashMap<>();
                AtomicBoolean isAborted = new AtomicBoolean(false);
                CountDownLatch completionLatch = new CountDownLatch(dsl.getNodes().size());
                Set<String> alreadyCompletedNodes = new HashSet<>();

                for (WorkflowNodeDsl node : dsl.getNodes()) {
                    StepRunEntity se = stepEntityMap.get(node.getId());
                    if (se != null) {
                        if ("SUCCEEDED".equalsIgnoreCase(se.getStatus())) {
                            nodeStatuses.put(node.getId(), StepRunStatus.SUCCEEDED);
                            completionLatch.countDown();
                            alreadyCompletedNodes.add(node.getId());
                            if (se.getOutputRef() != null) {
                                context.recordNodeOutput(node.getId(), se.getOutputRef());
                            }
                        } else if ("SKIPPED".equalsIgnoreCase(se.getStatus())) {
                            nodeStatuses.put(node.getId(), StepRunStatus.SKIPPED);
                            completionLatch.countDown();
                            alreadyCompletedNodes.add(node.getId());
                        }
                    }
                }

                ConcurrentMap<String, AtomicInteger> inDegrees = topologyPlanner.initializeInDegrees(topology, alreadyCompletedNodes);

                Set<String> scheduledNodes = Collections.newSetFromMap(new ConcurrentHashMap<>());
                @SuppressWarnings("unchecked")
                Consumer<WorkflowNodeDsl>[] taskLauncher = new Consumer[1];
                taskLauncher[0] = (node) -> {
                    if (node == null) return;
                    if (!scheduledNodes.add(node.getId())) {
                        log.debug("Node [{}] already scheduled, skipping duplicate dispatch", node.getId());
                        return;
                    }
                    leaseExecutor.getExecutionPool().submit(() -> {
                        nodeStatuses.put(node.getId(), StepRunStatus.RUNNING);
                        executeNode(runId, node, stepEntityMap.get(node.getId()), stepEntityMap, topology, inDegrees,
                                nodeMap, nodeStatuses, isAborted, completionLatch, context,
                                workspacePath, cancelToken, taskLauncher[0], incomingEdges);
                    });
                };

                List<WorkflowNodeDsl> rootNodes = topologyPlanner.findInitialReadyRootNodes(
                        topology, alreadyCompletedNodes, inDegrees, nodeMap);
                for (WorkflowNodeDsl rootNode : rootNodes) {
                    taskLauncher[0].accept(rootNode);
                }

                long startWait = System.currentTimeMillis();
                long timeoutMillis = effectiveTimeout * 1000L;
                while (completionLatch.getCount() > 0) {
                    if (cancelToken.isCancelled()) {
                        stateTransitioner.abortRemainingNodes(stepEntityMap, nodeStatuses, completionLatch, cancelToken.getReason());
                        break;
                    }
                    if (isAborted.get()) {
                        cancelToken.cancel("Workflow aborted due to node failure");
                        stateTransitioner.abortRemainingNodes(stepEntityMap, nodeStatuses, completionLatch, "Workflow aborted");
                        break;
                    }
                    if (System.currentTimeMillis() - startWait > timeoutMillis) {
                        break;
                    }
                    completionLatch.await(100, TimeUnit.MILLISECONDS);
                }

                try {
                    completionLatch.await(1, TimeUnit.SECONDS);
                } catch (InterruptedException ignored) {}

                if (isAborted.get() || nodeStatuses.values().stream().anyMatch(s -> s == StepRunStatus.FAILED)) {
                    stateTransitioner.transitionRun(runId, WorkflowRunStatus.FAILED, "scheduler", "One or more nodes failed");
                    eventRecorder.publishRunFailed(runId, "One or more nodes failed");
                    return WorkflowRunStatus.FAILED;
                }

                if (cancelToken.isCancelled()) {
                    WorkflowRunStatus finalStatus = cancelToken.isTimedOut() ? WorkflowRunStatus.TIMED_OUT : WorkflowRunStatus.CANCELLED;
                    try {
                        stateTransitioner.transitionRun(runId, finalStatus, "scheduler", cancelToken.getReason());
                    } catch (Exception ignored) {}
                    return finalStatus;
                }

                if (completionLatch.getCount() > 0) {
                    stateTransitioner.transitionRun(runId, WorkflowRunStatus.TIMED_OUT, "scheduler", "Timed out waiting for DAG nodes");
                    eventRecorder.publishRunTimedOut(runId, effectiveTimeout);
                    return WorkflowRunStatus.TIMED_OUT;
                }

                Map<String, Object> finalOutputs = topologyPlanner.resolveFinalOutputs(dsl.getOutputs(), context);
                try {
                    String contextJson = objectMapper.writeValueAsString(finalOutputs);
                    WorkflowRunEntity succRun = workflowRunRepository.findById(runId).orElse(null);
                    if (succRun != null) {
                        succRun.setContextDataJson(contextJson);
                        workflowRunRepository.save(succRun);
                    }
                } catch (Exception ignored) {}

                stateTransitioner.transitionRun(runId, WorkflowRunStatus.SUCCEEDED, "scheduler", "All DAG steps completed successfully");
                eventRecorder.publishRunCompleted(runId);
                return WorkflowRunStatus.SUCCEEDED;

            } catch (Exception e) {
                log.error("DAG execution failure for run [{}]: {}", runId, e.getMessage(), e);
                try {
                    stateTransitioner.transitionRun(runId, WorkflowRunStatus.FAILED, "scheduler", e.getMessage());
                } catch (Exception ignored) {}
                return WorkflowRunStatus.FAILED;
            } finally {
                timeoutWatchdog.cancel(false);
                cancelTokenRegistry.remove(runId);
                stateTransitioner.cleanupRun(runId);
                eventRecorder.evictRun(runId);
                if (lockAcquired && workspacePath != null) {
                    leaseExecutor.releaseWorkspaceLock(workspacePath, runId);
                }
                leaseExecutor.releaseRunLease(runId, INSTANCE_WORKER_ID);
            }
        }, leaseExecutor.getExecutionPool());
    }

    public CompletableFuture<WorkflowRunStatus> resumeRun(String runId) {
        WorkflowRunEntity run = workflowRunRepository.findById(runId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RUN_NOT_FOUND, "Run not found: " + runId));
        if (run.getDslSnapshot() == null || run.getDslSnapshot().isBlank()) {
            throw new BusinessException(ErrorCode.WORKFLOW_INVALID, "No persistent DSL snapshot for run: " + runId);
        }
        WorkflowDsl dsl;
        try {
            dsl = objectMapper.readValue(run.getDslSnapshot(), WorkflowDsl.class);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.WORKFLOW_INVALID, "Failed to parse DSL snapshot: " + e.getMessage());
        }
        long timeout = dsl.getTimeoutSeconds() != null && dsl.getTimeoutSeconds() > 0 ? dsl.getTimeoutSeconds() : 120;
        return executeDag(runId, dsl, null, Collections.emptyMap(), timeout, null);
    }

    private void executeNode(String runId,
                             WorkflowNodeDsl node,
                             StepRunEntity stepEntity,
                             Map<String, StepRunEntity> stepEntityMap,
                             TopologicalSortResult topology,
                             ConcurrentMap<String, AtomicInteger> inDegrees,
                             Map<String, WorkflowNodeDsl> nodeMap,
                             ConcurrentMap<String, StepRunStatus> nodeStatuses,
                             AtomicBoolean isAborted,
                             CountDownLatch completionLatch,
                             WorkflowExecutionContext context,
                             String workspacePath,
                             CancelToken cancelToken,
                             Consumer<WorkflowNodeDsl> launcher,
                             Map<String, List<WorkflowEdgeDsl>> incomingEdges) {
        String stepRunId = stepEntity.getId();
        try {
            if (cancelToken.isCancelled() || isAborted.get()) {
                stateTransitioner.markStepCancelled(stepRunId, cancelToken.getReason());
                nodeStatuses.put(node.getId(), StepRunStatus.CANCELLED);
                completionLatch.countDown();
                return;
            }

            DagTopologyPlanner.JoinEvaluation joinEval = topologyPlanner.evaluateNodeReadiness(
                    node, topology, incomingEdges, nodeStatuses, context, cancelToken, isAborted);

            if (joinEval.shouldSkip()) {
                stateTransitioner.markStepSkipped(stepEntity, node, joinEval.skipReason(), context);
                nodeStatuses.put(node.getId(), StepRunStatus.SKIPPED);
                completionLatch.countDown();
                topologyPlanner.triggerSuccessors(node.getId(), topology, inDegrees, nodeMap, launcher);
                return;
            }

            stateTransitioner.checkPauseWait(runId, cancelToken);
            if (cancelToken.isCancelled() || isAborted.get()) {
                stateTransitioner.markStepCancelled(stepRunId, cancelToken.getReason());
                nodeStatuses.put(node.getId(), StepRunStatus.CANCELLED);
                completionLatch.countDown();
                return;
            }

            if (node.isRequiresApproval() || "APPROVAL".equalsIgnoreCase(node.getType())) {
                boolean approved = stateTransitioner.handleNodeApproval(runId, stepEntity, node, cancelToken, context);
                if (cancelToken.isCancelled()) {
                    StepRunStatus finalStatus = cancelToken.isTimedOut() ? StepRunStatus.TIMED_OUT : StepRunStatus.CANCELLED;
                    nodeStatuses.put(node.getId(), finalStatus);
                    try {
                        stateTransitioner.transitionStep(stepEntity.getId(), finalStatus, "scheduler", cancelToken.getReason());
                    } catch (Exception ignored) {}
                    completionLatch.countDown();
                    return;
                }
                if (!approved) {
                    isAborted.set(true);
                    nodeStatuses.put(node.getId(), StepRunStatus.FAILED);
                    cancelToken.cancel("Approval rejected for node [" + node.getId() + "]");
                    completionLatch.countDown();
                    stateTransitioner.abortRemainingNodes(stepEntityMap, nodeStatuses, completionLatch, "Approval rejected");
                    return;
                }
                if ("APPROVAL".equalsIgnoreCase(node.getType()) || node.getAgentPlatform() == null) {
                    stateTransitioner.markStepSucceeded(stepEntity, node, "Approval granted", context);
                    nodeStatuses.put(node.getId(), StepRunStatus.SUCCEEDED);
                    completionLatch.countDown();
                    topologyPlanner.triggerSuccessors(node.getId(), topology, inDegrees, nodeMap, launcher);
                    return;
                }
            }

            if ("START".equalsIgnoreCase(node.getType())) {
                stateTransitioner.transitionStep(stepEntity.getId(), StepRunStatus.RUNNING, "scheduler", "Starting START node");
                stateTransitioner.markStepSucceeded(stepEntity, node, "Task Initiated", context);
                nodeStatuses.put(node.getId(), StepRunStatus.SUCCEEDED);
                completionLatch.countDown();
                topologyPlanner.triggerSuccessors(node.getId(), topology, inDegrees, nodeMap, launcher);
                return;
            }
            if ("END".equalsIgnoreCase(node.getType())) {
                stateTransitioner.transitionStep(stepEntity.getId(), StepRunStatus.RUNNING, "scheduler", "Starting END node");
                stateTransitioner.markStepSucceeded(stepEntity, node, "Workflow Finalized", context);
                nodeStatuses.put(node.getId(), StepRunStatus.SUCCEEDED);
                completionLatch.countDown();
                topologyPlanner.triggerSuccessors(node.getId(), topology, inDegrees, nodeMap, launcher);
                return;
            }

            boolean success = agentInvoker.executeAgentNodeWithRetry(
                    runId, INSTANCE_WORKER_ID, node, stepEntity, workspacePath, context, cancelToken);
            if (success) {
                nodeStatuses.put(node.getId(), StepRunStatus.SUCCEEDED);
                completionLatch.countDown();
                topologyPlanner.triggerSuccessors(node.getId(), topology, inDegrees, nodeMap, launcher);
            } else {
                if (cancelToken.isCancelled()) {
                    StepRunStatus finalStatus = cancelToken.isTimedOut() ? StepRunStatus.TIMED_OUT : StepRunStatus.CANCELLED;
                    nodeStatuses.put(node.getId(), finalStatus);
                    completionLatch.countDown();
                    return;
                }
                isAborted.set(true);
                nodeStatuses.put(node.getId(), StepRunStatus.FAILED);
                cancelToken.cancel("Predecessor node [" + node.getId() + "] failed");
                completionLatch.countDown();
                stateTransitioner.abortRemainingNodes(stepEntityMap, nodeStatuses, completionLatch, "Predecessor node failed");
            }

        } catch (Exception e) {
            log.error("Execution error for node [{}]: {}", node.getId(), e.getMessage(), e);
            if (cancelToken.isCancelled()) {
                StepRunStatus finalStatus = cancelToken.isTimedOut() ? StepRunStatus.TIMED_OUT : StepRunStatus.CANCELLED;
                nodeStatuses.put(node.getId(), finalStatus);
                completionLatch.countDown();
                return;
            }
            isAborted.set(true);
            nodeStatuses.put(node.getId(), StepRunStatus.FAILED);
            cancelToken.cancel("Node [" + node.getId() + "] error: " + e.getMessage());
            completionLatch.countDown();
            stateTransitioner.abortRemainingNodes(stepEntityMap, nodeStatuses, completionLatch, e.getMessage());
        }
    }

    @PreDestroy
    public void shutdown() {
        leaseExecutor.shutdown();
    }
}
