package com.agenthub.shared.contract.sdk;

import com.agenthub.shared.contract.ApiContractHeaders;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Standardized Product Release Manifest defining the authoritative v1.0.0-RELEASE
 * stable contract surface, migration pathways, and compatibility boundaries.
 */
public record AgentHubReleaseManifest(
        String releaseVersion,
        String apiVersion,
        String releaseDateUtc,
        List<String> supportedExecutionModes,
        List<String> stableContexts,
        List<String> stableResources,
        List<String> sdkClientContracts,
        List<String> extensionContracts,
        Map<String, String> migrationGuideV2ToV1,
        Map<String, String> compatibilityMatrix
) {

    public static final String CURRENT_RELEASE_VERSION = "1.0.0-RELEASE";

    public static AgentHubReleaseManifest createDefault() {
        return new AgentHubReleaseManifest(
                CURRENT_RELEASE_VERSION,
                ApiContractHeaders.API_VERSION_V1,
                "2026-10-15T00:00:00Z",
                List.of("DEMO_SIMULATED", "LIVE_SELF_HOSTED", "LIVE_TEAM"),
                List.of(
                        "identity", "project", "orchestration", "execution",
                        "agent", "audit", "sandbox", "ops"
                ),
                List.of(
                        "Project", "Workspace", "WorkflowDefinition", "WorkflowRun",
                        "StepRun", "Artifact", "QualityGateReport", "Approval",
                        "Deployment", "TokenUsage", "AuditLog"
                ),
                List.of(
                        "AgentHubProjectClient",
                        "AgentHubRunClient",
                        "AgentHubArtifactClient",
                        "AgentHubApprovalClient",
                        "AgentHubDeploymentClient"
                ),
                List.of(
                        "ProviderExtensionContract",
                        "ToolExtensionContract",
                        "AgentDefinitionExtensionContract",
                        "WorkflowTemplateExtensionContract",
                        "DeploymentTemplateExtensionContract"
                ),
                Map.of(
                        "legacyWorkspaceEndpoints", "Migrated to /api/workspaces/{workspaceId}/* with strict relative path validation",
                        "apiVersioning", "Send header X-API-Version: v1 on all REST calls",
                        "idempotency", "Supply Idempotency-Key on POST /api/executions/runs",
                        "webhookVerification", "Validate inbound webhooks using HMAC-SHA256 with X-AgentHub-Signature"
                ),
                Map.of(
                        "database", "MySQL 8.0+ / H2 In-Memory (Test)",
                        "flywayMigrations", "V1 through V12 (SHA-256 baseline locked)",
                        "frontend", "Next.js 14.2.13 (TypeScript, React 18, Tailwind CSS)",
                        "javaRuntime", "Java 17 LTS / Spring Boot 3.3.4"
                )
        );
    }
}
