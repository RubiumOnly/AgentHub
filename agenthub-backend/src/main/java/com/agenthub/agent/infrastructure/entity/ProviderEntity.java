package com.agenthub.agent.infrastructure.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "providers")
public class ProviderEntity {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "owner_id", nullable = false, length = 64)
    private String ownerId;

    @Column(name = "provider_type", nullable = false, length = 64)
    private String providerType;

    @Column(name = "base_url", length = 256)
    private String baseUrl;

    @Column(name = "secret_ref", length = 128)
    private String secretRef;

    @Column(length = 128)
    private String model;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(nullable = false)
    private int priority = 100;

    @Column(nullable = false)
    private int weight = 1;

    @Column(length = 256)
    private String capabilities;

    @Column(name = "cost_per_million_input")
    private Double costPerMillionInput = 0.0;

    @Column(name = "cost_per_million_output")
    private Double costPerMillionOutput = 0.0;

    @Column(name = "circuit_status", nullable = false, length = 32)
    private String circuitStatus = "CLOSED";

    @Column(name = "consecutive_failures", nullable = false)
    private int consecutiveFailures = 0;

    @Column(name = "last_failure_at")
    private LocalDateTime lastFailureAt;

    @Column(name = "last_success_at")
    private LocalDateTime lastSuccessAt;

    @Column(name = "avg_latency_ms")
    private Long avgLatencyMs = 0L;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public ProviderEntity() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public ProviderEntity(String id, String ownerId, String providerType, String baseUrl, String secretRef, String model, String status) {
        this.id = id;
        this.ownerId = ownerId;
        this.providerType = providerType;
        this.baseUrl = baseUrl;
        this.secretRef = secretRef;
        this.model = model;
        this.status = status != null ? status : "ACTIVE";
        this.priority = 100;
        this.weight = 1;
        this.circuitStatus = "CLOSED";
        this.consecutiveFailures = 0;
        this.avgLatencyMs = 0L;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
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
    public int getConsecutiveFailures() { return consecutiveFailures; }
    public void setConsecutiveFailures(int consecutiveFailures) { this.consecutiveFailures = consecutiveFailures; }
    public LocalDateTime getLastFailureAt() { return lastFailureAt; }
    public void setLastFailureAt(LocalDateTime lastFailureAt) { this.lastFailureAt = lastFailureAt; }
    public LocalDateTime getLastSuccessAt() { return lastSuccessAt; }
    public void setLastSuccessAt(LocalDateTime lastSuccessAt) { this.lastSuccessAt = lastSuccessAt; }
    public Long getAvgLatencyMs() { return avgLatencyMs; }
    public void setAvgLatencyMs(Long avgLatencyMs) { this.avgLatencyMs = avgLatencyMs; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
