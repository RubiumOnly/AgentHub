package com.agenthub.orchestration.scheduler;

import com.agenthub.execution.domain.model.CancelToken;
import com.agenthub.execution.domain.model.StepRunStatus;
import com.agenthub.orchestration.domain.dsl.TopologicalSortResult;
import com.agenthub.orchestration.domain.dsl.WorkflowDsl;
import com.agenthub.orchestration.domain.dsl.WorkflowDslValidator;
import com.agenthub.orchestration.domain.dsl.WorkflowEdgeDsl;
import com.agenthub.orchestration.domain.dsl.WorkflowNodeDsl;
import com.agenthub.orchestration.domain.evaluation.SafeExpressionEvaluator;
import com.agenthub.orchestration.domain.evaluation.WorkflowExecutionContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Internal DAG seam responsible for topological validation, in-degree dependency tracking,
 * upstream join policy evaluation (all_succeeded, any_succeeded, custom_condition),
 * edge/node condition evaluation, and successor triggering.
 */
@Component
public class DagTopologyPlanner {

    private final SafeExpressionEvaluator evaluator = new SafeExpressionEvaluator();

    public record JoinEvaluation(boolean shouldSkip, String skipReason) {}

    public TopologicalSortResult validateAndPlan(WorkflowDsl dsl) {
        return WorkflowDslValidator.validate(dsl);
    }

    public Map<String, List<WorkflowEdgeDsl>> buildIncomingEdgeMap(WorkflowDsl dsl) {
        Map<String, List<WorkflowEdgeDsl>> incomingEdges = new HashMap<>();
        if (dsl.getEdges() != null) {
            for (WorkflowEdgeDsl edge : dsl.getEdges()) {
                if (edge.getTarget() != null) {
                    incomingEdges.computeIfAbsent(edge.getTarget().trim(), k -> new ArrayList<>()).add(edge);
                }
            }
        }
        return incomingEdges;
    }

    public ConcurrentMap<String, AtomicInteger> initializeInDegrees(TopologicalSortResult topology, Set<String> alreadyCompletedNodes) {
        ConcurrentMap<String, AtomicInteger> inDegrees = new ConcurrentHashMap<>();
        for (String id : topology.getSortedNodeIds()) {
            inDegrees.put(id, new AtomicInteger(topology.getIncoming().get(id).size()));
        }
        for (String completedId : alreadyCompletedNodes) {
            Set<String> successors = topology.getOutgoing().get(completedId);
            if (successors != null) {
                for (String succId : successors) {
                    AtomicInteger deg = inDegrees.get(succId);
                    if (deg != null) {
                        deg.decrementAndGet();
                    }
                }
            }
        }
        return inDegrees;
    }

    public List<WorkflowNodeDsl> findInitialReadyRootNodes(TopologicalSortResult topology,
                                                           Set<String> alreadyCompletedNodes,
                                                           ConcurrentMap<String, AtomicInteger> inDegrees,
                                                           Map<String, WorkflowNodeDsl> nodeMap) {
        List<WorkflowNodeDsl> rootNodes = new ArrayList<>();
        for (String id : topology.getSortedNodeIds()) {
            if (!alreadyCompletedNodes.contains(id)) {
                AtomicInteger deg = inDegrees.get(id);
                if (deg != null && deg.get() == 0) {
                    WorkflowNodeDsl n = nodeMap.get(id);
                    if (n != null) {
                        rootNodes.add(n);
                    }
                }
            }
        }
        return rootNodes;
    }

    public JoinEvaluation evaluateNodeReadiness(WorkflowNodeDsl node,
                                                TopologicalSortResult topology,
                                                Map<String, List<WorkflowEdgeDsl>> incomingEdges,
                                                ConcurrentMap<String, StepRunStatus> nodeStatuses,
                                                WorkflowExecutionContext context,
                                                CancelToken cancelToken,
                                                AtomicBoolean isAborted) {
        Set<String> preds = topology.getIncoming().get(node.getId());
        List<WorkflowEdgeDsl> inEdges = incomingEdges != null
                ? incomingEdges.getOrDefault(node.getId(), Collections.emptyList())
                : Collections.emptyList();
        Map<String, WorkflowEdgeDsl> predToEdge = new HashMap<>();
        for (WorkflowEdgeDsl edge : inEdges) {
            if (edge.getSource() != null) {
                predToEdge.put(edge.getSource().trim(), edge);
            }
        }

        boolean shouldSkip = false;
        String skipReason = null;

        if (preds != null && !preds.isEmpty()) {
            String joinPolicy = node.getJoinPolicy() != null ? node.getJoinPolicy().trim().toLowerCase() : "all_succeeded";

            for (String p : preds) {
                long waitLimit = System.currentTimeMillis() + 3000L;
                while (System.currentTimeMillis() < waitLimit) {
                    StepRunStatus pStatus = nodeStatuses.get(p);
                    if (pStatus != null && pStatus != StepRunStatus.RUNNING && pStatus != StepRunStatus.PENDING) {
                        break;
                    }
                    if (cancelToken.isCancelled() || isAborted.get()) {
                        break;
                    }
                    try {
                        Thread.sleep(5);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }

            Predicate<String> isPredSatisfied = (p) -> {
                StepRunStatus pStatus = nodeStatuses.get(p);
                if (pStatus != StepRunStatus.SUCCEEDED) {
                    return false;
                }
                WorkflowEdgeDsl edge = predToEdge.get(p);
                if (edge != null && edge.getCondition() != null && !edge.getCondition().trim().isEmpty()) {
                    return evaluator.evaluateCondition(edge.getCondition(), context);
                }
                return true;
            };

            if ("any_succeeded".equals(joinPolicy)) {
                boolean anySatisfied = preds.stream().anyMatch(isPredSatisfied);
                if (!anySatisfied) {
                    shouldSkip = true;
                    skipReason = "No predecessor satisfied under ANY_SUCCEEDED join policy";
                }
            } else if ("custom_condition".equals(joinPolicy)) {
                boolean condPass = evaluator.evaluateCondition(node.getCondition(), context);
                if (!condPass) {
                    shouldSkip = true;
                    skipReason = "Custom condition not satisfied";
                }
            } else {
                boolean allSatisfied = preds.stream().allMatch(isPredSatisfied);
                if (!allSatisfied) {
                    boolean anySkipped = preds.stream().anyMatch(p -> nodeStatuses.get(p) == StepRunStatus.SKIPPED);
                    boolean anyEdgeFailed = preds.stream().anyMatch(p -> {
                        WorkflowEdgeDsl edge = predToEdge.get(p);
                        return edge != null && edge.getCondition() != null && !edge.getCondition().trim().isEmpty()
                                && !evaluator.evaluateCondition(edge.getCondition(), context);
                    });
                    shouldSkip = true;
                    if (anyEdgeFailed) {
                        skipReason = "Incoming edge condition not met in ALL_SUCCEEDED dependency";
                    } else if (anySkipped) {
                        skipReason = "Predecessor was SKIPPED in ALL_SUCCEEDED dependency";
                    } else {
                        skipReason = "Predecessor was not SUCCEEDED";
                    }
                }
            }
        }

        if (!shouldSkip && node.getCondition() != null && !node.getCondition().trim().isEmpty()) {
            boolean condPass = evaluator.evaluateCondition(node.getCondition(), context);
            if (!condPass) {
                shouldSkip = true;
                skipReason = "Node condition [" + node.getCondition() + "] evaluated to false";
            }
        }

        return new JoinEvaluation(shouldSkip, skipReason);
    }

    public void triggerSuccessors(String nodeId,
                                  TopologicalSortResult topology,
                                  ConcurrentMap<String, AtomicInteger> inDegrees,
                                  Map<String, WorkflowNodeDsl> nodeMap,
                                  Consumer<WorkflowNodeDsl> launcher) {
        Set<String> successors = topology.getOutgoing().get(nodeId);
        if (successors != null) {
            for (String succId : successors) {
                AtomicInteger deg = inDegrees.get(succId);
                if (deg != null) {
                    int rem = deg.decrementAndGet();
                    if (rem == 0) {
                        WorkflowNodeDsl succNode = nodeMap.get(succId);
                        if (succNode != null) {
                            launcher.accept(succNode);
                        }
                    }
                }
            }
        }
    }

    public Map<String, Object> resolveFinalOutputs(Map<String, Object> outputExpressions, WorkflowExecutionContext context) {
        return evaluator.resolveOutputs(outputExpressions, context);
    }

    public SafeExpressionEvaluator getEvaluator() {
        return evaluator;
    }
}
