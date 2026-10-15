package com.agenthub.agent.dto;

import java.io.Serializable;

public class ProviderCostSummaryView implements Serializable {

    private String providerId;
    private String providerType;
    private double estimatedCost;
    private long totalTokens;
    private long avgLatencyMs;
    private long invocations;

    public ProviderCostSummaryView() {}

    public ProviderCostSummaryView(String providerId,
                                   String providerType,
                                   double estimatedCost,
                                   long totalTokens,
                                   long avgLatencyMs,
                                   long invocations) {
        this.providerId = providerId;
        this.providerType = providerType;
        this.estimatedCost = estimatedCost;
        this.totalTokens = totalTokens;
        this.avgLatencyMs = avgLatencyMs;
        this.invocations = invocations;
    }

    public String getProviderId() { return providerId; }
    public void setProviderId(String providerId) { this.providerId = providerId; }
    public String getProviderType() { return providerType; }
    public void setProviderType(String providerType) { this.providerType = providerType; }
    public double getEstimatedCost() { return estimatedCost; }
    public void setEstimatedCost(double estimatedCost) { this.estimatedCost = estimatedCost; }
    public long getTotalTokens() { return totalTokens; }
    public void setTotalTokens(long totalTokens) { this.totalTokens = totalTokens; }
    public long getAvgLatencyMs() { return avgLatencyMs; }
    public void setAvgLatencyMs(long avgLatencyMs) { this.avgLatencyMs = avgLatencyMs; }
    public long getInvocations() { return invocations; }
    public void setInvocations(long invocations) { this.invocations = invocations; }
}
