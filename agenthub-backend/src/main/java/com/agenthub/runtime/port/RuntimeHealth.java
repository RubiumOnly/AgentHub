package com.agenthub.runtime.port;

import java.time.LocalDateTime;

/**
 * Health check report for an Agent Runtime.
 */
public class RuntimeHealth {
    private final boolean healthy;
    private final String message;
    private final long latencyMs;
    private final LocalDateTime checkedAt;

    public RuntimeHealth(boolean healthy, String message, long latencyMs) {
        this.healthy = healthy;
        this.message = message;
        this.latencyMs = latencyMs;
        this.checkedAt = LocalDateTime.now();
    }

    public static RuntimeHealth ok(String message, long latencyMs) {
        return new RuntimeHealth(true, message, latencyMs);
    }

    public static RuntimeHealth degraded(String message, long latencyMs) {
        return new RuntimeHealth(false, message, latencyMs);
    }

    public boolean isHealthy() { return healthy; }
    public String getMessage() { return message; }
    public long getLatencyMs() { return latencyMs; }
    public LocalDateTime getCheckedAt() { return checkedAt; }
}
