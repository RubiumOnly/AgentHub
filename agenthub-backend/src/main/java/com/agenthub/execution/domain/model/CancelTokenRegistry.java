package com.agenthub.execution.domain.model;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe registry for active workflow Run cancellation tokens.
 */
@Component
public class CancelTokenRegistry {

    private final Map<String, CancelToken> tokens = new ConcurrentHashMap<>();

    public CancelToken getOrCreate(String runId) {
        return tokens.computeIfAbsent(runId, CancelToken::new);
    }

    public Optional<CancelToken> get(String runId) {
        return Optional.ofNullable(tokens.get(runId));
    }

    public boolean cancel(String runId, String reason) {
        CancelToken token = tokens.get(runId);
        if (token != null) {
            return token.cancel(reason);
        }
        // If not in registry yet, register a cancelled token so subsequent starts abort immediately
        CancelToken preCancelled = new CancelToken(runId);
        preCancelled.cancel(reason);
        tokens.put(runId, preCancelled);
        return true;
    }

    public boolean timeout(String runId, String reason) {
        CancelToken token = tokens.get(runId);
        if (token != null) {
            return token.timeout(reason);
        }
        CancelToken preTimedOut = new CancelToken(runId);
        preTimedOut.timeout(reason);
        tokens.put(runId, preTimedOut);
        return true;
    }

    public void remove(String runId) {
        tokens.remove(runId);
    }

    public int activeCount() {
        return tokens.size();
    }
}
