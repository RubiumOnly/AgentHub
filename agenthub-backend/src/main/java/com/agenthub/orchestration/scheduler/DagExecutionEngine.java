package com.agenthub.orchestration.scheduler;

import com.agenthub.domain.agent.model.AgentExecutionRequest;
import com.agenthub.domain.agent.model.AgentPlatformType;
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
import com.agenthub.orchestration.domain.approval.ApprovalDecision;
import com.agenthub.orchestration.domain.approval.ApprovalDecisionResult;
import com.agenthub.orchestration.domain.approval.ApprovalSignalRegistry;
import com.agenthub.orchestration.domain.dsl.*;
import com.agenthub.orchestration.domain.evaluation.SafeExpressionEvaluator;
import com.agenthub.orchestration.domain.evaluation.WorkflowExecutionContext;
import com.agenthub.orchestration.infrastructure.entity.ApprovalEntity;
import com.agenthub.orchestration.infrastructure.repository.ApprovalRepository;
import com.agenthub.runtime.AgentRuntimeRegistry;
import com.agenthub.runtime.port.AgentRuntime;
import com.agenthub.runtime.port.ExecutionHandle;
import com.agenthub.runtime.port.RuntimeEventSink;
import com.agenthub.shared.context.RequestContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Enterprise DAG Parallel and Topological Execution Engine.
 * Supports:
 * - Dynamic in-degree topological scheduling with sibling node true concurrency
 * - all_succeeded / any_succeeded / custom_condition upstream join policies
 * - Safe data pipeline value passing ({{steps.node.outputs.key}})
 * - Conditional branching and graceful cascade skipping
 * - Human-in-the-loop approval suspension and resumption
 */
@Component
public class DagExecutionEngine {

    private static final Logger log = LoggerFactory.getLogger(DagExecutionEngine.class);

    private final ExecutionStateMachine stateMachine;
    private final CancelTokenRegistry cancelTokenRegistry;
    private final AgentRuntimeRegistry runtimeRegistry;
    private final RunEventBroadcaster broadcaster;
    private final StepRunRepository stepRunRepository;
    private final WorkflowRunRepository workflowRunRepository;
    private final WorkspaceLockManager lockManager;
    private final ApprovalRepository approvalRepository;
    private final ApprovalSignalRegistry approvalSignalRegistry;
    private final ObjectMapper objectMapper;
    private final SafeExpressionEvaluator evaluator = new SafeExpressionEvaluator();

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
        this.stateMachine = stateMachine;
        this.cancelTokenRegistry = cancelTokenRegistry;
        this.runtimeRegistry = runtimeRegistry;
        this.broadcaster = broadcaster;
        this.stepRunRepository = stepRunRepository;
        this.workflowRunRepository = workflowRunRepository;
        this.lockManager = lockManager;
        this.approvalRepository = approvalRepository;
        this.approvalSignalRegistry = approvalSignalRegistry;
        this.objectMapper = objectMapper;
    }

    public CompletableFuture<WorkflowRunStatus> executeDag(String runId,
                                                           WorkflowDsl dsl,
                                                           String workspacePath,
                                                           Map<String, Object> initialInputs,
                                                           long timeoutSeconds) {
        // Validate DSL strictly
        TopologicalSortResult topology = WorkflowDslValidator.validate(dsl);

        CancelToken cancelToken = cancelTokenRegistry.getOrCreate(runId);
        long effectiveTimeout = timeoutSeconds > 0 ? timeoutSeconds : (dsl.getTimeoutSeconds() != null && dsl.getTimeoutSeconds() > 0 ? dsl.getTimeoutSeconds() : 300);

        ScheduledFuture<?> timeoutWatchdog = watchdogScheduler.schedule(() -> {
            log.warn("DAG watchdog timeout triggered for run [{}] after {} seconds", runId, effectiveTimeout);
            cancelToken.timeout("Execution exceeded timeout limit of " + effectiveTimeout + "s");
            try {
                stateMachine.transitionRun(runId, WorkflowRunStatus.TIMED_OUT, "watchdog", "Timeout after " + effectiveTimeout + "s", null);
                broadcaster.publishEvent(runId, "RUN_TIMED_OUT", "{\"timeoutSeconds\":" + effectiveTimeout + "}");
            } catch (Exception e) {
                log.debug("Run [{}] already in terminal state on timeout watchdog: {}", runId, e.getMessage());
            }
        }, effectiveTimeout, TimeUnit.SECONDS);

        return CompletableFuture.supplyAsync(() -> {
            try {
                stateMachine.transitionRun(runId, WorkflowRunStatus.RUNNING, "scheduler", "DAG execution started", null);
                broadcaster.publishEvent(runId, "RUN_STARTED", "{\"runId\":\"" + runId + "\",\"nodeCount\":" + dsl.getNodes().size() + "}");

                WorkflowExecutionContext context = new WorkflowExecutionContext(initialInputs);

                // Build lookup maps
                Map<String, WorkflowNodeDsl> nodeMap = new HashMap<>();
                Map<String, StepRunEntity> stepEntityMap = new HashMap<>();
                for (WorkflowNodeDsl node : dsl.getNodes()) {
                    nodeMap.put(node.getId(), node);
                    String stepRunId = "step-" + UUID.randomUUID().toString().substring(0, 8);
                    StepRunEntity stepEntity = new StepRunEntity(stepRunId, runId, node.getId(), "PENDING");
                    stepEntity.setInputRef(node.getName() != null ? node.getName() : node.getId());
                    stepEntity.setRequiresApproval(node.isRequiresApproval() || "APPROVAL".equalsIgnoreCase(node.getType()));
                    stepRunRepository.save(stepEntity);
                    stepEntityMap.put(node.getId(), stepEntity);
                }

                ConcurrentMap<String, AtomicInteger> inDegrees = new ConcurrentHashMap<>();
                for (String id : topology.getSortedNodeIds()) {
                    inDegrees.put(id, new AtomicInteger(topology.getIncoming().get(id).size()));
                }

                ConcurrentMap<String, StepRunStatus> nodeStatuses = new ConcurrentHashMap<>();
                AtomicBoolean isAborted = new AtomicBoolean(false);
                CountDownLatch completionLatch = new CountDownLatch(dsl.getNodes().size());

                // Task execution routine
                java.util.function.Consumer<WorkflowNodeDsl>[] taskLauncher = new java.util.function.Consumer[1];
                taskLauncher[0] = (node) -> {
                    executionPool.submit(() -> {
                        executeNode(runId, node, stepEntityMap.get(node.getId()), stepEntityMap, topology, inDegrees,
                                nodeMap, nodeStatuses, isAborted, completionLatch, context,
                                workspacePath, cancelToken, taskLauncher[0]);
                    });
                };

                // Trigger all initial ready nodes (in-degree == 0) concurrently
                for (String id : topology.getSortedNodeIds()) {
                    if (inDegrees.get(id).get() == 0) {
                        taskLauncher[0].accept(nodeMap.get(id));
                    }
                }

                // Wait for all nodes to complete, abort, cancel, or timeout
                long startWait = System.currentTimeMillis();
                long timeoutMillis = effectiveTimeout * 1000L;
                while (completionLatch.getCount() > 0) {
                    if (cancelToken.isCancelled()) {
                        abortRemainingNodes(stepEntityMap, nodeStatuses, completionLatch, cancelToken.getReason());
                        break;
                    }
                    if (isAborted.get()) {
                        abortRemainingNodes(stepEntityMap, nodeStatuses, completionLatch, "Workflow aborted");
                        break;
                    }
                    if (System.currentTimeMillis() - startWait > timeoutMillis) {
                        break;
                    }
                    completionLatch.await(100, TimeUnit.MILLISECONDS);
                }

                if (cancelToken.isCancelled()) {
                    WorkflowRunStatus finalStatus = cancelToken.isTimedOut() ? WorkflowRunStatus.TIMED_OUT : WorkflowRunStatus.CANCELLED;
                    try {
                        stateMachine.transitionRun(runId, finalStatus, "scheduler", cancelToken.getReason(), null);
                    } catch (Exception ignored) {}
                    return finalStatus;
                }

                if (completionLatch.getCount() > 0) {
                    stateMachine.transitionRun(runId, WorkflowRunStatus.TIMED_OUT, "scheduler", "Timed out waiting for DAG nodes", null);
                    broadcaster.publishEvent(runId, "RUN_TIMED_OUT", "{\"timeoutSeconds\":" + effectiveTimeout + "}");
                    return WorkflowRunStatus.TIMED_OUT;
                }

                if (isAborted.get() || nodeStatuses.values().stream().anyMatch(s -> s == StepRunStatus.FAILED)) {
                    stateMachine.transitionRun(runId, WorkflowRunStatus.FAILED, "scheduler", "One or more nodes failed", null);
                    broadcaster.publishEvent(runId, "RUN_FAILED", "{\"reason\":\"One or more nodes failed\"}");
                    return WorkflowRunStatus.FAILED;
                }

                // DAG succeeded: resolve final outputs
                Map<String, Object> finalOutputs = evaluator.resolveOutputs(dsl.getOutputs(), context);
                try {
                    String contextJson = objectMapper.writeValueAsString(finalOutputs);
                    WorkflowRunEntity runEntity = workflowRunRepository.findById(runId).orElse(null);
                    if (runEntity != null) {
                        runEntity.setContextDataJson(contextJson);
                        workflowRunRepository.save(runEntity);
                    }
                } catch (Exception ignored) {}

                stateMachine.transitionRun(runId, WorkflowRunStatus.SUCCEEDED, "scheduler", "All DAG steps completed successfully", null);
                broadcaster.publishEvent(runId, "RUN_COMPLETED", "{\"status\":\"SUCCEEDED\"}");
                return WorkflowRunStatus.SUCCEEDED;

            } catch (Exception e) {
                log.error("DAG execution failure for run [{}]: {}", runId, e.getMessage(), e);
                try {
                    stateMachine.transitionRun(runId, WorkflowRunStatus.FAILED, "scheduler", e.getMessage(), null);
                } catch (Exception ignored) {}
                return WorkflowRunStatus.FAILED;
            } finally {
                timeoutWatchdog.cancel(false);
                cancelTokenRegistry.remove(runId);
                stateMachine.cleanupRun(runId);
                broadcaster.evictRun(runId);
                if (workspacePath != null) {
                    lockManager.unlock(workspacePath);
                }
            }
        }, executionPool);
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
                             java.util.function.Consumer<WorkflowNodeDsl> launcher) {
        String stepRunId = stepEntity.getId();
        try {
            if (cancelToken.isCancelled() || isAborted.get()) {
                markStepCancelled(stepRunId, cancelToken.getReason());
                nodeStatuses.put(node.getId(), StepRunStatus.CANCELLED);
                completionLatch.countDown();
                return;
            }

            // Check join policy against upstream predecessors
            Set<String> preds = topology.getIncoming().get(node.getId());
            boolean shouldSkip = false;
            String skipReason = null;

            if (preds != null && !preds.isEmpty()) {
                String joinPolicy = node.getJoinPolicy() != null ? node.getJoinPolicy().trim().toLowerCase() : "all_succeeded";
                if ("any_succeeded".equals(joinPolicy)) {
                    boolean anySucceeded = preds.stream().anyMatch(p -> nodeStatuses.get(p) == StepRunStatus.SUCCEEDED);
                    if (!anySucceeded) {
                        shouldSkip = true;
                        skipReason = "No predecessor succeeded under ANY_SUCCEEDED join policy";
                    }
                } else if ("custom_condition".equals(joinPolicy)) {
                    boolean condPass = evaluator.evaluateCondition(node.getCondition(), context);
                    if (!condPass) {
                        shouldSkip = true;
                        skipReason = "Custom condition not satisfied";
                    }
                } else { // default "all_succeeded"
                    boolean allSucceeded = preds.stream().allMatch(p -> nodeStatuses.get(p) == StepRunStatus.SUCCEEDED);
                    if (!allSucceeded) {
                        boolean anySkipped = preds.stream().anyMatch(p -> nodeStatuses.get(p) == StepRunStatus.SKIPPED);
                        shouldSkip = true;
                        skipReason = anySkipped ? "Predecessor was SKIPPED in ALL_SUCCEEDED dependency" : "Predecessor was not SUCCEEDED";
                    }
                }
            }

            // Check node-level condition expression if not already skipped
            if (!shouldSkip && node.getCondition() != null && !node.getCondition().trim().isEmpty()) {
                boolean condPass = evaluator.evaluateCondition(node.getCondition(), context);
                if (!condPass) {
                    shouldSkip = true;
                    skipReason = "Node condition [" + node.getCondition() + "] evaluated to false";
                }
            }

            // Skip handling
            if (shouldSkip) {
                markStepSkipped(stepEntity, node, skipReason, context);
                nodeStatuses.put(node.getId(), StepRunStatus.SKIPPED);
                completionLatch.countDown();
                triggerSuccessors(node.getId(), topology, inDegrees, nodeMap, launcher);
                return;
            }

            // Pause check
            checkPauseWait(runId, cancelToken);
            if (cancelToken.isCancelled() || isAborted.get()) {
                markStepCancelled(stepRunId, cancelToken.getReason());
                nodeStatuses.put(node.getId(), StepRunStatus.CANCELLED);
                completionLatch.countDown();
                return;
            }

            // Human approval gate
            if (node.isRequiresApproval() || "APPROVAL".equalsIgnoreCase(node.getType())) {
                boolean approved = handleNodeApproval(runId, stepEntity, node, cancelToken);
                if (!approved) {
                    isAborted.set(true);
                    nodeStatuses.put(node.getId(), StepRunStatus.FAILED);
                    completionLatch.countDown();
                    abortRemainingNodes(stepEntityMap, nodeStatuses, completionLatch, "Approval rejected");
                    return;
                }
                if ("APPROVAL".equalsIgnoreCase(node.getType()) || node.getAgentPlatform() == null) {
                    markStepSucceeded(stepEntity, node, "Approval granted", context);
                    nodeStatuses.put(node.getId(), StepRunStatus.SUCCEEDED);
                    completionLatch.countDown();
                    triggerSuccessors(node.getId(), topology, inDegrees, nodeMap, launcher);
                    return;
                }
            }

            // START / END node pass-through
            if ("START".equalsIgnoreCase(node.getType())) {
                stateMachine.transitionStep(stepEntity.getId(), StepRunStatus.RUNNING, "scheduler", "Starting START node", null);
                markStepSucceeded(stepEntity, node, "Task Initiated", context);
                nodeStatuses.put(node.getId(), StepRunStatus.SUCCEEDED);
                completionLatch.countDown();
                triggerSuccessors(node.getId(), topology, inDegrees, nodeMap, launcher);
                return;
            }
            if ("END".equalsIgnoreCase(node.getType())) {
                stateMachine.transitionStep(stepEntity.getId(), StepRunStatus.RUNNING, "scheduler", "Starting END node", null);
                markStepSucceeded(stepEntity, node, "Workflow Finalized", context);
                nodeStatuses.put(node.getId(), StepRunStatus.SUCCEEDED);
                completionLatch.countDown();
                triggerSuccessors(node.getId(), topology, inDegrees, nodeMap, launcher);
                return;
            }

            // AGENT execution with retries
            boolean success = executeAgentNodeWithRetry(runId, node, stepEntity, workspacePath, context, cancelToken);
            if (success) {
                nodeStatuses.put(node.getId(), StepRunStatus.SUCCEEDED);
                completionLatch.countDown();
                triggerSuccessors(node.getId(), topology, inDegrees, nodeMap, launcher);
            } else {
                isAborted.set(true);
                nodeStatuses.put(node.getId(), StepRunStatus.FAILED);
                completionLatch.countDown();
                abortRemainingNodes(stepEntityMap, nodeStatuses, completionLatch, "Predecessor node failed");
            }

        } catch (Exception e) {
            log.error("Execution error for node [{}]: {}", node.getId(), e.getMessage(), e);
            isAborted.set(true);
            nodeStatuses.put(node.getId(), StepRunStatus.FAILED);
            completionLatch.countDown();
            abortRemainingNodes(stepEntityMap, nodeStatuses, completionLatch, e.getMessage());
        }
    }

    private void abortRemainingNodes(Map<String, StepRunEntity> stepEntityMap,
                                     ConcurrentMap<String, StepRunStatus> nodeStatuses,
                                     CountDownLatch completionLatch,
                                     String reason) {
        if (stepEntityMap == null) return;
        for (Map.Entry<String, StepRunEntity> entry : stepEntityMap.entrySet()) {
            String nid = entry.getKey();
            StepRunStatus status = nodeStatuses.get(nid);
            if (status == null || status == StepRunStatus.PENDING) {
                nodeStatuses.put(nid, StepRunStatus.CANCELLED);
                markStepCancelled(entry.getValue().getId(), reason);
                completionLatch.countDown();
            }
        }
    }

    private void triggerSuccessors(String nodeId,
                                   TopologicalSortResult topology,
                                   ConcurrentMap<String, AtomicInteger> inDegrees,
                                   Map<String, WorkflowNodeDsl> nodeMap,
                                   java.util.function.Consumer<WorkflowNodeDsl> launcher) {
        Set<String> successors = topology.getOutgoing().get(nodeId);
        if (successors != null) {
            for (String succId : successors) {
                int rem = inDegrees.get(succId).decrementAndGet();
                if (rem == 0) {
                    launcher.accept(nodeMap.get(succId));
                }
            }
        }
    }

    private boolean executeAgentNodeWithRetry(String runId,
                                              WorkflowNodeDsl node,
                                              StepRunEntity stepEntity,
                                              String workspacePath,
                                              WorkflowExecutionContext context,
                                              CancelToken cancelToken) throws InterruptedException {
        int maxRetries = node.getRetryPolicy() != null ? node.getRetryPolicy().getMaxRetries() : 0;
        long backoffMs = node.getRetryPolicy() != null ? node.getRetryPolicy().getBackoffMs() : 500L;
        int maxAttempts = Math.max(1, maxRetries + 1);
        int attempts = 0;

        // Resolve inputs and prompt via SafeExpressionEvaluator
        Map<String, Object> resolvedInputs = evaluator.resolveInputs(node.getInputs(), context);
        context.recordNodeStarted(node.getId(), resolvedInputs);

        Object promptObj = evaluator.resolveTemplate(node.getPromptTemplate(), context);
        String prompt = promptObj != null ? String.valueOf(promptObj) : "";

        try {
            StepRunEntity current = stepRunRepository.findById(stepEntity.getId()).orElse(stepEntity);
            current.setInputsJson(objectMapper.writeValueAsString(resolvedInputs));
            stepRunRepository.save(current);
        } catch (Exception ignored) {}

        while (attempts < maxAttempts) {
            attempts++;
            if (cancelToken.isCancelled()) {
                markStepCancelled(stepEntity.getId(), cancelToken.getReason());
                return false;
            }

            stateMachine.transitionStep(stepEntity.getId(), StepRunStatus.RUNNING, "scheduler", "Attempt " + attempts, null);
            broadcaster.publishEvent(runId, "STEP_STARTED", "{\"stepRunId\":\"" + stepEntity.getId() + "\",\"nodeId\":\"" + node.getId() + "\",\"attempt\":" + attempts + "}");

            AgentPlatformType platformType = AgentPlatformType.fromString(node.getAgentPlatform());
            String runtimeType = node.getAgentPlatform() != null ? node.getAgentPlatform() : "SPRING_AI_API";
            AgentRuntime runtime = runtimeRegistry.getRuntime(runtimeType);

            AgentExecutionRequest req = new AgentExecutionRequest(
                    node.getId(),
                    platformType,
                    workspacePath,
                    prompt
            );

            CompletableFuture<String> stepFuture = new CompletableFuture<>();
            RuntimeEventSink sink = new RuntimeEventSink() {
                @Override
                public void onToken(String rId, String sId, String token) {
                    broadcaster.publishEvent(runId, "TOKEN", "{\"token\":\"" + escapeJson(token) + "\"}");
                }

                @Override
                public void onMessage(String rId, String sId, String sender, String content) {
                    broadcaster.publishEvent(runId, "MESSAGE", "{\"sender\":\"" + sender + "\",\"content\":\"" + escapeJson(content) + "\"}");
                }

                @Override
                public void onToolCall(String rId, String sId, String toolName, String inputJson) {
                    broadcaster.publishEvent(runId, "TOOL_CALL", "{\"tool\":\"" + toolName + "\",\"input\":" + inputJson + "}");
                }

                @Override
                public void onFileChange(String rId, String sId, String path, String changeType) {
                    broadcaster.publishEvent(runId, "FILE_CHANGE", "{\"path\":\"" + path + "\",\"changeType\":\"" + changeType + "\"}");
                }

                @Override
                public void onLog(String rId, String sId, String level, String message) {
                    broadcaster.publishEvent(runId, "LOG", "{\"level\":\"" + level + "\",\"message\":\"" + escapeJson(message) + "\"}");
                }

                @Override
                public void onUsage(String rId, String sId, int promptTokens, int completionTokens, double costEstimate) {
                    broadcaster.publishEvent(runId, "USAGE", "{\"promptTokens\":" + promptTokens + ",\"completionTokens\":" + completionTokens + ",\"cost\":" + costEstimate + "}");
                }

                @Override
                public void onStepCompleted(String rId, String sId, String output) {
                    stepFuture.complete(output);
                }

                @Override
                public void onStepFailed(String rId, String sId, String error, Throwable cause) {
                    stepFuture.completeExceptionally(new RuntimeException(error != null ? error : "Step failed"));
                }
            };

            ExecutionHandle handle = runtime.start(req, sink, cancelToken);

            try {
                long stepTimeout = node.getTimeoutSeconds() != null && node.getTimeoutSeconds() > 0 ? node.getTimeoutSeconds() : 60L;
                String result = stepFuture.get(stepTimeout, TimeUnit.SECONDS);

                context.recordNodeOutput(node.getId(), result);
                markStepSucceeded(stepEntity, node, result, context);
                broadcaster.publishEvent(runId, "STEP_COMPLETED", "{\"stepRunId\":\"" + stepEntity.getId() + "\",\"nodeId\":\"" + node.getId() + "\"}");
                return true;

            } catch (Exception e) {
                log.warn("Step [{}] node [{}] attempt [{}/{}] failed: {}", stepEntity.getId(), node.getId(), attempts, maxAttempts, e.getMessage());
                if (cancelToken.isCancelled()) {
                    markStepCancelled(stepEntity.getId(), cancelToken.getReason());
                    return false;
                }

                stateMachine.transitionStep(stepEntity.getId(), StepRunStatus.FAILED, "scheduler", e.getMessage(), null);

                if (attempts < maxAttempts) {
                    broadcaster.publishEvent(runId, "STEP_RETRYING", "{\"stepRunId\":\"" + stepEntity.getId() + "\",\"nextAttempt\":" + (attempts + 1) + "}");
                    stateMachine.transitionStep(stepEntity.getId(), StepRunStatus.PENDING, "scheduler", "Retrying after failure", null);
                    Thread.sleep(backoffMs);
                } else {
                    context.recordNodeFailed(node.getId(), e.getMessage());
                }
            }
        }
        return false;
    }

    private boolean handleNodeApproval(String runId,
                                        StepRunEntity stepEntity,
                                        WorkflowNodeDsl node,
                                        CancelToken cancelToken) {
        String approvalId = "appr-" + UUID.randomUUID().toString().substring(0, 8);
        ApprovalEntity approval = new ApprovalEntity(
                approvalId, runId, stepEntity.getId(), "PENDING", RequestContext.get().getUserId()
        );
        approvalRepository.save(approval);

        stateMachine.transitionStep(stepEntity.getId(), StepRunStatus.WAITING_APPROVAL, "scheduler", "Waiting for approval", null);
        stateMachine.transitionRun(runId, WorkflowRunStatus.WAITING_APPROVAL, "scheduler", "Waiting for approval on node " + node.getId(), null);

        Map<String, Object> eventData = Map.of(
                "approvalId", approvalId,
                "runId", runId,
                "stepRunId", stepEntity.getId(),
                "nodeId", node.getId(),
                "nodeName", node.getName() != null ? node.getName() : node.getId()
        );
        try {
            broadcaster.publishEvent(runId, "APPROVAL_REQUESTED", objectMapper.writeValueAsString(eventData));
        } catch (Exception ignored) {}

        CompletableFuture<ApprovalDecisionResult> signal = approvalSignalRegistry.register(approvalId);

        try {
            while (!signal.isDone()) {
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
                    // Check if DB state was updated asynchronously
                    ApprovalEntity current = approvalRepository.findById(approvalId).orElse(null);
                    if (current != null && !"PENDING".equalsIgnoreCase(current.getStatus())) {
                        if ("APPROVED".equalsIgnoreCase(current.getStatus())) {
                            signal.complete(new ApprovalDecisionResult(ApprovalDecision.APPROVED, current.getDecisionReason(), current.getReviewedBy()));
                        } else {
                            signal.complete(new ApprovalDecisionResult(ApprovalDecision.REJECTED, current.getDecisionReason(), current.getReviewedBy()));
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Approval wait error for node " + node.getId(), e);
            approvalSignalRegistry.remove(approvalId);
            return false;
        }
        return false;
    }

    private void markStepSucceeded(StepRunEntity stepEntity, WorkflowNodeDsl node, String output, WorkflowExecutionContext context) {
        stateMachine.transitionStep(stepEntity.getId(), StepRunStatus.SUCCEEDED, "scheduler", "Completed successfully", null);
        try {
            StepRunEntity current = stepRunRepository.findById(stepEntity.getId()).orElse(stepEntity);
            current.setOutputRef(output);
            current.setOutputsJson(objectMapper.writeValueAsString(context.getNodeState(node.getId()).getOutputs()));
            stepRunRepository.save(current);
        } catch (Exception ignored) {}
    }

    private void markStepSkipped(StepRunEntity stepEntity, WorkflowNodeDsl node, String reason, WorkflowExecutionContext context) {
        context.recordNodeSkipped(node.getId(), reason);
        stateMachine.transitionStep(stepEntity.getId(), StepRunStatus.SKIPPED, "scheduler", reason, null);
        try {
            StepRunEntity current = stepRunRepository.findById(stepEntity.getId()).orElse(stepEntity);
            current.setOutputsJson(objectMapper.writeValueAsString(context.getNodeState(node.getId()).getOutputs()));
            stepRunRepository.save(current);
        } catch (Exception ignored) {}
        broadcaster.publishEvent(stepEntity.getRunId(), "STEP_SKIPPED",
                "{\"stepRunId\":\"" + stepEntity.getId() + "\",\"nodeId\":\"" + node.getId() + "\",\"reason\":\"" + escapeJson(reason) + "\"}");
    }

    private void markStepCancelled(String stepRunId, String reason) {
        try {
            stateMachine.transitionStep(stepRunId, StepRunStatus.CANCELLED, "scheduler", reason, null);
        } catch (Exception ignored) {}
    }

    private void checkPauseWait(String runId, CancelToken cancelToken) throws InterruptedException {
        WorkflowRunEntity currentRun = workflowRunRepository.findById(runId).orElse(null);
        while (currentRun != null && "PAUSED".equalsIgnoreCase(currentRun.getStatus())) {
            if (cancelToken.isCancelled()) break;
            Thread.sleep(500);
            currentRun = workflowRunRepository.findById(runId).orElse(null);
        }
    }

    private String escapeJson(String raw) {
        if (raw == null) return "";
        return raw.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    @jakarta.annotation.PreDestroy
    public void shutdown() {
        log.info("Shutting down DagExecutionEngine thread pools...");
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
