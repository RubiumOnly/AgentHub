package com.agenthub.infrastructure.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Enterprise Production Observability Collector.
 * Records high-frequency execution metrics, latency distributions,
 * SSE connection counts, and resource contention via Micrometer.
 */
@Component
public class AgentHubMetricsCollector {

    private final MeterRegistry meterRegistry;

    // Gauges
    private final AtomicInteger activeRunsGauge = new AtomicInteger(0);
    private final AtomicInteger activeSseConnectionsGauge = new AtomicInteger(0);

    // Dynamic metrics caches
    private final ConcurrentMap<String, Counter> runCounterCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Counter> stepCounterCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Timer> stepTimerCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Counter> tokenCounterCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Counter> deploymentCounterCache = new ConcurrentHashMap<>();

    private final Timer workspaceLockWaitTimer;

    public AgentHubMetricsCollector(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        // Register core Gauges
        Gauge.builder("agenthub.runs.active", activeRunsGauge, AtomicInteger::get)
                .description("Number of currently executing Workflow Runs")
                .register(meterRegistry);

        Gauge.builder("agenthub.sse.connections.active", activeSseConnectionsGauge, AtomicInteger::get)
                .description("Number of currently active SSE streaming subscribers")
                .register(meterRegistry);

        // Core Timers
        this.workspaceLockWaitTimer = Timer.builder("agenthub.workspace.lock.wait")
                .description("Time spent waiting for workspace lease lock acquisition")
                .publishPercentiles(0.5, 0.9, 0.95, 0.99)
                .register(meterRegistry);
    }

    public void incrementActiveRuns() {
        activeRunsGauge.incrementAndGet();
    }

    public void decrementActiveRuns() {
        activeRunsGauge.updateAndGet(v -> Math.max(0, v - 1));
    }

    public int getActiveRuns() {
        return activeRunsGauge.get();
    }

    public void incrementActiveSseConnections() {
        activeSseConnectionsGauge.incrementAndGet();
    }

    public void decrementActiveSseConnections() {
        activeSseConnectionsGauge.updateAndGet(v -> Math.max(0, v - 1));
    }

    public int getActiveSseConnections() {
        return activeSseConnectionsGauge.get();
    }

    public void recordRunCompleted(String status, long durationMs) {
        String safeStatus = (status == null || status.isBlank()) ? "UNKNOWN" : status.toUpperCase();
        runCounterCache.computeIfAbsent(safeStatus, s ->
                Counter.builder("agenthub.runs.total")
                        .tag("status", s)
                        .description("Total number of workflow runs partitioned by terminal status")
                        .register(meterRegistry)
        ).increment();
    }

    public void recordStepExecution(String nodeType, String status, long durationMs) {
        String safeType = (nodeType == null || nodeType.isBlank()) ? "GENERAL" : nodeType;
        String safeStatus = (status == null || status.isBlank()) ? "UNKNOWN" : status.toUpperCase();
        String counterKey = safeType + ":" + safeStatus;

        stepCounterCache.computeIfAbsent(counterKey, k ->
                Counter.builder("agenthub.steps.total")
                        .tag("node_type", safeType)
                        .tag("status", safeStatus)
                        .description("Total step executions partitioned by type and status")
                        .register(meterRegistry)
        ).increment();

        stepTimerCache.computeIfAbsent(safeType, t ->
                Timer.builder("agenthub.steps.duration")
                        .tag("node_type", t)
                        .description("Execution duration distribution of workflow steps")
                        .publishPercentiles(0.5, 0.9, 0.95, 0.99)
                        .register(meterRegistry)
        ).record(durationMs, TimeUnit.MILLISECONDS);
    }

    public void recordTokenUsage(String provider, String model, long promptTokens, long completionTokens) {
        String safeProvider = (provider == null || provider.isBlank()) ? "DEFAULT" : provider;
        String safeModel = (model == null || model.isBlank()) ? "DEFAULT" : model;

        tokenCounterCache.computeIfAbsent(safeProvider + ":" + safeModel + ":prompt", k ->
                Counter.builder("agenthub.tokens.prompt.total")
                        .tag("provider", safeProvider)
                        .tag("model", safeModel)
                        .description("Total input prompt tokens consumed")
                        .register(meterRegistry)
        ).increment(promptTokens);

        tokenCounterCache.computeIfAbsent(safeProvider + ":" + safeModel + ":completion", k ->
                Counter.builder("agenthub.tokens.completion.total")
                        .tag("provider", safeProvider)
                        .tag("model", safeModel)
                        .description("Total output completion tokens consumed")
                        .register(meterRegistry)
        ).increment(completionTokens);
    }

    public void recordWorkspaceLockWait(long waitTimeMs) {
        workspaceLockWaitTimer.record(waitTimeMs, TimeUnit.MILLISECONDS);
    }

    public void recordDeployment(String status) {
        String safeStatus = (status == null || status.isBlank()) ? "UNKNOWN" : status.toUpperCase();
        deploymentCounterCache.computeIfAbsent(safeStatus, s ->
                Counter.builder("agenthub.deployments.total")
                        .tag("status", s)
                        .description("Total deployments completed partitioned by status")
                        .register(meterRegistry)
        ).increment();
    }
}
