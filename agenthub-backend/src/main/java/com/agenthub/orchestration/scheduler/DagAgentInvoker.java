package com.agenthub.orchestration.scheduler;

import com.agenthub.domain.agent.model.AgentExecutionRequest;
import com.agenthub.domain.agent.model.AgentPlatformType;
import com.agenthub.execution.domain.model.CancelToken;
import com.agenthub.execution.domain.model.StepRunStatus;
import com.agenthub.execution.infrastructure.entity.StepRunEntity;
import com.agenthub.orchestration.domain.dsl.WorkflowNodeDsl;
import com.agenthub.orchestration.domain.evaluation.SafeExpressionEvaluator;
import com.agenthub.orchestration.domain.evaluation.WorkflowExecutionContext;
import com.agenthub.runtime.AgentRuntimeRegistry;
import com.agenthub.runtime.port.AgentRuntime;
import com.agenthub.runtime.port.RuntimeEventSink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Internal DAG seam responsible for resolving step inputs & prompt templates, invoking
 * AgentRuntime via AgentRuntimeRegistry, enforcing step timeouts, and executing retry backoff.
 */
@Component
public class DagAgentInvoker {

    private static final Logger log = LoggerFactory.getLogger(DagAgentInvoker.class);

    private final AgentRuntimeRegistry runtimeRegistry;
    private final DagLeaseExecutor leaseExecutor;
    private final DagStateTransitioner stateTransitioner;
    private final DagEventRecorder eventRecorder;
    private final com.agenthub.orchestration.application.OrchestratorPlanApplication planApplication;
    private final com.agenthub.agent.application.BudgetGovernanceGuard budgetGovernanceGuard;
    private final SafeExpressionEvaluator evaluator = new SafeExpressionEvaluator();

    public DagAgentInvoker(AgentRuntimeRegistry runtimeRegistry,
                           DagLeaseExecutor leaseExecutor,
                           DagStateTransitioner stateTransitioner,
                           DagEventRecorder eventRecorder) {
        this(runtimeRegistry, leaseExecutor, stateTransitioner, eventRecorder, null, null);
    }

    public DagAgentInvoker(AgentRuntimeRegistry runtimeRegistry,
                           DagLeaseExecutor leaseExecutor,
                           DagStateTransitioner stateTransitioner,
                           DagEventRecorder eventRecorder,
                           com.agenthub.orchestration.application.OrchestratorPlanApplication planApplication) {
        this(runtimeRegistry, leaseExecutor, stateTransitioner, eventRecorder, planApplication, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public DagAgentInvoker(AgentRuntimeRegistry runtimeRegistry,
                           DagLeaseExecutor leaseExecutor,
                           DagStateTransitioner stateTransitioner,
                           DagEventRecorder eventRecorder,
                           @org.springframework.beans.factory.annotation.Autowired(required = false) com.agenthub.orchestration.application.OrchestratorPlanApplication planApplication,
                           @org.springframework.beans.factory.annotation.Autowired(required = false) com.agenthub.agent.application.BudgetGovernanceGuard budgetGovernanceGuard) {
        this.runtimeRegistry = runtimeRegistry;
        this.leaseExecutor = leaseExecutor;
        this.stateTransitioner = stateTransitioner;
        this.eventRecorder = eventRecorder;
        this.planApplication = planApplication;
        this.budgetGovernanceGuard = budgetGovernanceGuard;
    }

    public boolean executeAgentNodeWithRetry(String runId,
                                             String workerId,
                                             WorkflowNodeDsl node,
                                             StepRunEntity stepEntity,
                                             String workspacePath,
                                             WorkflowExecutionContext context,
                                             CancelToken cancelToken) throws InterruptedException {
        int maxRetries = node.getRetryPolicy() != null ? node.getRetryPolicy().getMaxRetries() : 0;
        long backoffMs = node.getRetryPolicy() != null ? node.getRetryPolicy().getBackoffMs() : 500L;
        int maxAttempts = Math.max(1, maxRetries + 1);
        int attempts = 0;

        Map<String, Object> resolvedInputs = evaluator.resolveInputs(node.getInputs(), context);
        context.recordNodeStarted(node.getId(), resolvedInputs);

        // Guard: Enforce high-impact tool authorization against confirmed Orchestrator plan
        if (node.getRequiredTools() != null && !node.getRequiredTools().isEmpty() && planApplication != null) {
            String planId = resolvedInputs != null && resolvedInputs.get("planId") != null
                    ? String.valueOf(resolvedInputs.get("planId"))
                    : (context.getInputs() != null && context.getInputs().get("planId") != null
                            ? String.valueOf(context.getInputs().get("planId")) : null);
            for (String toolName : node.getRequiredTools()) {
                try {
                    planApplication.enforceHighImpactToolAuthorization(planId, toolName);
                } catch (Exception e) {
                    log.warn("High-impact tool [{}] authorization failed for node [{}]: {}", toolName, node.getId(), e.getMessage());
                    com.agenthub.execution.domain.retry.ExecutionErrorClassifier.ClassificationResult cl =
                            com.agenthub.execution.domain.retry.ExecutionErrorClassifier.classify(e);
                    stateTransitioner.transitionStep(stepEntity.getId(), StepRunStatus.FAILED, "scheduler",
                            "Tool authorization blocked (" + cl.getCategory() + "): " + e.getMessage());
                    context.recordNodeFailed(node.getId(), e.getMessage());
                    return false;
                }
            }
        }

        Object promptObj = evaluator.resolveTemplate(node.getPromptTemplate(), context);
        String prompt = promptObj != null ? String.valueOf(promptObj) : "";

        leaseExecutor.claimStepLease(stepEntity, workerId, null, resolvedInputs);

        while (attempts < maxAttempts) {
            attempts++;
            if (cancelToken.isCancelled()) {
                stateTransitioner.markStepCancelled(stepEntity.getId(), cancelToken.getReason());
                return false;
            }

            if (budgetGovernanceGuard != null) {
                try {
                    budgetGovernanceGuard.enforceBudget(runId, stepEntity.getRunId(), 0.05, 1000);
                } catch (Exception e) {
                    log.warn("Budget limit exceeded for step [{}]: {}", stepEntity.getId(), e.getMessage());
                    stateTransitioner.transitionStep(stepEntity.getId(), StepRunStatus.FAILED, "scheduler", e.getMessage());
                    context.recordNodeFailed(node.getId(), e.getMessage());
                    return false;
                }
            }

            leaseExecutor.claimStepLease(stepEntity, workerId, attempts, null);

            AgentPlatformType platformType = AgentPlatformType.fromString(node.getAgentPlatform());
            String runtimeType = node.getAgentPlatform() != null ? node.getAgentPlatform() : "SPRING_AI_API";
            AgentRuntime runtime = runtimeRegistry.getRuntime(runtimeType);
            boolean simulatedRuntime = runtime != null && runtime.describe() != null && runtime.describe().isSimulated();

            stateTransitioner.transitionStep(stepEntity.getId(), StepRunStatus.RUNNING, "scheduler", "Attempt " + attempts);
            eventRecorder.publishStepStarted(runId, stepEntity.getId(), node.getId(), attempts, runtimeType, simulatedRuntime);

            AgentExecutionRequest req = new AgentExecutionRequest(
                    node.getId(),
                    platformType,
                    workspacePath,
                    prompt
            );
            req.setRunId(runId);
            req.setStepRunId(stepEntity.getId());

            CompletableFuture<String> stepFuture = new CompletableFuture<>();
            RuntimeEventSink sink = eventRecorder.createStepSink(runId, stepFuture);

            runtime.start(req, sink, cancelToken);

            try {
                long stepTimeout = node.getTimeoutSeconds() != null && node.getTimeoutSeconds() > 0 ? node.getTimeoutSeconds() : 60L;
                String result = stepFuture.get(stepTimeout, TimeUnit.SECONDS);

                context.recordNodeOutput(node.getId(), result);

                stateTransitioner.markStepSucceeded(stepEntity, node, result, context);
                eventRecorder.publishStepCompleted(runId, stepEntity.getId(), node.getId(), runtimeType, simulatedRuntime);
                return true;

            } catch (Exception e) {
                log.warn("Step [{}] node [{}] attempt [{}/{}] failed: {}", stepEntity.getId(), node.getId(), attempts, maxAttempts, e.getMessage());
                if (cancelToken.isCancelled()) {
                    stateTransitioner.markStepCancelled(stepEntity.getId(), cancelToken.getReason());
                    return false;
                }

                com.agenthub.execution.domain.retry.ExecutionErrorClassifier.ClassificationResult cl =
                        com.agenthub.execution.domain.retry.ExecutionErrorClassifier.classify(e);

                stateTransitioner.transitionStep(stepEntity.getId(), StepRunStatus.FAILED, "scheduler", e.getMessage());

                if (cl.isCanAutoRetry() && attempts < maxAttempts) {
                    eventRecorder.publishStepRetrying(runId, stepEntity.getId(), attempts + 1);
                    stateTransitioner.transitionStep(stepEntity.getId(), StepRunStatus.PENDING, "scheduler",
                            "Retrying after failure (" + cl.getCategory() + ")");
                    Thread.sleep(backoffMs);
                } else {
                    context.recordNodeFailed(node.getId(), e.getMessage());
                    break;
                }
            }
        }
        return false;
    }
}
