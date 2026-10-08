package com.agenthub.agent.domain.provider.model;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe Circuit Breaker state machine for high-availability LLM providers.
 * Manages CLOSED, OPEN, and HALF_OPEN transitions, failure tripping, and recovery probing.
 */
public class CircuitBreaker {

    private final String providerId;
    private final int failureThreshold;
    private final long openTimeoutMs;

    private final AtomicReference<CircuitStatus> status = new AtomicReference<>(CircuitStatus.CLOSED);
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicLong lastFailureTime = new AtomicLong(0L);
    private final AtomicLong lastSuccessTime = new AtomicLong(0L);
    private final AtomicLong lastLatencyMs = new AtomicLong(0L);
    private final AtomicBoolean probeInFlight = new AtomicBoolean(false);

    public CircuitBreaker(String providerId) {
        this(providerId, 3, 30_000L);
    }

    public CircuitBreaker(String providerId, int failureThreshold, long openTimeoutMs) {
        this.providerId = providerId;
        this.failureThreshold = Math.max(1, failureThreshold);
        this.openTimeoutMs = Math.max(1L, openTimeoutMs);
    }

    /**
     * Determines whether a request is allowed to proceed to this provider.
     */
    public boolean allowRequest() {
        CircuitStatus current = status.get();
        if (current == CircuitStatus.CLOSED) {
            return true;
        }

        if (current == CircuitStatus.OPEN) {
            long now = System.currentTimeMillis();
            if (now - lastFailureTime.get() >= openTimeoutMs) {
                // Try transition to HALF_OPEN to probe recovery
                if (status.compareAndSet(CircuitStatus.OPEN, CircuitStatus.HALF_OPEN)) {
                    probeInFlight.set(true);
                    return true;
                }
            }
            return false;
        }

        // HALF_OPEN allows exactly ONE probe request in flight to avoid stampede
        return probeInFlight.compareAndSet(false, true);
    }

    /**
     * Record a successful invocation.
     */
    public void recordSuccess(long latencyMs) {
        lastSuccessTime.set(System.currentTimeMillis());
        lastLatencyMs.set(Math.max(0L, latencyMs));
        consecutiveFailures.set(0);
        probeInFlight.set(false);
        status.set(CircuitStatus.CLOSED);
    }

    /**
     * Record a failure (429, 5xx, timeout, or network exception).
     */
    public void recordFailure(Throwable cause) {
        lastFailureTime.set(System.currentTimeMillis());
        probeInFlight.set(false);
        CircuitStatus current = status.get();
        if (current == CircuitStatus.HALF_OPEN) {
            // Immediate re-trip to OPEN if probe fails
            status.set(CircuitStatus.OPEN);
            return;
        }
        int failures = consecutiveFailures.incrementAndGet();
        if (failures >= failureThreshold) {
            status.set(CircuitStatus.OPEN);
        }
    }

    /**
     * Explicitly trip the circuit to OPEN state (e.g. for testing or severe 429).
     */
    public void trip() {
        lastFailureTime.set(System.currentTimeMillis());
        consecutiveFailures.set(failureThreshold);
        probeInFlight.set(false);
        status.set(CircuitStatus.OPEN);
    }

    /**
     * Reset the circuit breaker back to CLOSED state.
     */
    public void reset() {
        consecutiveFailures.set(0);
        probeInFlight.set(false);
        status.set(CircuitStatus.CLOSED);
    }

    public String getProviderId() { return providerId; }
    public CircuitStatus getStatus() { return status.get(); }
    public int getConsecutiveFailures() { return consecutiveFailures.get(); }
    public long getLastFailureTime() { return lastFailureTime.get(); }
    public long getLastSuccessTime() { return lastSuccessTime.get(); }
    public long getLastLatencyMs() { return lastLatencyMs.get(); }
    public void setLastLatencyMs(long latencyMs) { this.lastLatencyMs.set(Math.max(0L, latencyMs)); }
    public int getFailureThreshold() { return failureThreshold; }
    public long getOpenTimeoutMs() { return openTimeoutMs; }
}
