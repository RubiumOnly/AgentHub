package com.agenthub.agent.domain.provider.model;

/**
 * Health check status of an LLM provider.
 */
public class ProviderHealth {

    public enum Status {
        UP,
        DEGRADED,
        DOWN
    }

    private Status status;
    private long latencyMs;
    private String message;

    public ProviderHealth() {}

    public ProviderHealth(Status status, long latencyMs, String message) {
        this.status = status;
        this.latencyMs = latencyMs;
        this.message = message;
    }

    public static ProviderHealth up(long latencyMs, String message) {
        return new ProviderHealth(Status.UP, latencyMs, message);
    }

    public static ProviderHealth degraded(long latencyMs, String message) {
        return new ProviderHealth(Status.DEGRADED, latencyMs, message);
    }

    public static ProviderHealth down(String message) {
        return new ProviderHealth(Status.DOWN, -1L, message);
    }

    public boolean isAvailable() {
        return status != Status.DOWN;
    }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public long getLatencyMs() { return latencyMs; }
    public void setLatencyMs(long latencyMs) { this.latencyMs = latencyMs; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
