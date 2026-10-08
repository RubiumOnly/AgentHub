package com.agenthub.agent.dto;

import java.util.List;

public class RouteDecisionView {
    private String primaryProviderId;
    private String primaryProviderName;
    private String primaryProviderType;
    private String primaryModel;
    private int primaryPriority;
    private double inputPricePerMillion;
    private double outputPricePerMillion;
    private List<String> candidateProviderIds;

    public RouteDecisionView() {}

    public RouteDecisionView(String primaryProviderId,
                             String primaryProviderName,
                             String primaryProviderType,
                             String primaryModel,
                             int primaryPriority,
                             double inputPricePerMillion,
                             double outputPricePerMillion,
                             List<String> candidateProviderIds) {
        this.primaryProviderId = primaryProviderId;
        this.primaryProviderName = primaryProviderName;
        this.primaryProviderType = primaryProviderType;
        this.primaryModel = primaryModel;
        this.primaryPriority = primaryPriority;
        this.inputPricePerMillion = inputPricePerMillion;
        this.outputPricePerMillion = outputPricePerMillion;
        this.candidateProviderIds = candidateProviderIds;
    }

    public String getPrimaryProviderId() { return primaryProviderId; }
    public String getPrimaryProviderName() { return primaryProviderName; }
    public String getPrimaryProviderType() { return primaryProviderType; }
    public String getPrimaryModel() { return primaryModel; }
    public int getPrimaryPriority() { return primaryPriority; }
    public double getInputPricePerMillion() { return inputPricePerMillion; }
    public double getOutputPricePerMillion() { return outputPricePerMillion; }
    public List<String> getCandidateProviderIds() { return candidateProviderIds; }
}
