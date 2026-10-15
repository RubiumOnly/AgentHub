package com.agenthub.agent.dto;

import java.io.Serializable;

public class ModelCostSummaryView implements Serializable {

    private String model;
    private long promptTokens;
    private long completionTokens;
    private long totalTokens;
    private double estimatedCost;
    private long invocations;

    public ModelCostSummaryView() {}

    public ModelCostSummaryView(String model,
                                long promptTokens,
                                long completionTokens,
                                long totalTokens,
                                double estimatedCost,
                                long invocations) {
        this.model = model;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.totalTokens = totalTokens;
        this.estimatedCost = estimatedCost;
        this.invocations = invocations;
    }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public long getPromptTokens() { return promptTokens; }
    public void setPromptTokens(long promptTokens) { this.promptTokens = promptTokens; }
    public long getCompletionTokens() { return completionTokens; }
    public void setCompletionTokens(long completionTokens) { this.completionTokens = completionTokens; }
    public long getTotalTokens() { return totalTokens; }
    public void setTotalTokens(long totalTokens) { this.totalTokens = totalTokens; }
    public double getEstimatedCost() { return estimatedCost; }
    public void setEstimatedCost(double estimatedCost) { this.estimatedCost = estimatedCost; }
    public long getInvocations() { return invocations; }
    public void setInvocations(long invocations) { this.invocations = invocations; }
}
