package com.agenthub.agent.infrastructure.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Granular Token usage and cost accounting audit entity.
 * Persists token metrics, real measured latency, and estimated cost for each LLM provider call.
 */
@Entity
@Table(name = "token_usages")
public class TokenUsageAuditEntity {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "run_id", length = 64)
    private String runId;

    @Column(name = "step_run_id", length = 64)
    private String stepRunId;

    @Column(name = "provider_id", length = 64)
    private String providerId;

    @Column(name = "provider_type", nullable = false, length = 64)
    private String providerType;

    @Column(nullable = false, length = 128)
    private String model;

    @Column(name = "prompt_tokens", nullable = false)
    private int promptTokens;

    @Column(name = "completion_tokens", nullable = false)
    private int completionTokens;

    @Column(name = "total_tokens", nullable = false)
    private int totalTokens;

    @Column(name = "latency_ms", nullable = false)
    private long latencyMs;

    @Column(name = "estimated_cost", nullable = false)
    private double estimatedCost;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "error_message", length = 512)
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public TokenUsageAuditEntity() {
        this.createdAt = LocalDateTime.now();
    }

    public TokenUsageAuditEntity(String id,
                                 String runId,
                                 String stepRunId,
                                 String providerId,
                                 String providerType,
                                 String model,
                                 int promptTokens,
                                 int completionTokens,
                                 int totalTokens,
                                 long latencyMs,
                                 double estimatedCost,
                                 String status,
                                 String errorMessage) {
        this.id = id;
        this.runId = runId;
        this.stepRunId = stepRunId;
        this.providerId = providerId;
        this.providerType = providerType;
        this.model = model;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.totalTokens = totalTokens;
        this.latencyMs = latencyMs;
        this.estimatedCost = estimatedCost;
        this.status = status != null ? status : "SUCCESS";
        this.errorMessage = errorMessage;
        this.createdAt = LocalDateTime.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }
    public String getStepRunId() { return stepRunId; }
    public void setStepRunId(String stepRunId) { this.stepRunId = stepRunId; }
    public String getProviderId() { return providerId; }
    public void setProviderId(String providerId) { this.providerId = providerId; }
    public String getProviderType() { return providerType; }
    public void setProviderType(String providerType) { this.providerType = providerType; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public int getPromptTokens() { return promptTokens; }
    public void setPromptTokens(int promptTokens) { this.promptTokens = promptTokens; }
    public int getCompletionTokens() { return completionTokens; }
    public void setCompletionTokens(int completionTokens) { this.completionTokens = completionTokens; }
    public int getTotalTokens() { return totalTokens; }
    public void setTotalTokens(int totalTokens) { this.totalTokens = totalTokens; }
    public long getLatencyMs() { return latencyMs; }
    public void setLatencyMs(long latencyMs) { this.latencyMs = latencyMs; }
    public double getEstimatedCost() { return estimatedCost; }
    public void setEstimatedCost(double estimatedCost) { this.estimatedCost = estimatedCost; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
