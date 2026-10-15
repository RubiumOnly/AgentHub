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
    private final ConcurrentMap<String, AtomicInteger> circuitBreakerStateGauges = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Counter> rbacCounterCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Counter> auditCounterCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Counter> backupCounterCache = new ConcurrentHashMap<>();
    private final AtomicInteger workspaceDiskAvailablePercent = new AtomicInteger(85);

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

        Gauge.builder("agenthub.workspace.disk.avail.ratio", workspaceDiskAvailablePercent, v -> v.get() / 100.0)
                .tag("mountpoint", "/var/lib/agenthub")
                .description("Available disk space ratio on controlled workspace mountpoint")
                .register(meterRegistry);

        // Core Timers (publish both client percentiles and Prometheus histogram buckets for histogram_quantile)
        this.workspaceLockWaitTimer = Timer.builder("agenthub.workspace.lock.wait")
                .description("Time spent waiting for workspace lease lock acquisition")
                .publishPercentiles(0.5, 0.9, 0.95, 0.99)
                .publishPercentileHistogram()
                .register(meterRegistry);
    }

    public void incrementActiveRuns() {
        activeRunsGauge.incrementAndGet();
    }

    public void setActiveRunsForDrill(int count) {
        activeRunsGauge.set(Math.max(0, count));
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

    public void recordProviderCircuitBreakerState(String provider, String state, boolean active) {
        String safeProvider = (provider == null || provider.isBlank()) ? "DEFAULT" : provider.toLowerCase();
        String safeState = (state == null || state.isBlank()) ? "CLOSED" : state.toUpperCase();
        String key = safeProvider + ":" + safeState;
        AtomicInteger gaugeVal = circuitBreakerStateGauges.computeIfAbsent(key, k -> {
            AtomicInteger holder = new AtomicInteger(0);
            Gauge.builder("agenthub.provider.circuit.breaker.state", holder, AtomicInteger::get)
                    .tag("provider", safeProvider)
                    .tag("state", safeState)
                    .description("Circuit breaker state indicator (1 = active in state, 0 = inactive)")
                    .register(meterRegistry);
            return holder;
        });
        gaugeVal.set(active ? 1 : 0);
    }

    public void recordWorkspaceDiskAvailablePercent(int percent) {
        int clamped = Math.max(0, Math.min(100, percent));
        workspaceDiskAvailablePercent.set(clamped);
    }

    public double getWorkspaceDiskAvailableRatio() {
        return workspaceDiskAvailablePercent.get() / 100.0;
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
                        .publishPercentileHistogram()
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

    public void recordRbacEvaluation(String role, String decision, String permission) {
        String safeRole = (role == null || role.isBlank()) ? "UNKNOWN" : role.toUpperCase();
        String safeDecision = (decision == null || decision.isBlank()) ? "UNKNOWN" : decision.toUpperCase();
        String safePerm = (permission == null || permission.isBlank()) ? "UNKNOWN" : permission.toUpperCase();
        String key = safeRole + ":" + safeDecision + ":" + safePerm;
        rbacCounterCache.computeIfAbsent(key, k ->
                Counter.builder("agenthub.rbac.evaluations.total")
                        .tag("role", safeRole)
                        .tag("decision", safeDecision)
                        .tag("permission", safePerm)
                        .description("Total RBAC permission evaluations partitioned by role and decision")
                        .register(meterRegistry)
        ).increment();
    }

    public void recordAuditEvent(String eventType) {
        String safeType = (eventType == null || eventType.isBlank()) ? "UNKNOWN" : eventType.toUpperCase();
        auditCounterCache.computeIfAbsent(safeType, k ->
                Counter.builder("agenthub.audit.events.total")
                        .tag("event_type", safeType)
                        .description("Total immutable audit events recorded partitioned by type")
                        .register(meterRegistry)
        ).increment();
    }

    public void recordBackupOperation(String action, boolean success) {
        String safeAction = (action == null || action.isBlank()) ? "BACKUP" : action.toUpperCase();
        String status = success ? "SUCCESS" : "FAILURE";
        String key = safeAction + ":" + status;
        backupCounterCache.computeIfAbsent(key, k ->
                Counter.builder("agenthub.backup.operations.total")
                        .tag("action", safeAction)
                        .tag("status", status)
                        .description("Total disaster recovery operations partitioned by action and status")
                        .register(meterRegistry)
        ).increment();
    }

    public int getWorkspaceDiskAvailablePercent() {
        return workspaceDiskAvailablePercent.get();
    }

    public java.util.Map<String, String> getCircuitBreakerStates() {
        java.util.Map<String, String> states = new java.util.LinkedHashMap<>();
        for (java.util.Map.Entry<String, java.util.concurrent.atomic.AtomicInteger> entry : circuitBreakerStateGauges.entrySet()) {
            if (entry.getValue().get() == 1) {
                String[] parts = entry.getKey().split(":");
                if (parts.length == 2) {
                    states.put(parts[0], parts[1]);
                }
            }
        }
        return states;
    }

    public java.util.Map<String, Long> getRbacEvaluationCounts() {
        java.util.Map<String, Long> counts = new java.util.LinkedHashMap<>();
        long allowed = 0;
        long denied = 0;
        for (java.util.Map.Entry<String, Counter> entry : rbacCounterCache.entrySet()) {
            if (entry.getKey().contains(":ALLOW:")) {
                allowed += (long) entry.getValue().count();
            } else if (entry.getKey().contains(":DENY:")) {
                denied += (long) entry.getValue().count();
            }
        }
        counts.put("allowed", allowed);
        counts.put("denied", denied);
        return counts;
    }
}
