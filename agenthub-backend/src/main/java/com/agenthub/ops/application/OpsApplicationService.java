package com.agenthub.ops.application;

import com.agenthub.shared.contract.ApiContractHeaders;
import com.agenthub.shared.contract.BoundedContext;
import com.agenthub.shared.filter.LegacyEndpointCallTracker;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class OpsApplicationService implements OpsApplication {

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
                "defaultPage", 1,
                "defaultSize", 20,
                "maxSize", 200,
                "defaultSortDirection", "DESC"
        ));
        meta.put("headers", List.of(
                ApiContractHeaders.HEADER_API_VERSION,
                ApiContractHeaders.HEADER_SERVER_TIMEZONE,
                ApiContractHeaders.HEADER_IDEMPOTENCY_KEY,
                ApiContractHeaders.HEADER_ETAG,
                ApiContractHeaders.HEADER_IF_NONE_MATCH,
                ApiContractHeaders.HEADER_LAST_EVENT_ID,
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
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("openapi", "3.0.3");
        spec.put("info", Map.of(
                "title", "AgentHub Enterprise API Contract v1",
                "version", "1.0.0",
                "description", "Authoritative REST/SSE contract for AgentHub 8 production bounded contexts (UTC, ETag, Idempotency-Key, Last-Event-ID)."
        ));
        spec.put("servers", List.of(Map.of("url", "/", "description", "Default API Gateway")));

        Map<String, Object> paths = new LinkedHashMap<>();
        paths.put("/api/auth/login", Map.of("post", endpoint("identity", "Authenticate user and issue JWT")));
        paths.put("/api/auth/register", Map.of("post", endpoint("identity", "Register user account")));
        paths.put("/api/auth/refresh", Map.of("post", endpoint("identity", "Rotate JWT token")));
        paths.put("/api/auth/stream-ticket", Map.of("post", endpoint("identity", "Issue 60s single-use SSE stream ticket")));
        paths.put("/api/auth/me", Map.of("get", endpoint("identity", "Get current authenticated principal")));
        paths.put("/api/projects", Map.of(
                "get", endpoint("project", "List projects with pagination/sorting"),
                "post", endpoint("project", "Create project and provision managed Git workspace")
        ));
        paths.put("/api/projects/{id}", Map.of(
                "get", endpoint("project", "Get project details with ETag header"),
                "delete", endpoint("project", "Delete project")
        ));
        paths.put("/api/workspaces/{workspaceId}/tree", Map.of("get", endpoint("project", "List workspace file tree")));
        paths.put("/api/workspaces/{workspaceId}/file", Map.of(
                "get", endpoint("project", "Read workspace file detail"),
                "post", endpoint("project", "Write workspace file"),
                "put", endpoint("project", "Update workspace file"),
                "delete", endpoint("project", "Delete workspace file")
        ));
        paths.put("/api/workspaces/{workspaceId}/diff", Map.of("get", endpoint("project", "Compute structured Git diff against baseline")));
        paths.put("/api/workspaces/{workspaceId}/git/snapshot", Map.of("post", endpoint("project", "Create step Git commit snapshot and artifact")));
        paths.put("/api/workspaces/{workspaceId}/lock/acquire", Map.of("post", endpoint("project", "Acquire workspace lease lock")));
        paths.put("/api/workflows", Map.of(
                "get", endpoint("orchestration", "List versioned workflow templates"),
                "post", endpoint("orchestration", "Register workflow template")
        ));
        paths.put("/api/executions/runs", Map.of(
                "get", endpoint("execution", "List workflow runs with pagination and status filtering"),
                "post", endpoint("execution", "Start workflow run (supports Idempotency-Key header)")
        ));
        paths.put("/api/executions/runs/{runId}", Map.of("get", endpoint("execution", "Get workflow run with ETag header")));
        paths.put("/api/executions/runs/{runId}/cancel", Map.of("post", endpoint("execution", "Cancel active workflow run")));
        paths.put("/api/executions/runs/{runId}/stream", Map.of("get", endpoint("execution", "Subscribe to run SSE stream (supports Last-Event-ID)")));
        paths.put("/api/executions/runs/{runId}/evidence", Map.of("get", endpoint("execution", "Export deterministic Run Evidence Bundle")));
        paths.put("/api/approvals", Map.of("get", endpoint("audit", "List human-in-the-loop approvals")));
        paths.put("/api/approvals/{approvalId}/approve", Map.of("post", endpoint("audit", "Approve high-impact step")));
        paths.put("/api/approvals/{approvalId}/reject", Map.of("post", endpoint("audit", "Reject high-impact step")));
        paths.put("/api/artifacts/{artifactId}", Map.of("get", endpoint("audit", "Get artifact metadata")));
        paths.put("/api/artifacts/{artifactId}/accept", Map.of("post", endpoint("audit", "Accept artifact change")));
        paths.put("/api/artifacts/{artifactId}/reject", Map.of("post", endpoint("audit", "Reject artifact change")));
        paths.put("/api/artifacts/{artifactId}/revert", Map.of("post", endpoint("audit", "Revert artifact change to Git baseline")));
        paths.put("/api/agents", Map.of("get", endpoint("agent", "List registered agent definitions")));
        paths.put("/api/providers", Map.of(
                "get", endpoint("agent", "List LLM providers"),
                "post", endpoint("agent", "Register LLM provider")
        ));
        paths.put("/api/runtimes", Map.of("get", endpoint("agent", "List agent runtimes and health status")));
        paths.put("/api/deployments", Map.of(
                "get", endpoint("sandbox", "List sandbox deployments"),
                "post", endpoint("sandbox", "Create sandbox deployment")
        ));
        paths.put("/api/system/health", Map.of("get", endpoint("ops", "Get system health status")));
        paths.put("/api/system/contracts/v1", Map.of("get", endpoint("ops", "Get API v1 contract metadata and bounded context registry")));
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
