package com.agenthub.agent.dto;

import java.io.Serializable;
import java.time.LocalDateTime;

public class ProviderProbeResultView implements Serializable {

    private String providerId;
    private String providerType;
    private String model;
    private String status; // "UP", "DEGRADED", "DOWN"
    private long latencyMs;
    private String circuitStatus; // "CLOSED", "HALF_OPEN", "OPEN"
    private boolean simulated;
    private String message;
    private LocalDateTime checkedAt;

    public ProviderProbeResultView() {}

    public ProviderProbeResultView(String providerId,
                                   String providerType,
                                   String model,
                                   String status,
                                   long latencyMs,
                                   String circuitStatus,
                                   boolean simulated,
                                   String message,
                                   LocalDateTime checkedAt) {
        this.providerId = providerId;
        this.providerType = providerType;
        this.model = model;
        this.status = status;
        this.latencyMs = latencyMs;
        this.circuitStatus = circuitStatus;
        this.simulated = simulated;
        this.message = message;
        this.checkedAt = checkedAt != null ? checkedAt : LocalDateTime.now();
    }

    public String getProviderId() { return providerId; }
    public void setProviderId(String providerId) { this.providerId = providerId; }
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
    public LocalDateTime getCheckedAt() { return checkedAt; }
    public void setCheckedAt(LocalDateTime checkedAt) { this.checkedAt = checkedAt; }
}
