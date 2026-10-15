package com.agenthub.agent.domain.provider.model;

/**
 * Standard AgentHub Execution Modes across UI, API, Provider Router, and Evidence Bundles.
 * Strictly segregates simulated development from live production workloads.
 */
public enum ProviderExecutionMode {

    /**
     * Offline review, CI regression, and keyless evaluation.
     * Allows mock responses and deterministic simulated executions.
     */
    DEMO_SIMULATED,

    /**
     * Single-node self-hosted deployment with configured real LLM providers.
     * Strictly forbids mock fallback; real API keys and provider responses required.
     */
    LIVE_SELF_HOSTED,

    /**
     * Enterprise team production environment with RBAC, hard budget quotas,
     * immutable audit trails, and multi-tenant isolation. Strictly live execution.
     */
    LIVE_TEAM;

    public static ProviderExecutionMode fromString(String val) {
        if (val == null || val.isBlank()) {
            return LIVE_SELF_HOSTED;
        }
        String normalized = val.trim().toUpperCase();
        return switch (normalized) {
            case "DEMO_SIMULATED", "SIMULATED", "DEMO" -> DEMO_SIMULATED;
            case "LIVE_TEAM", "TEAM" -> LIVE_TEAM;
            case "LIVE_SELF_HOSTED", "LIVE", "LIVE_PROVIDER", "PROD" -> LIVE_SELF_HOSTED;
            default -> LIVE_SELF_HOSTED;
        };
    }

    public boolean isLive() {
        return this != DEMO_SIMULATED;
    }
}
