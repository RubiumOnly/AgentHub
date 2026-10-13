package com.agenthub.agent.domain.context;

import java.util.ArrayList;
import java.util.List;

public class AssembledContext {

    private int totalTokens;
    private int totalBudget;
    private boolean withinBudget;
    private List<ContextLayer> layers = new ArrayList<>();
    private String assembledPrompt;

    public AssembledContext() {}

    public AssembledContext(int totalTokens, int totalBudget, boolean withinBudget, List<ContextLayer> layers, String assembledPrompt) {
        this.totalTokens = totalTokens;
        this.totalBudget = totalBudget;
        this.withinBudget = withinBudget;
        this.layers = layers != null ? layers : new ArrayList<>();
        this.assembledPrompt = assembledPrompt;
    }

    public int getTotalTokens() { return totalTokens; }
    public void setTotalTokens(int totalTokens) { this.totalTokens = totalTokens; }
    public int getTotalBudget() { return totalBudget; }
    public void setTotalBudget(int totalBudget) { this.totalBudget = totalBudget; }
    public boolean isWithinBudget() { return withinBudget; }
    public void setWithinBudget(boolean withinBudget) { this.withinBudget = withinBudget; }
    public List<ContextLayer> getLayers() { return layers; }
    public void setLayers(List<ContextLayer> layers) { this.layers = layers != null ? layers : new ArrayList<>(); }
    public String getAssembledPrompt() { return assembledPrompt; }
    public void setAssembledPrompt(String assembledPrompt) { this.assembledPrompt = assembledPrompt; }
}
