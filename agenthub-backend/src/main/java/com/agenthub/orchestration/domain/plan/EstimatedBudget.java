package com.agenthub.orchestration.domain.plan;

public class EstimatedBudget {

    private int estimatedTokens;
    private double estimatedCost;
    private long maxTimeoutSeconds;

    public EstimatedBudget() {}

    public EstimatedBudget(int estimatedTokens, double estimatedCost, long maxTimeoutSeconds) {
        this.estimatedTokens = estimatedTokens;
        this.estimatedCost = estimatedCost;
        this.maxTimeoutSeconds = maxTimeoutSeconds;
    }

    public int getEstimatedTokens() { return estimatedTokens; }
    public void setEstimatedTokens(int estimatedTokens) { this.estimatedTokens = estimatedTokens; }
    public double getEstimatedCost() { return estimatedCost; }
    public void setEstimatedCost(double estimatedCost) { this.estimatedCost = estimatedCost; }
    public long getMaxTimeoutSeconds() { return maxTimeoutSeconds; }
    public void setMaxTimeoutSeconds(long maxTimeoutSeconds) { this.maxTimeoutSeconds = maxTimeoutSeconds; }
}
