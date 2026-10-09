package com.agenthub.identity.infrastructure.security;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * High-performance sliding-window in-memory rate limiter for authentication endpoints.
 */
@Component
public class AuthRateLimiter {

    private final ConcurrentHashMap<String, Deque<Long>> requestHistory = new ConcurrentHashMap<>();

    /**
     * Checks if the given key is allowed to make a request, and records the attempt if allowed.
     *
     * @param key           identifier (e.g. "login:ip", "register:ip")
     * @param maxRequests   maximum allowed requests within the time window
     * @param windowSeconds time window size in seconds
     * @return true if request is permitted, false if rate limited
     */
    public boolean tryAcquire(String key, int maxRequests, long windowSeconds) {
        long now = Instant.now().getEpochSecond();
        long windowStart = now - windowSeconds;

        Deque<Long> timestamps = requestHistory.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());

        // Evict expired entries
        while (!timestamps.isEmpty() && timestamps.peekFirst() < windowStart) {
            timestamps.pollFirst();
        }

        if (timestamps.size() >= maxRequests) {
            return false;
        }

        timestamps.addLast(now);
        return true;
    }

    public void clear(String key) {
        requestHistory.remove(key);
    }
}
