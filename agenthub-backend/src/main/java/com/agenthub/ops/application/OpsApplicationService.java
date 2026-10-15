package com.agenthub.ops.application;

import com.agenthub.shared.contract.ApiContractHeaders;
import com.agenthub.shared.contract.BoundedContext;
import com.agenthub.shared.contract.PageRequestQuery;
import com.agenthub.shared.filter.LegacyEndpointCallTracker;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class OpsApplicationService implements OpsApplication {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final LegacyEndpointCallTracker legacyCallTracker;

    public OpsApplicationService(LegacyEndpointCallTracker legacyCallTracker) {
        this.legacyCallTracker = legacyCallTracker;
    }

    @Override
    public Map<String, Object> getSystemHealth() {
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("status", "UP");
        health.put("app", "AgentHub Enterprise Core");
        health.put("version", "1.0.0");
        health.put("apiVersion", ApiContractHeaders.API_VERSION_V1);
        health.put("timezone", ApiContractHeaders.SERVER_TIMEZONE_UTC);
        health.put("serverTimeUtc", Instant.now().toString());
        health.put("javaVersion", System.getProperty("java.version"));
        health.put("os", System.getProperty("os.name"));
        return health;
    }

    @Override
    public Map<String, Object> getApiContractMetadataV1() {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("apiVersion", ApiContractHeaders.API_VERSION_V1);
        meta.put("timezone", ApiContractHeaders.SERVER_TIMEZONE_UTC);
        meta.put("pagination", Map.of(
                "defaultPage", PageRequestQuery.DEFAULT_PAGE,
                "defaultSize", PageRequestQuery.DEFAULT_SIZE,
                "maxSize", PageRequestQuery.MAX_SIZE,
                "defaultSortDirection", PageRequestQuery.DEFAULT_SORT_DIR
        ));
        meta.put("headers", List.of(
                ApiContractHeaders.HEADER_API_VERSION,
                ApiContractHeaders.HEADER_SERVER_TIMEZONE,
                ApiContractHeaders.HEADER_IDEMPOTENCY_KEY,
                ApiContractHeaders.HEADER_ETAG,
                ApiContractHeaders.HEADER_IF_NONE_MATCH,
                ApiContractHeaders.HEADER_LAST_EVENT_ID,
                ApiContractHeaders.HEADER_DEPRECATION,
                ApiContractHeaders.HEADER_SUNSET,
                ApiContractHeaders.HEADER_LINK,
                ApiContractHeaders.HEADER_DEPRECATED_ENDPOINT,
                ApiContractHeaders.HEADER_DEPRECATED_MIGRATION_TARGET
        ));
        meta.put("productionContexts", Arrays.stream(BoundedContext.values())
                .filter(BoundedContext::isProductionCore)
                .map(c -> Map.of(
                        "code", c.getCode(),
                        "rootPackage", c.getRootPackage(),
                        "errorCodePrefix", c.getErrorCodePrefix(),
                        "description", c.getDescription()
                ))
                .toList());
        meta.put("isolatedExtensions", Arrays.stream(BoundedContext.values())
                .filter(c -> !c.isProductionCore())
                .map(c -> Map.of(
                        "code", c.getCode(),
                        "rootPackage", c.getRootPackage(),
                        "errorCodePrefix", c.getErrorCodePrefix(),
                        "description", c.getDescription()
                ))
                .toList());
        meta.put("legacyEndpoints", legacyCallTracker.snapshot());
        return meta;
    }

    @Override
    public Map<String, Object> getOpenApiSpecV1() {
        for (Path candidate : List.of(
                Paths.get("docs/openapi-v1.json"),
                Paths.get("../docs/openapi-v1.json")
        )) {
            if (Files.isRegularFile(candidate)) {
                try {
                    String content = Files.readString(candidate, StandardCharsets.UTF_8);
                    return MAPPER.readValue(content, new TypeReference<LinkedHashMap<String, Object>>() {});
                } catch (Exception ignored) {
                }
            }
        }

        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("openapi", "3.0.3");
        spec.put("info", Map.of(
                "title", "AgentHub Enterprise API Contract v1",
                "version", "1.0.0",
                "description", "Authoritative REST/SSE contract for AgentHub 8 production bounded contexts (identity, project, orchestration, execution, agent, audit, sandbox, ops). All timestamps use UTC ISO-8601. Supports X-API-Version: v1, X-Server-Timezone: UTC, ETag/If-None-Match (304 Not Modified), Idempotency-Key, and Last-Event-ID."
        ));
        spec.put("servers", List.of(Map.of("url", "/", "description", "Default API Gateway")));
        spec.put("tags", List.of(
                Map.of("name", "identity", "description", "User authentication, JWT rotation, and single-use SSE stream tickets (1000-1999)"),
                Map.of("name", "project", "description", "Managed projects and single-model /api/workspaces/{workspaceId} resources (2000-3009)"),
                Map.of("name", "audit", "description", "Artifact review, JGit structured diff, and non-destructive revert (3010-3999)"),
                Map.of("name", "agent", "description", "Agent definitions, LLM providers, routing policies, and token usage (5000-5999)"),
                Map.of("name", "orchestration", "description", "Versioned workflow templates, DAG topology validation, and approvals (6000-6019)"),
                Map.of("name", "execution", "description", "WorkflowRun, StepRun, RunEvent SSE stream, and Run Evidence Bundle (6020-6999)"),
                Map.of("name", "sandbox", "description", "Controlled sandbox preview deployments and lifecycle management (7000-7999)"),
                Map.of("name", "ops", "description", "System health, API v1 contract metadata, OpenAPI spec, and legacy endpoint telemetry (8000-8999)")
        ));

        Map<String, Object> paths = new LinkedHashMap<>();
        paths.put("/api/auth/login", Map.of("post", endpoint("identity", "Authenticate user credentials and issue JWT")));
        paths.put("/api/auth/register", Map.of("post", endpoint("identity", "Register user account")));
        paths.put("/api/auth/refresh", Map.of("post", endpoint("identity", "Rotate JWT token and invalidate prior JTI")));
        paths.put("/api/auth/stream-ticket", Map.of("post", endpoint("identity", "Issue 60s single-use SSE stream ticket")));
        paths.put("/api/auth/me", Map.of("get", endpoint("identity", "Get current authenticated principal")));
        paths.put("/api/projects", Map.of(
                "get", endpoint("project", "List projects owned by current user"),
                "post", endpoint("project", "Create project and provision managed JGit workspace")
        ));
        paths.put("/api/projects/page", Map.of("get", endpoint("project", "List projects with standardized v1 pagination and sorting")));
        paths.put("/api/projects/{id}", Map.of("get", endpoint("project", "Get project details with weak ETag and If-None-Match (304 Not Modified) support")));
        paths.put("/api/workspaces/{workspaceId}/tree", Map.of("get", endpoint("project", "List workspace file tree via WorkspaceApplication (zero host path leakage)")));
        paths.put("/api/workspaces/{workspaceId}/file", Map.of(
                "get", endpoint("project", "Read workspace file detail"),
                "post", endpoint("project", "Write workspace file"),
                "delete", endpoint("project", "Delete workspace file")
        ));
        paths.put("/api/workspaces/{workspaceId}/diff", Map.of("get", endpoint("project", "Compute structured JGit diff against workspace baseline")));
        paths.put("/api/workspaces/{workspaceId}/git/snapshot", Map.of("post", endpoint("project", "Create JGit commit snapshot and audit artifact")));
        paths.put("/api/workspaces/{workspaceId}/lock/status", Map.of("get", endpoint("project", "Get workspace lock lease status via WorkspaceLockStatusView DTO")));
        paths.put("/api/workflows/definitions", Map.of(
                "get", endpoint("orchestration", "List versioned workflow templates"),
                "post", endpoint("orchestration", "Create versioned workflow template")
        ));
        paths.put("/api/workflows/templates", Map.of("get", endpoint("orchestration", "List canonical workflow templates (BUG_FIX_WITH_TESTS, NEW_FEATURE_ENDPOINT)")));
        paths.put("/api/workflows/orchestrator/plans", Map.of("post", endpoint("orchestration", "Generate structured Orchestrator plan with budget and quality gates")));
        paths.put("/api/workflows/orchestrator/plans/{planId}/confirm", Map.of("post", endpoint("orchestration", "Confirm Orchestrator plan and authorize high-impact tools")));
        paths.put("/api/executions/runs", Map.of("post", endpoint("execution", "Start workflow run with Idempotency-Key header deduplication")));
        paths.put("/api/executions/projects/{projectId}/runs/page", Map.of("get", endpoint("execution", "List workflow runs for project with v1 pagination, sorting, and status filter")));
        paths.put("/api/executions/runs/{runId}", Map.of("get", endpoint("execution", "Get workflow run with weak ETag and If-None-Match (304 Not Modified) support")));
        paths.put("/api/executions/runs/{runId}/stream", Map.of("get", endpoint("execution", "Subscribe to authoritative Run SSE event stream with Last-Event-ID / afterSeq cursor")));
        paths.put("/api/executions/runs/{runId}/evidence", Map.of("get", endpoint("execution", "Generate deterministic, secret-masked Run Evidence Bundle")));
        paths.put("/api/executions/runs/{runId}/operations", Map.of("get", endpoint("execution", "Get unified execution operations view, state machine decision, and memory summary")));
        paths.put("/api/approvals/runs/{runId}", Map.of("get", endpoint("orchestration", "List human-in-the-loop approvals for run")));
        paths.put("/api/audit/artifacts/{artifactId}/revert", Map.of("post", endpoint("audit", "Non-destructively revert artifact change in JGit workspace")));
        paths.put("/api/audit/artifacts/lineage/{runId}", Map.of("get", endpoint("audit", "Get full lifecycle artifact lineage view for run")));
        paths.put("/api/audit/artifacts/{artifactId}/diff", Map.of("get", endpoint("audit", "Get structured JGit diff review for artifact")));
        paths.put("/api/agents", Map.of("get", endpoint("agent", "List registered agent definitions")));
        paths.put("/api/agents/tools", Map.of("get", endpoint("agent", "List registered agent tools with schemas and output limits")));
        paths.put("/api/agents/context-assembly", Map.of("post", endpoint("agent", "Assemble layered context with per-layer token budgets and truncation")));
        paths.put("/api/agents/evaluations/run", Map.of("post", endpoint("agent", "Execute offline evaluation benchmark suite across 5 core task categories")));
        paths.put("/api/providers", Map.of(
                "get", endpoint("agent", "List LLM providers"),
                "post", endpoint("agent", "Register LLM provider")
        ));
        paths.put("/api/deployments", Map.of(
                "get", endpoint("sandbox", "List sandbox deployments for project"),
                "post", endpoint("sandbox", "Create controlled sandbox preview deployment")
        ));
        paths.put("/api/system/health", Map.of("get", endpoint("ops", "Get system health status in UTC")));
        paths.put("/api/system/contracts/v1", Map.of("get", endpoint("ops", "Get API v1 contract metadata, bounded context registry, and legacy route telemetry")));
        paths.put("/api/system/legacy-endpoints", Map.of("get", endpoint("ops", "Get deprecated legacy endpoint invocation counts")));
        paths.put("/api/v1/openapi.json", Map.of("get", endpoint("ops", "Get OpenAPI 3.0.3 contract specification")));
        spec.put("paths", paths);
        return spec;
    }

    @Override
    public Map<String, Long> getLegacyEndpointInvocationCounts() {
        return legacyCallTracker.snapshot();
    }

    private Map<String, Object> endpoint(String tag, String summary) {
        return Map.of(
                "tags", List.of(tag),
                "summary", summary,
                "responses", Map.of("200", Map.of("description", "Standard Result<T> envelope in UTC"))
        );
    }
}
