package com.agenthub.agent.dto;

import java.time.LocalDateTime;

public class TokenUsageAuditView {
    private String id;
    private String runId;
    private String stepRunId;
    private String providerId;
    private String providerType;
    private String model;
    private int promptTokens;
    private int completionTokens;
    private int totalTokens;
    private long latencyMs;
    private double estimatedCost;
    private String status;
    private String errorMessage;
    private LocalDateTime createdAt;

    public TokenUsageAuditView() {}

    public TokenUsageAuditView(String id,
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
                              String errorMessage,
                              LocalDateTime createdAt) {
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
        this.status = status;
        this.errorMessage = errorMessage;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public String getRunId() { return runId; }
    public String getStepRunId() { return stepRunId; }
    public String getProviderId() { return providerId; }
    public String getProviderType() { return providerType; }
    public String getModel() { return model; }
    public int getPromptTokens() { return promptTokens; }
    public int getCompletionTokens() { return completionTokens; }
    public int getTotalTokens() { return totalTokens; }
    public long getLatencyMs() { return latencyMs; }
    public double getEstimatedCost() { return estimatedCost; }
    public String getStatus() { return status; }
    public String getErrorMessage() { return errorMessage; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
