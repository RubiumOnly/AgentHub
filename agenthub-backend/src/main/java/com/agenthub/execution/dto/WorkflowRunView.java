package com.agenthub.execution.dto;

import java.time.LocalDateTime;

public class WorkflowRunView {
    private String id;
    private String projectId;
    private String definitionId;
    private String status;
    private String idempotencyKey;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String cancelReason;
    private LocalDateTime cancelledAt;
    private String correlationId;

    public WorkflowRunView() {}

    public WorkflowRunView(String id, String projectId, String definitionId, String status, String idempotencyKey, LocalDateTime startedAt, LocalDateTime finishedAt, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this(id, projectId, definitionId, status, idempotencyKey, startedAt, finishedAt, createdAt, updatedAt, null, null, null);
    }

    public WorkflowRunView(String id, String projectId, String definitionId, String status, String idempotencyKey, LocalDateTime startedAt, LocalDateTime finishedAt, LocalDateTime createdAt, LocalDateTime updatedAt, String cancelReason, LocalDateTime cancelledAt, String correlationId) {
        this.id = id;
        this.projectId = projectId;
        this.definitionId = definitionId;
        this.status = status;
        this.idempotencyKey = idempotencyKey;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.cancelReason = cancelReason;
        this.cancelledAt = cancelledAt;
        this.correlationId = correlationId;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }
    public String getDefinitionId() { return definitionId; }
    public void setDefinitionId(String definitionId) { this.definitionId = definitionId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(LocalDateTime finishedAt) { this.finishedAt = finishedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public String getCancelReason() { return cancelReason; }
    public void setCancelReason(String cancelReason) { this.cancelReason = cancelReason; }
    public LocalDateTime getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(LocalDateTime cancelledAt) { this.cancelledAt = cancelledAt; }
    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
}
