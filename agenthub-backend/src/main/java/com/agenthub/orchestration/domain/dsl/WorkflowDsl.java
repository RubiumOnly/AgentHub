package com.agenthub.orchestration.domain.dsl;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WorkflowDsl implements Serializable {
    private String id;
    private String name;
    private String description;
    private String version = "1.0.0";
    private String schemaVersion = "v1";
    private String entryNodeId;
    private Long timeoutSeconds = 300L;
    private List<WorkflowNodeDsl> nodes = new ArrayList<>();
    private List<WorkflowEdgeDsl> edges = new ArrayList<>();
    private Map<String, Object> outputs = new HashMap<>();

    public WorkflowDsl() {}

    public WorkflowDsl(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public String getSchemaVersion() { return schemaVersion; }
    public void setSchemaVersion(String schemaVersion) { this.schemaVersion = schemaVersion; }
    public String getEntryNodeId() { return entryNodeId; }
    public void setEntryNodeId(String entryNodeId) { this.entryNodeId = entryNodeId; }
    public Long getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(Long timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    public List<WorkflowNodeDsl> getNodes() { return nodes; }
    public void setNodes(List<WorkflowNodeDsl> nodes) { this.nodes = nodes != null ? nodes : new ArrayList<>(); }
    public List<WorkflowEdgeDsl> getEdges() { return edges; }
    public void setEdges(List<WorkflowEdgeDsl> edges) { this.edges = edges != null ? edges : new ArrayList<>(); }
    public Map<String, Object> getOutputs() { return outputs; }
    public void setOutputs(Map<String, Object> outputs) { this.outputs = outputs != null ? outputs : new HashMap<>(); }
}
