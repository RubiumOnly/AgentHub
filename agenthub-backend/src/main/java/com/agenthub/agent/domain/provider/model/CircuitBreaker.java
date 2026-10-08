package com.agenthub.agent.domain.provider.model;

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

    public CircuitBreaker(String providerId) {
        this(providerId, 3, 30_000L);
    }

    public CircuitBreaker(String providerId, int failureThreshold, long openTimeoutMs) {
        this.providerId = providerId;
        this.failureThreshold = Math.max(1, failureThreshold);
        this.openTimeoutMs = Math.max(1000L, openTimeoutMs);
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
                    return true;
                }
            }
            return false;
        }

        // HALF_OPEN allows probe request
        return true;
    }

    /**
     * Record a successful invocation.
     */
    public void recordSuccess(long latencyMs) {
        lastSuccessTime.set(System.currentTimeMillis());
        consecutiveFailures.set(0);
        status.set(CircuitStatus.CLOSED);
    }

    /**
     * Record a failure (429, 5xx, timeout, or network exception).
     */
    public void recordFailure(Throwable cause) {
        lastFailureTime.set(System.currentTimeMillis());
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
        status.set(CircuitStatus.OPEN);
    }

    /**
     * Reset the circuit breaker back to CLOSED state.
     */
    public void reset() {
        consecutiveFailures.set(0);
        status.set(CircuitStatus.CLOSED);
    }

    public String getProviderId() { return providerId; }
    public CircuitStatus getStatus() { return status.get(); }
    public int getConsecutiveFailures() { return consecutiveFailures.get(); }
    public long getLastFailureTime() { return lastFailureTime.get(); }
    public long getLastSuccessTime() { return lastSuccessTime.get(); }
    public int getFailureThreshold() { return failureThreshold; }
    public long getOpenTimeoutMs() { return openTimeoutMs; }
}
