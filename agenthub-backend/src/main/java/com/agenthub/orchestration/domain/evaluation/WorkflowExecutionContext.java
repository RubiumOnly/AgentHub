package com.agenthub.orchestration.domain.evaluation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class WorkflowExecutionContext implements Serializable {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Map<String, Object> inputs = new ConcurrentHashMap<>();
    private final Map<String, NodeState> steps = new ConcurrentHashMap<>();
    private String initiatorUserId;

    public static class NodeState implements Serializable {
        private String status = "PENDING";
        private String output = "";
        private Map<String, Object> outputs = new HashMap<>();
        private Map<String, Object> inputs = new HashMap<>();
        private String error;
        private Long durationMs;

        public NodeState() {}

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getOutput() { return output; }
        public void setOutput(String output) { this.output = output; }
        public Map<String, Object> getOutputs() { return outputs; }
        public void setOutputs(Map<String, Object> outputs) { this.outputs = outputs != null ? outputs : new HashMap<>(); }
        public Map<String, Object> getInputs() { return inputs; }
        public void setInputs(Map<String, Object> inputs) { this.inputs = inputs != null ? inputs : new HashMap<>(); }
        public String getError() { return error; }
        public void setError(String error) { this.error = error; }
        public Long getDurationMs() { return durationMs; }
        public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }
    }

    public WorkflowExecutionContext() {}

    public WorkflowExecutionContext(Map<String, Object> initialInputs) {
        this(initialInputs, null);
    }

    public WorkflowExecutionContext(Map<String, Object> initialInputs, String initiatorUserId) {
        if (initialInputs != null) {
            this.inputs.putAll(initialInputs);
        }
        this.initiatorUserId = initiatorUserId;
    }

    public String getInitiatorUserId() {
        return initiatorUserId;
    }

    public void setInitiatorUserId(String initiatorUserId) {
        this.initiatorUserId = initiatorUserId;
    }

    public Map<String, Object> getInputs() {
        return inputs;
    }

    public Map<String, NodeState> getSteps() {
        return steps;
    }

    public NodeState getNodeState(String nodeId) {
        return steps.computeIfAbsent(nodeId, k -> new NodeState());
    }

    public void recordNodeStarted(String nodeId, Map<String, Object> resolvedInputs) {
        NodeState state = getNodeState(nodeId);
        state.setStatus("RUNNING");
        if (resolvedInputs != null) {
            state.setInputs(resolvedInputs);
        }
    }

    public void recordNodeOutput(String nodeId, String output) {
        NodeState state = getNodeState(nodeId);
        state.setStatus("SUCCEEDED");
        state.setOutput(output != null ? output : "");

        Map<String, Object> parsedMap = new HashMap<>();
        parsedMap.put("result", output != null ? output : "");
        if (output != null && output.trim().startsWith("{") && output.trim().endsWith("}")) {
            try {
                Map<String, Object> jsonMap = MAPPER.readValue(output, new TypeReference<Map<String, Object>>() {});
                parsedMap.putAll(jsonMap);
            } catch (Exception ignored) {
                // Not valid JSON, keep result string
            }
        }
        state.setOutputs(parsedMap);
    }

    public void recordNodeSkipped(String nodeId, String reason) {
        NodeState state = getNodeState(nodeId);
        state.setStatus("SKIPPED");
        state.setError(reason);
        Map<String, Object> skippedOutputs = new HashMap<>();
        skippedOutputs.put("skipped", true);
        skippedOutputs.put("reason", reason != null ? reason : "Condition not met");
        state.setOutputs(skippedOutputs);
    }

    public void recordNodeFailed(String nodeId, String error) {
        NodeState state = getNodeState(nodeId);
        state.setStatus("FAILED");
        state.setError(error);
    }

    /**
     * Resolves a dotted path in the context, e.g.:
     * - "inputs.code"
     * - "steps.gen_code.outputs.result"
     * - "steps.gen_code.status"
     * - "steps.gen_code.output"
     */
    public Object resolvePath(String path) {
        if (path == null || path.trim().isEmpty()) {
            return null;
        }
        String[] parts = path.trim().split("\\.");
        if (parts.length == 0) {
            return null;
        }

        String root = parts[0];
        if ("inputs".equals(root)) {
            if (parts.length == 1) return inputs;
            return resolveMapPath(inputs, parts, 1);
        } else if ("steps".equals(root)) {
            if (parts.length == 1) return steps;
            if (parts.length < 2) return null;
            String nodeId = parts[1];
            NodeState nodeState = steps.get(nodeId);
            if (nodeState == null) {
                return null;
            }
            if (parts.length == 2) {
                return nodeState;
            }
            String field = parts[2];
            if ("status".equalsIgnoreCase(field)) {
                return nodeState.getStatus();
            } else if ("output".equalsIgnoreCase(field)) {
                return nodeState.getOutput();
            } else if ("error".equalsIgnoreCase(field)) {
                return nodeState.getError();
            } else if ("inputs".equalsIgnoreCase(field)) {
                if (parts.length == 3) return nodeState.getInputs();
                return resolveMapPath(nodeState.getInputs(), parts, 3);
            } else if ("outputs".equalsIgnoreCase(field)) {
                if (parts.length == 3) return nodeState.getOutputs();
                return resolveMapPath(nodeState.getOutputs(), parts, 3);
            }
        }
        // Direct lookup in inputs
        return inputs.get(path);
    }

    private Object resolveMapPath(Map<?, ?> map, String[] parts, int index) {
        if (map == null || index >= parts.length) {
            return map;
        }
        Object current = map.get(parts[index]);
        if (index == parts.length - 1) {
            return current;
        }
        if (current instanceof Map) {
            return resolveMapPath((Map<?, ?>) current, parts, index + 1);
        }
        return null;
    }
}
