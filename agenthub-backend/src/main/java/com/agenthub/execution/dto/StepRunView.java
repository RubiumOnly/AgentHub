package com.agenthub.execution.dto;

import java.time.LocalDateTime;

public class StepRunView {
    private String id;
    private String runId;
    private String nodeId;
    private String status;
    private Integer attempt;
    private String inputRef;
    private String outputRef;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Long durationMs;
    private String correlationId;

    public StepRunView() {}

    public StepRunView(String id, String runId, String nodeId, String status, Integer attempt, String inputRef, String outputRef, String errorMessage, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this(id, runId, nodeId, status, attempt, inputRef, outputRef, errorMessage, createdAt, updatedAt, null, null, null, null);
    }

    public StepRunView(String id, String runId, String nodeId, String status, Integer attempt, String inputRef, String outputRef, String errorMessage, LocalDateTime createdAt, LocalDateTime updatedAt, LocalDateTime startedAt, LocalDateTime finishedAt, Long durationMs, String correlationId) {
        this.id = id;
        this.runId = runId;
        this.nodeId = nodeId;
        this.status = status;
        this.attempt = attempt;
        this.inputRef = inputRef;
        this.outputRef = outputRef;
        this.errorMessage = errorMessage;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.durationMs = durationMs;
        this.correlationId = correlationId;
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
}
