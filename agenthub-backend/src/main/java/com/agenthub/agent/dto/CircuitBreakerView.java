package com.agenthub.agent.dto;

import java.io.Serializable;

public class CircuitBreakerView implements Serializable {

    private String providerId;
    private String status; // "CLOSED", "HALF_OPEN", "OPEN"
    private int consecutiveFailures;
    private int failureThreshold;
    private long openTimeoutMs;
    private long lastFailureTime;
    private long lastSuccessTime;
    private long lastLatencyMs;

    public CircuitBreakerView() {}

    public CircuitBreakerView(String providerId,
                              String status,
                              int consecutiveFailures,
                              int failureThreshold,
                              long openTimeoutMs,
                              long lastFailureTime,
                              long lastSuccessTime,
                              long lastLatencyMs) {
        this.providerId = providerId;
        this.status = status;
        this.consecutiveFailures = consecutiveFailures;
        this.failureThreshold = failureThreshold;
        this.openTimeoutMs = openTimeoutMs;
        this.lastFailureTime = lastFailureTime;
        this.lastSuccessTime = lastSuccessTime;
        this.lastLatencyMs = lastLatencyMs;
    }

    public String getProviderId() { return providerId; }
    public void setProviderId(String providerId) { this.providerId = providerId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getState() { return status; }
    public boolean isRequestAllowed() { return "CLOSED".equalsIgnoreCase(status); }
    public int getConsecutiveFailures() { return consecutiveFailures; }
    public void setConsecutiveFailures(int consecutiveFailures) { this.consecutiveFailures = consecutiveFailures; }
    public int getFailureThreshold() { return failureThreshold; }
    public void setFailureThreshold(int failureThreshold) { this.failureThreshold = failureThreshold; }
    public long getOpenTimeoutMs() { return openTimeoutMs; }
    public void setOpenTimeoutMs(long openTimeoutMs) { this.openTimeoutMs = openTimeoutMs; }
    public long getLastFailureTime() { return lastFailureTime; }
    public void setLastFailureTime(long lastFailureTime) { this.lastFailureTime = lastFailureTime; }
    public long getLastSuccessTime() { return lastSuccessTime; }
    public void setLastSuccessTime(long lastSuccessTime) { this.lastSuccessTime = lastSuccessTime; }
    public long getLastLatencyMs() { return lastLatencyMs; }
    public void setLastLatencyMs(long lastLatencyMs) { this.lastLatencyMs = lastLatencyMs; }
}
