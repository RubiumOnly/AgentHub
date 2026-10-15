package com.agenthub.ops.application;

import com.agenthub.agent.infrastructure.repository.TokenUsageAuditRepository;
import com.agenthub.execution.infrastructure.repository.StepRunRepository;
import com.agenthub.execution.infrastructure.repository.WorkflowRunRepository;
import com.agenthub.identity.dto.AuthAuditLogView;
import com.agenthub.identity.infrastructure.repository.AuthAuditLogRepository;
import com.agenthub.identity.infrastructure.security.AuthAuditService;
import com.agenthub.infrastructure.metrics.AgentHubMetricsCollector;
import com.agenthub.infrastructure.ops.DisasterRecoveryBackupService;
import com.agenthub.ops.dto.*;
import com.agenthub.sandbox.infrastructure.repository.DeploymentRepository;
import com.agenthub.shared.context.RequestContext;
import com.agenthub.shared.contract.ApiContractHeaders;
import com.agenthub.shared.contract.BoundedContext;
import com.agenthub.shared.contract.PageRequestQuery;
import com.agenthub.shared.contract.PageResultView;
import com.agenthub.shared.contract.sdk.AgentHubReleaseManifest;
import com.agenthub.shared.contract.webhook.WebhookEventType;
import com.agenthub.shared.contract.webhook.WebhookSignatureVerifier;
import com.agenthub.shared.exception.BusinessException;
import com.agenthub.shared.exception.ErrorCode;
import com.agenthub.shared.filter.LegacyEndpointCallTracker;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired(required = false)
    private DisasterRecoveryBackupService disasterRecoveryBackupService;

    @Autowired(required = false)
    private AuthAuditService authAuditService;

    @Autowired(required = false)
    private AuthAuditLogRepository auditLogRepository;

    @Autowired(required = false)
    private AgentHubMetricsCollector metricsCollector;

    @Autowired(required = false)
    private WorkflowRunRepository workflowRunRepository;

    @Autowired(required = false)
    private StepRunRepository stepRunRepository;

    @Autowired(required = false)
    private DeploymentRepository deploymentRepository;

    @Autowired(required = false)
    private TokenUsageAuditRepository tokenUsageRepository;

    public OpsApplicationService(LegacyEndpointCallTracker legacyCallTracker) {
        this(legacyCallTracker, null, null, null, null, null, null, null, null);
    }

    @Autowired
    public OpsApplicationService(
            LegacyEndpointCallTracker legacyCallTracker,
            @Autowired(required = false) DisasterRecoveryBackupService disasterRecoveryBackupService,
            @Autowired(required = false) AuthAuditService authAuditService,
            @Autowired(required = false) AuthAuditLogRepository auditLogRepository,
            @Autowired(required = false) AgentHubMetricsCollector metricsCollector,
            @Autowired(required = false) WorkflowRunRepository workflowRunRepository,
            @Autowired(required = false) StepRunRepository stepRunRepository,
            @Autowired(required = false) DeploymentRepository deploymentRepository,
            @Autowired(required = false) TokenUsageAuditRepository tokenUsageRepository) {
        this.legacyCallTracker = legacyCallTracker;
        this.disasterRecoveryBackupService = disasterRecoveryBackupService;
        this.authAuditService = authAuditService;
        this.auditLogRepository = auditLogRepository;
        this.metricsCollector = metricsCollector;
        this.workflowRunRepository = workflowRunRepository;
        this.stepRunRepository = stepRunRepository;
        this.deploymentRepository = deploymentRepository;
        this.tokenUsageRepository = tokenUsageRepository;
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
        AgentHubReleaseManifest manifest = AgentHubReleaseManifest.createDefault();
        meta.put("releaseManifest", manifest);
        meta.put("webhookSpecification", Map.of(
                "signatureHeader", WebhookSignatureVerifier.SIGNATURE_HEADER,
                "algorithm", WebhookSignatureVerifier.HMAC_ALGORITHM,
                "defaultToleranceSeconds", WebhookSignatureVerifier.DEFAULT_TOLERANCE_SECONDS,
                "supportedEvents", Arrays.stream(WebhookEventType.values())
                        .map(WebhookEventType::getEventName)
                        .toList()
        ));
        meta.put("sdkSpecification", Map.of(
                "stableResources", manifest.stableResources(),
                "clientContracts", manifest.sdkClientContracts()
        ));
        meta.put("extensionContracts", manifest.extensionContracts());
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
        paths.put("/api/executions/runs/{runId}/steps/{stepRunId}/rework", Map.of("post", endpoint("execution", "Request step rework with reviewer feedback and reset to PENDING")));
        paths.put("/api/executions/runs/{runId}/reject", Map.of("post", endpoint("execution", "Reject run, reset workspace changes to baseline, and mark status as REJECTED")));
        paths.put("/api/executions/runs/{runId}/evidence/export", Map.of("get", endpoint("execution", "Export deterministic, secret-masked Run Evidence Package with SHA-256 checksum")));
        paths.put("/api/approvals/runs/{runId}", Map.of("get", endpoint("orchestration", "List human-in-the-loop approvals for run")));
        paths.put("/api/audit/artifacts/{artifactId}/revert", Map.of("post", endpoint("audit", "Non-destructively revert artifact change in JGit workspace")));
        paths.put("/api/audit/artifacts/lineage/{runId}", Map.of("get", endpoint("audit", "Get full lifecycle artifact lineage view for run")));
        paths.put("/api/audit/artifacts/{artifactId}/diff", Map.of("get", endpoint("audit", "Get structured JGit diff review for artifact")));
        paths.put("/api/audit/artifacts/{artifactId}/quality-gate", Map.of("get", endpoint("audit", "Get quality gate evaluation report with SHA-256 fingerprint")));
        paths.put("/api/audit/artifacts/{artifactId}/quality-gate/evaluate", Map.of("post", endpoint("audit", "Evaluate quality gate rules across 5 dimensions")));
        paths.put("/api/audit/artifacts/{artifactId}/pr-draft", Map.of("get", endpoint("audit", "Generate structured Pull Request draft specification")));
        paths.put("/api/audit/artifacts/{artifactId}/revert-file", Map.of("post", endpoint("audit", "Revert a single modified file to git baseline commit")));
        paths.put("/api/agents", Map.of("get", endpoint("agent", "List registered agent definitions")));
        paths.put("/api/agents/tools", Map.of("get", endpoint("agent", "List registered agent tools with schemas and output limits")));
        paths.put("/api/agents/context-assembly", Map.of("post", endpoint("agent", "Assemble layered context with per-layer token budgets and truncation")));
        paths.put("/api/agents/evaluations/run", Map.of("post", endpoint("agent", "Execute offline evaluation benchmark suite across 5 core task categories")));
        paths.put("/api/providers", Map.of(
                "get", endpoint("agent", "List LLM providers"),
                "post", endpoint("agent", "Register LLM provider")
        ));
        paths.put("/api/providers/health", Map.of("get", endpoint("agent", "Get provider cluster health status and metrics")));
        paths.put("/api/providers/{id}/probe", Map.of("post", endpoint("agent", "Actively probe provider endpoint and measure latency")));
        paths.put("/api/providers/{id}/circuit-breaker", Map.of("get", endpoint("agent", "Get provider circuit breaker status and metrics")));
        paths.put("/api/providers/{id}/circuit-breaker/reset", Map.of("post", endpoint("agent", "Reset provider circuit breaker state to CLOSED")));
        paths.put("/api/token-usages/governance", Map.of("get", endpoint("agent", "Get token usage and cost budget governance overview")));
        paths.put("/api/token-usages/budget/check", Map.of("post", endpoint("agent", "Pre-check cost and token budget before execution")));
        paths.put("/api/deployments", Map.of(
                "get", endpoint("sandbox", "List sandbox deployments for project"),
                "post", endpoint("sandbox", "Create controlled sandbox preview deployment")
        ));
        paths.put("/api/deployments/{id}/probe", Map.of("post", endpoint("sandbox", "Execute active HTTP health check probe for sandbox deployment")));
        paths.put("/api/teams/projects/{projectId}/roles/me", Map.of("get", endpoint("project", "Get current user role and permissions on project")));
        paths.put("/api/teams/projects/{projectId}/members", Map.of(
                "get", endpoint("project", "List project team members"),
                "post", endpoint("project", "Add team member to project with RBAC role")
        ));
        paths.put("/api/teams/projects/{projectId}/members/{memberId}/role", Map.of("put", endpoint("project", "Update project team member RBAC role")));
        paths.put("/api/system/audit-logs", Map.of("get", endpoint("ops", "Query immutable audit logs and security trace history")));
        paths.put("/api/system/metrics/summary", Map.of("get", endpoint("ops", "Get aggregated Prometheus/Micrometer metrics and SLO indicators")));
        paths.put("/api/system/backup", Map.of("post", endpoint("ops", "Trigger point-in-time consistent disaster recovery backup snapshot")));
        paths.put("/api/system/backup/verify", Map.of("post", endpoint("ops", "Verify cryptographic and structural integrity of backup archive")));
        paths.put("/api/system/backup/restore", Map.of("post", endpoint("ops", "Restore backup snapshot to target workspace and live database")));
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

    @Override
    public PageResultView<AuthAuditLogView> queryAuditLogs(String eventType, String userId, PageRequestQuery query) {
        String currentUserId = RequestContext.get().getUserId();
        if (!RequestContext.get().isSystem() && (currentUserId == null || currentUserId.isBlank())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Authentication required to query audit logs");
        }
        if (authAuditService == null) {
            PageRequestQuery pageQuery = query != null ? query : PageRequestQuery.ofDefaults();
            return pageQuery.slice(List.of());
        }
        int page = query != null ? query.getPage() : 1;
        int size = query != null ? query.getSize() : 20;
        return authAuditService.queryAuditLogs(eventType, userId, page, size);
    }

    @Override
    public DisasterRecoveryBackupService.BackupManifest triggerBackup(BackupTriggerCommand cmd) {
        String currentUserId = RequestContext.get().getUserId();
        if (!RequestContext.get().isSystem() && (currentUserId == null || currentUserId.isBlank())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Authentication required for disaster recovery backup creation");
        }
        if (disasterRecoveryBackupService == null) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "Disaster recovery backup service not available");
        }
        try {
            Path workspaceRoot = Paths.get("data/workspaces");
            Path backupOutputDir = (cmd != null && cmd.getBackupDir() != null && !cmd.getBackupDir().isBlank())
                    ? Paths.get(cmd.getBackupDir())
                    : Paths.get("data/backups/manual-" + System.currentTimeMillis());
            String passphrase = cmd != null ? cmd.getEncryptionPassphrase() : null;

            DisasterRecoveryBackupService.BackupManifest manifest =
                    disasterRecoveryBackupService.createBackup(workspaceRoot, backupOutputDir, passphrase);

            if (authAuditService != null) {
                authAuditService.record("BACKUP_SNAPSHOT_TRIGGERED", currentUserId, null, null,
                        "Backup snapshot created [" + manifest.backupId + "] at [" + backupOutputDir + "] in " + manifest.backupDurationMs + "ms");
            }
            if (metricsCollector != null) {
                metricsCollector.recordBackupOperation("CREATE", true);
            }
            return manifest;
        } catch (Exception e) {
            if (metricsCollector != null) {
                metricsCollector.recordBackupOperation("CREATE", false);
            }
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "Disaster recovery backup creation failed: " + e.getMessage(), e);
        }
    }

    @Override
    public DisasterRecoveryBackupService.BackupManifest verifyBackup(BackupVerifyCommand cmd) {
        String currentUserId = RequestContext.get().getUserId();
        if (!RequestContext.get().isSystem() && (currentUserId == null || currentUserId.isBlank())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Authentication required for backup verification");
        }
        if (disasterRecoveryBackupService == null) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "Disaster recovery backup service not available");
        }
        if (cmd == null || cmd.getBackupDir() == null || cmd.getBackupDir().isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Backup directory is required");
        }
        try {
            Path backupDir = Paths.get(cmd.getBackupDir());
            DisasterRecoveryBackupService.BackupManifest manifest =
                    disasterRecoveryBackupService.verifyBackupIntegrity(backupDir, cmd.getEncryptionPassphrase());
            if (metricsCollector != null) {
                metricsCollector.recordBackupOperation("VERIFY", true);
            }
            return manifest;
        } catch (Exception e) {
            if (metricsCollector != null) {
                metricsCollector.recordBackupOperation("VERIFY", false);
            }
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Backup integrity verification failed: " + e.getMessage(), e);
        }
    }

    @Override
    public DisasterRecoveryBackupService.RestoreVerificationReport restoreBackup(BackupRestoreCommand cmd) {
        String currentUserId = RequestContext.get().getUserId();
        if (!RequestContext.get().isSystem() && (currentUserId == null || currentUserId.isBlank())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Authentication required for disaster recovery restoration");
        }
        if (disasterRecoveryBackupService == null) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "Disaster recovery backup service not available");
        }
        if (cmd == null || cmd.getBackupDir() == null || cmd.getBackupDir().isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Backup directory is required");
        }
        try {
            Path backupDir = Paths.get(cmd.getBackupDir());
            Path targetWorkspaceRoot = (cmd.getTargetWorkspaceRoot() != null && !cmd.getTargetWorkspaceRoot().isBlank())
                    ? Paths.get(cmd.getTargetWorkspaceRoot())
                    : Paths.get("data/workspaces");

            DisasterRecoveryBackupService.RestoreVerificationReport report =
                    disasterRecoveryBackupService.restoreToCleanEnvironment(backupDir, targetWorkspaceRoot, cmd.getEncryptionPassphrase());

            if (authAuditService != null) {
                authAuditService.record("DISASTER_RESTORE_TRIGGERED", RequestContext.get().getUserId(), null, null,
                        "Disaster recovery restore executed for backup [" + report.backupId() + "] in " + report.restoreDurationMs() + "ms");
            }
            if (metricsCollector != null) {
                metricsCollector.recordBackupOperation("RESTORE", true);
            }
            return report;
        } catch (Exception e) {
            if (metricsCollector != null) {
                metricsCollector.recordBackupOperation("RESTORE", false);
            }
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "Disaster recovery restoration failed: " + e.getMessage(), e);
        }
    }

    @Override
    public OpsMetricsSummaryView getMetricsSummary() {
        OpsMetricsSummaryView view = new OpsMetricsSummaryView();
        view.setSystemHealth("UP");

        int activeRuns = metricsCollector != null ? metricsCollector.getActiveRuns() : 0;
        int activeSse = metricsCollector != null ? metricsCollector.getActiveSseConnections() : 0;
        double diskRatio = metricsCollector != null ? metricsCollector.getWorkspaceDiskAvailablePercent() / 100.0 : 0.85;
        Map<String, String> cbStates = metricsCollector != null ? metricsCollector.getCircuitBreakerStates() : Map.of();
        Map<String, Long> rbacCounts = metricsCollector != null ? metricsCollector.getRbacEvaluationCounts() : Map.of("allowed", 0L, "denied", 0L);

        view.setActiveRuns(activeRuns);
        view.setActiveSseConnections(activeSse);
        view.setWorkspaceDiskAvailableRatio(diskRatio);
        view.setCircuitBreakerStates(cbStates);
        view.setRbacEvaluations(rbacCounts);

        long totalRuns = workflowRunRepository != null ? workflowRunRepository.count() : 0L;
        long totalSteps = stepRunRepository != null ? stepRunRepository.count() : 0L;
        long totalDeployments = deploymentRepository != null ? deploymentRepository.count() : 0L;
        long auditCount = auditLogRepository != null ? auditLogRepository.count() : 0L;
        long totalTokens = 0L;
        if (tokenUsageRepository != null) {
            totalTokens = tokenUsageRepository.findAll().stream()
                    .mapToLong(t -> t.getPromptTokens() + t.getCompletionTokens())
                    .sum();
        }

        view.setTotalRuns(totalRuns);
        view.setTotalSteps(totalSteps);
        view.setTotalDeployments(totalDeployments);
        view.setTotalTokensConsumed(totalTokens);
        view.setAuditLogsRecorded(auditCount);

        long uptime = java.lang.management.ManagementFactory.getRuntimeMXBean().getUptime() / 1000L;
        view.setUptimeSeconds(uptime);

        Runtime rt = Runtime.getRuntime();
        long totalMem = rt.totalMemory() / (1024 * 1024);
        long freeMem = rt.freeMemory() / (1024 * 1024);
        long maxMem = rt.maxMemory() / (1024 * 1024);
        view.setJvmMemoryTotalMb(totalMem);
        view.setJvmMemoryUsedMb(totalMem - freeMem);
        view.setJvmMemoryMaxMb(maxMem);

        if (diskRatio < 0.15 || cbStates.containsValue("OPEN")) {
            view.setSloStatus("CRITICAL");
        } else if (activeRuns > 20 || rbacCounts.getOrDefault("denied", 0L) > 50) {
            view.setSloStatus("WARNING");
        } else {
            view.setSloStatus("HEALTHY");
        }

        return view;
    }

    private Map<String, Object> endpoint(String tag, String summary) {
        return Map.of(
                "tags", List.of(tag),
                "summary", summary,
                "responses", Map.of("200", Map.of("description", "Standard Result<T> envelope in UTC"))
        );
    }
}
