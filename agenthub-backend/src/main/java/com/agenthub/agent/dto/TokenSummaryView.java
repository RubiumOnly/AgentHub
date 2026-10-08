package com.agenthub.agent.dto;

public class TokenSummaryView {
    private long totalPromptTokens;
    private long totalCompletionTokens;
    private long totalTokens;
    private double totalEstimatedCost;
    private long totalInvocations;

    public TokenSummaryView() {}

    public TokenSummaryView(long totalPromptTokens, long totalCompletionTokens, long totalTokens, double totalEstimatedCost, long totalInvocations) {
        this.totalPromptTokens = totalPromptTokens;
        this.totalCompletionTokens = totalCompletionTokens;
        this.totalTokens = totalTokens;
        this.totalEstimatedCost = totalEstimatedCost;
        this.totalInvocations = totalInvocations;
    }

    public long getTotalPromptTokens() { return totalPromptTokens; }
    public long getTotalCompletionTokens() { return totalCompletionTokens; }
    public long getTotalTokens() { return totalTokens; }
    public double getTotalEstimatedCost() { return totalEstimatedCost; }
    public long getTotalInvocations() { return totalInvocations; }
}
