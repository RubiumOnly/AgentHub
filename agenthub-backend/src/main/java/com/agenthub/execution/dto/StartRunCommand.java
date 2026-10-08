package com.agenthub.execution.dto;

import com.agenthub.orchestration.domain.dsl.WorkflowDsl;

import java.util.Map;

public class StartRunCommand {
    private String projectId;
    private String definitionId;
    private String idempotencyKey;
    private WorkflowDsl workflowDsl;
    private Map<String, Object> inputs;

    public StartRunCommand() {}

    public StartRunCommand(String projectId, String definitionId, String idempotencyKey) {
        this.projectId = projectId;
        this.definitionId = definitionId;
        this.idempotencyKey = idempotencyKey;
    }

    public StartRunCommand(String projectId, String definitionId, String idempotencyKey, WorkflowDsl workflowDsl, Map<String, Object> inputs) {
        this.projectId = projectId;
        this.definitionId = definitionId;
        this.idempotencyKey = idempotencyKey;
        this.workflowDsl = workflowDsl;
        this.inputs = inputs;
    }

    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }
    public String getDefinitionId() { return definitionId; }
    public void setDefinitionId(String definitionId) { this.definitionId = definitionId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public WorkflowDsl getWorkflowDsl() { return workflowDsl; }
    public void setWorkflowDsl(WorkflowDsl workflowDsl) { this.workflowDsl = workflowDsl; }
    public Map<String, Object> getInputs() { return inputs; }
    public void setInputs(Map<String, Object> inputs) { this.inputs = inputs; }
}
