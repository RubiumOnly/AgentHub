package com.agenthub.execution.dto;

public class StartRunCommand {
    private String projectId;
    private String definitionId;
    private String idempotencyKey;

    public StartRunCommand() {}

    public StartRunCommand(String projectId, String definitionId, String idempotencyKey) {
        this.projectId = projectId;
        this.definitionId = definitionId;
        this.idempotencyKey = idempotencyKey;
    }

    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }
    public String getDefinitionId() { return definitionId; }
    public void setDefinitionId(String definitionId) { this.definitionId = definitionId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
}
