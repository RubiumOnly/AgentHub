package com.agenthub.shared.filter;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tracks invocation counts and migration targets for deprecated legacy REST routes.
 * Used in Phase 1 to verify zero production invocations before physical deletion.
 */
@Component
public class LegacyEndpointCallTracker {

    public static final Map<String, String> LEGACY_ROUTE_MIGRATION_MAP;
    static {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("/api/workspace", "/api/workspaces/{workspaceId}");
        map.put("/api/im", "/api/conversations");
        map.put("/api/sandbox", "/api/deployments");
        map.put("/api/runs", "/api/executions/runs");
        LEGACY_ROUTE_MIGRATION_MAP = Collections.unmodifiableMap(map);
    }

    private final ConcurrentMap<String, AtomicLong> callCounts = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AtomicLong> blockedInProdCounts = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> lastAccessedAt = new ConcurrentHashMap<>();
    private final AtomicLong totalCalls = new AtomicLong(0);

    public LegacyEndpointCallTracker() {
        for (String prefix : LEGACY_ROUTE_MIGRATION_MAP.keySet()) {
            callCounts.put(prefix, new AtomicLong(0));
            blockedInProdCounts.put(prefix, new AtomicLong(0));
        }
    }

    public String matchLegacyPrefix(String requestUri) {
        if (requestUri == null || requestUri.isBlank()) {
            return null;
        }
        if (requestUri.equals("/api/workspace") || requestUri.startsWith("/api/workspace/")) {
            return "/api/workspace";
        }
        if (requestUri.equals("/api/im") || requestUri.startsWith("/api/im/")) {
            return "/api/im";
        }
        if (requestUri.equals("/api/sandbox") || requestUri.startsWith("/api/sandbox/")) {
            return "/api/sandbox";
        }
        if (requestUri.equals("/api/runs") || requestUri.startsWith("/api/runs/")) {
            return "/api/runs";
        }
        return null;
    }

    public String resolveMigrationTarget(String legacyPrefix) {
        return LEGACY_ROUTE_MIGRATION_MAP.getOrDefault(legacyPrefix, "/api/v1/openapi.json");
    }

    public void recordCall(String legacyPrefix, boolean blockedWith410) {
        if (legacyPrefix == null) {
            return;
        }
        callCounts.computeIfAbsent(legacyPrefix, k -> new AtomicLong(0)).incrementAndGet();
        if (blockedWith410) {
            blockedInProdCounts.computeIfAbsent(legacyPrefix, k -> new AtomicLong(0)).incrementAndGet();
        }
        lastAccessedAt.put(legacyPrefix, Instant.now().toString());
        totalCalls.incrementAndGet();
    }

    public long getCallCount(String legacyPrefix) {
        AtomicLong counter = callCounts.get(legacyPrefix);
        return counter != null ? counter.get() : 0L;
    }

    public long getBlockedCount(String legacyPrefix) {
        AtomicLong counter = blockedInProdCounts.get(legacyPrefix);
        return counter != null ? counter.get() : 0L;
    }

    public long getTotalCalls() {
        return totalCalls.get();
    }

    public Map<String, Object> snapshotTelemetry() {
        Map<String, Object> routes = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : LEGACY_ROUTE_MIGRATION_MAP.entrySet()) {
            String prefix = entry.getKey();
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("deprecatedPrefix", prefix);
            detail.put("migrationTarget", entry.getValue());
            detail.put("totalCalls", getCallCount(prefix));
            detail.put("blocked410Calls", getBlockedCount(prefix));
            detail.put("lastAccessedAt", lastAccessedAt.get(prefix));
            detail.put("registeredInProdProfile", false);
            routes.put(prefix, detail);
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalLegacyCalls", totalCalls.get());
        summary.put("routes", routes);
        return summary;
    }

    public Map<String, Long> snapshot() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String prefix : LEGACY_ROUTE_MIGRATION_MAP.keySet()) {
            counts.put(prefix, getCallCount(prefix));
        }
        return counts;
    }

    public void reset() {
        totalCalls.set(0);
        lastAccessedAt.clear();
        for (String prefix : LEGACY_ROUTE_MIGRATION_MAP.keySet()) {
            callCounts.put(prefix, new AtomicLong(0));
            blockedInProdCounts.put(prefix, new AtomicLong(0));
        }
    }
}
