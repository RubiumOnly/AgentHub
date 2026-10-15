package com.agenthub.agent.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class TokenGovernanceView implements Serializable {

    private long totalPromptTokens;
    private long totalCompletionTokens;
    private long totalTokens;
    private double totalEstimatedCost;
    private long totalInvocations;

    private double hardBudgetLimitUsd;
    private double remainingBudgetUsd;
    private double budgetUtilizationPercent;
    private boolean budgetExhausted;
    private boolean budgetWarning; // true if utilization >= 80%

    private String activeMode; // "DEMO_SIMULATED", "LIVE_SELF_HOSTED", "LIVE_TEAM"
    private List<ModelCostSummaryView> modelBreakdown = new ArrayList<>();
    private List<ProviderCostSummaryView> providerBreakdown = new ArrayList<>();

    public TokenGovernanceView() {}

    public TokenGovernanceView(long totalPromptTokens,
                               long totalCompletionTokens,
                               long totalTokens,
                               double totalEstimatedCost,
                               long totalInvocations,
                               double hardBudgetLimitUsd,
                               double remainingBudgetUsd,
                               double budgetUtilizationPercent,
                               boolean budgetExhausted,
                               boolean budgetWarning,
                               String activeMode,
                               List<ModelCostSummaryView> modelBreakdown,
                               List<ProviderCostSummaryView> providerBreakdown) {
        this.totalPromptTokens = totalPromptTokens;
        this.totalCompletionTokens = totalCompletionTokens;
        this.totalTokens = totalTokens;
        this.totalEstimatedCost = totalEstimatedCost;
        this.totalInvocations = totalInvocations;
        this.hardBudgetLimitUsd = hardBudgetLimitUsd;
        this.remainingBudgetUsd = remainingBudgetUsd;
        this.budgetUtilizationPercent = budgetUtilizationPercent;
        this.budgetExhausted = budgetExhausted;
        this.budgetWarning = budgetWarning;
        this.activeMode = activeMode;
        this.modelBreakdown = modelBreakdown != null ? modelBreakdown : new ArrayList<>();
        this.providerBreakdown = providerBreakdown != null ? providerBreakdown : new ArrayList<>();
    }

    public long getTotalPromptTokens() { return totalPromptTokens; }
    public void setTotalPromptTokens(long totalPromptTokens) { this.totalPromptTokens = totalPromptTokens; }
    public long getTotalCompletionTokens() { return totalCompletionTokens; }
    public void setTotalCompletionTokens(long totalCompletionTokens) { this.totalCompletionTokens = totalCompletionTokens; }
    public long getTotalTokens() { return totalTokens; }
    public void setTotalTokens(long totalTokens) { this.totalTokens = totalTokens; }
    public double getTotalEstimatedCost() { return totalEstimatedCost; }
    public void setTotalEstimatedCost(double totalEstimatedCost) { this.totalEstimatedCost = totalEstimatedCost; }
    public long getTotalInvocations() { return totalInvocations; }
    public void setTotalInvocations(long totalInvocations) { this.totalInvocations = totalInvocations; }
    public double getHardBudgetLimitUsd() { return hardBudgetLimitUsd; }
    public void setHardBudgetLimitUsd(double hardBudgetLimitUsd) { this.hardBudgetLimitUsd = hardBudgetLimitUsd; }
    public double getRemainingBudgetUsd() { return remainingBudgetUsd; }
    public void setRemainingBudgetUsd(double remainingBudgetUsd) { this.remainingBudgetUsd = remainingBudgetUsd; }
    public double getBudgetUtilizationPercent() { return budgetUtilizationPercent; }
    public void setBudgetUtilizationPercent(double budgetUtilizationPercent) { this.budgetUtilizationPercent = budgetUtilizationPercent; }
    public boolean isBudgetExhausted() { return budgetExhausted; }
    public void setBudgetExhausted(boolean budgetExhausted) { this.budgetExhausted = budgetExhausted; }
    public boolean isBudgetWarning() { return budgetWarning; }
    public void setBudgetWarning(boolean budgetWarning) { this.budgetWarning = budgetWarning; }
    public String getActiveMode() { return activeMode; }
    public void setActiveMode(String activeMode) { this.activeMode = activeMode; }
    public List<ModelCostSummaryView> getModelBreakdown() { return modelBreakdown; }
    public void setModelBreakdown(List<ModelCostSummaryView> modelBreakdown) {
        this.modelBreakdown = modelBreakdown != null ? modelBreakdown : new ArrayList<>();
    }
    public List<ProviderCostSummaryView> getProviderBreakdown() { return providerBreakdown; }
    public void setProviderBreakdown(List<ProviderCostSummaryView> providerBreakdown) {
        this.providerBreakdown = providerBreakdown != null ? providerBreakdown : new ArrayList<>();
    }

    public double getTotalEstimatedCostUsd() { return totalEstimatedCost; }
    public List<ModelCostSummaryView> getModelSummaries() { return modelBreakdown; }
    public List<ProviderCostSummaryView> getProviderSummaries() { return providerBreakdown; }
    public String getExecutionMode() { return activeMode; }
}
