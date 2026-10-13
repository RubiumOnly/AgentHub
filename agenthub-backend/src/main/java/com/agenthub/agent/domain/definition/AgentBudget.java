package com.agenthub.agent.domain.definition;

import java.io.Serializable;

public class AgentBudget implements Serializable {

    private Integer maxTokens = 32000;
    private Double maxCost = 0.50;

    public AgentBudget() {}

    public AgentBudget(Integer maxTokens, Double maxCost) {
        this.maxTokens = maxTokens;
        this.maxCost = maxCost;
    }

    public Integer getMaxTokens() { return maxTokens; }
    public void setMaxTokens(Integer maxTokens) { this.maxTokens = maxTokens; }
    public Double getMaxCost() { return maxCost; }
    public void setMaxCost(Double maxCost) { this.maxCost = maxCost; }
}
