package com.agenthub.ops.dto;

import java.util.Map;

/**
 * Aggregated observability summary view for operations, SLO monitoring, and health indicators.
 */
public class OpsMetricsSummaryView {

    private String systemHealth;
    private int activeRuns;
    private int activeSseConnections;
    private double workspaceDiskAvailableRatio;
    private Map<String, String> circuitBreakerStates;
    private long totalRuns;
    private long totalSteps;
    private long totalTokensConsumed;
    private long totalDeployments;
    private Map<String, Long> rbacEvaluations;
    private long auditLogsRecorded;
    private long uptimeSeconds;
    private long jvmMemoryUsedMb;
    private long jvmMemoryTotalMb;
    private long jvmMemoryMaxMb;
    private String sloStatus;

    public OpsMetricsSummaryView() {}

    public OpsMetricsSummaryView(String systemHealth, Map<String, String> circuitBreakerStates, Map<String, Long> rbacEvaluations, String sloStatus) {
        this.systemHealth = systemHealth;
        this.circuitBreakerStates = circuitBreakerStates;
        this.rbacEvaluations = rbacEvaluations;
        this.sloStatus = sloStatus;
    }

    public String getSystemHealth() { return systemHealth; }
    public void setSystemHealth(String systemHealth) { this.systemHealth = systemHealth; }

    public int getActiveRuns() { return activeRuns; }
    public void setActiveRuns(int activeRuns) { this.activeRuns = activeRuns; }

    public int getActiveSseConnections() { return activeSseConnections; }
    public void setActiveSseConnections(int activeSseConnections) { this.activeSseConnections = activeSseConnections; }

    public double getWorkspaceDiskAvailableRatio() { return workspaceDiskAvailableRatio; }
    public void setWorkspaceDiskAvailableRatio(double workspaceDiskAvailableRatio) { this.workspaceDiskAvailableRatio = workspaceDiskAvailableRatio; }

    public Map<String, String> getCircuitBreakerStates() { return circuitBreakerStates; }
    public void setCircuitBreakerStates(Map<String, String> circuitBreakerStates) { this.circuitBreakerStates = circuitBreakerStates; }

    public long getTotalRuns() { return totalRuns; }
    public void setTotalRuns(long totalRuns) { this.totalRuns = totalRuns; }

    public long getTotalSteps() { return totalSteps; }
    public void setTotalSteps(long totalSteps) { this.totalSteps = totalSteps; }

    public long getTotalTokensConsumed() { return totalTokensConsumed; }
    public void setTotalTokensConsumed(long totalTokensConsumed) { this.totalTokensConsumed = totalTokensConsumed; }

    public long getTotalDeployments() { return totalDeployments; }
    public void setTotalDeployments(long totalDeployments) { this.totalDeployments = totalDeployments; }

    public Map<String, Long> getRbacEvaluations() { return rbacEvaluations; }
    public void setRbacEvaluations(Map<String, Long> rbacEvaluations) { this.rbacEvaluations = rbacEvaluations; }

    public long getAuditLogsRecorded() { return auditLogsRecorded; }
    public void setAuditLogsRecorded(long auditLogsRecorded) { this.auditLogsRecorded = auditLogsRecorded; }

    public long getUptimeSeconds() { return uptimeSeconds; }
    public void setUptimeSeconds(long uptimeSeconds) { this.uptimeSeconds = uptimeSeconds; }

    public long getJvmMemoryUsedMb() { return jvmMemoryUsedMb; }
    public void setJvmMemoryUsedMb(long jvmMemoryUsedMb) { this.jvmMemoryUsedMb = jvmMemoryUsedMb; }

    public long getJvmMemoryTotalMb() { return jvmMemoryTotalMb; }
    public void setJvmMemoryTotalMb(long jvmMemoryTotalMb) { this.jvmMemoryTotalMb = jvmMemoryTotalMb; }

    public long getJvmMemoryMaxMb() { return jvmMemoryMaxMb; }
    public void setJvmMemoryMaxMb(long jvmMemoryMaxMb) { this.jvmMemoryMaxMb = jvmMemoryMaxMb; }

    public String getSloStatus() { return sloStatus; }
    public void setSloStatus(String sloStatus) { this.sloStatus = sloStatus; }
}
