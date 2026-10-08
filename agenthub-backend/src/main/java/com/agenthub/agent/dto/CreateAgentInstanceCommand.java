package com.agenthub.agent.dto;

public class CreateAgentInstanceCommand {
    private String projectId;
    private String definitionId;
    private String runtimeType;
    private String providerId;

    public CreateAgentInstanceCommand() {}

    public CreateAgentInstanceCommand(String projectId, String definitionId, String runtimeType, String providerId) {
        this.projectId = projectId;
        this.definitionId = definitionId;
        this.runtimeType = runtimeType;
        this.providerId = providerId;
    }

    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }
    public String getDefinitionId() { return definitionId; }
    public void setDefinitionId(String definitionId) { this.definitionId = definitionId; }
    public String getRuntimeType() { return runtimeType; }
    public void setRuntimeType(String runtimeType) { this.runtimeType = runtimeType; }
    public String getProviderId() { return providerId; }
    public void setProviderId(String providerId) { this.providerId = providerId; }
}
