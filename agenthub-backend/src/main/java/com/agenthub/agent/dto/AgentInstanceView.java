package com.agenthub.agent.dto;

import java.time.LocalDateTime;

public class AgentInstanceView {
    private String id;
    private String ownerId;
    private String projectId;
    private String definitionId;
    private String runtimeType;
    private String providerId;
    private String status;
    private LocalDateTime createdAt;

    public AgentInstanceView() {}

    public AgentInstanceView(String id, String ownerId, String projectId, String definitionId, String runtimeType, String providerId, String status, LocalDateTime createdAt) {
        this.id = id;
        this.ownerId = ownerId;
        this.projectId = projectId;
        this.definitionId = definitionId;
        this.runtimeType = runtimeType;
        this.providerId = providerId;
        this.status = status;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }
    public String getDefinitionId() { return definitionId; }
    public void setDefinitionId(String definitionId) { this.definitionId = definitionId; }
    public String getRuntimeType() { return runtimeType; }
    public void setRuntimeType(String runtimeType) { this.runtimeType = runtimeType; }
    public String getProviderId() { return providerId; }
    public void setProviderId(String providerId) { this.providerId = providerId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
