package com.agenthub.domain.workflow.model;

import com.agenthub.domain.agent.model.AgentPlatformType;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WorkflowModels {

    public enum NodeType {
        START,
        AGENT,
        CONDITION,
        END
    }

    public enum ExecutionStatus {
        PENDING,
        RUNNING,
        SUCCEEDED,
        FAILED,
        SKIPPED
    }

    public static class NodeDefinition implements Serializable {
        private String id;
        private String label;
        private NodeType type;
        private AgentPlatformType agentPlatform;
        private String promptTemplate;
        private List<String> nextNodeIds = new ArrayList<>();

        public NodeDefinition() {}

        public NodeDefinition(String id, String label, NodeType type, AgentPlatformType agentPlatform, String promptTemplate) {
            this.id = id;
            this.label = label;
            this.type = type;
            this.agentPlatform = agentPlatform;
            this.promptTemplate = promptTemplate;
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }
        public NodeType getType() { return type; }
        public void setType(NodeType type) { this.type = type; }
        public AgentPlatformType getAgentPlatform() { return agentPlatform; }
        public void setAgentPlatform(AgentPlatformType agentPlatform) { this.agentPlatform = agentPlatform; }
        public String getPromptTemplate() { return promptTemplate; }
        public void setPromptTemplate(String promptTemplate) { this.promptTemplate = promptTemplate; }
        public List<String> getNextNodeIds() { return nextNodeIds; }
        public void setNextNodeIds(List<String> nextNodeIds) { this.nextNodeIds = nextNodeIds; }
    }

    public static class WorkflowDefinition implements Serializable {
        private String id;
        private String name;
        private String description;
        private List<NodeDefinition> nodes = new ArrayList<>();

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public List<NodeDefinition> getNodes() { return nodes; }
        public void setNodes(List<NodeDefinition> nodes) { this.nodes = nodes; }
    }

    public static class NodeRunState implements Serializable {
        private String nodeId;
        private ExecutionStatus status = ExecutionStatus.PENDING;
        private String output;
        private String error;
        private long durationMs;

        public String getNodeId() { return nodeId; }
        public void setNodeId(String nodeId) { this.nodeId = nodeId; }
        public ExecutionStatus getStatus() { return status; }
        public void setStatus(ExecutionStatus status) { this.status = status; }
        public String getOutput() { return output; }
        public void setOutput(String output) { this.output = output; }
        public String getError() { return error; }
        public void setError(String error) { this.error = error; }
        public long getDurationMs() { return durationMs; }
        public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
    }

    public static class WorkflowExecutionResult implements Serializable {
        private String executionId;
        private String workflowId;
        private ExecutionStatus status;
        private Map<String, NodeRunState> nodeStates = new HashMap<>();
        private String finalOutput;

        public String getExecutionId() { return executionId; }
        public void setExecutionId(String executionId) { this.executionId = executionId; }
        public String getWorkflowId() { return workflowId; }
        public void setWorkflowId(String workflowId) { this.workflowId = workflowId; }
        public ExecutionStatus getStatus() { return status; }
        public void setStatus(ExecutionStatus status) { this.status = status; }
        public Map<String, NodeRunState> getNodeStates() { return nodeStates; }
        public void setNodeStates(Map<String, NodeRunState> nodeStates) { this.nodeStates = nodeStates; }
        public String getFinalOutput() { return finalOutput; }
        public void setFinalOutput(String finalOutput) { this.finalOutput = finalOutput; }
    }
}
