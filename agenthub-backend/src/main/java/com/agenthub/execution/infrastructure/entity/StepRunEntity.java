package com.agenthub.execution.infrastructure.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "step_runs")
public class StepRunEntity {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "run_id", nullable = false, length = 64)
    private String runId;

    @Column(name = "node_id", nullable = false, length = 64)
    private String nodeId;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(nullable = false)
    private Integer attempt;

    @Column(name = "input_ref", length = 256)
    private String inputRef;

    @Column(name = "output_ref", length = 256)
    private String outputRef;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @Column(name = "inputs_json", columnDefinition = "TEXT")
    private String inputsJson;

    @Column(name = "outputs_json", columnDefinition = "TEXT")
    private String outputsJson;

    @Column(name = "requires_approval")
    private Boolean requiresApproval = false;

    @Column(name = "lease_owner", length = 64)
    private String leaseOwner;

    @Column(name = "lease_until")
    private LocalDateTime leaseUntil;

    @Column(name = "heartbeat_at")
    private LocalDateTime heartbeatAt;

    public StepRunEntity() {}

    public StepRunEntity(String id, String runId, String nodeId, String status) {
        this.id = id;
        this.runId = runId;
        this.nodeId = nodeId;
        this.status = status != null ? status : "PENDING";
        this.attempt = 1;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }
    public String getNodeId() { return nodeId; }
    public void setNodeId(String nodeId) { this.nodeId = nodeId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getAttempt() { return attempt; }
    public void setAttempt(Integer attempt) { this.attempt = attempt; }
    public String getInputRef() { return inputRef; }
    public void setInputRef(String inputRef) { this.inputRef = inputRef; }
    public String getOutputRef() { return outputRef; }
    public void setOutputRef(String outputRef) { this.outputRef = outputRef; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(LocalDateTime finishedAt) { this.finishedAt = finishedAt; }
    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }
    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
    public String getInputsJson() { return inputsJson; }
    public void setInputsJson(String inputsJson) { this.inputsJson = inputsJson; }
    public String getOutputsJson() { return outputsJson; }
    public void setOutputsJson(String outputsJson) { this.outputsJson = outputsJson; }
    public Boolean getRequiresApproval() { return requiresApproval; }
    public void setRequiresApproval(Boolean requiresApproval) { this.requiresApproval = requiresApproval; }
    public String getLeaseOwner() { return leaseOwner; }
    public void setLeaseOwner(String leaseOwner) { this.leaseOwner = leaseOwner; }
    public LocalDateTime getLeaseUntil() { return leaseUntil; }
    public void setLeaseUntil(LocalDateTime leaseUntil) { this.leaseUntil = leaseUntil; }
    public LocalDateTime getHeartbeatAt() { return heartbeatAt; }
    public void setHeartbeatAt(LocalDateTime heartbeatAt) { this.heartbeatAt = heartbeatAt; }
}
