package com.agenthub.agent.dto;

import java.time.LocalDateTime;

public class ProviderView {
    private String id;
    private String ownerId;
    private String providerType;
    private String baseUrl;
    private String secretRef;
    private String model;
    private String status;
    private int priority = 100;
    private int weight = 1;
    private String capabilities;
    private Double costPerMillionInput = 0.0;
    private Double costPerMillionOutput = 0.0;
    private String circuitStatus = "CLOSED";
    private Long avgLatencyMs = 0L;
    private LocalDateTime createdAt;

    public ProviderView() {}

    public ProviderView(String id, String ownerId, String providerType, String baseUrl, String secretRef, String model, String status, LocalDateTime createdAt) {
        this.id = id;
        this.ownerId = ownerId;
        this.providerType = providerType;
        this.baseUrl = baseUrl;
        this.secretRef = secretRef;
        this.model = model;
        this.status = status;
        this.createdAt = createdAt;
    }

    public ProviderView(String id,
                        String ownerId,
                        String providerType,
                        String baseUrl,
                        String secretRef,
                        String model,
                        String status,
                        int priority,
                        int weight,
                        String capabilities,
                        Double costPerMillionInput,
                        Double costPerMillionOutput,
                        String circuitStatus,
                        Long avgLatencyMs,
                        LocalDateTime createdAt) {
        this.id = id;
        this.ownerId = ownerId;
        this.providerType = providerType;
        this.baseUrl = baseUrl;
        this.secretRef = secretRef;
        this.model = model;
        this.status = status;
        this.priority = priority;
        this.weight = weight;
        this.capabilities = capabilities;
        this.costPerMillionInput = costPerMillionInput;
        this.costPerMillionOutput = costPerMillionOutput;
        this.circuitStatus = circuitStatus;
        this.avgLatencyMs = avgLatencyMs;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
    public String getProviderType() { return providerType; }
    public void setProviderType(String providerType) { this.providerType = providerType; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getSecretRef() { return secretRef; }
    public void setSecretRef(String secretRef) { this.secretRef = secretRef; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
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
    public String getCircuitStatus() { return circuitStatus; }
    public void setCircuitStatus(String circuitStatus) { this.circuitStatus = circuitStatus; }
    public Long getAvgLatencyMs() { return avgLatencyMs; }
    public void setAvgLatencyMs(Long avgLatencyMs) { this.avgLatencyMs = avgLatencyMs; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
