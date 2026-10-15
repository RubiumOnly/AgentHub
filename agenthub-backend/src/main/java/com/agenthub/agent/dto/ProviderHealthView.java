package com.agenthub.agent.dto;

import java.io.Serializable;

public class ProviderHealthView implements Serializable {

    private String providerId;
    private String name;
    private String providerType;
    private String model;
    private String status; // "UP", "DEGRADED", "DOWN"
    private long latencyMs;
    private String circuitStatus; // "CLOSED", "HALF_OPEN", "OPEN"
    private boolean simulated;
    private String message;

    public ProviderHealthView() {}

    public ProviderHealthView(String providerId,
                              String name,
                              String providerType,
                              String model,
                              String status,
                              long latencyMs,
                              String circuitStatus,
                              boolean simulated,
                              String message) {
        this.providerId = providerId;
        this.name = name;
        this.providerType = providerType;
        this.model = model;
        this.status = status;
        this.latencyMs = latencyMs;
        this.circuitStatus = circuitStatus;
        this.simulated = simulated;
        this.message = message;
    }

    public String getProviderId() { return providerId; }
    public void setProviderId(String providerId) { this.providerId = providerId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getProviderType() { return providerType; }
    public void setProviderType(String providerType) { this.providerType = providerType; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public long getLatencyMs() { return latencyMs; }
    public void setLatencyMs(long latencyMs) { this.latencyMs = latencyMs; }
    public String getCircuitStatus() { return circuitStatus; }
    public void setCircuitStatus(String circuitStatus) { this.circuitStatus = circuitStatus; }
    public boolean isSimulated() { return simulated; }
    public void setSimulated(boolean simulated) { this.simulated = simulated; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
