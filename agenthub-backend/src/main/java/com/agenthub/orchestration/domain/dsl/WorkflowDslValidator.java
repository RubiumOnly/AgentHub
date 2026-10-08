package com.agenthub.orchestration.domain.dsl;

import com.agenthub.shared.exception.BusinessException;
import com.agenthub.shared.exception.ErrorCode;

import java.util.*;

public class WorkflowDslValidator {

    public static TopologicalSortResult validate(WorkflowDsl dsl) {
        if (dsl == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Workflow DSL must not be null");
        }

        if (dsl.getNodes() == null || dsl.getNodes().isEmpty()) {
            throw new BusinessException(ErrorCode.WORKFLOW_INVALID, "Workflow DSL must contain at least one node");
        }

        Set<String> nodeIds = new HashSet<>();
        Map<String, WorkflowNodeDsl> nodeMap = new HashMap<>();

        for (WorkflowNodeDsl node : dsl.getNodes()) {
            if (node.getId() == null || node.getId().trim().isEmpty()) {
                throw new BusinessException(ErrorCode.WORKFLOW_INVALID, "Node ID must not be blank");
            }
            String trimmedId = node.getId().trim();
            if (!nodeIds.add(trimmedId)) {
                throw new BusinessException(ErrorCode.WORKFLOW_INVALID, "Duplicate node ID: " + trimmedId);
            }
            nodeMap.put(trimmedId, node);

            if (node.getTimeoutSeconds() != null && node.getTimeoutSeconds() < 0) {
                throw new BusinessException(ErrorCode.WORKFLOW_INVALID, "Node timeout must not be negative: " + trimmedId);
            }

            if (node.getRetryPolicy() != null) {
                if (node.getRetryPolicy().getMaxRetries() < 0 || node.getRetryPolicy().getMaxRetries() > 10) {
                    throw new BusinessException(ErrorCode.WORKFLOW_INVALID, "Node maxRetries must be between 0 and 10: " + trimmedId);
                }
                if (node.getRetryPolicy().getBackoffMs() < 0 || node.getRetryPolicy().getBackoffMs() > 60000) {
                    throw new BusinessException(ErrorCode.WORKFLOW_INVALID, "Node backoffMs must be between 0 and 60000: " + trimmedId);
                }
            }
        }

        Map<String, Set<String>> outgoing = new HashMap<>();
        Map<String, Set<String>> incoming = new HashMap<>();
        for (String id : nodeIds) {
            outgoing.put(id, new LinkedHashSet<>());
            incoming.put(id, new LinkedHashSet<>());
        }

        // Process explicit edges
        if (dsl.getEdges() != null) {
            for (WorkflowEdgeDsl edge : dsl.getEdges()) {
                if (edge.getSource() == null || !nodeIds.contains(edge.getSource().trim())) {
                    throw new BusinessException(ErrorCode.WORKFLOW_INVALID, "Edge source node not found: " + edge.getSource());
                }
                if (edge.getTarget() == null || !nodeIds.contains(edge.getTarget().trim())) {
                    throw new BusinessException(ErrorCode.WORKFLOW_INVALID, "Edge target node not found: " + edge.getTarget());
                }
                String src = edge.getSource().trim();
                String tgt = edge.getTarget().trim();
                if (src.equals(tgt)) {
                    throw new BusinessException(ErrorCode.WORKFLOW_INVALID, "Self-loop edge detected on node: " + src);
                }
                outgoing.get(src).add(tgt);
                incoming.get(tgt).add(src);
            }
        }

        // Process node nextNodeIds for backward compatibility
        for (WorkflowNodeDsl node : dsl.getNodes()) {
            String src = node.getId().trim();
            if (node.getNextNodeIds() != null) {
                for (String nextId : node.getNextNodeIds()) {
                    if (nextId == null || !nodeIds.contains(nextId.trim())) {
                        throw new BusinessException(ErrorCode.WORKFLOW_INVALID, "Node [" + src + "] nextNodeId not found: " + nextId);
                    }
                    String tgt = nextId.trim();
                    if (src.equals(tgt)) {
                        throw new BusinessException(ErrorCode.WORKFLOW_INVALID, "Self-loop edge detected on node: " + src);
                    }
                    outgoing.get(src).add(tgt);
                    incoming.get(tgt).add(src);
                }
            }
        }

        // Island node check: in a multi-node workflow, no node should be completely isolated
        if (nodeIds.size() > 1) {
            for (String id : nodeIds) {
                if (incoming.get(id).isEmpty() && outgoing.get(id).isEmpty()) {
                    throw new BusinessException(ErrorCode.WORKFLOW_INVALID,
                            "Isolated island node detected with no incoming or outgoing connections: " + id);
                }
            }
        }

        // Check entryNodeId if specified
        if (dsl.getEntryNodeId() != null && !dsl.getEntryNodeId().trim().isEmpty()) {
            String entryId = dsl.getEntryNodeId().trim();
            if (!nodeIds.contains(entryId)) {
                throw new BusinessException(ErrorCode.WORKFLOW_INVALID, "Entry node not found: " + entryId);
            }
            if (!incoming.get(entryId).isEmpty()) {
                throw new BusinessException(ErrorCode.WORKFLOW_INVALID, "Entry node must have zero incoming dependencies: " + entryId);
            }
        }

        // Cycle Detection via Kahn's Topological Sort Algorithm
        Map<String, Integer> inDegrees = new LinkedHashMap<>();
        Queue<String> queue = new ArrayDeque<>();
        for (String id : nodeIds) {
            int deg = incoming.get(id).size();
            inDegrees.put(id, deg);
            if (deg == 0) {
                queue.add(id);
            }
        }

        // Preserve initial in-degrees before Kahn reduction
        Map<String, Integer> initialInDegrees = new LinkedHashMap<>(inDegrees);

        List<String> sortedOrder = new ArrayList<>();
        while (!queue.isEmpty()) {
            String current = queue.poll();
            sortedOrder.add(current);
            for (String neighbor : outgoing.get(current)) {
                int newDeg = inDegrees.get(neighbor) - 1;
                inDegrees.put(neighbor, newDeg);
                if (newDeg == 0) {
                    queue.add(neighbor);
                }
            }
        }

        if (sortedOrder.size() < nodeIds.size()) {
            List<String> cycleNodes = new ArrayList<>();
            for (Map.Entry<String, Integer> entry : inDegrees.entrySet()) {
                if (entry.getValue() > 0) {
                    cycleNodes.add(entry.getKey());
                }
            }
            throw new BusinessException(ErrorCode.WORKFLOW_INVALID,
                    "Cycle detected in workflow graph involving nodes: " + cycleNodes);
        }

        return new TopologicalSortResult(sortedOrder, initialInDegrees, outgoing, incoming);
    }
}
