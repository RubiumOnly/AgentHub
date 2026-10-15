package com.agenthub.execution.dto;

import java.util.List;

public class HierarchicalMemorySummary {
    private String projectId;
    private String runId;
    private int totalTiers;
    private int totalSteps;
    private int compressedSteps;
    private long estimatedTokens;
    private boolean budgetExceeded;
    private String aociCognitionStatus;
    private List<String> activeContextSources;

    public HierarchicalMemorySummary() {}

    public HierarchicalMemorySummary(String projectId, String runId, int totalTiers, int totalSteps,
                                     int compressedSteps, long estimatedTokens, boolean budgetExceeded,
                                     String aociCognitionStatus, List<String> activeContextSources) {
        this.projectId = projectId;
        this.runId = runId;
        this.totalTiers = totalTiers;
        this.totalSteps = totalSteps;
        this.compressedSteps = compressedSteps;
        this.estimatedTokens = estimatedTokens;
        this.budgetExceeded = budgetExceeded;
        this.aociCognitionStatus = aociCognitionStatus;
        this.activeContextSources = activeContextSources;
    }

    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }

    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }

    public int getTotalTiers() { return totalTiers; }
    public void setTotalTiers(int totalTiers) { this.totalTiers = totalTiers; }

    public int getTotalSteps() { return totalSteps; }
    public void setTotalSteps(int totalSteps) { this.totalSteps = totalSteps; }

    public int getCompressedSteps() { return compressedSteps; }
    public void setCompressedSteps(int compressedSteps) { this.compressedSteps = compressedSteps; }

    public long getEstimatedTokens() { return estimatedTokens; }
    public void setEstimatedTokens(long estimatedTokens) { this.estimatedTokens = estimatedTokens; }

    public boolean isBudgetExceeded() { return budgetExceeded; }
    public void setBudgetExceeded(boolean budgetExceeded) { this.budgetExceeded = budgetExceeded; }

    public String getAociCognitionStatus() { return aociCognitionStatus; }
    public void setAociCognitionStatus(String aociCognitionStatus) { this.aociCognitionStatus = aociCognitionStatus; }

    public List<String> getActiveContextSources() { return activeContextSources; }
    public void setActiveContextSources(List<String> activeContextSources) { this.activeContextSources = activeContextSources; }
}
