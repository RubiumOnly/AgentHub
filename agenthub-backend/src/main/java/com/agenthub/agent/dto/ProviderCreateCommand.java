package com.agenthub.agent.dto;

public class ProviderCreateCommand {
    private String providerType;
    private String baseUrl;
    private String secretRef;
    private String model;
    private int priority = 100;
    private int weight = 1;
    private String capabilities = "general";
    private Double costPerMillionInput = 0.0;
    private Double costPerMillionOutput = 0.0;

    public ProviderCreateCommand() {}

    public String getProviderType() { return providerType; }
    public void setProviderType(String providerType) { this.providerType = providerType; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getSecretRef() { return secretRef; }
    public void setSecretRef(String secretRef) { this.secretRef = secretRef; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }
    public int getWeight() { return weight; }
    public void setWeight(int weight) { this.weight = weight; }
    public String getCapabilities() { return capabilities; }
    public void setCapabilities(String capabilities) { this.capabilities = capabilities; }
    public Double getCostPerMillionInput() { return costPerMillionInput; }
    public void setCostPerMillionInput(Double costPerMillionInput) { this.costPerMillionInput = costPerMillionInput; }
    public Double getCostPerMillionOutput() { return costPerMillionOutput; }
    public void setCostPerMillionOutput(Double costPerMillionOutput) { this.costPerMillionOutput = costPerMillionOutput; }
}
