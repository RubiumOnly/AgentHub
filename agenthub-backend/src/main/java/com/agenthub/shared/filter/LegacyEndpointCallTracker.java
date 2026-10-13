package com.agenthub.shared.filter;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
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

    public static final String METRIC_LEGACY_ENDPOINT_CALLS_TOTAL = "agenthub_legacy_endpoint_calls_total";

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
    private final MeterRegistry meterRegistry;

    public LegacyEndpointCallTracker() {
        this(null);
    }

    @Autowired
    public LegacyEndpointCallTracker(@Autowired(required = false) MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        for (String prefix : LEGACY_ROUTE_MIGRATION_MAP.keySet()) {
            callCounts.put(prefix, new AtomicLong(0));
            blockedInProdCounts.put(prefix, new AtomicLong(0));
        }
    }

    public String matchLegacyPrefix(String requestUri) {
        if (requestUri == null || requestUri.isBlank()) {
            return null;
        }
        String normalized = requestUri.trim().replaceAll("/{2,}", "/");
        if (normalized.equals("/api/workspace") || normalized.startsWith("/api/workspace/")) {
            return "/api/workspace";
        }
        if (normalized.equals("/api/im") || normalized.startsWith("/api/im/")) {
            return "/api/im";
        }
        if (normalized.equals("/api/sandbox") || normalized.startsWith("/api/sandbox/")) {
            return "/api/sandbox";
        }
        if (normalized.equals("/api/runs") || normalized.startsWith("/api/runs/")) {
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
        if (meterRegistry != null) {
            Counter.builder(METRIC_LEGACY_ENDPOINT_CALLS_TOTAL)
                    .description("Total invocations of deprecated legacy REST endpoints")
                    .tag("prefix", legacyPrefix)
                    .tag("blocked", String.valueOf(blockedWith410))
                    .register(meterRegistry)
                    .increment();
        }
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
