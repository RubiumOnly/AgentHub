package com.agenthub;

import com.agenthub.audit.infrastructure.entity.ArtifactEntity;
import com.agenthub.audit.infrastructure.repository.ArtifactRepository;
import com.agenthub.domain.workspace.service.WorkspaceResolver;
import com.agenthub.execution.application.ExecutionApplication;
import com.agenthub.execution.dto.StartRunCommand;
import com.agenthub.execution.dto.WorkflowRunView;
import com.agenthub.sandbox.api.dto.CreateDeploymentRequest;
import com.agenthub.sandbox.api.dto.DeploymentResponse;
import com.agenthub.sandbox.application.DeploymentApplication;
import com.agenthub.sandbox.domain.model.SandboxExecutionRequest;
import com.agenthub.sandbox.domain.provider.DockerSandbox;
import com.agenthub.sandbox.domain.service.PortAllocationService;
import com.agenthub.sandbox.infrastructure.entity.DeploymentEntity;
import com.agenthub.sandbox.infrastructure.repository.DeploymentRepository;
import com.agenthub.shared.context.RequestContext;
import com.agenthub.shared.exception.BusinessException;
import com.agenthub.shared.exception.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 阶段 D（真实沙箱部署与项目交付闭环）全维度验收与边界防御集成测试套件。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PhaseDSandboxLifecycleAndDeliveryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DeploymentApplication deploymentApplication;

    @Autowired
    private DeploymentRepository deploymentRepository;

    @Autowired
    private PortAllocationService portAllocationService;

    @Autowired
    private WorkspaceResolver workspaceResolver;

    @Autowired
    private ExecutionApplication executionApplication;

    @Autowired
    private ArtifactRepository artifactRepository;

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    private String registerAndGetToken(String email) throws Exception {
        String payload = String.format("{\"email\":\"%s\",\"password\":\"SecurePass123!\",\"displayName\":\"PhaseDUser\"}", email);
        MvcResult res = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        JsonNode root = objectMapper.readTree(res.getResponse().getContentAsString());
        return root.at("/data/token").asText();
    }

    private String extractJsonField(MvcResult res, String pointer) throws Exception {
        JsonNode root = objectMapper.readTree(res.getResponse().getContentAsString());
        return root.at(pointer).asText();
    }

    // =========================================================================
    // 1. 从通过质量门禁的 Artifact 创建真实 Deployment，外部 HTTP 客户端真实访问，停止后实例消失
    // =========================================================================

    @Test
    @DisplayName("阶段D测试1：从通过质量门禁的 Artifact 创建真实部署，独立 HTTP 客户端直接访问返回真实项目内容且拦截目录穿越，停止后服务真实下线")
    void shouldDeployFromApprovedArtifactAndServeLiveProjectContentThenStopCleanly() throws Exception {
        String tokenAlice = registerAndGetToken("alice_phased_1_" + System.currentTimeMillis() + "@agenthub.local");

        // 1. Alice 创建项目
        MvcResult projRes = mockMvc.perform(post("/api/projects")
                        .header("Authorization", "Bearer " + tokenAlice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"PhaseD Live Delivery App\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String projectId = extractJsonField(projRes, "/data/id");

        // 2. 在受控工作区写入真实构建产物 dist/index.html 与带 '+' 的静态资源文件及敏感 .env 文件
        Path wsRoot = workspaceResolver.getWorkspaceRoot(projectId);
        Path distDir = wsRoot.resolve("dist");
        Files.createDirectories(distDir);
        String deliverableContent = "<!DOCTYPE html><html><body><h1>Phase D Verified Deliverable Content 2026</h1></body></html>";
        Files.writeString(distDir.resolve("index.html"), deliverableContent, StandardCharsets.UTF_8);
        Files.writeString(wsRoot.resolve("app+v1.js"), "console.log('plus-file-ok');", StandardCharsets.UTF_8);
        Files.writeString(wsRoot.resolve(".env"), "SECRET_KEY=should-never-leak", StandardCharsets.UTF_8);

        // 3. 创建一次 Run 并生成通过质量门禁 (APPROVED) 的 Artifact
        RequestContext.get().setSystem(true);
        WorkflowRunView run = executionApplication.startRun(new StartRunCommand(projectId, null, "Phase D Delivery Run"));
        String artifactId = "art-verified-" + UUID.randomUUID().toString().substring(0, 8);
        ArtifactEntity approvedArtifact = new ArtifactEntity(
                artifactId, run.getId(), "step-1", "STATIC_BUNDLE", "dist/index.html", "sha256-verified-123", "{}"
        );
        approvedArtifact.setReviewStatus("APPROVED");
        artifactRepository.save(approvedArtifact);
        RequestContext.clear();

        // 4. Alice 基于该 Artifact 创建真实部署
        CreateDeploymentRequest deployReq = new CreateDeploymentRequest(projectId, "STATIC_PREVIEW", "LOCAL_PROCESS");
        deployReq.setArtifactId(artifactId);
        deployReq.setTemplateType("STATIC_WEB_TEMPLATE");

        MvcResult deployRes = mockMvc.perform(post("/api/deployments")
                        .header("Authorization", "Bearer " + tokenAlice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(deployReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("RUNNING"))
                .andExpect(jsonPath("$.data.artifactId").value(artifactId))
                .andExpect(jsonPath("$.data.templateType").value("STATIC_WEB_TEMPLATE"))
                .andExpect(jsonPath("$.data.lifecycleTrace").value("CREATED->BUILD->START->HEALTH_CHECK->RUNNING"))
                .andExpect(jsonPath("$.data.processId").isNumber())
                .andExpect(jsonPath("$.data.containerId").isNotEmpty())
                .andExpect(jsonPath("$.data.workingDirectory").isNotEmpty())
                .andExpect(jsonPath("$.data.logCursor").isNumber())
                .andReturn();

        String deploymentId = extractJsonField(deployRes, "/data/id");
        String liveUrl = extractJsonField(deployRes, "/data/url");
        int boundPort = Integer.parseInt(extractJsonField(deployRes, "/data/port"));

        // 5. 使用独立 HttpClient 直接请求返回的 URL 与带 '+' 的静态文件，验证真实服务存活且 '+' 不被篡改为空格
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        HttpResponse<String> pageResp = client.send(
                HttpRequest.newBuilder().uri(URI.create(liveUrl)).GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );
        assertThat(pageResp.statusCode()).isEqualTo(200);
        assertThat(pageResp.body()).contains("Phase D Verified Deliverable Content 2026");

        HttpResponse<String> plusFileResp = client.send(
                HttpRequest.newBuilder().uri(URI.create("http://127.0.0.1:" + boundPort + "/app+v1.js")).GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );
        assertThat(plusFileResp.statusCode()).isEqualTo(200);
        assertThat(plusFileResp.body()).contains("plus-file-ok");

        // 6. 验证静态预览服务严格阻断 .git、.env、单重/双重 URL 编码目录穿越及 Windows 非法路径字符 (不崩溃断连)
        for (String blockedPath : List.of(
                "/.git/config",
                "/.env",
                "/%2e%2e/%2e%2e/etc/passwd",
                "/%252e%252e/%252e%252e/etc/passwd",
                "/C:/Windows/win.ini"
        )) {
            HttpResponse<String> blockedResp = client.send(
                    HttpRequest.newBuilder().uri(URI.create("http://127.0.0.1:" + boundPort + blockedPath)).GET().build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            assertThat(blockedResp.statusCode()).as("Path should be blocked with 403: " + blockedPath).isEqualTo(403);
        }

        // 7. 验证基于游标 (cursor 与 offset 双参数兼容) 的增量日志拉取
        MvcResult fullLogRes = mockMvc.perform(get("/api/deployments/" + deploymentId + "/logs")
                        .header("Authorization", "Bearer " + tokenAlice))
                .andExpect(status().isOk())
                .andReturn();
        String fullLogs = extractJsonField(fullLogRes, "/data");
        assertThat(fullLogs).contains("DEPLOYMENT READY");

        int cursorOffset = fullLogs.length() - 10;
        MvcResult tailByCursorRes = mockMvc.perform(get("/api/deployments/" + deploymentId + "/logs?cursor=" + cursorOffset)
                        .header("Authorization", "Bearer " + tokenAlice))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(extractJsonField(tailByCursorRes, "/data")).hasSize(10);

        MvcResult tailByOffsetRes = mockMvc.perform(get("/api/deployments/" + deploymentId + "/logs?offset=" + cursorOffset)
                        .header("Authorization", "Bearer " + tokenAlice))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(extractJsonField(tailByOffsetRes, "/data")).hasSize(10);

        // 8. 停止部署，验证状态机走完 STOP->CLEANUP->STOPPED，端口释放，且 HTTP 服务真实消亡
        mockMvc.perform(post("/api/deployments/" + deploymentId + "/stop")
                        .header("Authorization", "Bearer " + tokenAlice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("STOPPED"))
                .andExpect(jsonPath("$.data.terminationReason").value("USER_STOPPED"))
                .andExpect(jsonPath("$.data.lifecycleTrace").value("CREATED->BUILD->START->HEALTH_CHECK->RUNNING->STOP->CLEANUP->STOPPED"));

        assertThat(portAllocationService.isPortAllocated(boundPort)).isFalse();
        assertThatThrownBy(() -> client.send(
                HttpRequest.newBuilder().uri(URI.create(liveUrl)).timeout(Duration.ofMillis(500)).GET().build(),
                HttpResponse.BodyHandlers.discarding()
        )).isInstanceOf(Exception.class);
    }

    // =========================================================================
    // 2. 质量门禁未通过/跨项目 Artifact 阻断与非法模板拦截
    // =========================================================================

    @Test
    @DisplayName("阶段D测试2：拒绝部署未通过质量门禁 (REJECTED) 或跨项目归属的 Artifact，并拦截未知任意模板")
    void shouldRejectDeploymentOfFailedQualityGateOrCrossProjectArtifact() throws Exception {
        String tokenAlice = registerAndGetToken("alice_phased_2_" + System.currentTimeMillis() + "@agenthub.local");

        MvcResult projARes = mockMvc.perform(post("/api/projects")
                        .header("Authorization", "Bearer " + tokenAlice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Project A\"}"))
                .andReturn();
        String projA = extractJsonField(projARes, "/data/id");

        MvcResult projBRes = mockMvc.perform(post("/api/projects")
                        .header("Authorization", "Bearer " + tokenAlice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Project B\"}"))
                .andReturn();
        String projB = extractJsonField(projBRes, "/data/id");

        RequestContext.get().setSystem(true);
        WorkflowRunView runA = executionApplication.startRun(new StartRunCommand(projA, null, "Run A"));
        String rejectedArtId = "art-rejected-" + UUID.randomUUID().toString().substring(0, 8);
        ArtifactEntity rejectedArt = new ArtifactEntity(rejectedArtId, runA.getId(), "s1", "BUNDLE", "dist", "chk", "{}");
        rejectedArt.setReviewStatus("REJECTED");
        artifactRepository.save(rejectedArt);

        String approvedArtAId = "art-projA-" + UUID.randomUUID().toString().substring(0, 8);
        ArtifactEntity approvedArtA = new ArtifactEntity(approvedArtAId, runA.getId(), "s2", "BUNDLE", "dist", "chk", "{}");
        approvedArtA.setReviewStatus("APPROVED");
        artifactRepository.save(approvedArtA);
        RequestContext.clear();

        // 1. 尝试部署 REJECTED 产物 -> 返回 400 (PARAM_ERROR)
        CreateDeploymentRequest reqRejected = new CreateDeploymentRequest(projA, "STATIC_PREVIEW", "LOCAL_PROCESS");
        reqRejected.setArtifactId(rejectedArtId);
        mockMvc.perform(post("/api/deployments")
                        .header("Authorization", "Bearer " + tokenAlice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqRejected)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));

        // 2. 尝试在 Project B 中部署属于 Project A 的产物 -> 返回 1002 (FORBIDDEN)
        CreateDeploymentRequest reqCrossProj = new CreateDeploymentRequest(projB, "STATIC_PREVIEW", "LOCAL_PROCESS");
        reqCrossProj.setArtifactId(approvedArtAId);
        mockMvc.perform(post("/api/deployments")
                        .header("Authorization", "Bearer " + tokenAlice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqCrossProj)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1002));

        // 3. 尝试传入不受支持的任意部署模板 -> 返回 400 (PARAM_ERROR)
        CreateDeploymentRequest reqBadTemplate = new CreateDeploymentRequest(projA, "STATIC_PREVIEW", "LOCAL_PROCESS");
        reqBadTemplate.setTemplateType("UNTRUSTED_RAW_SHELL_TEMPLATE");
        mockMvc.perform(post("/api/deployments")
                        .header("Authorization", "Bearer " + tokenAlice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqBadTemplate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    // =========================================================================
    // 3. 端口预留竞态修复、外部占用检测与启动/健康检查失败自动清理释放端口
    // =========================================================================

    @Test
    @DisplayName("阶段D测试3：端口分配准确检测宿主机真实 TCP 端口占用，且当服务启动或健康检查失败时自动清理并释放端口")
    void shouldDetectOccupiedOsSocketAndReleasePortOnStartupOrHealthCheckFailure() throws Exception {
        // 1. 在 127.0.0.1 上真实占用一个端口，验证 allocateOrReserveSpecificPort 拒绝分配
        try (ServerSocket occupiedSocket = new ServerSocket()) {
            occupiedSocket.setReuseAddress(false);
            occupiedSocket.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0));
            int occupiedPort = occupiedSocket.getLocalPort();

            assertThat(portAllocationService.isSocketAvailable(occupiedPort)).isFalse();
            assertThatThrownBy(() -> portAllocationService.allocateOrReserveSpecificPort("dep-conflict", occupiedPort))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.DEPLOYMENT_PORT_EXHAUSTED));
        }

        // 2. 验证禁止请求绑定后端或基础设施保留端口 (8080 / 3306 / 6379)
        assertThatThrownBy(() -> portAllocationService.allocateOrReserveSpecificPort("dep-infra", 8080))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.PARAM_ERROR));

        // 3. 启动一个 LOCAL_PROCESS 部署但未提供 startCommand，验证在 START 阶段立即失败并释放端口
        RequestContext.get().setSystem(true);
        CreateDeploymentRequest missingStartReq = new CreateDeploymentRequest("proj-default", "LOCAL_PROCESS", "LOCAL_PROCESS");
        DeploymentResponse missingStartResp = deploymentApplication.createAndDeploy(missingStartReq);
        assertThat(missingStartResp.getStatus()).isEqualTo("FAILED");
        assertThat(missingStartResp.getTerminationReason()).isEqualTo("START_FAILED");
        assertThat(missingStartResp.getLifecycleTrace()).contains("START->CLEANUP->FAILED");
        assertThat(portAllocationService.isPortAllocated(missingStartResp.getPort())).isFalse();

        // 4. 启动一个 LOCAL_PROCESS 部署但该命令不监听 HTTP 端口，导致健康检查失败，验证状态机走完 CLEANUP->FAILED 且端口被自动释放
        CreateDeploymentRequest failReq = new CreateDeploymentRequest("proj-default", "LOCAL_PROCESS", "LOCAL_PROCESS");
        failReq.setStartCommand("echo non-listening-command");
        failReq.setHealthCheckPath("/health");

        DeploymentResponse failedResp = deploymentApplication.createAndDeploy(failReq);
        assertThat(failedResp.getStatus()).isEqualTo("FAILED");
        assertThat(failedResp.getTerminationReason()).isIn("START_FAILED", "HEALTH_CHECK_FAILED");
        assertThat(failedResp.getLifecycleTrace()).contains("CLEANUP->FAILED");
        assertThat(portAllocationService.isPortAllocated(failedResp.getPort())).isFalse();
    }

    // =========================================================================
    // 4. 后端重启/进程崩溃后的孤儿部署对账回收 (Orphan Reconciliation)
    // =========================================================================

    @Test
    @DisplayName("阶段D测试4：后端重启或进程意外崩溃后，孤儿部署对账器 (reconcileOrphanDeployments) 自动将失活 RUNNING 部署清理为 FAILED 并释放端口")
    void shouldReconcileOrphanedRunningDeploymentsAndReleasePorts() {
        RequestContext.get().setSystem(true);
        String orphanId = "dep-orphan-" + UUID.randomUUID().toString().substring(0, 8);
        int orphanPort = portAllocationService.allocatePort(orphanId);

        DeploymentEntity orphanEntity = new DeploymentEntity(
                orphanId, "proj-default", "art-orphan", "RUNNING", "LOCAL_PROCESS", "http://localhost:" + orphanPort
        );
        orphanEntity.setPort(orphanPort);
        orphanEntity.setLifecycleTrace("CREATED->BUILD->START->HEALTH_CHECK->RUNNING");
        deploymentRepository.save(orphanEntity);

        assertThat(portAllocationService.isPortAllocated(orphanPort)).isTrue();

        // 执行孤儿对账
        int reconciledCount = deploymentApplication.reconcileOrphanDeployments();
        assertThat(reconciledCount).isGreaterThanOrEqualTo(1);

        DeploymentResponse afterReconcile = deploymentApplication.getDeployment(orphanId);
        assertThat(afterReconcile.getStatus()).isEqualTo("FAILED");
        assertThat(afterReconcile.getTerminationReason()).isEqualTo("ORPHAN_RECONCILED");
        assertThat(afterReconcile.getLifecycleTrace()).endsWith("STOP->CLEANUP->FAILED");
        assertThat(portAllocationService.isPortAllocated(orphanPort)).isFalse();
    }

    // =========================================================================
    // 5. Docker 沙箱容器隔离、非 root 强约束、敏感宿主挂载拦截与生产环境远程 Worker 策略
    // =========================================================================

    @Test
    @DisplayName("阶段D测试5：Docker 沙箱强制校验非 root 用户、禁止挂载宿主敏感目录，且生产环境拒绝未配置隔离 Worker 的本地 daemon 假设")
    void shouldEnforceDockerIsolationNonRootMountBoundariesAndProdWorkerPolicy(@TempDir Path tempWorkspace) {
        DockerSandbox dockerSandbox = new DockerSandbox();

        // 1. 生产 Profile 下未配置远程 Docker Worker 时，拒绝本地 Docker CLI/daemon 假设
        dockerSandbox.configureIsolationPolicy(true, false, "", "");
        assertThat(dockerSandbox.isAvailable()).isFalse();
        assertThatThrownBy(dockerSandbox::validateIsolationPolicy)
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.SANDBOX_EXECUTION_FAILED));

        // 2. 生产 Profile 下试图配置挂载宿主 /var/run/docker.sock 被拦截
        dockerSandbox.configureIsolationPolicy(true, true, "unix:///var/run/docker.sock", "");
        assertThatThrownBy(dockerSandbox::validateIsolationPolicy)
                .isInstanceOf(BusinessException.class);

        // 3. 生产 Profile 下配置受限远程 Docker Worker (tcp://10.0.0.50:2375) 时策略校验通过并正确生成参数
        dockerSandbox.configureIsolationPolicy(true, true, "tcp://10.0.0.50:2375", "");
        dockerSandbox.validateIsolationPolicy();

        SandboxExecutionRequest serviceReq = SandboxExecutionRequest.builder()
                .executionId("dep-docker-1")
                .command("node")
                .args(List.of("server.js"))
                .workingDirectory(tempWorkspace)
                .containerUser("1000:1000")
                .readOnlyRoot(true)
                .build();

        List<String> serviceArgs = dockerSandbox.buildDockerServiceArgs(serviceReq, "node:20-alpine", 18088, 18088);
        assertThat(serviceArgs).contains(
                "-H", "tcp://10.0.0.50:2375",
                "-d",
                "--name", "agenthub-dep-docker-1",
                "--user", "1000:1000",
                "--security-opt=no-new-privileges:true",
                "--cap-drop=ALL",
                "--pids-limit", "64",
                "--read-only",
                "/var/run:rw,noexec,nosuid,size=16m",
                "/var/cache:rw,noexec,nosuid,size=32m",
                "-p", "127.0.0.1:18088:18088"
        );

        List<String> inspectArgs = dockerSandbox.buildDockerInspectArgs("dep-docker-1");
        assertThat(inspectArgs).containsExactly(
                "docker", "-H", "tcp://10.0.0.50:2375", "inspect", "-f", "{{.State.Running}}", "agenthub-dep-docker-1"
        );

        // 4. 试图使用 root (0:0 或 root) 启动容器被严格阻断
        SandboxExecutionRequest rootReq = SandboxExecutionRequest.builder()
                .executionId("dep-root-attempt")
                .command("node")
                .workingDirectory(tempWorkspace)
                .containerUser("0:0")
                .build();
        assertThatThrownBy(() -> dockerSandbox.buildDockerArgs(rootReq, "node:20-alpine"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.SANDBOX_COMMAND_BLOCKED));

        // 5. 试图挂载宿主根目录 / 或包含 .. 的越权目录被严格阻断
        SandboxExecutionRequest rootMountReq = SandboxExecutionRequest.builder()
                .executionId("dep-mount-attempt")
                .command("node")
                .workingDirectory(Path.of("/"))
                .containerUser("1000:1000")
                .build();
        assertThatThrownBy(() -> dockerSandbox.buildDockerArgs(rootMountReq, "node:20-alpine"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.WORKSPACE_TRAVERSAL_DENIED));
    }

    // =========================================================================
    // 6. 预览端点多租户权限校验与前端 Dockerfile 语法修复验证
    // =========================================================================

    @Test
    @DisplayName("阶段D测试6：前端 Dockerfile 消除非法 shell COPY 语法（旧 /api/sandbox/preview 路由已随 Phase 10 退场，部署 Owner 校验由测试 1-5 的 /api/deployments 链路覆盖）")
    void shouldEnforcePreviewOwnershipAndVerifyFrontendDockerfile() throws Exception {
        // 校验前端 Dockerfile 已修复非法 COPY ... 2>/dev/null || true 语法且 public 目录存在
        Path frontendDockerfile = Path.of("../agenthub-frontend/Dockerfile").normalize();
        if (Files.exists(frontendDockerfile)) {
            String dockerfileContent = Files.readString(frontendDockerfile, StandardCharsets.UTF_8);
            assertThat(dockerfileContent).doesNotContain("2>/dev/null || true");
            assertThat(dockerfileContent).contains("COPY --from=builder --chown=nextjs:nodejs /app/public ./public");
        }
    }

    // =========================================================================
    // 7. 高并发多部署端口分配、真实 HTTP 实例并行探活与并发释放零泄漏压测
    // =========================================================================

    @Test
    @DisplayName("阶段D测试7：12 线程高并发创建部署时端口分配无冲突、全部 HTTP 实例真实可达且并发停止后 100% 释放无泄漏")
    void shouldHandleHighConcurrencyPortAllocationAndParallelDeploymentsWithoutCollisionOrLeak() throws Exception {
        int concurrency = 12;
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(concurrency);
        java.util.concurrent.CountDownLatch startLatch = new java.util.concurrent.CountDownLatch(1);
        java.util.List< java.util.concurrent.Future<DeploymentResponse> > futures = new java.util.ArrayList<>();

        for (int i = 0; i < concurrency; i++) {
            final int idx = i;
            futures.add(pool.submit(() -> {
                startLatch.await(5, java.util.concurrent.TimeUnit.SECONDS);
                RequestContext.get().setSystem(true);
                try {
                    CreateDeploymentRequest req = new CreateDeploymentRequest(
                            "proj-concurrent-" + idx, "STATIC_PREVIEW", "LOCAL_PROCESS"
                    );
                    req.setTemplateType("STATIC_WEB_TEMPLATE");
                    return deploymentApplication.createAndDeploy(req);
                } finally {
                    RequestContext.clear();
                }
            }));
        }

        startLatch.countDown();

        java.util.Set<Integer> allocatedPorts = new java.util.HashSet<>();
        java.util.List<DeploymentResponse> responses = new java.util.ArrayList<>();
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

        try {
            for (java.util.concurrent.Future<DeploymentResponse> f : futures) {
                DeploymentResponse resp = f.get(15, java.util.concurrent.TimeUnit.SECONDS);
                responses.add(resp);
                assertThat(resp.getStatus()).isEqualTo("RUNNING");
                assertThat(resp.getPort()).isNotNull();
                assertThat(allocatedPorts.add(resp.getPort()))
                        .as("Port %s must be uniquely allocated across concurrent deployments", resp.getPort())
                        .isTrue();

                HttpResponse<String> healthResp = client.send(
                        HttpRequest.newBuilder().uri(URI.create("http://127.0.0.1:" + resp.getPort() + "/health")).GET().build(),
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
                );
                assertThat(healthResp.statusCode()).isEqualTo(200);
                assertThat(healthResp.body()).contains(resp.getId());
            }
        } finally {
            RequestContext.get().setSystem(true);
            for (DeploymentResponse resp : responses) {
                deploymentApplication.stopDeployment(resp.getId());
                assertThat(portAllocationService.isPortAllocated(resp.getPort())).isFalse();
            }
            RequestContext.clear();
            pool.shutdownNow();
        }
    }
}
