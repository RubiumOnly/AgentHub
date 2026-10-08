package com.agenthub.agent.dto;

public class ProviderUpdateCommand {
    private String baseUrl;
    private String secretRef;
    private String model;
    private Integer priority;
    private Integer weight;
    private String capabilities;
    private String status;
    private Double costPerMillionInput;
    private Double costPerMillionOutput;

    public ProviderUpdateCommand() {}

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getSecretRef() { return secretRef; }
    public void setSecretRef(String secretRef) { this.secretRef = secretRef; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public Integer getPriority() { return priority; }
    public void setPriority(Integer priority) { this.priority = priority; }
    public Integer getWeight() { return weight; }
    public void setWeight(Integer weight) { this.weight = weight; }
    public String getCapabilities() { return capabilities; }
    public void setCapabilities(String capabilities) { this.capabilities = capabilities; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Double getCostPerMillionInput() { return costPerMillionInput; }
    public void setCostPerMillionInput(Double costPerMillionInput) { this.costPerMillionInput = costPerMillionInput; }
    public Double getCostPerMillionOutput() { return costPerMillionOutput; }
    public void setCostPerMillionOutput(Double costPerMillionOutput) { this.costPerMillionOutput = costPerMillionOutput; }
}
