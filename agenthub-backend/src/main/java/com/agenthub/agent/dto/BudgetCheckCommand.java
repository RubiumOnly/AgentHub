package com.agenthub.agent.dto;

import com.agenthub.shared.contract.ContextCommand;

import java.io.Serializable;

public class BudgetCheckCommand implements ContextCommand, Serializable {

    private String runId;
    private String projectId;
    private Double estimatedCost = 0.05;
    private Integer requiredTokens = 1000;

    public BudgetCheckCommand() {}

    public BudgetCheckCommand(String runId, String projectId, Double estimatedCost, Integer requiredTokens) {
        this.runId = runId;
        this.projectId = projectId;
        this.estimatedCost = estimatedCost != null ? estimatedCost : 0.05;
        this.requiredTokens = requiredTokens != null ? requiredTokens : 1000;
    }

    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }
    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }
    public Double getEstimatedCost() { return estimatedCost; }
    public void setEstimatedCost(Double estimatedCost) { this.estimatedCost = estimatedCost; }
    public Integer getRequiredTokens() { return requiredTokens; }
    public void setRequiredTokens(Integer requiredTokens) { this.requiredTokens = requiredTokens; }

    @Override
    public com.agenthub.shared.contract.BoundedContext boundedContext() {
        return com.agenthub.shared.contract.BoundedContext.AGENT;
    }
}
