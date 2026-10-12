package com.agenthub;

import com.agenthub.audit.application.ArtifactApplication;
import com.agenthub.audit.dto.ArtifactView;
import com.agenthub.audit.infrastructure.repository.ArtifactRepository;
import com.agenthub.domain.workspace.service.JGitWorkspaceManager;
import com.agenthub.domain.workspace.service.WorkspaceResolver;
import com.agenthub.execution.application.ExecutionApplication;
import com.agenthub.execution.domain.model.WorkflowRunStatus;
import com.agenthub.execution.dto.RunEventView;
import com.agenthub.execution.dto.StartRunCommand;
import com.agenthub.execution.dto.WorkflowRunView;
import com.agenthub.execution.infrastructure.repository.RunEventRepository;
import com.agenthub.execution.infrastructure.repository.StepRunRepository;
import com.agenthub.execution.infrastructure.repository.WorkflowRunRepository;
import com.agenthub.identity.application.AuthApplication;
import com.agenthub.identity.dto.AuthTokenView;
import com.agenthub.identity.dto.LoginCommand;
import com.agenthub.identity.dto.RegisterCommand;
import com.agenthub.identity.infrastructure.security.ActuatorSecurityFilter;
import com.agenthub.infrastructure.concurrency.WorkspaceLockManager;
import com.agenthub.infrastructure.metrics.AgentHubMetricsCollector;
import com.agenthub.infrastructure.ops.DisasterRecoveryBackupService;
import com.agenthub.infrastructure.ops.ProductionEnvironmentValidator;
import com.agenthub.infrastructure.ops.PrometheusAlertEvaluator;
import com.agenthub.orchestration.application.ApprovalApplication;
import com.agenthub.orchestration.domain.dsl.WorkflowDsl;
import com.agenthub.orchestration.domain.dsl.WorkflowEdgeDsl;
import com.agenthub.orchestration.domain.dsl.WorkflowNodeDsl;
import com.agenthub.orchestration.dto.ApprovalView;
import com.agenthub.orchestration.infrastructure.repository.ApprovalRepository;
import com.agenthub.orchestration.scheduler.DagExecutionEngine;
import com.agenthub.project.application.ProjectApplication;
import com.agenthub.project.dto.CreateProjectCommand;
import com.agenthub.project.dto.ProjectView;
import com.agenthub.project.infrastructure.entity.ProjectEntity;
import com.agenthub.project.infrastructure.repository.ProjectRepository;
import com.agenthub.project.infrastructure.repository.WorkspaceRepository;
import com.agenthub.sandbox.api.dto.CreateDeploymentRequest;
import com.agenthub.sandbox.api.dto.DeploymentResponse;
import com.agenthub.sandbox.application.DeploymentApplication;
import com.agenthub.sandbox.domain.model.DeploymentStatus;
import com.agenthub.sandbox.infrastructure.repository.DeploymentRepository;
import com.agenthub.shared.context.RequestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.FileSystemUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 阶段 E（生产配置、CI/CD 与灾备演练）全链路集成测试与故障注入演练套件。
 * 覆盖：
 * 1. .env.production.example 占位符与弱密码启动拦截门禁；
 * 2. 生产管理端点 /actuator/prometheus 与 /actuator/metrics 受信网络及 Scrape Token 鉴权；
 * 3. Dockerfile、docker-compose.prod.yml 最小权限/健康探针规范与灾备手册高危命令剔除；
 * 4. Prometheus 抓取配置、告警规则加载校验与实时指标异常触发告警；
 * 5. 多实例数据库级工作区排他锁（含毫秒级 expires_at_epoch_ms）互斥竞争与过期自愈；
 * 6. 数据库 + 受控工作区 AES-256-GCM 加密备份、SHA-256 校验、防篡改拦截与空环境完整还原；
 * 7. 数据库关联的工作区归档清理（禁止按 mtime 盲删活跃项目）与完整 Staging 交付闭环演练。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class PhaseEProductionOpsAndDisasterRecoveryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductionEnvironmentValidator productionEnvironmentValidator;

    @Autowired
    private PrometheusAlertEvaluator prometheusAlertEvaluator;

    @Autowired
    private DisasterRecoveryBackupService disasterRecoveryBackupService;

    @Autowired
    private ActuatorSecurityFilter actuatorSecurityFilter;

    @Autowired
    private AgentHubMetricsCollector metricsCollector;

    @Autowired
    private WorkspaceResolver workspaceResolver;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AuthApplication authApplication;

    @Autowired
    private ProjectApplication projectApplication;

    @Autowired
    private ExecutionApplication executionApplication;

    @Autowired
    private DagExecutionEngine dagExecutionEngine;

    @Autowired
    private ApprovalApplication approvalApplication;

    @Autowired
    private ArtifactApplication artifactApplication;

    @Autowired
    private DeploymentApplication deploymentApplication;

    @Autowired
    private JGitWorkspaceManager gitWorkspaceManager;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private WorkflowRunRepository workflowRunRepository;

    @Autowired
    private StepRunRepository stepRunRepository;

    @Autowired
    private RunEventRepository runEventRepository;

    @Autowired
    private ArtifactRepository artifactRepository;

    @Autowired
    private ApprovalRepository approvalRepository;

    @Autowired
    private DeploymentRepository deploymentRepository;

    @BeforeEach
    void setUp() {
        RequestContext.get().setUserId("user-1");
        actuatorSecurityFilter.configureForTesting(false, "", true);
        metricsCollector.setActiveRunsForDrill(0);
        metricsCollector.recordWorkspaceDiskAvailablePercent(85);
        metricsCollector.recordProviderCircuitBreakerState("deepseek", "OPEN", false);
    }

    @AfterEach
    void tearDown() {
        actuatorSecurityFilter.configureForTesting(false, "", true);
        metricsCollector.setActiveRunsForDrill(0);
        metricsCollector.recordWorkspaceDiskAvailablePercent(85);
        metricsCollector.recordProviderCircuitBreakerState("deepseek", "OPEN", false);
        RequestContext.clear();
        org.eclipse.jgit.lib.RepositoryCache.clear();
    }

    private Path resolveRepoFile(String relativePath) {
        Path direct = Path.of(relativePath);
        if (Files.exists(direct)) {
            return direct.toAbsolutePath().normalize();
        }
        Path parent = Path.of("..").resolve(relativePath);
        return parent.toAbsolutePath().normalize();
    }

    // =========================================================================
    // 1. 生产环境模板占位符与弱密码启动拦截校验
    // =========================================================================

    @Test
    @DisplayName("阶段E测试1：.env.production.example 必须仅含占位符且被预检门禁拦截，强随机密钥配置可通过")
    void shouldValidateProductionEnvTemplateAndRejectWeakOrPlaceholderSecrets() {
        Path envExamplePath = resolveRepoFile(".env.production.example");
        assertThat(Files.exists(envExamplePath)).isTrue();

        ProductionEnvironmentValidator.ValidationResult exampleResult =
                productionEnvironmentValidator.validateEnvFile(envExamplePath);
        assertThat(exampleResult.valid()).isFalse();
        assertThat(exampleResult.violations())
                .anyMatch(v -> v.contains("AGENTHUB_AUTH_SECRET"))
                .anyMatch(v -> v.contains("MYSQL_ROOT_PASSWORD"))
                .anyMatch(v -> v.contains("MYSQL_PASSWORD"))
                .anyMatch(v -> v.contains("REDIS_PASSWORD"))
                .anyMatch(v -> v.contains("ACTUATOR_PROMETHEUS_TOKEN"));

        // 测试弱密码、过短密钥与未替换的 API Key 占位符拦截
        Map<String, String> weakEnv = new LinkedHashMap<>();
        weakEnv.put("AGENTHUB_AUTH_SECRET", "short-secret");
        weakEnv.put("MYSQL_ROOT_PASSWORD", "123456");
        weakEnv.put("MYSQL_PASSWORD", "password");
        weakEnv.put("REDIS_PASSWORD", "ChangeMe_Redis_123");
        weakEnv.put("OPENAI_API_KEY", "sk-your-openai-key-here");

        ProductionEnvironmentValidator.ValidationResult weakResult =
                productionEnvironmentValidator.validateEnvMap(weakEnv);
        assertThat(weakResult.valid()).isFalse();
        assertThat(weakResult.violations()).hasSizeGreaterThanOrEqualTo(5);

        // 测试合规高强度生产配置通过
        Map<String, String> hardenedEnv = new LinkedHashMap<>();
        hardenedEnv.put("AGENTHUB_AUTH_SECRET", "Prod_Crypto_Jwt_Secret_2026_9f8e7d6c5b4a3f2e1d0c9b8a7f6e5d4c!");
        hardenedEnv.put("MYSQL_ROOT_PASSWORD", "MysqlRoot#987654321_Prod!");
        hardenedEnv.put("MYSQL_PASSWORD", "MysqlApp#987654321_Prod!");
        hardenedEnv.put("REDIS_PASSWORD", "RedisCache#987654321_Prod!");
        hardenedEnv.put("ACTUATOR_PROMETHEUS_TOKEN", "PromScrapeToken#2026_Secure_998877");
        hardenedEnv.put("DEEPSEEK_API_KEY", "");
        hardenedEnv.put("OPENAI_API_KEY", "");

        ProductionEnvironmentValidator.ValidationResult validResult =
                productionEnvironmentValidator.validateEnvMap(hardenedEnv);
        assertThat(validResult.valid()).as("Violations: %s", validResult.violations()).isTrue();
    }

    // =========================================================================
    // 2. 生产管理与 Prometheus 端点受信网络及鉴权拦截
    // =========================================================================

    @Test
    @DisplayName("阶段E测试2：生产模式下 /actuator/health 公开可探测，/actuator/prometheus 与 /actuator/metrics 严格要求受信网络或鉴权凭据")
    void shouldEnforceActuatorAndPrometheusAccessControlInProduction() throws Exception {
        String validScrapeToken = "ObsScrapeToken_Prod_2026_SecureKey_99!";
        actuatorSecurityFilter.configureForTesting(true, validScrapeToken, true);

        // 1. /actuator/health 即使来自外部 IP 也保持 200 OK 供探针探测
        mockMvc.perform(get("/actuator/health")
                        .header("X-Forwarded-For", "203.0.113.88"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        // 2. 外部非受信 IP 未携带凭据访问 /actuator/prometheus 必须被 403 拦截
        mockMvc.perform(get("/actuator/prometheus")
                        .header("X-Forwarded-For", "203.0.113.88"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1002));

        // 3. 外部非受信 IP 访问 /actuator/metrics 同样被 403 拦截
        mockMvc.perform(get("/actuator/metrics")
                        .header("X-Real-IP", "198.51.100.42"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1002));

        // 4. 外部非受信 IP 携带错误 Scrape Token 被 403 拦截
        mockMvc.perform(get("/actuator/prometheus")
                        .header("X-Forwarded-For", "203.0.113.88")
                        .header("Authorization", "Bearer invalid-scrape-token"))
                .andExpect(status().isForbidden());

        // 5. 外部 IP 携带合法 X-Observability-Token 或 Bearer Scrape Token 允许访问 (200 OK)
        mockMvc.perform(get("/actuator/prometheus")
                        .header("X-Forwarded-For", "203.0.113.88")
                        .header("X-Observability-Token", validScrapeToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/actuator/prometheus")
                        .header("X-Forwarded-For", "203.0.113.88")
                        .header("Authorization", "Bearer " + validScrapeToken))
                .andExpect(status().isOk());

        // 6. 容器内网受信网段 (172.18.0.10) 直接抓取允许访问 (200 OK)
        mockMvc.perform(get("/actuator/prometheus")
                        .with(req -> {
                            req.setRemoteAddr("172.18.0.10");
                            return req;
                        }))
                .andExpect(status().isOk());
    }

    // =========================================================================
    // 3. Compose 容器最小权限、Dockerfile 规范与灾备手册高危命令拦截
    // =========================================================================

    @Test
    @DisplayName("阶段E测试3：校验 docker-compose.prod.yml、前后端 Dockerfile、Nginx TLS 配置与灾备手册安全红线")
    void shouldVerifyDockerfilesComposeAndDisasterRecoveryRunbookSafety(@TempDir Path tempDir) throws Exception {
        // 1. 校验 docker-compose.prod.yml
        Path composePath = resolveRepoFile("docker-compose.prod.yml");
        ProductionEnvironmentValidator.ValidationResult composeResult =
                productionEnvironmentValidator.validateDockerComposeSpec(composePath);
        assertThat(composeResult.valid()).as("Compose violations: %s", composeResult.violations()).isTrue();

        // 2. 校验前后端 Dockerfile 非 root 用户与健康检查
        String backendDockerfile = Files.readString(resolveRepoFile("agenthub-backend/Dockerfile"), StandardCharsets.UTF_8);
        assertThat(backendDockerfile).contains("USER agenthub");
        assertThat(backendDockerfile).contains("/app/logs");
        assertThat(backendDockerfile).contains("HEALTHCHECK");

        String frontendDockerfile = Files.readString(resolveRepoFile("agenthub-frontend/Dockerfile"), StandardCharsets.UTF_8);
        assertThat(frontendDockerfile).contains("USER nextjs");
        assertThat(frontendDockerfile).contains("HEALTHCHECK");
        assertThat(frontendDockerfile).doesNotContain("2>/dev/null || true");

        // 3. 校验 Nginx 生产配置与 TLS 配置
        String nginxConf = Files.readString(resolveRepoFile("docker/nginx/agenthub.conf"), StandardCharsets.UTF_8);
        assertThat(nginxConf).contains("location ^~ /actuator/");
        assertThat(nginxConf).contains("deny all;");
        assertThat(nginxConf).contains("proxy_buffering off;");

        String nginxTlsConf = Files.readString(resolveRepoFile("docker/nginx/agenthub-tls.conf"), StandardCharsets.UTF_8);
        assertThat(nginxTlsConf).contains("listen 443 ssl http2;");
        assertThat(nginxTlsConf).contains("ssl_protocols TLSv1.2 TLSv1.3;");
        assertThat(nginxTlsConf).contains("Strict-Transport-Security");

        // 4. 校验灾备手册已彻底移除高危命令
        Path runbookPath = resolveRepoFile("docs/ops/DISASTER_RECOVERY_RUNBOOK.md");
        ProductionEnvironmentValidator.ValidationResult runbookResult =
                productionEnvironmentValidator.validateRunbookSafety(runbookPath);
        assertThat(runbookResult.valid()).as("Runbook violations: %s", runbookResult.violations()).isTrue();

        // 5. 负向验证：包含高危命令的手册必须被拦截
        Path unsafeRunbook = tempDir.resolve("unsafe-runbook.md");
        Files.writeString(unsafeRunbook, """
                ```bash
                find /var/lib/agenthub/workspaces -mindepth 1 -maxdepth 1 -type d -mtime +30 -exec rm -rf {} +
                docker system prune -f --volumes
                ```
                """, StandardCharsets.UTF_8);
        ProductionEnvironmentValidator.ValidationResult unsafeResult =
                productionEnvironmentValidator.validateRunbookSafety(unsafeRunbook);
        assertThat(unsafeResult.valid()).isFalse();
        assertThat(unsafeResult.violations()).hasSize(2);
    }

    // =========================================================================
    // 4. Prometheus 配置文件加载校验与实时告警规则触发演练
    // =========================================================================

    @Test
    @DisplayName("阶段E测试4：校验 Prometheus 配置文件与告警规则，并注入异常指标触发实时测试告警")
    void shouldValidatePrometheusConfigAndTriggerAlertRules() throws Exception {
        Path promYml = resolveRepoFile("docker/prometheus/prometheus.yml");
        Path alertsYml = resolveRepoFile("docker/prometheus/agenthub-alerts.yml");

        PrometheusAlertEvaluator.PrometheusValidationReport validation =
                prometheusAlertEvaluator.validatePrometheusConfigAndRules(promYml, alertsYml);
        assertThat(validation.valid()).as("Prometheus config errors: %s", validation.errors()).isTrue();
        assertThat(validation.rules()).hasSize(8);

        // 记录 Step 与 Lock 延迟，验证直方图 _bucket 导出
        metricsCollector.recordStepExecution("AGENT", "SUCCEEDED", 420);
        metricsCollector.recordWorkspaceLockWait(25);

        // 注入三项异常告警条件：
        // (1) 活跃并发 Run = 25 (> 20) -> 触发 AgentHubHighPendingRuns
        // (2) Provider deepseek 熔断器 OPEN = 1 -> 触发 AgentHubProviderCircuitBreakerOpen
        // (3) 工作区可用磁盘空间 = 8% (< 15%) -> 触发 AgentHubDiskUsageCritical
        metricsCollector.setActiveRunsForDrill(25);
        metricsCollector.recordProviderCircuitBreakerState("deepseek", "OPEN", true);
        metricsCollector.recordWorkspaceDiskAvailablePercent(8);

        String prometheusText = mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(prometheusText).contains("agenthub_steps_duration_seconds_bucket");
        assertThat(prometheusText).contains("agenthub_workspace_lock_wait_seconds_bucket");
        assertThat(prometheusText).contains("agenthub_provider_circuit_breaker_state");
        assertThat(prometheusText).contains("agenthub_workspace_disk_avail_ratio");

        List<PrometheusAlertEvaluator.FiringAlert> firingAlerts =
                prometheusAlertEvaluator.evaluateAlertsAgainstPrometheusText(
                        validation.rules(), prometheusText, metricsCollector);

        assertThat(firingAlerts)
                .extracting(PrometheusAlertEvaluator.FiringAlert::alertName)
                .contains(
                        "AgentHubHighPendingRuns",
                        "AgentHubProviderCircuitBreakerOpen",
                        "AgentHubDiskUsageCritical"
                );
    }

    // =========================================================================
    // 5. 多实例数据库级工作区排他锁互斥与租约过期自愈演练
    // =========================================================================

    @Test
    @DisplayName("阶段E测试5：跨实例数据库工作区锁互斥竞争与租约到期毫秒级自动接管")
    void shouldEnforceDatabaseWorkspaceLockMutualExclusionAndExpiryRecovery() throws Exception {
        WorkspaceLockManager nodeA = new WorkspaceLockManager(workspaceResolver, jdbcTemplate);
        WorkspaceLockManager nodeB = new WorkspaceLockManager(workspaceResolver, jdbcTemplate);

        String workspaceKey = "ws-phase-e-lock-" + UUID.randomUUID().toString().substring(0, 8);

        // 1. Node A 获取锁，租约 TTL = 250ms
        boolean acquiredByA = nodeA.tryAcquireLock(workspaceKey, "node-alpha", 100, 250);
        assertThat(acquiredByA).isTrue();
        assertThat(nodeA.getDatabaseLockOwner(workspaceKey)).contains("node-alpha");

        // 2. 租约有效期内，独立实例 Node B 尝试获取同一工作区锁，必须被数据库排他锁拒绝
        boolean acquiredByBWhileActive = nodeB.tryAcquireLock(workspaceKey, "node-beta", 40, 250);
        assertThat(acquiredByBWhileActive).isFalse();
        assertThat(nodeB.getDatabaseLockOwner(workspaceKey)).contains("node-alpha");

        // 3. 等待 320ms 使 Node A 租约过期（模拟 Node A 宕机未释放锁）
        Thread.sleep(320);

        // 4. Node B 再次申请锁，自动检测 expires_at_epoch_ms 过期并接管所有权
        boolean recoveredByB = nodeB.tryAcquireLock(workspaceKey, "node-beta", 100, 1000);
        assertThat(recoveredByB).isTrue();
        assertThat(nodeB.getDatabaseLockOwner(workspaceKey)).contains("node-beta");

        // 5. Node B 正常释放锁
        boolean released = nodeB.releaseLock(workspaceKey, "node-beta");
        assertThat(released).isTrue();
        assertThat(nodeB.getDatabaseLockOwner(workspaceKey)).isEmpty();
    }

    // =========================================================================
    // 6. 全量加密备份、SHA-256 校验、防篡改拦截与空环境完整还原演练
    // =========================================================================

    @Test
    @DisplayName("阶段E测试6：数据库与受控工作区 AES-256-GCM 加密备份、篡改检测拦截与空环境完整还原校验")
    void shouldExecuteFullBackupVerifyTamperDetectionAndRestoreToCleanEnvironment(@TempDir Path tempDir) throws Exception {
        Path sourceWorkspaceRoot = tempDir.resolve("live-workspaces");
        Path backupRoot = tempDir.resolve("backups");
        Path restoredWorkspaceRoot = tempDir.resolve("restored-workspaces");
        String passphrase = "ProdBackupPassphrase#2026_AES256GCM!";

        // 1. 创建真实项目、工作区文件、JGit 快照、Run、Step、Event、Artifact 与 Deployment
        ProjectView project = projectApplication.createProject(
                new CreateProjectCommand("PhaseE-DR-Project", "灾备备份还原验证项目", null));
        String projectId = project.getId();
        String workspaceId = project.getWorkspaceId();

        Path projectWsDir = sourceWorkspaceRoot.resolve(workspaceId);
        Files.createDirectories(projectWsDir.resolve("src"));
        gitWorkspaceManager.initWorkspace(projectWsDir.toFile());
        Path sourceFile = projectWsDir.resolve("src/DeliveryApp.java");
        String expectedSourceCode = "public class DeliveryApp { public static String status() { return \"OK-PHASE-E\"; } }";
        Files.writeString(sourceFile, expectedSourceCode, StandardCharsets.UTF_8);

        WorkflowRunView run = executionApplication.startRun(
                new StartRunCommand(projectId, null, "执行灾备快照前任务"));
        String runId = run.getId();
        executionApplication.appendEvent(runId, "dr_checkpoint", "{\"stage\":\"pre_backup\"}");

        JGitWorkspaceManager.StepSnapshotResult gitSnap = gitWorkspaceManager.createStepSnapshot(
                projectWsDir.toFile(), runId, "step-dr-1", "Commit before backup", "dr-agent");
        ArtifactView artifact = artifactApplication.recordStepSnapshot(
                runId, "step-dr-1", workspaceId, gitSnap.getCommitHash(), gitSnap.getTagName(),
                gitSnap.getChangedFiles(), gitSnap.getFileChecksums(), "dr-agent");

        // 2. 执行一致性加密备份
        Path backupDir = backupRoot.resolve("bkp-drill-1");
        DisasterRecoveryBackupService.BackupManifest manifest =
                disasterRecoveryBackupService.createBackup(sourceWorkspaceRoot, backupDir, passphrase);

        assertThat(manifest.encrypted).isTrue();
        assertThat(manifest.dbSnapshotSha256).isNotBlank();
        assertThat(manifest.workspaceFileChecksums).containsKey(workspaceId + "/src/DeliveryApp.java");
        assertThat(manifest.sampledRunFingerprints).containsKey(runId);
        assertThat(Files.exists(backupDir.resolve("backup-manifest.json"))).isTrue();
        assertThat(Files.exists(backupDir.resolve("SHA256SUMS"))).isTrue();
        assertThat(Files.exists(backupDir.resolve("db-snapshot.json.enc"))).isTrue();

        // 3. 防篡改演练：复制备份目录并篡改其中一个工作区文件，验证还原被拒绝
        Path tamperedBackupDir = backupRoot.resolve("bkp-tampered");
        FileSystemUtils.copyRecursively(backupDir, tamperedBackupDir);
        Path tamperedFile = tamperedBackupDir.resolve("workspaces").resolve(workspaceId).resolve("src/DeliveryApp.java");
        Files.writeString(tamperedFile, "TAMPERED_MALICIOUS_CONTENT", StandardCharsets.UTF_8);

        assertThatThrownBy(() -> disasterRecoveryBackupService.verifyBackupIntegrity(tamperedBackupDir, passphrase))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("checksum mismatch");

        assertThatThrownBy(() -> disasterRecoveryBackupService.restoreToCleanEnvironment(
                tamperedBackupDir, restoredWorkspaceRoot, passphrase))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("checksum mismatch");

        // 4. 模拟灾难清空：删除创建的业务记录与源工作区目录
        org.eclipse.jgit.lib.RepositoryCache.clear();
        FileSystemUtils.deleteRecursively(sourceWorkspaceRoot);
        artifactRepository.deleteById(artifact.getId());
        runEventRepository.deleteAll(runEventRepository.findByRunIdOrderBySequenceNumAsc(runId));
        stepRunRepository.deleteAll(stepRunRepository.findByRunIdOrderByCreatedAtAsc(runId));
        workflowRunRepository.deleteById(runId);
        workspaceRepository.deleteById(workspaceId);
        projectRepository.deleteById(projectId);

        assertThat(projectRepository.findById(projectId)).isEmpty();
        assertThat(workflowRunRepository.findById(runId)).isEmpty();

        // 5. 从完好加密备份还原至干净环境并执行多重校验
        DisasterRecoveryBackupService.RestoreVerificationReport report =
                disasterRecoveryBackupService.restoreToCleanEnvironment(backupDir, restoredWorkspaceRoot, passphrase);

        assertThat(report.checksumVerified()).isTrue();
        assertThat(report.schemaVersionVerified()).isTrue();
        assertThat(report.sampledRunsVerified()).isTrue();
        assertThat(report.restoredWorkspaceFilesCount()).isGreaterThanOrEqualTo(1);

        // 验证数据库记录与工作区文件内容已 100% 恢复
        assertThat(projectRepository.findById(projectId)).isPresent();
        assertThat(workflowRunRepository.findById(runId)).isPresent();
        assertThat(artifactRepository.findById(artifact.getId())).isPresent();

        Path restoredSourceFile = restoredWorkspaceRoot.resolve(workspaceId).resolve("src/DeliveryApp.java");
        assertThat(Files.exists(restoredSourceFile)).isTrue();
        assertThat(Files.readString(restoredSourceFile, StandardCharsets.UTF_8)).isEqualTo(expectedSourceCode);
    }

    // =========================================================================
    // 7. 数据库关联的工作区归档清理与完整 Staging 交付闭环演练
    // =========================================================================

    @Test
    @DisplayName("阶段E测试7：数据库关联工作区安全归档清理（禁止按 mtime 盲删活跃项目）与完整 Staging Run 演练")
    void shouldEnforceDatabaseReconciledWorkspaceCleanupAndCompleteStagingDrill(@TempDir Path tempDir) throws Exception {
        // --- Part A: 验证数据库关联工作区清理策略 ---
        Path wsRoot = tempDir.resolve("cleanup-workspaces");
        Files.createDirectories(wsRoot);

        ProjectView activeProj = projectApplication.createProject(
                new CreateProjectCommand("Active-Long-Lived-Project", "长期活跃项目", null));
        ProjectView archivedProj = projectApplication.createProject(
                new CreateProjectCommand("Archived-Legacy-Project", "已归档历史项目", null));

        // 将 archivedProj 在数据库中标记为 ARCHIVED
        ProjectEntity archivedEntity = projectRepository.findById(archivedProj.getId()).orElseThrow();
        archivedEntity.setStatus("ARCHIVED");
        projectRepository.saveAndFlush(archivedEntity);

        Path activeDir = wsRoot.resolve(activeProj.getWorkspaceId());
        Path archivedDir = wsRoot.resolve(archivedProj.getWorkspaceId());
        Files.createDirectories(activeDir);
        Files.createDirectories(archivedDir);
        Files.writeString(activeDir.resolve("main.java"), "class Active {}", StandardCharsets.UTF_8);
        Files.writeString(archivedDir.resolve("old.java"), "class Archived {}", StandardCharsets.UTF_8);

        // 将两个目录的 mtime 都设置为 60 天前
        FileTime sixtyDaysAgo = FileTime.from(Instant.now().minus(Duration.ofDays(60)));
        Files.setLastModifiedTime(activeDir, sixtyDaysAgo);
        Files.setLastModifiedTime(archivedDir, sixtyDaysAgo);

        // 执行数据库对账清理（阈值 30 天）
        List<String> cleaned = disasterRecoveryBackupService.cleanupArchivedWorkspacesWithDbReconciliation(
                wsRoot, Duration.ofDays(30));

        // 断言：已归档项目目录被清理，而活跃项目即使超过 60 天也 100% 受到保护！
        assertThat(cleaned).containsExactly(archivedProj.getWorkspaceId());
        assertThat(Files.exists(archivedDir)).isFalse();
        assertThat(Files.exists(activeDir)).isTrue();

        // --- Part B: 完整 Staging Run 闭环演练 (注册/登录 -> 项目 -> 计划/执行 -> 审计 -> 审批 -> 部署 -> 停止) ---
        String stagingEmail = "staging-ops-" + java.util.UUID.randomUUID().toString().substring(0, 8) + "@agenthub.local";
        authApplication.register(new RegisterCommand(stagingEmail, "StagingOps_2026!", "Staging Ops Admin"));
        AuthTokenView auth = authApplication.login(new LoginCommand(stagingEmail, "StagingOps_2026!"));
        assertThat(auth.getToken()).isNotBlank();
        String stagingUserId = auth.getUser().getId();
        RequestContext.get().setUserId(stagingUserId);

        ProjectView stagingProject = projectApplication.createProject(
                new CreateProjectCommand("Staging-E2E-Delivery", "阶段E全链路演练项目", null));
        Path stagingWsDir = workspaceResolver.getWorkspaceRoot(stagingProject.getWorkspaceId());
        Files.createDirectories(stagingWsDir);
        gitWorkspaceManager.initWorkspace(stagingWsDir.toFile());
        Files.writeString(stagingWsDir.resolve("index.html"),
                "<html><body><h1>AgentHub Phase E Staging Verified</h1></body></html>", StandardCharsets.UTF_8);

        WorkflowRunView stagingRun = executionApplication.startRun(
                new StartRunCommand(stagingProject.getId(), null, "执行阶段 E Staging 全链路交付"));
        String runId = stagingRun.getId();

        WorkflowDsl dsl = new WorkflowDsl("wf-phase-e-staging", "Phase E Staging DAG");
        WorkflowNodeDsl codeNode = new WorkflowNodeDsl("code_step", "代码生成", "AGENT", "MOCK", "生成交付页");
        WorkflowNodeDsl approvalNode = new WorkflowNodeDsl("approval_step", "上线审批", "APPROVAL");
        approvalNode.setRequiresApproval(true);
        WorkflowNodeDsl releaseNode = new WorkflowNodeDsl("release_step", "发布确认", "AGENT", "MOCK", "确认发布");
        dsl.setNodes(List.of(codeNode, approvalNode, releaseNode));
        dsl.setEdges(List.of(
                new WorkflowEdgeDsl("code_step", "approval_step"),
                new WorkflowEdgeDsl("approval_step", "release_step")
        ));

        CompletableFuture<WorkflowRunStatus> dagFuture = dagExecutionEngine.executeDag(
                runId, dsl, stagingWsDir.toAbsolutePath().toString(), Map.of(), 60);

        // 等待进入人工审批挂起状态
        long deadline = System.currentTimeMillis() + 15_000;
        List<ApprovalView> approvals = List.of();
        while (System.currentTimeMillis() < deadline) {
            approvals = approvalApplication.listApprovalsByRunId(runId);
            if (!approvals.isEmpty() && "PENDING".equalsIgnoreCase(approvals.get(0).getStatus())) {
                break;
            }
            Thread.sleep(100);
        }
        assertThat(approvals).hasSize(1);

        // 执行 JGit 产物快照与质量门禁审核通过
        JGitWorkspaceManager.StepSnapshotResult snapshot = gitWorkspaceManager.createStepSnapshot(
                stagingWsDir.toFile(), runId, "code_step", "Staging artifact snapshot", "staging-agent");
        ArtifactView stagingArtifact = artifactApplication.recordStepSnapshot(
                runId, "code_step", stagingProject.getWorkspaceId(),
                snapshot.getCommitHash(), snapshot.getTagName(),
                snapshot.getChangedFiles(), snapshot.getFileChecksums(), "staging-agent");
        artifactApplication.acceptArtifact(stagingArtifact.getId(), stagingUserId);

        // 批准人工审批节点，等待 DAG 完成
        approvalApplication.approve(approvals.get(0).getId(), "Staging release approved");
        WorkflowRunStatus finalRunStatus = dagFuture.get(15, TimeUnit.SECONDS);
        assertThat(finalRunStatus).isEqualTo(WorkflowRunStatus.SUCCEEDED);

        // 验证 SSE 事件持久化与游标补发
        List<RunEventView> replayEvents = executionApplication.listEvents(runId, 1L);
        assertThat(replayEvents).isNotEmpty();

        // 创建并拉起真实静态预览沙箱部署，验证外部 HTTP 客户端访问与停止清理
        CreateDeploymentRequest deployReq = new CreateDeploymentRequest();
        deployReq.setProjectId(stagingProject.getId());
        deployReq.setArtifactId(stagingArtifact.getId());
        deployReq.setSandboxType("STATIC_PREVIEW");
        deployReq.setTemplateType("STATIC_WEB_TEMPLATE");
        deployReq.setTarget("STATIC_PREVIEW");
        deployReq.setHealthCheckPath("/health");

        DeploymentResponse deployed = deploymentApplication.createAndDeploy(deployReq);
        assertThat(deployed.getStatus()).isEqualTo(DeploymentStatus.RUNNING.name());
        assertThat(deployed.getUrl()).startsWith("http://");

        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        HttpResponse<String> previewRes = httpClient.send(
                HttpRequest.newBuilder().uri(URI.create(deployed.getUrl() + "/")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(previewRes.statusCode()).isEqualTo(200);
        assertThat(previewRes.body()).contains("AgentHub Phase E Staging Verified");

        // 停止部署并验证状态与端口释放
        DeploymentResponse stopped = deploymentApplication.stopDeployment(deployed.getId());
        assertThat(stopped.getStatus()).isEqualTo(DeploymentStatus.STOPPED.name());
        assertThat(stopped.getLifecycleTrace()).contains("STOP").contains("CLEANUP");
    }
}
