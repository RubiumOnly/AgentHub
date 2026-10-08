package com.agenthub.execution.scheduler;

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
import com.agenthub.runtime.AgentRuntimeRegistry;
import com.agenthub.runtime.port.AgentRuntime;
import com.agenthub.runtime.port.ExecutionHandle;
import com.agenthub.runtime.port.RuntimeEventSink;
import com.agenthub.shared.exception.BusinessException;
import com.agenthub.shared.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

/**
 * Decoupled Execution Scheduler.
 * Coordinates workflow state transitions, step sequencing, retries, timeouts, and cancellations.
 * Delegates concrete execution details strictly to AgentRuntime implementations.
 */
@Component
public class ExecutionScheduler {

    private static final Logger log = LoggerFactory.getLogger(ExecutionScheduler.class);

    private final ExecutionStateMachine stateMachine;
    private final CancelTokenRegistry cancelTokenRegistry;
    private final AgentRuntimeRegistry runtimeRegistry;
    private final RunEventBroadcaster broadcaster;
    private final StepRunRepository stepRunRepository;
    private final WorkflowRunRepository workflowRunRepository;
    private final WorkspaceLockManager lockManager;

    private final ScheduledExecutorService watchdogScheduler = Executors.newScheduledThreadPool(2);
    private final ExecutorService executionPool = Executors.newCachedThreadPool();

    public ExecutionScheduler(ExecutionStateMachine stateMachine,
                              CancelTokenRegistry cancelTokenRegistry,
                              AgentRuntimeRegistry runtimeRegistry,
                              RunEventBroadcaster broadcaster,
                              StepRunRepository stepRunRepository,
                              WorkflowRunRepository workflowRunRepository,
                              WorkspaceLockManager lockManager) {
        this.stateMachine = stateMachine;
        this.cancelTokenRegistry = cancelTokenRegistry;
        this.runtimeRegistry = runtimeRegistry;
        this.broadcaster = broadcaster;
        this.stepRunRepository = stepRunRepository;
        this.workflowRunRepository = workflowRunRepository;
        this.lockManager = lockManager;
    }

    public static class ScheduledTaskNode {
        public String nodeId;
        public String label;
        public String runtimeType;
        public String prompt;
        public int maxRetries = 2;

        public ScheduledTaskNode(String nodeId, String label, String runtimeType, String prompt) {
            this.nodeId = nodeId;
            this.label = label;
            this.runtimeType = runtimeType;
            this.prompt = prompt;
        }

        public ScheduledTaskNode(String nodeId, String label, String runtimeType, String prompt, int maxRetries) {
            this.nodeId = nodeId;
            this.label = label;
            this.runtimeType = runtimeType;
            this.prompt = prompt;
            this.maxRetries = maxRetries;
        }
    }

    /**
     * Asynchronously execute a sequence of task nodes with strict state machine progression,
     * timeout watchdog, retry governance, and cooperative cancellation.
     */
    public CompletableFuture<WorkflowRunStatus> scheduleRun(String runId,
                                                             List<ScheduledTaskNode> nodes,
                                                             String workspacePath,
                                                             long timeoutSeconds) {
        CancelToken cancelToken = cancelTokenRegistry.getOrCreate(runId);
        long effectiveTimeout = timeoutSeconds > 0 ? timeoutSeconds : 120;

        // Schedule timeout watchdog
        ScheduledFuture<?> timeoutWatchdog = watchdogScheduler.schedule(() -> {
            log.warn("Watchdog timeout triggered for run [{}] after {} seconds", runId, effectiveTimeout);
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
                // Ensure run is transitioned to RUNNING
                stateMachine.transitionRun(runId, WorkflowRunStatus.RUNNING, "scheduler", "Workflow execution started", null);

                String previousOutput = "";
                for (ScheduledTaskNode node : nodes) {
                    if (cancelToken.isCancelled()) {
                        break;
                    }

                    // Check pause
                    WorkflowRunEntity currentRun = workflowRunRepository.findById(runId).orElse(null);
                    if (currentRun != null && "PAUSED".equalsIgnoreCase(currentRun.getStatus())) {
                        log.info("Run [{}] is paused, waiting for resume signal...", runId);
                        while (currentRun != null && "PAUSED".equalsIgnoreCase(currentRun.getStatus())) {
                            if (cancelToken.isCancelled()) break;
                            Thread.sleep(500);
                            currentRun = workflowRunRepository.findById(runId).orElse(null);
                        }
                    }

                    if (cancelToken.isCancelled()) {
                        break;
                    }

                    // Execute Step with retry governance
                    String stepOutput = executeStepWithRetry(runId, node, workspacePath, previousOutput, cancelToken);
                    if (stepOutput == null && !cancelToken.isCancelled()) {
                        // Step failed and retries exhausted
                        stateMachine.transitionRun(runId, WorkflowRunStatus.FAILED, "scheduler", "Node " + node.nodeId + " failed", null);
                        broadcaster.publishEvent(runId, "RUN_FAILED", "{\"failedNode\":\"" + node.nodeId + "\"}");
                        return WorkflowRunStatus.FAILED;
                    }
                    previousOutput = stepOutput != null ? stepOutput : "";
                }

                if (cancelToken.isCancelled()) {
                    WorkflowRunStatus finalStatus = cancelToken.isTimedOut() ? WorkflowRunStatus.TIMED_OUT : WorkflowRunStatus.CANCELLED;
                    try {
                        stateMachine.transitionRun(runId, finalStatus, "scheduler", cancelToken.getReason(), null);
                    } catch (Exception ignored) {}
                    return finalStatus;
                }

                // All nodes succeeded
                stateMachine.transitionRun(runId, WorkflowRunStatus.SUCCEEDED, "scheduler", "All steps completed successfully", null);
                broadcaster.publishEvent(runId, "RUN_COMPLETED", "{\"status\":\"SUCCEEDED\"}");
                return WorkflowRunStatus.SUCCEEDED;

            } catch (Exception e) {
                log.error("Execution error for run [{}]: {}", runId, e.getMessage(), e);
                try {
                    stateMachine.transitionRun(runId, WorkflowRunStatus.FAILED, "scheduler", e.getMessage(), null);
                } catch (Exception ignored) {}
                return WorkflowRunStatus.FAILED;
            } finally {
                timeoutWatchdog.cancel(false);
                cancelTokenRegistry.remove(runId);
                if (workspacePath != null) {
                    lockManager.unlock(workspacePath);
                }
            }
        }, executionPool);
    }

    private String executeStepWithRetry(String runId,
                                        ScheduledTaskNode node,
                                        String workspacePath,
                                        String upstreamOutput,
                                        CancelToken cancelToken) throws InterruptedException {
        String stepRunId = "step-" + UUID.randomUUID().toString().substring(0, 8);
        StepRunEntity stepEntity = new StepRunEntity(stepRunId, runId, node.nodeId, "PENDING");
        stepEntity.setInputRef(node.label);
        stepRunRepository.save(stepEntity);

        int attempts = 0;
        int maxAttempts = Math.max(1, node.maxRetries + 1);

        while (attempts < maxAttempts) {
            attempts++;
            if (cancelToken.isCancelled()) {
                stateMachine.transitionStep(stepRunId,
                        cancelToken.isTimedOut() ? StepRunStatus.TIMED_OUT : StepRunStatus.CANCELLED,
                        "scheduler", cancelToken.getReason(), null);
                return null;
            }

            stateMachine.transitionStep(stepRunId, StepRunStatus.RUNNING, "scheduler", "Attempt " + attempts, null);
            broadcaster.publishEvent(runId, "STEP_STARTED", "{\"stepRunId\":\"" + stepRunId + "\",\"nodeId\":\"" + node.nodeId + "\",\"attempt\":" + attempts + "}");

            AgentRuntime runtime = runtimeRegistry.getRuntime(node.runtimeType);
            String fullPrompt = (node.prompt != null ? node.prompt : "") +
                    (upstreamOutput != null && !upstreamOutput.isBlank() ? "\n\n[Upstream Context]:\n" + upstreamOutput : "");

            AgentExecutionRequest req = new AgentExecutionRequest(
                    node.nodeId,
                    AgentPlatformType.SPRING_AI_API,
                    workspacePath,
                    fullPrompt
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
                String result = stepFuture.get(60, TimeUnit.SECONDS);
                stateMachine.transitionStep(stepRunId, StepRunStatus.SUCCEEDED, "scheduler", "Completed attempt " + attempts, null);
                broadcaster.publishEvent(runId, "STEP_COMPLETED", "{\"stepRunId\":\"" + stepRunId + "\",\"nodeId\":\"" + node.nodeId + "\"}");
                return result;
            } catch (Exception e) {
                log.warn("Step [{}] node [{}] attempt [{}/{}] failed: {}", stepRunId, node.nodeId, attempts, maxAttempts, e.getMessage());
                if (cancelToken.isCancelled()) {
                    stateMachine.transitionStep(stepRunId,
                            cancelToken.isTimedOut() ? StepRunStatus.TIMED_OUT : StepRunStatus.CANCELLED,
                            "scheduler", cancelToken.getReason(), null);
                    return null;
                }

                stateMachine.transitionStep(stepRunId, StepRunStatus.FAILED, "scheduler", e.getMessage(), null);

                if (attempts < maxAttempts) {
                    broadcaster.publishEvent(runId, "STEP_RETRYING", "{\"stepRunId\":\"" + stepRunId + "\",\"nextAttempt\":" + (attempts + 1) + "}");
                    stateMachine.transitionStep(stepRunId, StepRunStatus.PENDING, "scheduler", "Retrying after failure", null);
                    Thread.sleep(300); // Backoff before retry
                }
            }
        }

        return null;
    }

    private String escapeJson(String raw) {
        if (raw == null) return "";
        return raw.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r");
    }
}
