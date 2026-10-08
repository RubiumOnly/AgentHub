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
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
