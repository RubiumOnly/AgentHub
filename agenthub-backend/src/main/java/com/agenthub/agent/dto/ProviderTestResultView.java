package com.agenthub.agent.dto;

import com.agenthub.agent.domain.provider.model.TokenUsage;

public class ProviderTestResultView {
    private String providerId;
    private String status;
    private long latencyMs;
    private String outputSnippet;
    private TokenUsage tokenUsage;
    private double estimatedCost;

    public ProviderTestResultView() {}

    public ProviderTestResultView(String providerId, String status, long latencyMs, String outputSnippet, TokenUsage tokenUsage, double estimatedCost) {
        this.providerId = providerId;
        this.status = status;
        this.latencyMs = latencyMs;
        this.outputSnippet = outputSnippet;
        this.tokenUsage = tokenUsage;
        this.estimatedCost = estimatedCost;
    }

    public String getProviderId() { return providerId; }
    public String getStatus() { return status; }
    public long getLatencyMs() { return latencyMs; }
    public String getOutputSnippet() { return outputSnippet; }
    public TokenUsage getTokenUsage() { return tokenUsage; }
    public double getEstimatedCost() { return estimatedCost; }
}
