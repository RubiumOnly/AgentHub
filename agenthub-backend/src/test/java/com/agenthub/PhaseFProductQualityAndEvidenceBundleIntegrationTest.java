package com.agenthub;

import com.agenthub.agent.infrastructure.entity.TokenUsageAuditEntity;
import com.agenthub.agent.infrastructure.repository.TokenUsageAuditRepository;
import com.agenthub.audit.infrastructure.repository.ArtifactRepository;
import com.agenthub.execution.application.ExecutionApplication;
import com.agenthub.execution.application.ExecutionApplicationService;
import com.agenthub.execution.dto.RunEvidenceBundleView;
import com.agenthub.execution.infrastructure.entity.StepRunEntity;
import com.agenthub.execution.infrastructure.entity.WorkflowRunEntity;
import com.agenthub.execution.infrastructure.repository.RunEventRepository;
import com.agenthub.execution.infrastructure.repository.StepRunRepository;
import com.agenthub.execution.infrastructure.repository.WorkflowRunRepository;
import com.agenthub.orchestration.infrastructure.repository.ApprovalRepository;
import com.agenthub.orchestration.infrastructure.repository.WorkflowDefinitionRepository;
import com.agenthub.project.infrastructure.repository.ProjectRepository;
import com.agenthub.project.infrastructure.repository.WorkspaceRepository;
import com.agenthub.sandbox.infrastructure.repository.DeploymentRepository;
import com.agenthub.shared.context.RequestContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 阶段 F（产品质量、性能边界和求职作品证据）全链路集成测试套件。
 * 覆盖：
 * 1. E2E_DEMO_GUIDE.md 全链路真实 HTTP 契约演练（注册 -> 项目 -> 工作区 -> DSL 校验与定义 -> 启动运行 -> SSE 票据 -> 人工审批放行 -> JGit 变更提交 -> 结构化 Diff -> 产物采纳 -> 沙箱预览部署与销毁 -> 脱敏证据包导出）；
 * 2. 敏感凭据多维脱敏门禁（API Key、Bearer Token、环境变量赋值、JSON 密钥字段、URL 查询参数零明文泄露）；
 * 3. Demo 离线模拟模式（simulated=true / DEMO_SIMULATED）与真实 Provider 计量模式（simulated=false / LIVE_PROVIDER）精确区分；
 * 4. 证据包确定性 SHA-256 校验和幂等性（跨时间导出 Checksum 恒定）与单字节防篡改校验；
 * 5. 多租户安全边界（未认证 1001、跨租户越权访问 1002、不存在运行 6001）；
 * 6. 仓库级求职作品证据文件（docs/evidence/sample-run-evidence-bundle.json）、跨平台脚本与技术文档诚实性契约校验。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class PhaseFProductQualityAndEvidenceBundleIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ExecutionApplication executionApplication;

    @Autowired
    private WorkflowRunRepository workflowRunRepository;

    @Autowired
    private StepRunRepository stepRunRepository;

    @Autowired
    private RunEventRepository runEventRepository;

    @Autowired
    private WorkflowDefinitionRepository workflowDefinitionRepository;

    @Autowired
    private ApprovalRepository approvalRepository;

    @Autowired
    private ArtifactRepository artifactRepository;

    @Autowired
    private DeploymentRepository deploymentRepository;

    @Autowired
    private TokenUsageAuditRepository tokenUsageAuditRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        RequestContext.clear();
    }

    @AfterEach
    void tearDown() {
        RequestContext.clear();
        org.eclipse.jgit.lib.RepositoryCache.clear();
    }

    @Test
    @DisplayName("1. E2E 演示指南全链路 HTTP 契约演练、敏感信息脱敏与确定性 SHA-256 证据包导出验证")
    void shouldExecuteFullE2EDemoGuideContractAndExportSanitizedEvidenceBundle() throws Exception {
        // Step 1: 注册演示账号并获取 JWT
        String email = "phasef_demo_" + UUID.randomUUID().toString().substring(0, 8) + "@agenthub.dev";
        MvcResult regResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", "PhaseFDemo#2026!",
                                "displayName", "Phase F E2E Auditor"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andReturn();

        JsonNode regJson = objectMapper.readTree(regResult.getResponse().getContentAsString(StandardCharsets.UTF_8));
        String token = regJson.path("data").path("token").asText();

        // Step 2: 创建受控项目并自动初始化 JGit 工作区
        MvcResult projResult = mockMvc.perform(post("/api/projects")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "AgentHub Phase-F E2E Demo",
                                "description", "End-to-End Evidence Bundle & Sandbox Delivery Verification"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andReturn();

        String projectId = objectMapper.readTree(projResult.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("id").asText();

        // Step 3: 查询项目绑定的受控工作区与基线 Commit
        MvcResult wsResult = mockMvc.perform(get("/api/projects/{projectId}/workspace", projectId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andReturn();

        JsonNode wsData = objectMapper.readTree(wsResult.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
        String workspaceId = wsData.path("id").asText();
        assertThat(workspaceId).isNotBlank();

        // Step 4: 校验并注册 DAG 工作流定义（同时覆盖别名字段 runtime/prompt 与 from/to，以及注入模拟敏感凭据验证脱敏）
        Map<String, Object> dslPayload = Map.of(
                "version", "1.0",
                "name", "Phase-F Audited Delivery Pipeline",
                "nodes", List.of(
                        Map.of(
                                "id", "plan-arch",
                                "type", "AGENT",
                                "name", "Architecture Planner",
                                "runtime", "MOCK",
                                "prompt", "Analyze architecture. Never leak sk-proj-1234567890abcdefghijklmnop or DEEPSEEK_API_KEY=sk-live-9876543210abcdef123456."
                        ),
                        Map.of(
                                "id", "approve-deploy",
                                "type", "APPROVAL",
                                "name", "Human Release Gate",
                                "requiresApproval", true
                        ),
                        Map.of(
                                "id", "verify-delivery",
                                "type", "AGENT",
                                "name", "Delivery Verifier",
                                "agentPlatform", "MOCK",
                                "promptTemplate", "Generate final release verification report"
                        )
                ),
                "edges", List.of(
                        Map.of("from", "plan-arch", "to", "approve-deploy"),
                        Map.of("source", "approve-deploy", "target", "verify-delivery")
                ),
                "timeoutSeconds", 120
        );
        String dslJsonString = objectMapper.writeValueAsString(dslPayload);

        mockMvc.perform(post("/api/workflows/definitions/validate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(dslJsonString))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.valid").value(true));

        MvcResult defResult = mockMvc.perform(post("/api/workflows/definitions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "projectId", projectId,
                                "name", "Phase-F Audited Delivery Pipeline",
                                "version", 1,
                                "dsl", dslPayload
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andReturn();

        String definitionId = objectMapper.readTree(defResult.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("id").asText();

        // Step 5: 触发工作流运行并签发短效单次消费 SSE 票据
        MvcResult runResult = mockMvc.perform(post("/api/executions/runs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "projectId", projectId,
                                "definitionId", definitionId,
                                "idempotencyKey", "phasef-e2e-" + UUID.randomUUID()
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andReturn();

        String runId = objectMapper.readTree(runResult.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("id").asText();

        mockMvc.perform(post("/api/auth/stream-ticket")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.ticket").isNotEmpty());

        // Step 6: 等待 DAG 推进至人工审批节点并放行
        String approvalId = waitForPendingApproval(token, runId);
        assertThat(approvalId).isNotBlank();

        mockMvc.perform(post("/api/approvals/{approvalId}/approve", approvalId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "reason", "E2E automated gate approved (checked Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTYifQ.sig)"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("APPROVED"));

        // Step 7: 等待工作流全链路执行完成 (SUCCEEDED)
        String terminalStatus = waitForRunStatus(token, runId, "SUCCEEDED");
        assertThat(terminalStatus).isEqualTo("SUCCEEDED");

        // Step 8: 在受控工作区写入交付物文件（含模拟误写密钥与转义 JSON 密钥）并提交 JGit 快照，验证结构化 Diff
        mockMvc.perform(post("/api/workspaces/{workspaceId}/file", workspaceId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "path", "deliverables/release-summary.md",
                                "content", "# AgentHub Release Summary\n\n- Run ID: " + runId + "\n- Verification: PASSED\n- Token Check: {\"apiKey\": \"sk-json-secret-1234567890abcdef\"}\n- Escaped Check: {\\\"password\\\":\\\"MyPlaintextDbSecret2026!\\\",\\\"clientSecret\\\":\\\"oauth-secret-value-9999\\\",\\\"jwtSecret\\\":\\\"jwt-super-secret-key-2026\\\"}\n"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(post("/api/workspaces/{workspaceId}/file", workspaceId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "path", "index.html",
                                "content", "<!DOCTYPE html><html><head><title>Preview</title></head><body><h1>AgentHub Phase F Preview</h1></body></html>"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        MvcResult snapResult = mockMvc.perform(post("/api/workspaces/{workspaceId}/git/snapshot", workspaceId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "runId", runId,
                                "stepRunId", "plan-arch",
                                "author", email,
                                "message", "feat(delivery): commit audited E2E release summary and preview page"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.commitHash").isNotEmpty())
                .andExpect(jsonPath("$.data.artifactId").isNotEmpty())
                .andReturn();
        String snapArtifactId = objectMapper.readTree(snapResult.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("artifactId").asText();

        mockMvc.perform(get("/api/workspaces/{workspaceId}/diff", workspaceId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.totalFilesChanged").value(3));

        // Step 9: 查询运行产物并执行人工采纳 (ACCEPT)
        MvcResult artifactsResult = mockMvc.perform(get("/api/audit/artifacts/run/{runId}", runId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        JsonNode artifactsArray = objectMapper.readTree(artifactsResult.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
        assertThat(artifactsArray.isArray()).isTrue();
        assertThat(artifactsArray.size()).isGreaterThanOrEqualTo(1);
        String firstArtifactId = artifactsArray.get(0).path("id").asText();
        assertThat(firstArtifactId).isEqualTo(snapArtifactId);

        mockMvc.perform(post("/api/audit/artifacts/{artifactId}/accept", firstArtifactId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "reviewer", email,
                                "comment", "Accepted in Phase-F E2E contract test"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.reviewStatus").value("ACCEPTED"));

        // Step 10: 启动受控沙箱静态预览部署 -> 健康探针就绪 -> 停止并回收
        MvcResult deployResult = mockMvc.perform(post("/api/deployments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "projectId", projectId,
                                "artifactId", firstArtifactId,
                                "target", "STATIC_PREVIEW",
                                "sandboxType", "STATIC_PREVIEW",
                                "templateType", "STATIC_WEB_TEMPLATE",
                                "healthCheckPath", "/health"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("RUNNING"))
                .andExpect(jsonPath("$.data.port").isNumber())
                .andReturn();

        String deploymentId = objectMapper.readTree(deployResult.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("id").asText();

        mockMvc.perform(post("/api/deployments/{deploymentId}/stop", deploymentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("STOPPED"));

        // Step 11: 导出脱敏 Run Evidence Bundle（同时验证标准路径与别名下载路径）
        MvcResult evidenceRes1 = mockMvc.perform(get("/api/executions/runs/{runId}/evidence", runId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.schemaVersion").value("agenthub.evidence.v1"))
                .andExpect(jsonPath("$.data.simulated").value(true))
                .andExpect(jsonPath("$.data.executionMode").value("DEMO_SIMULATED"))
                .andExpect(jsonPath("$.data.checksum").isNotEmpty())
                .andReturn();

        String rawEvidenceResponse1 = evidenceRes1.getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode bundleNode1 = objectMapper.readTree(rawEvidenceResponse1).path("data");
        RunEvidenceBundleView bundle1 = objectMapper.treeToValue(bundleNode1, RunEvidenceBundleView.class);

        // 验证核心证据字段完整性
        assertThat(bundle1.getBundleId()).isEqualTo("evd-" + runId);
        assertThat(bundle1.isSimulated()).isTrue();
        assertThat(bundle1.getExecutionMode()).isEqualTo("DEMO_SIMULATED");
        assertThat(bundle1.getProject().getId()).isEqualTo(projectId);
        assertThat(bundle1.getProject().getWorkspaceId()).isEqualTo(workspaceId);
        assertThat(bundle1.getRun().getId()).isEqualTo(runId);
        assertThat(bundle1.getRun().getStatus()).isEqualTo("SUCCEEDED");
        assertThat(bundle1.getSteps()).hasSize(3);
        assertThat(bundle1.getEvents()).isNotEmpty();
        assertThat(bundle1.getProviderRuntime().isSimulated()).isTrue();
        assertThat(bundle1.getProviderRuntime().getRuntimeTypes()).contains("MOCK");
        assertThat(bundle1.getGitDiff().getTotalFilesChanged()).isGreaterThanOrEqualTo(2);
        assertThat(bundle1.getArtifacts()).isNotEmpty();
        assertThat(bundle1.getApprovals()).hasSize(1);
        assertThat(bundle1.getDeployments()).hasSize(1);
        assertThat(bundle1.getDeployments().get(0).getId()).isEqualTo(deploymentId);
        assertThat(bundle1.getTestReportSummary().getStatus()).isEqualTo("PASSED");
        assertThat(bundle1.getTestReportSummary().getSucceededSteps()).isEqualTo(3);

        // 验证单调递增事件序列及 STEP_STARTED/STEP_COMPLETED 的 runtimeType 与 simulated 标记
        long prevSeq = 0L;
        boolean foundStepMetadata = false;
        for (var ev : bundle1.getEvents()) {
            assertThat(ev.getSequenceNum()).isGreaterThan(prevSeq);
            prevSeq = ev.getSequenceNum();
            if ("STEP_STARTED".equals(ev.getEventType()) || "STEP_COMPLETED".equals(ev.getEventType())) {
                JsonNode evPayload = objectMapper.readTree(ev.getPayload());
                if (evPayload.has("runtimeType") && evPayload.has("simulated")) {
                    foundStepMetadata = true;
                    assertThat(evPayload.get("simulated").asBoolean()).isTrue();
                }
            }
        }
        assertThat(foundStepMetadata).isTrue();

        // 验证敏感信息零明文残留（DSL、审批备注、JGit Diff 及转义 JSON 中的密钥均已脱敏）
        assertThat(rawEvidenceResponse1).doesNotContain("sk-proj-1234567890abcdefghijklmnop");
        assertThat(rawEvidenceResponse1).doesNotContain("sk-live-9876543210abcdef123456");
        assertThat(rawEvidenceResponse1).doesNotContain("sk-json-secret-1234567890abcdef");
        assertThat(rawEvidenceResponse1).doesNotContain("MyPlaintextDbSecret2026!");
        assertThat(rawEvidenceResponse1).doesNotContain("oauth-secret-value-9999");
        assertThat(rawEvidenceResponse1).doesNotContain("jwt-super-secret-key-2026");
        assertThat(rawEvidenceResponse1).doesNotContain("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTYifQ.sig");

        // 验证 SHA-256 校验和合法且跨时间重复导出保持完全幂等
        assertThat(bundle1.getChecksum()).matches("^[0-9a-f]{64}$");
        assertThat(executionApplication.verifyEvidenceBundleChecksum(bundle1)).isTrue();

        Thread.sleep(15L); // 确保 exportedAt 时间戳发生变化
        String safeFileName = "evidence-bundle-" + runId.replaceAll("[^a-zA-Z0-9_-]", "") + ".json";
        MvcResult downloadRes = mockMvc.perform(get("/api/runs/{runId}/evidence/download", runId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + safeFileName + "\""))
                .andReturn();

        RunEvidenceBundleView bundle2 = objectMapper.readValue(
                downloadRes.getResponse().getContentAsString(StandardCharsets.UTF_8),
                RunEvidenceBundleView.class
        );
        assertThat(bundle2.getChecksum()).isEqualTo(bundle1.getChecksum());
        assertThat(executionApplication.verifyEvidenceBundleChecksum(bundle2)).isTrue();

        // Step 12: 同一项目触发后续 Run 2（修改工作区文件、提交新快照并启动新部署），验证 Run 1 的证据包与 SHA-256 指纹绝不被污染
        MvcResult run2Res = mockMvc.perform(post("/api/executions/runs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "projectId", projectId,
                                "definitionId", definitionId,
                                "idempotencyKey", "phasef-e2e-run2-" + UUID.randomUUID()
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        String run2Id = objectMapper.readTree(run2Res.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("id").asText();

        String approval2Id = waitForPendingApproval(token, run2Id);
        mockMvc.perform(post("/api/approvals/{approvalId}/approve", approval2Id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reason", "Run 2 approved"))))
                .andExpect(status().isOk());
        assertThat(waitForRunStatus(token, run2Id, "SUCCEEDED")).isEqualTo("SUCCEEDED");

        mockMvc.perform(post("/api/workspaces/{workspaceId}/file", workspaceId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "path", "index.html",
                                "content", "<!DOCTYPE html><html><body><h1>Modified by Run 2</h1></body></html>"
                        ))))
                .andExpect(status().isOk());

        MvcResult snap2Res = mockMvc.perform(post("/api/workspaces/{workspaceId}/git/snapshot", workspaceId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "runId", run2Id,
                                "stepRunId", "plan-arch",
                                "author", email,
                                "message", "feat(run2): modify index.html in run 2"
                        ))))
                .andExpect(status().isOk())
                .andReturn();
        String run2ArtifactId = objectMapper.readTree(snap2Res.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("artifactId").asText();

        mockMvc.perform(post("/api/audit/artifacts/{artifactId}/accept", run2ArtifactId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reviewer", email, "comment", "Run 2 accepted"))))
                .andExpect(status().isOk());

        MvcResult dep2Res = mockMvc.perform(post("/api/deployments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "projectId", projectId,
                                "artifactId", run2ArtifactId,
                                "target", "STATIC_PREVIEW",
                                "sandboxType", "STATIC_PREVIEW",
                                "templateType", "STATIC_WEB_TEMPLATE",
                                "healthCheckPath", "/health"
                        ))))
                .andExpect(status().isOk())
                .andReturn();
        String dep2Id = objectMapper.readTree(dep2Res.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("id").asText();
        mockMvc.perform(post("/api/deployments/{deploymentId}/stop", dep2Id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        // 再次导出 Run 1 的证据包，验证其基线、Diff、Deployments 与 SHA-256 Checksum 保持 100% 不变
        MvcResult evidenceAfterRun2Res = mockMvc.perform(get("/api/executions/runs/{runId}/evidence", runId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        RunEvidenceBundleView bundleAfterRun2 = objectMapper.treeToValue(
                objectMapper.readTree(evidenceAfterRun2Res.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data"),
                RunEvidenceBundleView.class
        );
        assertThat(bundleAfterRun2.getDeployments()).hasSize(1);
        assertThat(bundleAfterRun2.getDeployments().get(0).getId()).isEqualTo(deploymentId);
        assertThat(bundleAfterRun2.getProject().getGitBaselineCommit()).isEqualTo(bundle1.getProject().getGitBaselineCommit());
        assertThat(bundleAfterRun2.getGitDiff().getCurrentCommit()).isEqualTo(bundle1.getGitDiff().getCurrentCommit());
        assertThat(bundleAfterRun2.getChecksum()).isEqualTo(bundle1.getChecksum());
    }

    @Test
    @DisplayName("2. 真实 Provider 计量运行模式（simulated=false / LIVE_PROVIDER）识别、降级/混合运行防伪造与单字节防篡改校验")
    void shouldDistinguishSimulatedDemoFromLiveProviderExecutionAndDetectTampering() throws Exception {
        String email = "phasef_live_" + UUID.randomUUID().toString().substring(0, 8) + "@agenthub.dev";
        MvcResult regResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", "PhaseFLive#2026!",
                                "displayName", "Phase F Live Auditor"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

        JsonNode regData = objectMapper.readTree(regResult.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
        String token = regData.path("token").asText();

        MvcResult projResult = mockMvc.perform(post("/api/projects")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Live Provider Evidence Project",
                                "description", "Verify simulated=false when real provider token usage is recorded"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        String projectId = objectMapper.readTree(projResult.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("id").asText();

        // 构造一条使用 PROVIDER_DRIVEN 平台并附带真实 TokenUsageAuditEntity 记录的已完成运行
        String runId = "run-live-" + UUID.randomUUID().toString().substring(0, 8);
        String stepId = "step-live-" + UUID.randomUUID().toString().substring(0, 8);
        LocalDateTime now = LocalDateTime.now();

        String liveDsl = objectMapper.writeValueAsString(Map.of(
                "version", "1.0",
                "name", "Live Provider Pipeline",
                "nodes", List.of(Map.of(
                        "id", "live-coder",
                        "type", "AGENT",
                        "name", "Live LLM Coder",
                        "agentPlatform", "PROVIDER_DRIVEN",
                        "promptTemplate", "Implement feature with real provider"
                )),
                "edges", List.of()
        ));

        WorkflowRunEntity runEntity = new WorkflowRunEntity();
        runEntity.setId(runId);
        runEntity.setProjectId(projectId);
        runEntity.setDefinitionId("def-live-001");
        runEntity.setStatus("SUCCEEDED");
        runEntity.setIdempotencyKey("idem-live-" + runId);
        runEntity.setDslSnapshot(liveDsl);
        runEntity.setStartedAt(now.minusSeconds(5));
        runEntity.setFinishedAt(now);
        runEntity.setCreatedAt(now.minusSeconds(6));
        runEntity.setUpdatedAt(now);
        workflowRunRepository.save(runEntity);

        StepRunEntity stepEntity = new StepRunEntity();
        stepEntity.setId(stepId);
        stepEntity.setRunId(runId);
        stepEntity.setNodeId("live-coder");
        stepEntity.setStatus("SUCCEEDED");
        stepEntity.setAttempt(1);
        stepEntity.setStartedAt(now.minusSeconds(5));
        stepEntity.setFinishedAt(now);
        stepEntity.setDurationMs(5000L);
        stepEntity.setCreatedAt(now.minusSeconds(5));
        stepEntity.setUpdatedAt(now);
        stepRunRepository.save(stepEntity);

        TokenUsageAuditEntity usage = new TokenUsageAuditEntity();
        usage.setId(UUID.randomUUID().toString());
        usage.setRunId(runId);
        usage.setStepRunId(stepId);
        usage.setProviderId(null);
        usage.setProviderType("OPENAI");
        usage.setModel("gpt-4o-mini");
        usage.setPromptTokens(320);
        usage.setCompletionTokens(180);
        usage.setTotalTokens(500);
        usage.setLatencyMs(840L);
        usage.setEstimatedCost(0.000425);
        usage.setStatus("SUCCESS");
        usage.setCreatedAt(now.minusSeconds(1));
        tokenUsageAuditRepository.save(usage);

        MvcResult liveEvidenceRes = mockMvc.perform(get("/api/executions/runs/{runId}/evidence", runId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.simulated").value(false))
                .andExpect(jsonPath("$.data.executionMode").value("LIVE_PROVIDER"))
                .andExpect(jsonPath("$.data.providerRuntime.simulated").value(false))
                .andExpect(jsonPath("$.data.providerRuntime.totalTokens").value(500))
                .andReturn();

        JsonNode liveData = objectMapper.readTree(liveEvidenceRes.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
        RunEvidenceBundleView liveBundle = objectMapper.treeToValue(liveData, RunEvidenceBundleView.class);

        assertThat(liveBundle.isSimulated()).isFalse();
        assertThat(liveBundle.getExecutionMode()).isEqualTo("LIVE_PROVIDER");
        assertThat(liveBundle.getProviderRuntime().getProviderTypes()).containsExactly("OPENAI");
        assertThat(liveBundle.getProviderRuntime().getModels()).containsExactly("gpt-4o-mini");
        assertThat(liveBundle.getProviderRuntime().getTokenUsageRecords()).hasSize(1);
        assertThat(executionApplication.verifyEvidenceBundleChecksum(liveBundle)).isTrue();

        // 防伪造场景 A：PROVIDER_DRIVEN 运行因未配置外部 Key 降级至 MockLlmProvider (providerType=MOCK / prov-mock) -> 必须标记为 simulated=true (DEMO_SIMULATED)
        String fallbackRunId = "run-fb-" + UUID.randomUUID().toString().substring(0, 8);
        String fallbackStepId = "step-fb-" + UUID.randomUUID().toString().substring(0, 8);
        WorkflowRunEntity fallbackRun = new WorkflowRunEntity(fallbackRunId, projectId, "def-live-001", "SUCCEEDED", "idem-fb-" + fallbackRunId);
        fallbackRun.setDslSnapshot(liveDsl);
        workflowRunRepository.save(fallbackRun);

        StepRunEntity fallbackStep = new StepRunEntity(fallbackStepId, fallbackRunId, "live-coder", "SUCCEEDED");
        stepRunRepository.save(fallbackStep);

        TokenUsageAuditEntity mockFallbackUsage = new TokenUsageAuditEntity();
        mockFallbackUsage.setId(UUID.randomUUID().toString());
        mockFallbackUsage.setRunId(fallbackRunId);
        mockFallbackUsage.setStepRunId(fallbackStepId);
        mockFallbackUsage.setProviderId("prov-mock");
        mockFallbackUsage.setProviderType("MOCK");
        mockFallbackUsage.setModel("mock-v1");
        mockFallbackUsage.setPromptTokens(80);
        mockFallbackUsage.setCompletionTokens(40);
        mockFallbackUsage.setTotalTokens(120);
        mockFallbackUsage.setLatencyMs(12L);
        mockFallbackUsage.setEstimatedCost(0.0);
        mockFallbackUsage.setStatus("SUCCESS");
        mockFallbackUsage.setCreatedAt(now);
        tokenUsageAuditRepository.save(mockFallbackUsage);

        mockMvc.perform(get("/api/executions/runs/{runId}/evidence", fallbackRunId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.simulated").value(true))
                .andExpect(jsonPath("$.data.executionMode").value("DEMO_SIMULATED"))
                .andExpect(jsonPath("$.data.providerRuntime.simulated").value(true));

        // 防伪造场景 B：PROVIDER_DRIVEN 运行在调用外部 LLM 前失败 (status=FAILED, providerType=ROUTER, totalTokens=0) -> 必须标记为 simulated=true
        String failedRouterRunId = "run-fail-" + UUID.randomUUID().toString().substring(0, 8);
        String failedRouterStepId = "step-fail-" + UUID.randomUUID().toString().substring(0, 8);
        WorkflowRunEntity failedRouterRun = new WorkflowRunEntity(failedRouterRunId, projectId, "def-live-001", "FAILED", "idem-fail-" + failedRouterRunId);
        failedRouterRun.setDslSnapshot(liveDsl);
        workflowRunRepository.save(failedRouterRun);

        StepRunEntity failedRouterStep = new StepRunEntity(failedRouterStepId, failedRouterRunId, "live-coder", "FAILED");
        stepRunRepository.save(failedRouterStep);

        TokenUsageAuditEntity failedRouterUsage = new TokenUsageAuditEntity();
        failedRouterUsage.setId(UUID.randomUUID().toString());
        failedRouterUsage.setRunId(failedRouterRunId);
        failedRouterUsage.setStepRunId(failedRouterStepId);
        failedRouterUsage.setProviderId(null);
        failedRouterUsage.setProviderType("ROUTER");
        failedRouterUsage.setModel("unknown");
        failedRouterUsage.setPromptTokens(0);
        failedRouterUsage.setCompletionTokens(0);
        failedRouterUsage.setTotalTokens(0);
        failedRouterUsage.setLatencyMs(0L);
        failedRouterUsage.setEstimatedCost(0.0);
        failedRouterUsage.setStatus("FAILED");
        failedRouterUsage.setCreatedAt(now);
        tokenUsageAuditRepository.save(failedRouterUsage);

        mockMvc.perform(get("/api/executions/runs/{runId}/evidence", failedRouterRunId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.simulated").value(true))
                .andExpect(jsonPath("$.data.executionMode").value("DEMO_SIMULATED"));

        // 防伪造场景 C：混合 MOCK 与 PROVIDER_DRIVEN 节点的运行 -> 必须标记为 simulated=true
        String mixedRunId = "run-mix-" + UUID.randomUUID().toString().substring(0, 8);
        String mixedStepId = "step-mix-" + UUID.randomUUID().toString().substring(0, 8);
        String mixedDsl = objectMapper.writeValueAsString(Map.of(
                "version", "1.0",
                "name", "Mixed Pipeline",
                "nodes", List.of(
                        Map.of("id", "mock-step", "type", "AGENT", "name", "Mock Step", "agentPlatform", "MOCK"),
                        Map.of("id", "live-step", "type", "AGENT", "name", "Live Step", "agentPlatform", "PROVIDER_DRIVEN")
                ),
                "edges", List.of()
        ));
        WorkflowRunEntity mixedRun = new WorkflowRunEntity(mixedRunId, projectId, "def-mix-001", "SUCCEEDED", "idem-mix-" + mixedRunId);
        mixedRun.setDslSnapshot(mixedDsl);
        workflowRunRepository.save(mixedRun);
        StepRunEntity mixedStep = new StepRunEntity(mixedStepId, mixedRunId, "live-step", "SUCCEEDED");
        stepRunRepository.save(mixedStep);

        TokenUsageAuditEntity mixedLiveUsage = new TokenUsageAuditEntity();
        mixedLiveUsage.setId(UUID.randomUUID().toString());
        mixedLiveUsage.setRunId(mixedRunId);
        mixedLiveUsage.setStepRunId(mixedStepId);
        mixedLiveUsage.setProviderType("OPENAI");
        mixedLiveUsage.setModel("gpt-4o-mini");
        mixedLiveUsage.setPromptTokens(100);
        mixedLiveUsage.setCompletionTokens(50);
        mixedLiveUsage.setTotalTokens(150);
        mixedLiveUsage.setStatus("SUCCESS");
        mixedLiveUsage.setCreatedAt(now);
        tokenUsageAuditRepository.save(mixedLiveUsage);

        mockMvc.perform(get("/api/executions/runs/{runId}/evidence", mixedRunId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.simulated").value(true))
                .andExpect(jsonPath("$.data.executionMode").value("DEMO_SIMULATED"));

        // 防篡改测试 1：篡改 simulated 布尔标记（企图将模拟运行伪造为真实运行或反之）
        liveBundle.setSimulated(true);
        assertThat(executionApplication.verifyEvidenceBundleChecksum(liveBundle)).isFalse();
        liveBundle.setSimulated(false);
        assertThat(executionApplication.verifyEvidenceBundleChecksum(liveBundle)).isTrue();

        // 防篡改测试 2：篡改运行状态
        liveBundle.getRun().setStatus("FAILED");
        assertThat(executionApplication.verifyEvidenceBundleChecksum(liveBundle)).isFalse();
        liveBundle.getRun().setStatus("SUCCEEDED");
        assertThat(executionApplication.verifyEvidenceBundleChecksum(liveBundle)).isTrue();

        // 防篡改测试 3：篡改 Checksum 末位字符
        String originalChecksum = liveBundle.getChecksum();
        char lastChar = originalChecksum.charAt(originalChecksum.length() - 1);
        String corruptedChecksum = originalChecksum.substring(0, originalChecksum.length() - 1) + (lastChar == 'a' ? 'b' : 'a');
        liveBundle.setChecksum(corruptedChecksum);
        assertThat(executionApplication.verifyEvidenceBundleChecksum(liveBundle)).isFalse();
    }

    @Test
    @DisplayName("3. 证据包与工作区快照接口多租户/跨项目隔离门禁：未认证 1001、越权 1002 与不存在运行 6002")
    void shouldEnforceMultiTenantSecurityBoundaryOnEvidenceEndpoints() throws Exception {
        // 注册租户 A 与租户 B
        String tokenA = registerUser("tenant_a_" + UUID.randomUUID().toString().substring(0, 6) + "@agenthub.dev");
        String tokenB = registerUser("tenant_b_" + UUID.randomUUID().toString().substring(0, 6) + "@agenthub.dev");

        MvcResult projARes = mockMvc.perform(post("/api/projects")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Tenant A Secret Project",
                                "description", "Owned strictly by Tenant A"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        JsonNode projAData = objectMapper.readTree(projARes.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
        String projectIdA = projAData.path("id").asText();
        String workspaceIdA = projAData.path("workspaceId").asText();

        MvcResult projA2Res = mockMvc.perform(post("/api/projects")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Tenant A Second Project",
                                "description", "Second project of Tenant A"
                        ))))
                .andExpect(status().isOk())
                .andReturn();
        String workspaceIdA2 = objectMapper.readTree(projA2Res.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("workspaceId").asText();

        MvcResult projBRes = mockMvc.perform(post("/api/projects")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Tenant B Own Project",
                                "description", "Owned by Tenant B"
                        ))))
                .andExpect(status().isOk())
                .andReturn();
        String workspaceIdB = objectMapper.readTree(projBRes.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("workspaceId").asText();

        String runIdA = "run-sec-" + UUID.randomUUID().toString().substring(0, 8);
        LocalDateTime now = LocalDateTime.now();
        WorkflowRunEntity runA = new WorkflowRunEntity();
        runA.setId(runIdA);
        runA.setProjectId(projectIdA);
        runA.setDefinitionId("def-sec-a");
        runA.setStatus("SUCCEEDED");
        runA.setCreatedAt(now);
        runA.setUpdatedAt(now);
        workflowRunRepository.save(runA);

        // 1) 未携带 Token 访问证据包与下载接口 -> code = 1001 (UNAUTHORIZED)
        mockMvc.perform(get("/api/executions/runs/{runId}/evidence", runIdA))
                .andExpect(jsonPath("$.code").value(1001));

        mockMvc.perform(get("/api/runs/{runId}/evidence/download", runIdA))
                .andExpect(jsonPath("$.code").value(1001));

        // 2) 租户 B 越权访问租户 A 的运行证据包 -> code = 1002 (FORBIDDEN)
        mockMvc.perform(get("/api/executions/runs/{runId}/evidence", runIdA)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
                .andExpect(jsonPath("$.code").value(1002));

        mockMvc.perform(get("/api/runs/{runId}/evidence/download", runIdA)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
                .andExpect(jsonPath("$.code").value(1002));

        // 3) 访问不存在的运行证据包 -> code = 6002 (RUN_NOT_FOUND)
        mockMvc.perform(get("/api/executions/runs/{runId}/evidence", "run-non-existent-99999")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(jsonPath("$.code").value(6002));

        // 4) 租户 B 在自身工作区调用 git/snapshot 但传入租户 A 的 runIdA 企图注入伪造产物 -> 必须拦截 (1002 FORBIDDEN)
        mockMvc.perform(post("/api/workspaces/{workspaceId}/git/snapshot", workspaceIdB)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "runId", runIdA,
                                "message", "malicious cross-tenant artifact injection"
                        ))))
                .andExpect(jsonPath("$.code").value(1002));

        // 5) 租户 A 在项目 2 的工作区调用 git/snapshot 但传入项目 1 的 runIdA -> 必须拦截跨项目绑定 (1002 FORBIDDEN)
        mockMvc.perform(post("/api/workspaces/{workspaceId}/git/snapshot", workspaceIdA2)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "runId", runIdA,
                                "message", "cross-project artifact mismatch"
                        ))))
                .andExpect(jsonPath("$.code").value(1002));

        // 6) 传入不存在的 runId 调用 git/snapshot -> 必须拦截 (6002 RUN_NOT_FOUND)
        mockMvc.perform(post("/api/workspaces/{workspaceId}/git/snapshot", workspaceIdA)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "runId", "run-non-existent-99999",
                                "message", "non-existent run snapshot"
                        ))))
                .andExpect(jsonPath("$.code").value(6002));
    }

    @Test
    @DisplayName("4. 仓库样例证据包（sample-run-evidence-bundle.json）、跨平台脚本与文档口径诚实性校验")
    void shouldVerifySampleEvidenceBundleAndDocumentationHonestyContracts() throws Exception {
        Path repoRoot = resolveRepositoryRoot();

        // 1) 校验 docs/evidence/sample-run-evidence-bundle.json 存在、结构完整、枚举值与真实后端一致、无明文密钥且 SHA-256 校验和 100% 匹配
        Path samplePath = repoRoot.resolve("docs/evidence/sample-run-evidence-bundle.json");
        assertThat(Files.exists(samplePath))
                .as("Sample evidence bundle must exist at docs/evidence/sample-run-evidence-bundle.json")
                .isTrue();

        String sampleRaw = Files.readString(samplePath, StandardCharsets.UTF_8);
        assertThat(sampleRaw).doesNotContain("sk-proj-");
        assertThat(sampleRaw).doesNotContain("sk-live-");
        assertThat(sampleRaw).doesNotContain("sk-ant-");

        RunEvidenceBundleView sampleBundle = objectMapper.readValue(sampleRaw, RunEvidenceBundleView.class);
        assertThat(sampleBundle.getSchemaVersion()).isEqualTo("agenthub.evidence.v1");
        assertThat(sampleBundle.getBundleId()).isNotBlank();
        assertThat(sampleBundle.getProject()).isNotNull();
        assertThat(sampleBundle.getRun()).isNotNull();
        assertThat(sampleBundle.getSteps()).isNotEmpty();
        assertThat(sampleBundle.getEvents()).isNotEmpty();
        assertThat(sampleBundle.getProviderRuntime()).isNotNull();
        assertThat(sampleBundle.getGitDiff()).isNotNull();
        assertThat(sampleBundle.getTestReportSummary()).isNotNull();
        assertThat(sampleBundle.getArtifacts()).isNotEmpty();
        assertThat(sampleBundle.getArtifacts().get(0).getArtifactType()).isEqualTo("SNAPSHOT");
        assertThat(sampleBundle.getDeployments()).isNotEmpty();
        assertThat(sampleBundle.getDeployments().get(0).getTarget()).isEqualTo("STATIC_PREVIEW");
        assertThat(sampleBundle.getDeployments().get(0).getSandboxType()).isEqualTo("LOCAL_PROCESS");
        assertThat(sampleBundle.getDeployments().get(0).getTemplateType()).isEqualTo("STATIC_WEB_TEMPLATE");
        assertThat(sampleBundle.getDeployments().get(0).getPort()).isBetween(18000, 18999);
        assertThat(sampleBundle.getDeployments().get(0).getLifecycleTrace())
                .isEqualTo("CREATED->BUILD->START->HEALTH_CHECK->RUNNING->STOP->CLEANUP->STOPPED");

        String expectedChecksum = ExecutionApplicationService.computeDeterministicChecksum(sampleBundle);
        assertThat(sampleBundle.getChecksum())
                .as("Committed sample-run-evidence-bundle.json checksum must match deterministic SHA-256 calculation")
                .isEqualTo(expectedChecksum);
        assertThat(executionApplication.verifyEvidenceBundleChecksum(sampleBundle)).isTrue();

        // 2) 校验跨平台一键演示脚本存在且使用真实 API 路由（严禁残留废弃的 /api/v1/ 假接口或错误的 data[-1].id 索引）
        Path bashScript = repoRoot.resolve("scripts/run-e2e-demo.sh");
        Path psScript = repoRoot.resolve("scripts/run-e2e-demo.ps1");
        assertThat(Files.exists(bashScript)).isTrue();
        assertThat(Files.exists(psScript)).isTrue();

        for (Path script : List.of(bashScript, psScript)) {
            String content = Files.readString(script, StandardCharsets.UTF_8);
            assertThat(content).contains("/api/auth/register");
            assertThat(content).contains("/api/projects");
            assertThat(content).contains("/api/workflows/definitions/validate");
            assertThat(content).contains("/api/workflows/definitions");
            assertThat(content).contains("/api/executions/runs");
            assertThat(content).contains("/api/approvals");
            assertThat(content).contains("/api/deployments");
            assertThat(content).contains("/evidence/download");
            assertThat(content).doesNotContain("data[-1].id");
            assertThat(content).doesNotContain("/api/v1/workflows/run");
            assertThat(content).doesNotContain("/api/v1/sandboxes/");
            assertThat(content).doesNotContain("/api/v1/workspaces/");
        }

        // 3) 校验关键文档与 ADR 存在且口径诚实（无未实测的 10,000+ QPS 吹嘘、无失效 /api/v1/ 演示路由、无过时状态机枚举）
        Path e2eGuide = repoRoot.resolve("docs/E2E_DEMO_GUIDE.md");
        Path capabilityDoc = repoRoot.resolve("docs/CURRENT_CAPABILITY_BOUNDARIES.md");
        Path readmeDoc = repoRoot.resolve("README.md");
        Path whitepaperDoc = repoRoot.resolve("docs/TECH_WHITEPAPER_AND_DEFENSE.md");
        Path adr002 = repoRoot.resolve("docs/adr/ADR-002-sse-and-persistent-event-stream.md");
        Path adr005 = repoRoot.resolve("docs/adr/ADR-005-database-driven-lease-and-crash-recovery.md");

        for (Path doc : List.of(e2eGuide, capabilityDoc, readmeDoc, whitepaperDoc, adr002, adr005)) {
            assertThat(Files.exists(doc)).as("Required Phase-F doc must exist: " + doc).isTrue();
        }

        String e2eContent = Files.readString(e2eGuide, StandardCharsets.UTF_8);
        assertThat(e2eContent).contains("/api/executions/runs");
        assertThat(e2eContent).contains("/evidence/download");
        assertThat(e2eContent).doesNotContain("data[-1].id");
        assertThat(e2eContent).doesNotContain("/api/v1/workflows/run");
        assertThat(e2eContent).doesNotContain("/api/v1/sandboxes/create");

        String whitepaperContent = Files.readString(whitepaperDoc, StandardCharsets.UTF_8);
        assertThat(whitepaperContent).doesNotContain("10,000+ QPS");
        assertThat(whitepaperContent).contains("HighConcurrencyStressIntegrationTest");
        assertThat(whitepaperContent).contains("PersistentExecutionRecoveryAndLeaseTest");
    }

    private String registerUser(String email) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", "TenantPass#2026!",
                                "displayName", email
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("token").asText();
    }

    private String waitForPendingApproval(String token, String runId) throws Exception {
        long deadline = System.currentTimeMillis() + 15_000L;
        while (System.currentTimeMillis() < deadline) {
            MvcResult res = mockMvc.perform(get("/api/approvals/runs/{runId}", runId)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk())
                    .andReturn();
            JsonNode arr = objectMapper.readTree(res.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
            if (arr.isArray()) {
                for (JsonNode item : arr) {
                    if ("PENDING".equalsIgnoreCase(item.path("status").asText())) {
                        return item.path("id").asText();
                    }
                }
            }
            Thread.sleep(150L);
        }
        throw new AssertionError("Timed out waiting for PENDING approval for runId=" + runId);
    }

    private String waitForRunStatus(String token, String runId, String expectedStatus) throws Exception {
        long deadline = System.currentTimeMillis() + 15_000L;
        String lastStatus = "";
        while (System.currentTimeMillis() < deadline) {
            MvcResult res = mockMvc.perform(get("/api/executions/runs/{runId}", runId)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk())
                    .andReturn();
            lastStatus = objectMapper.readTree(res.getResponse().getContentAsString(StandardCharsets.UTF_8))
                    .path("data").path("status").asText();
            if (expectedStatus.equalsIgnoreCase(lastStatus)
                    || "FAILED".equalsIgnoreCase(lastStatus)
                    || "CANCELLED".equalsIgnoreCase(lastStatus)
                    || "TIMED_OUT".equalsIgnoreCase(lastStatus)) {
                return lastStatus;
            }
            Thread.sleep(150L);
        }
        return lastStatus;
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
