package com.agenthub.shared.contract;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Authoritative registry of AgentHub bounded contexts (Phase 1 module convergence).
 * Distinguishes the 8 core production contexts from isolated collaboration extensions.
 */
public enum BoundedContext {

    IDENTITY(
            "identity",
            "com.agenthub.identity",
            1000, 1999,
            "User",
            "/api/auth",
            true
    ),
    PROJECT(
            "project",
            "com.agenthub.project",
            2000, 3009,
            "Project / Workspace",
            "/api/projects, /api/workspaces",
            true
    ),
    AUDIT(
            "audit",
            "com.agenthub.audit",
            3010, 3999,
            "Artifact",
            "/api/audit/artifacts",
            true
    ),
    CONVERSATION_EXTENSION(
            "conversation",
            "com.agenthub.conversation",
            4000, 4004,
            "Conversation / Message",
            "/api/conversations",
            false
    ),
    TEAM_EXTENSION(
            "team",
            "com.agenthub.team",
            4005, 4999,
            "Team",
            "/api/teams",
            false
    ),
    AGENT(
            "agent",
            "com.agenthub.agent",
            5000, 5999,
            "AgentDefinition / Provider / TokenUsage",
            "/api/agents, /api/providers, /api/token-usages",
            true
    ),
    ORCHESTRATION(
            "orchestration",
            "com.agenthub.orchestration",
            6000, 6019,
            "WorkflowDefinition / Approval",
            "/api/workflows, /api/approvals",
            true
    ),
    EXECUTION(
            "execution",
            "com.agenthub.execution",
            6020, 6999,
            "WorkflowRun / StepRun / RunEvent",
            "/api/executions",
            true
    ),
    SANDBOX(
            "sandbox",
            "com.agenthub.sandbox",
            7000, 7999,
            "Deployment",
            "/api/deployments",
            true
    ),
    OPS(
            "ops",
            "com.agenthub.ops",
            8000, 8999,
            "SystemHealth / ApiContract / BackupManifest",
            "/api/system, /api/v1/openapi.json",
            true
    );

    private final String contextKey;
    private final String basePackage;
    private final int errorCodeStart;
    private final int errorCodeEnd;
    private final String primaryAggregate;
    private final String restPrefixSummary;
    private final boolean productionCore;

    BoundedContext(String contextKey,
                   String basePackage,
                   int errorCodeStart,
                   int errorCodeEnd,
                   String primaryAggregate,
                   String restPrefixSummary,
                   boolean productionCore) {
        this.contextKey = contextKey;
        this.basePackage = basePackage;
        this.errorCodeStart = errorCodeStart;
        this.errorCodeEnd = errorCodeEnd;
        this.primaryAggregate = primaryAggregate;
        this.restPrefixSummary = restPrefixSummary;
        this.productionCore = productionCore;
    }

    public String getContextKey() {
        return contextKey;
    }

    public String getBasePackage() {
        return basePackage;
    }

    public int getErrorCodeStart() {
        return errorCodeStart;
    }

    public int getErrorCodeEnd() {
        return errorCodeEnd;
    }

    public String getPrimaryAggregate() {
        return primaryAggregate;
    }

    public String getRestPrefixSummary() {
        return restPrefixSummary;
    }

    public boolean isProductionCore() {
        return productionCore;
    }

    public String getCode() {
        return contextKey;
    }

    public String getRootPackage() {
        return basePackage;
    }

    public String getErrorCodePrefix() {
        return errorCodeStart + "-" + errorCodeEnd;
    }

    public String getDescription() {
        return primaryAggregate + " (" + restPrefixSummary + ")";
    }

    public static List<BoundedContext> productionCoreContexts() {
        return Arrays.stream(values())
                .filter(BoundedContext::isProductionCore)
                .collect(Collectors.toList());
    }
}
