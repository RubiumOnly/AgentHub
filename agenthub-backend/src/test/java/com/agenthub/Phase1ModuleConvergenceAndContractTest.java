package com.agenthub;

import com.agenthub.audit.infrastructure.entity.ArtifactEntity;
import com.agenthub.audit.infrastructure.repository.ArtifactRepository;
import com.agenthub.execution.application.ExecutionApplication;
import com.agenthub.execution.dto.StartRunCommand;
import com.agenthub.execution.dto.WorkflowRunView;
import com.agenthub.identity.infrastructure.security.ResourceAccessGuard;
import com.agenthub.identity.infrastructure.security.ResourceOwnershipVerifier;
import com.agenthub.ops.application.OpsApplication;
import com.agenthub.project.application.ProjectApplication;
import com.agenthub.project.dto.CreateProjectCommand;
import com.agenthub.project.dto.ProjectView;
import com.agenthub.sandbox.infrastructure.entity.DeploymentEntity;
import com.agenthub.sandbox.infrastructure.repository.DeploymentRepository;
import com.agenthub.agent.dto.ProviderCreateCommand;
import com.agenthub.execution.dto.RunEventView;
import com.agenthub.execution.infrastructure.entity.WorkflowRunEntity;
import com.agenthub.execution.infrastructure.repository.WorkflowRunRepository;
import com.agenthub.identity.dto.LoginCommand;
import com.agenthub.identity.dto.RegisterCommand;
import com.agenthub.orchestration.application.ApprovalApplication;
import com.agenthub.orchestration.dto.ApprovalDecisionCommand;
import com.agenthub.orchestration.dto.CreateWorkflowDefinitionCommand;
import com.agenthub.orchestration.infrastructure.entity.ApprovalEntity;
import com.agenthub.orchestration.infrastructure.repository.ApprovalRepository;
import com.agenthub.project.application.WorkspaceApplication;
import com.agenthub.project.dto.WorkspaceLockStatusView;
import com.agenthub.sandbox.api.dto.CreateDeploymentRequest;
import com.agenthub.shared.context.RequestContext;
import com.agenthub.shared.contract.ApiContractHeaders;
import com.agenthub.shared.contract.BoundedContext;
import com.agenthub.shared.contract.ContextCommand;
import com.agenthub.shared.contract.ContextDomainEvent;
import com.agenthub.shared.contract.ContextQuery;
import com.agenthub.shared.contract.PageRequestQuery;
import com.agenthub.shared.contract.PageResultView;
import com.agenthub.shared.exception.BusinessException;
import com.agenthub.shared.exception.ErrorCode;
import com.agenthub.shared.filter.LegacyEndpointCallTracker;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 1（模块收敛与 API/数据契约 v1）五维边界与集成验收测试套件。
 * 覆盖：
 * 1. 八大生产限界上下文、重复实现物理删除、ExecutionApplicationService 与 DagExecutionEngine 深模块拆分校验；
 * 2. API v1 契约头（X-API-Version / X-Server-Timezone / ETag / If-None-Match 304 / Idempotency-Key）与分页排序极值边界校验；
 * 3. 遗留接口调用遥测（LegacyEndpointCallTracker）、迁移响应头与 HTTP 410 Gone 强制阻断校验；
 * 4. 跨资源统一归属校验（ResourceOwnershipVerifier：Project/Workspace/Workflow/Run/Artifact/Deployment）及 user-1/system 越权防御；
 * 5. OpenAPI v1 契约快照（docs/openapi-v1.json）与前端 10 个 API 子模块 + 4 个页面壳组件契约对齐校验。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class Phase1ModuleConvergenceAndContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OpsApplication opsApplication;

    @Autowired
    private ProjectApplication projectApplication;

    @Autowired
    private ExecutionApplication executionApplication;

    @Autowired
    private ResourceOwnershipVerifier ownershipVerifier;

    @Autowired
    private ResourceAccessGuard accessGuard;

    @Autowired
    private LegacyEndpointCallTracker legacyCallTracker;

    @Autowired
    private ArtifactRepository artifactRepository;

    @Autowired
    private DeploymentRepository deploymentRepository;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private WorkflowRunRepository workflowRunRepository;

    @Autowired
    private WorkspaceApplication workspaceApplication;

    @Autowired
    private ApprovalApplication approvalApplication;

    @Autowired
    private ApprovalRepository approvalRepository;

    @BeforeEach
    void setUp() {
        RequestContext.clear();
        legacyCallTracker.reset();
    }

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    @Test
    @DisplayName("1. 八大生产限界上下文、重复类删除与深模块拆分（Execution 4 模块 + DAG 5 Seam）验证")
    void shouldEnforceEightProductionContextsDeletedDuplicatesAndDeepModuleSeamSplits() throws Exception {
        // 1) 验证八大核心生产上下文与两个隔离扩展上下文定义精确对齐
        List<BoundedContext> coreContexts = BoundedContext.productionCoreContexts();
        assertThat(coreContexts).hasSize(8);
        Set<String> coreCodes = coreContexts.stream().map(BoundedContext::getCode).collect(Collectors.toSet());
        assertThat(coreCodes).containsExactlyInAnyOrder(
                "identity", "project", "orchestration", "execution", "agent", "audit", "sandbox", "ops"
        );

        Path repoRoot = resolveRepositoryRoot();
        Path mainJava = repoRoot.resolve("agenthub-backend/src/main/java");

        // 2) 验证重复实现与旧包冗余 Controller 已物理删除
        assertThat(Files.exists(mainJava.resolve("com/agenthub/infrastructure/jgit/WorkspaceLockManager.java")))
                .as("Duplicate jgit.WorkspaceLockManager must be deleted in Phase 1")
                .isFalse();
        assertThat(Files.exists(mainJava.resolve("com/agenthub/infrastructure/common/exception/ErrorCode.java")))
                .as("Duplicate infrastructure ErrorCode must be deleted in Phase 1")
                .isFalse();
        assertThat(Files.exists(mainJava.resolve("com/agenthub/adapter/web/SystemHealthController.java")))
                .as("Old adapter.web.SystemHealthController must be moved to com.agenthub.ops.api")
                .isFalse();
        assertThat(Files.exists(mainJava.resolve("com/agenthub/adapter/web/AgentRegistryController.java")))
                .as("Old adapter.web.AgentRegistryController must be moved to com.agenthub.agent.api")
                .isFalse();

        // 3) 验证 ExecutionApplicationService 已拆分为 4 个内部深模块且门面行数显著收敛
        Path execAppDir = mainJava.resolve("com/agenthub/execution/application");
        assertThat(Files.exists(execAppDir.resolve("RunCommandModule.java"))).isTrue();
        assertThat(Files.exists(execAppDir.resolve("RunQueryModule.java"))).isTrue();
        assertThat(Files.exists(execAppDir.resolve("RunEvidenceModule.java"))).isTrue();
        assertThat(Files.exists(execAppDir.resolve("RunEventStreamModule.java"))).isTrue();
        long execFacadeLines = Files.readAllLines(execAppDir.resolve("ExecutionApplicationService.java"), StandardCharsets.UTF_8).size();
        assertThat(execFacadeLines)
                .as("ExecutionApplicationService facade must be converged below 200 lines")
                .isLessThan(200L);

        // 4) 验证 DagExecutionEngine 已拆分为 5 个内部 seam 且主类行数收敛
        Path dagDir = mainJava.resolve("com/agenthub/orchestration/scheduler");
        assertThat(Files.exists(dagDir.resolve("DagTopologyPlanner.java"))).isTrue();
        assertThat(Files.exists(dagDir.resolve("DagLeaseExecutor.java"))).isTrue();
        assertThat(Files.exists(dagDir.resolve("DagStateTransitioner.java"))).isTrue();
        assertThat(Files.exists(dagDir.resolve("DagAgentInvoker.java"))).isTrue();
        assertThat(Files.exists(dagDir.resolve("DagEventRecorder.java"))).isTrue();
        long dagEngineLines = Files.readAllLines(dagDir.resolve("DagExecutionEngine.java"), StandardCharsets.UTF_8).size();
        assertThat(dagEngineLines)
                .as("DagExecutionEngine orchestrator must be converged below 480 lines")
                .isLessThan(480L);
    }

    @Test
    @DisplayName("2. API 契约 v1 响应头、ETag/304 Not Modified、Idempotency-Key 幂等头与分页排序极值边界验证")
    void shouldVerifyApiContractV1HeadersEtag304IdempotencyKeyAndPaginationBoundaries() throws Exception {
        // 1) 验证 /api/system/contracts/v1 与 /api/v1/openapi.json 契约端点与全局响应头
        mockMvc.perform(get("/api/system/contracts/v1").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string(ApiContractHeaders.HEADER_API_VERSION, "v1"))
                .andExpect(header().string(ApiContractHeaders.HEADER_SERVER_TIMEZONE, "UTC"))
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.apiVersion").value("v1"))
                .andExpect(jsonPath("$.data.timezone").value("UTC"))
                .andExpect(jsonPath("$.data.pagination.maxSize").value(100))
                .andExpect(jsonPath("$.data.productionContexts.length()").value(8));

        mockMvc.perform(get("/api/v1/openapi.json").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string(ApiContractHeaders.HEADER_API_VERSION, "v1"))
                .andExpect(jsonPath("$.openapi").value("3.0.3"))
                .andExpect(jsonPath("$.info.version").value("1.0.0"))
                .andExpect(jsonPath("$.paths['/api/workspaces/{workspaceId}/diff']").exists())
                .andExpect(jsonPath("$.paths['/api/executions/runs']").exists());

        // 2) 注册测试用户并通过 HTTP 验证 Project ETag / If-None-Match 304 Not Modified
        String token = registerUserAndGetToken("phase1_contract_" + System.nanoTime() + "@agenthub.local");

        String createProjBody = "{\"name\":\"Phase1-Contract-Project\",\"description\":\"ETag & Pagination Verification\"}";
        MvcResult createProjRes = mockMvc.perform(post("/api/projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createProjBody))
                .andExpect(status().isOk())
                .andExpect(header().exists(ApiContractHeaders.HEADER_ETAG))
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

        JsonNode projNode = objectMapper.readTree(createProjRes.getResponse().getContentAsString()).path("data");
        String projectId = projNode.path("id").asText();
        String initialEtag = createProjRes.getResponse().getHeader(ApiContractHeaders.HEADER_ETAG);
        assertThat(initialEtag).startsWith("W/\"proj-");

        // 携带匹配的 If-None-Match 请求头应返回 304 Not Modified
        mockMvc.perform(get("/api/projects/" + projectId)
                        .header("Authorization", "Bearer " + token)
                        .header(ApiContractHeaders.HEADER_IF_NONE_MATCH, initialEtag))
                .andExpect(status().isNotModified())
                .andExpect(header().string(ApiContractHeaders.HEADER_ETAG, initialEtag));

        // 携带 If-None-Match: * 通配头应返回 304 Not Modified
        mockMvc.perform(get("/api/projects/" + projectId)
                        .header("Authorization", "Bearer " + token)
                        .header(ApiContractHeaders.HEADER_IF_NONE_MATCH, "*"))
                .andExpect(status().isNotModified());

        // 携带包含匹配 ETag 的逗号分隔列表应返回 304 Not Modified
        mockMvc.perform(get("/api/projects/" + projectId)
                        .header("Authorization", "Bearer " + token)
                        .header(ApiContractHeaders.HEADER_IF_NONE_MATCH, "W/\"v1-stale\", " + initialEtag))
                .andExpect(status().isNotModified());

        // 携带不匹配的 If-None-Match 请求头应返回 200 OK
        mockMvc.perform(get("/api/projects/" + projectId)
                        .header("Authorization", "Bearer " + token)
                        .header(ApiContractHeaders.HEADER_IF_NONE_MATCH, "W/\"v1-stale\""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(projectId));

        // 3) 验证 POST /api/executions/runs 通过 HTTP Header Idempotency-Key 实现幂等去重
        String idemHeaderKey = "hdr-idem-" + UUID.randomUUID();
        String startRunPayload = String.format("{\"projectId\":\"%s\",\"definitionId\":\"wf-enterprise-auth-delivery\"}", projectId);

        MvcResult runRes1 = mockMvc.perform(post("/api/executions/runs")
                        .header("Authorization", "Bearer " + token)
                        .header(ApiContractHeaders.HEADER_IDEMPOTENCY_KEY, idemHeaderKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(startRunPayload))
                .andExpect(status().isOk())
                .andExpect(header().exists(ApiContractHeaders.HEADER_ETAG))
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

        MvcResult runRes2 = mockMvc.perform(post("/api/executions/runs")
                        .header("Authorization", "Bearer " + token)
                        .header(ApiContractHeaders.HEADER_IDEMPOTENCY_KEY, idemHeaderKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(startRunPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

        String runId1 = objectMapper.readTree(runRes1.getResponse().getContentAsString()).path("data").path("id").asText();
        String runId2 = objectMapper.readTree(runRes2.getResponse().getContentAsString()).path("data").path("id").asText();
        assertThat(runId1).isNotBlank().isEqualTo(runId2);

        // 4) 验证分页与排序极值边界（page <= 0、size <= 0、size 超上限 99999、非法 sortDir）
        PageRequestQuery extremeQuery = PageRequestQuery.of(-10, 99999, "name", "INVALID_DIRECTION");
        assertThat(extremeQuery.getPage()).isEqualTo(PageRequestQuery.DEFAULT_PAGE);
        assertThat(extremeQuery.getSize()).isEqualTo(PageRequestQuery.MAX_SIZE);
        assertThat(extremeQuery.getSortDir()).isEqualTo("DESC");

        PageResultView<String> emptySlice = PageResultView.slice(List.of(), extremeQuery);
        assertThat(emptySlice.getItems()).isEmpty();
        assertThat(emptySlice.getTotalElements()).isZero();
        assertThat(emptySlice.isHasNext()).isFalse();

        mockMvc.perform(get("/api/projects/page?page=1&size=10&sortBy=name&sortDir=ASC")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(10))
                .andExpect(jsonPath("$.data.sortBy").value("name"))
                .andExpect(jsonPath("$.data.sortDirection").value("ASC"))
                .andExpect(jsonPath("$.data.totalElements").value(1));

        mockMvc.perform(get("/api/projects/page?page=1&size=10&sortBy=createdAt&sortDir=ASC")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.sortBy").value("createdAt"))
                .andExpect(jsonPath("$.data.sortDirection").value("ASC"));

        mockMvc.perform(get("/api/executions/projects/" + projectId + "/runs/page?page=1&size=5&sortDir=DESC")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(runId1));
    }

    @Test
    @DisplayName("3. 遗留接口调用计数遥测、废弃迁移响应头与 HTTP 410 Gone 强制阻断验证")
    void shouldTrackLegacyEndpointCallsAndEnforce410GoneWithMigrationHeaders() throws Exception {
        // 初始状态下所有遗留路由调用计数均为 0
        Map<String, Long> initialCounts = opsApplication.getLegacyEndpointInvocationCounts();
        assertThat(initialCounts.values()).allMatch(count -> count == 0L);

        // 1) 当携带 X-Enforce-Legacy-Gone: true 时，四个遗留路由族均返回 HTTP 410 Gone 及标准迁移目标头
        Map<String, String> expectedMigrations = Map.of(
                "/api/workspace/diff", "/api/workspaces/{workspaceId}",
                "/api/im/conversations", "/api/conversations",
                "/api/sandbox/preview/default", "/api/deployments",
                "/api/runs/run-123/stream", "/api/executions/runs"
        );

        for (Map.Entry<String, String> entry : expectedMigrations.entrySet()) {
            mockMvc.perform(get(entry.getKey())
                            .header(ApiContractHeaders.HEADER_ENFORCE_LEGACY_GONE, "true"))
                    .andExpect(status().isGone())
                    .andExpect(header().string(ApiContractHeaders.HEADER_DEPRECATION, "true"))
                    .andExpect(header().string(ApiContractHeaders.HEADER_MIGRATION_TARGET, entry.getValue()))
                    .andExpect(header().string(ApiContractHeaders.HEADER_LINK, "<" + entry.getValue() + ">; rel=\"successor-version\""))
                    .andExpect(jsonPath("$.code").value(410))
                    .andExpect(jsonPath("$.data.migrationTarget").value(entry.getValue()));
        }

        // 验证多重斜杠路径（如 /api//workspace/diff）同样被规范化拦截且不绕过安全规则
        mockMvc.perform(get("/api//workspace/diff")
                        .header(ApiContractHeaders.HEADER_ENFORCE_LEGACY_GONE, "true"))
                .andExpect(status().isGone())
                .andExpect(header().string(ApiContractHeaders.HEADER_MIGRATION_TARGET, "/api/workspaces/{workspaceId}"));

        // 2) 验证 /api/system/legacy-endpoints 精确记录了上述拦截调用
        mockMvc.perform(get("/api/system/legacy-endpoints"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data['/api/workspace']").value(2))
                .andExpect(jsonPath("$.data['/api/im']").value(1))
                .andExpect(jsonPath("$.data['/api/sandbox']").value(1))
                .andExpect(jsonPath("$.data['/api/runs']").value(1));

        // 3) 验证 Micrometer 业务指标 agenthub_legacy_endpoint_calls_total 真实记录
        Counter workspaceCounter = meterRegistry.find("agenthub_legacy_endpoint_calls_total")
                .tag("prefix", "/api/workspace")
                .tag("blocked", "true")
                .counter();
        assertThat(workspaceCounter).isNotNull();
        assertThat(workspaceCounter.count()).isGreaterThanOrEqualTo(2.0);
    }

    @Test
    @DisplayName("4. 跨资源统一归属校验（ResourceOwnershipVerifier）与零 user-1/system 越权旁路验证")
    void shouldEnforceUnifiedResourceOwnershipAcrossAllAggregatesAndRejectUser1Bypass() throws Exception {
        String ownerId = registerUserAndGetUserId("owner_" + System.nanoTime() + "@agenthub.local");
        String attackerId = "attacker-" + UUID.randomUUID();

        // 1) 以 ownerId 身份创建受控 Project、Workspace 与 Run
        RequestContext.get().setUserId(ownerId);
        CreateProjectCommand createCmd = new CreateProjectCommand();
        createCmd.setName("Ownership-Guard-Project");
        createCmd.setDescription("Unified ownership verifier test");
        ProjectView project = projectApplication.createProject(createCmd);
        String projectId = project.getId();
        String workspaceId = project.getWorkspaceId();

        StartRunCommand runCmd = new StartRunCommand();
        runCmd.setProjectId(projectId);
        runCmd.setDefinitionId("wf-enterprise-auth-delivery");
        runCmd.setIdempotencyKey("own-idem-" + UUID.randomUUID());
        WorkflowRunView run = executionApplication.startRun(runCmd);

        ArtifactEntity artifact = new ArtifactEntity();
        artifact.setId("art-" + UUID.randomUUID());
        artifact.setRunId(run.getId());
        artifact.setStepRunId("step-own-1");
        artifact.setArtifactType("CODE_DIFF");
        artifact.setPathOrRef("git://commit/abc1234");
        artifact.setChecksum("sha256-test");
        artifact.setReviewStatus("PENDING");
        artifact.setCreatedAt(LocalDateTime.now());
        artifactRepository.save(artifact);

        DeploymentEntity deployment = new DeploymentEntity();
        deployment.setId("dep-" + UUID.randomUUID());
        deployment.setProjectId(projectId);
        deployment.setArtifactId(artifact.getId());
        deployment.setStatus("STOPPED");
        deployment.setTarget("STATIC_PREVIEW");
        deployment.setSandboxType("LOCAL_PROCESS");
        deployment.setStartedAt(LocalDateTime.now());
        deployment.setCreatedAt(LocalDateTime.now());
        deployment.setUpdatedAt(LocalDateTime.now());
        deploymentRepository.save(deployment);

        boolean originalBypass = accessGuard.isAllowSuperuserBypass();
        accessGuard.setAllowSuperuserBypass(false);
        try {
            // 2) Owner 访问全部 6 类资源均通过且归属解析一致
            assertThat(ownershipVerifier.verifyProjectAccess(projectId, ownerId).getId()).isEqualTo(projectId);
            assertThat(ownershipVerifier.verifyWorkspaceAccess(workspaceId, ownerId).getId()).isEqualTo(workspaceId);
            assertThat(ownershipVerifier.verifyWorkflowAccess("wf-enterprise-auth-delivery", projectId, ownerId).getId())
                    .isEqualTo("wf-enterprise-auth-delivery");
            assertThat(ownershipVerifier.verifyRunAccess(run.getId(), ownerId).getId()).isEqualTo(run.getId());
            assertThat(ownershipVerifier.verifyArtifactAccess(artifact.getId(), ownerId).getId()).isEqualTo(artifact.getId());
            assertThat(ownershipVerifier.verifyDeploymentAccess(deployment.getId(), ownerId).getId()).isEqualTo(deployment.getId());

            assertThat(ownershipVerifier.resolveProjectOwnerId(projectId)).isEqualTo(ownerId);
            assertThat(ownershipVerifier.resolveWorkspaceOwnerId(workspaceId)).isEqualTo(ownerId);
            assertThat(ownershipVerifier.resolveRunOwnerId(run.getId())).isEqualTo(ownerId);
            assertThat(ownershipVerifier.resolveArtifactOwnerId(artifact.getId())).isEqualTo(ownerId);
            assertThat(ownershipVerifier.resolveDeploymentOwnerId(deployment.getId())).isEqualTo(ownerId);

            // 3) 非归属用户（含历史超级用户常量 "user-1" 和 "system"）访问均被严格拦截（FORBIDDEN）
            for (String unauthorizedCaller : List.of(attackerId, "user-1", "system")) {
                assertThat(accessGuard.isConfiguredSuperuser(unauthorizedCaller)).isFalse();
                assertThatThrownBy(() -> ownershipVerifier.verifyProjectAccess(projectId, unauthorizedCaller))
                        .isInstanceOf(BusinessException.class)
                        .extracting(ex -> ((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.FORBIDDEN);
                assertThatThrownBy(() -> ownershipVerifier.verifyWorkspaceAccess(workspaceId, unauthorizedCaller))
                        .isInstanceOf(BusinessException.class)
                        .extracting(ex -> ((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.FORBIDDEN);
                assertThatThrownBy(() -> ownershipVerifier.verifyRunAccess(run.getId(), unauthorizedCaller))
                        .isInstanceOf(BusinessException.class)
                        .extracting(ex -> ((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.FORBIDDEN);
                assertThatThrownBy(() -> ownershipVerifier.verifyArtifactAccess(artifact.getId(), unauthorizedCaller))
                        .isInstanceOf(BusinessException.class)
                        .extracting(ex -> ((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.FORBIDDEN);
                assertThatThrownBy(() -> ownershipVerifier.verifyDeploymentAccess(deployment.getId(), unauthorizedCaller))
                        .isInstanceOf(BusinessException.class)
                        .extracting(ex -> ((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.FORBIDDEN);
            }

            // 4) 验证 WorkspaceLockStatusView 正确保留 workspaceId, workspaceKey, remainingTtlMs 与绝对 leaseExpiresAt
            WorkspaceLockStatusView runLockStatus = workspaceApplication.getLockStatus(workspaceId);
            assertThat(runLockStatus.isLocked()).isTrue();
            assertThat(runLockStatus.getOwnerId()).isEqualTo(run.getId());
            assertThat(runLockStatus.getWorkspaceId()).isEqualTo(workspaceId);
            assertThat(runLockStatus.getWorkspaceKey()).contains(projectId);
            assertThat(runLockStatus.getRemainingTtlMs()).isGreaterThan(0L);
            assertThat(runLockStatus.getLeaseExpiresAt()).isGreaterThan(0L);

            // 5) 验证孤儿 Run（projectId 不在 projectRepository）被统一拦截为 PROJECT_NOT_FOUND
            WorkflowRunEntity orphanRun = new WorkflowRunEntity();
            orphanRun.setId("run-orphan-" + UUID.randomUUID());
            orphanRun.setProjectId("proj-missing-" + UUID.randomUUID());
            orphanRun.setStatus("RUNNING");
            orphanRun.setCreatedAt(LocalDateTime.now());
            orphanRun.setUpdatedAt(LocalDateTime.now());
            workflowRunRepository.save(orphanRun);

            assertThatThrownBy(() -> ownershipVerifier.verifyRunAccess(orphanRun.getId(), ownerId))
                    .isInstanceOf(BusinessException.class)
                    .extracting(ex -> ((BusinessException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.PROJECT_NOT_FOUND);

            // 6) 验证 ApprovalApplicationService 拒绝未认证/非所有者审查（禁止隐式回退到 system）
            ApprovalEntity testApproval = new ApprovalEntity(
                    "appr-" + UUID.randomUUID(), run.getId(), "step-own-1", "PENDING", ownerId
            );
            approvalRepository.save(testApproval);

            ApprovalDecisionCommand decisionCmd = new ApprovalDecisionCommand("APPROVED", "LGTM");
            RequestContext.clear();
            assertThatThrownBy(() -> approvalApplication.decide(testApproval.getId(), decisionCmd))
                    .isInstanceOf(BusinessException.class)
                    .extracting(ex -> ((BusinessException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.UNAUTHORIZED);
        } finally {
            accessGuard.setAllowSuperuserBypass(originalBypass);
        }
    }

    @Test
    @DisplayName("5. OpenAPI v1 快照与前端 10 个资源 API 模块 + 4 个页面壳组件拆分对齐验证")
    void shouldVerifyOpenApiSnapshotAndFrontendModularSplit() throws Exception {
        Path repoRoot = resolveRepositoryRoot();

        // 1) 验证 docs/openapi-v1.json 快照文件存在且与运行时 OpenAPI v1 规范对齐
        Path openApiFile = repoRoot.resolve("docs/openapi-v1.json");
        assertThat(Files.exists(openApiFile)).isTrue();
        JsonNode openApiJson = objectMapper.readTree(Files.readString(openApiFile, StandardCharsets.UTF_8));
        assertThat(openApiJson.path("openapi").asText()).isEqualTo("3.0.3");
        assertThat(openApiJson.path("tags").size()).isEqualTo(8);
        assertThat(openApiJson.path("paths").has("/api/workspaces/{workspaceId}/tree")).isTrue();
        assertThat(openApiJson.path("paths").has("/api/executions/runs")).isTrue();
        assertThat(openApiJson.path("paths").has("/api/v1/openapi.json")).isTrue();

        // 2) 运行时 OpenAPI 规范必须包含完整 8 个 tag 且路径集合与快照完全一致
        MvcResult runtimeOpenApiRes = mockMvc.perform(get("/api/v1/openapi.json").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode runtimeOpenApiJson = objectMapper.readTree(runtimeOpenApiRes.getResponse().getContentAsString());
        assertThat(runtimeOpenApiJson.path("tags").size()).isEqualTo(8);
        assertThat(runtimeOpenApiJson.path("paths").size()).isEqualTo(openApiJson.path("paths").size());

        // 3) 验证前端 services/api.ts 已拆分为 10 个子模块，且主入口文件收敛在 120 行以内
        Path frontendSrc = repoRoot.resolve("agenthub-frontend/src");
        Path apiBarrel = frontendSrc.resolve("services/api.ts");
        assertThat(Files.readAllLines(apiBarrel, StandardCharsets.UTF_8).size()).isLessThan(120);

        List<String> expectedApiModules = List.of(
                "auth.ts", "projects.ts", "workspaces.ts", "workflows.ts", "runs.ts",
                "approvals.ts", "artifacts.ts", "deployments.ts", "stream.ts", "errors.ts"
        );
        for (String mod : expectedApiModules) {
            Path modPath = frontendSrc.resolve("services/api").resolve(mod);
            assertThat(Files.isRegularFile(modPath))
                    .as("Frontend modular API file must exist: services/api/%s", mod)
                    .isTrue();
        }

        // 4) 验证前端 app/page.tsx 已拆分为 ProjectShell、RunWorkspacePanel、ReviewSidebar、DeploymentPanel 四个组件
        Path pageTsx = frontendSrc.resolve("app/page.tsx");
        assertThat(Files.readAllLines(pageTsx, StandardCharsets.UTF_8).size()).isLessThan(420);
        for (String shellComp : List.of(
                "ProjectShell.tsx", "RunWorkspacePanel.tsx", "ReviewSidebar.tsx", "DeploymentPanel.tsx"
        )) {
            Path compPath = frontendSrc.resolve("components/dashboard").resolve(shellComp);
            assertThat(Files.isRegularFile(compPath))
                    .as("Frontend dashboard shell component must exist: components/dashboard/%s", shellComp)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("6. 限界上下文通用 Marker 契约（ContextCommand / ContextQuery / ContextDomainEvent）实现验证")
    void shouldEnforceMarkerContractsForCommandsQueriesAndEvents() {
        // 验证各限界上下文的核心 Command 显式实现 ContextCommand 契约
        assertThat(ContextCommand.class.isAssignableFrom(CreateProjectCommand.class)).isTrue();
        assertThat(ContextCommand.class.isAssignableFrom(StartRunCommand.class)).isTrue();
        assertThat(ContextCommand.class.isAssignableFrom(LoginCommand.class)).isTrue();
        assertThat(ContextCommand.class.isAssignableFrom(RegisterCommand.class)).isTrue();
        assertThat(ContextCommand.class.isAssignableFrom(CreateWorkflowDefinitionCommand.class)).isTrue();
        assertThat(ContextCommand.class.isAssignableFrom(ApprovalDecisionCommand.class)).isTrue();
        assertThat(ContextCommand.class.isAssignableFrom(ProviderCreateCommand.class)).isTrue();
        assertThat(ContextCommand.class.isAssignableFrom(CreateDeploymentRequest.class)).isTrue();

        // 验证 Query 与 DomainEvent 契约实现
        assertThat(ContextQuery.class.isAssignableFrom(PageRequestQuery.class)).isTrue();
        assertThat(ContextDomainEvent.class.isAssignableFrom(RunEventView.class)).isTrue();
    }

    private String registerUserAndGetToken(String email) throws Exception {
        String payload = String.format(
                "{\"email\":\"%s\",\"password\":\"Phase1Pass#2026\",\"displayName\":\"Phase1Tester\"}",
                email
        );
        MvcResult res = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString())
                .path("data")
                .path("token")
                .asText();
    }

    private String registerUserAndGetUserId(String email) throws Exception {
        String payload = String.format(
                "{\"email\":\"%s\",\"password\":\"Phase1Pass#2026\",\"displayName\":\"Phase1Owner\"}",
                email
        );
        MvcResult res = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString())
                .path("data")
                .path("user")
                .path("id")
                .asText();
    }

    private Path resolveRepositoryRoot() {
        Path cwd = Paths.get("").toAbsolutePath().normalize();
        if (Files.exists(cwd.resolve("docs")) && Files.exists(cwd.resolve("agenthub-backend"))) {
            return cwd;
        }
        Path parent = cwd.getParent();
        if (parent != null && Files.exists(parent.resolve("docs")) && Files.exists(parent.resolve("agenthub-backend"))) {
            return parent;
        }
        return cwd;
    }
}
