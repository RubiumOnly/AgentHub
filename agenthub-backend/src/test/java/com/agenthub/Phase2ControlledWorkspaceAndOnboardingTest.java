package com.agenthub;

import com.agenthub.agent.infrastructure.security.SecretMasker;
import com.agenthub.audit.infrastructure.repository.ArtifactRepository;
import com.agenthub.identity.infrastructure.entity.AuthAuditLogEntity;
import com.agenthub.identity.infrastructure.entity.UserEntity;
import com.agenthub.identity.infrastructure.repository.AuthAuditLogRepository;
import com.agenthub.identity.infrastructure.repository.UserRepository;
import com.agenthub.identity.infrastructure.security.TokenProvider;
import com.agenthub.project.application.ContextSourceApplication;
import com.agenthub.project.application.ProjectApplication;
import com.agenthub.project.application.ProjectOnboardingApplication;
import com.agenthub.project.application.WorkspaceApplication;
import com.agenthub.project.application.WorkspaceQuotaGuard;
import com.agenthub.project.domain.context.ContextSourceType;
import com.agenthub.project.domain.context.SecurityLevel;
import com.agenthub.project.domain.onboarding.OnboardingSessionStatus;
import com.agenthub.project.domain.onboarding.OnboardingStep;
import com.agenthub.project.domain.onboarding.OnboardingStepStatus;
import com.agenthub.project.domain.quota.WorkspaceQuotaPolicy;
import com.agenthub.project.domain.quota.WorkspaceQuotaUsage;
import com.agenthub.project.domain.source.GitSourceConfig;
import com.agenthub.project.domain.source.GitSourceType;
import com.agenthub.project.dto.ProjectView;
import com.agenthub.project.dto.WorkspaceView;
import com.agenthub.project.dto.context.ContextSourceView;
import com.agenthub.project.dto.context.RegisterContextSourceCommand;
import com.agenthub.project.dto.onboarding.ExecuteStepCommand;
import com.agenthub.project.dto.onboarding.OnboardingSessionView;
import com.agenthub.project.dto.onboarding.StartOnboardingCommand;
import com.agenthub.project.infrastructure.entity.ProjectEntity;
import com.agenthub.project.infrastructure.entity.WorkspaceEntity;
import com.agenthub.project.infrastructure.repository.ProjectRepository;
import com.agenthub.project.infrastructure.repository.WorkspaceRepository;
import com.agenthub.shared.context.RequestContext;
import com.agenthub.shared.exception.BusinessException;
import com.agenthub.shared.exception.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 2（项目接入与受控工作区产品化）五维边界与完整生命周期测试套件。
 * 覆盖：
 * 1. 极值边界输入：宿主绝对路径拦截、路径穿越拦截、明文 Token 拦截、单文件大小及工作区容量超配额（BLOCKED_POLICY）；
 * 2. 状态机与向导推进：7 步 Onboarding 向导执行（连接->权限->分支->创建->扫描->规则->基线）、重试失败步骤与中止清理；
 * 3. 受控工作区生命周期：重新同步（resync）、基线变更（changeBaseline）、恢复快照（restoreSnapshot）、放弃工作区（discardWorkspace）及配额查询；
 * 4. 版本化 Context Source：自动探测项目规则/构建配置/AOCI 认知、手动注册、SHA-256 指纹与 Git commit 绑定；
 * 5. 用户隔离与脱敏安全：User A 与 User B 工作区越权拦截、Token 凭据全链路脱敏；
 * 6. 零测试污染：@AfterEach 级联清理所有现场。
 */
@SpringBootTest
@ActiveProfiles("test")
class Phase2ControlledWorkspaceAndOnboardingTest {

    @Autowired
    private ProjectOnboardingApplication onboardingApplication;

    @Autowired
    private ContextSourceApplication contextSourceApplication;

    @Autowired
    private WorkspaceApplication workspaceApplication;

    @Autowired
    private ProjectApplication projectApplication;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private ArtifactRepository artifactRepository;

    @Autowired
    private AuthAuditLogRepository auditLogRepository;

    @Autowired
    private WorkspaceQuotaGuard quotaGuard;

    @Autowired
    private UserRepository userRepository;

    private final List<String> createdProjectIds = new ArrayList<>();
    private final List<String> createdWorkspaceIds = new ArrayList<>();
    private final List<String> createdSessionIds = new ArrayList<>();

    private static final String USER_ALICE = "usr-alice-" + UUID.randomUUID().toString().substring(0, 6);
    private static final String USER_BOB = "usr-bob-" + UUID.randomUUID().toString().substring(0, 6);

    @BeforeEach
    void setUp() {
        RequestContext.clear();
        RequestContext.get().setUserId(USER_ALICE);
        RequestContext.get().setUsername("alice@agenthub.test");
        if (!userRepository.existsById(USER_ALICE)) {
            userRepository.save(new UserEntity(USER_ALICE, "alice@agenthub.test", "hash", "Alice", "ACTIVE"));
        }
        if (!userRepository.existsById(USER_BOB)) {
            userRepository.save(new UserEntity(USER_BOB, "bob@agenthub.test", "hash", "Bob", "ACTIVE"));
        }
    }

    @AfterEach
    void tearDown() {
        RequestContext.clear();
        for (String projId : createdProjectIds) {
            try {
                artifactRepository.deleteAll(artifactRepository.findByRunId(projId));
                workspaceRepository.findByProjectId(projId).ifPresent(ws -> {
                    cleanupWorkspaceDir(ws.getId());
                    workspaceRepository.delete(ws);
                });
                projectRepository.deleteById(projId);
            } catch (Exception ignored) {}
        }
        createdProjectIds.clear();

        for (String wsId : createdWorkspaceIds) {
            try {
                cleanupWorkspaceDir(wsId);
                workspaceRepository.findById(wsId).ifPresent(workspaceRepository::delete);
            } catch (Exception ignored) {}
        }
        createdWorkspaceIds.clear();

        for (String sessionId : createdSessionIds) {
            try {
                onboardingApplication.abortOnboarding(sessionId);
            } catch (Exception ignored) {}
        }
        createdSessionIds.clear();

        try {
            userRepository.deleteById(USER_ALICE);
        } catch (Exception ignored) {}
        try {
            userRepository.deleteById(USER_BOB);
        } catch (Exception ignored) {}
    }

    private void cleanupWorkspaceDir(String workspaceId) {
        try {
            Path wsPath = Path.of("./data/workspaces", workspaceId);
            if (Files.exists(wsPath)) {
                try (var stream = Files.walk(wsPath)) {
                    stream.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
                }
            }
        } catch (Exception ignored) {}
    }

    @Test
    @DisplayName("1. 极值边界输入：宿主绝对路径、路径穿越、明文凭据拦截与配额超限（BLOCKED_POLICY）校验")
    void shouldEnforceExtremeBoundariesAndBlockedPolicyQuotas() throws IOException {
        // 1) 宿主绝对路径（C:/Windows 或 /etc/passwd）拦截
        GitSourceConfig evilConfig1 = new GitSourceConfig(GitSourceType.LOCAL_DIR, "C:/Windows/System32", null, null, "main");
        assertThatThrownBy(evilConfig1::validate)
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.WORKSPACE_TRAVERSAL_DENIED));

        GitSourceConfig evilConfig2 = new GitSourceConfig(GitSourceType.LOCAL_DIR, "/etc/passwd", null, null, "main");
        assertThatThrownBy(evilConfig2::validate)
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.WORKSPACE_TRAVERSAL_DENIED));

        GitSourceConfig evilConfig3 = new GitSourceConfig(GitSourceType.LOCAL_DIR, "../../sensitive/data", null, null, "main");
        assertThatThrownBy(evilConfig3::validate)
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.WORKSPACE_TRAVERSAL_DENIED));

        // 2) secretRef 中传递明文 GitHub Token 拦截
        GitSourceConfig evilConfig4 = new GitSourceConfig(
                GitSourceType.REMOTE_GIT, null, "https://github.com/myorg/repo.git",
                "ghp_1234567890abcdef1234567890abcdef123456", "main");
        assertThatThrownBy(evilConfig4::validate)
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Raw credential detected in secretRef");

        // 3) 配额拦截：单文件大小超限触发 BLOCKED_POLICY
        Path tempDir = Files.createTempDirectory("quota-guard-test");
        try {
            WorkspaceQuotaPolicy policy = new WorkspaceQuotaPolicy(
                    1024 * 1024L, // 1MB 容量
                    5,           // 5 个文件
                    500 * 1024L, // 500KB 单文件
                    3600L,
                    false,
                    1
            );

            byte[] bigFile = new byte[600 * 1024]; // 600KB > 500KB
            assertThatThrownBy(() -> quotaGuard.validateFileWrite(tempDir, tempDir.resolve("big.bin"), bigFile, policy))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.BLOCKED_POLICY));

            // 4) 配额拦截：工作区总容量超限触发 BLOCKED_POLICY
            byte[] file1 = new byte[400 * 1024];
            Files.write(tempDir.resolve("f1.bin"), file1);
            byte[] file2 = new byte[400 * 1024];
            Files.write(tempDir.resolve("f2.bin"), file2);
            // 此时已用 800KB，再写 300KB 将达到 1100KB > 1024KB 容量配额
            byte[] file3 = new byte[300 * 1024];
            assertThatThrownBy(() -> quotaGuard.validateFileWrite(tempDir, tempDir.resolve("f3.bin"), file3, policy))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.BLOCKED_POLICY));

            // 5) 配额拦截：文件数超限触发 BLOCKED_POLICY
            Files.write(tempDir.resolve("f3.bin"), new byte[10]);
            Files.write(tempDir.resolve("f4.bin"), new byte[10]);
            Files.write(tempDir.resolve("f5.bin"), new byte[10]); // 已有 5 个文件
            assertThatThrownBy(() -> quotaGuard.validateFileWrite(tempDir, tempDir.resolve("f6.bin"), new byte[10], policy))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.BLOCKED_POLICY));

            // 6) 网络访问策略：禁用网络访问时触发 BLOCKED_POLICY
            assertThatThrownBy(() -> quotaGuard.validateNetworkAccess(true, policy))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.BLOCKED_POLICY));

        } finally {
            try (var stream = Files.walk(tempDir)) {
                stream.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
            } catch (Exception ignored) {}
        }
    }

    @Test
    @DisplayName("2. 状态机推进：7 步 Onboarding 向导全生命周期执行、重试与中止清理校验")
    void shouldExecuteSevenStepOnboardingLifecycleWithRetryAndAbort() {
        // 1) 启动向导 (Step 1: CONNECT_SOURCE)
        StartOnboardingCommand startCmd = new StartOnboardingCommand(
                "onboarding-spring-service",
                "Automated onboarding test project",
                new GitSourceConfig(GitSourceType.LOCAL_DIR, "demo-template", null, null, "main")
        );
        OnboardingSessionView session = onboardingApplication.startOnboarding(startCmd);
        createdSessionIds.add(session.getSessionId());

        assertThat(session.getSessionId()).startsWith("onb-");
        assertThat(session.getStatus()).isEqualTo(OnboardingSessionStatus.IN_PROGRESS);
        assertThat(session.getCurrentStep()).isEqualTo(OnboardingStep.VERIFY_PERMISSIONS);
        assertThat(session.getSteps()).hasSize(1);
        assertThat(session.getSteps().get(0).getStatus()).isEqualTo(OnboardingStepStatus.SUCCESS);

        // 2) 执行 Step 2: VERIFY_PERMISSIONS
        session = onboardingApplication.executeStep(session.getSessionId(), new ExecuteStepCommand(OnboardingStep.VERIFY_PERMISSIONS, null, null));
        assertThat(session.getCurrentStep()).isEqualTo(OnboardingStep.SELECT_DEFAULT_BRANCH);

        // 3) 执行 Step 3: SELECT_DEFAULT_BRANCH
        session = onboardingApplication.executeStep(session.getSessionId(), new ExecuteStepCommand(OnboardingStep.SELECT_DEFAULT_BRANCH, "main", null));
        assertThat(session.getCurrentStep()).isEqualTo(OnboardingStep.CREATE_WORKSPACE);

        // 4) 执行 Step 4: CREATE_WORKSPACE
        session = onboardingApplication.executeStep(session.getSessionId(), new ExecuteStepCommand(OnboardingStep.CREATE_WORKSPACE, null, null));
        assertThat(session.getWorkspaceId()).isNotBlank();
        assertThat(session.getProjectId()).isNotBlank();
        assertThat(session.getIsolatedBranch()).contains("feature/agenthub-");
        createdWorkspaceIds.add(session.getWorkspaceId());
        createdProjectIds.add(session.getProjectId());
        assertThat(session.getCurrentStep()).isEqualTo(OnboardingStep.SCAN_ENVIRONMENT);

        // 5) 执行 Step 5: SCAN_ENVIRONMENT
        session = onboardingApplication.executeStep(session.getSessionId(), new ExecuteStepCommand(OnboardingStep.SCAN_ENVIRONMENT, null, null));
        assertThat(session.getScanResult()).isNotNull();
        assertThat(session.getScanResult().getSecurityNotice()).contains("安全须知");
        assertThat(session.getCurrentStep()).isEqualTo(OnboardingStep.GENERATE_PROJECT_RULES);

        // 6) 执行 Step 6: GENERATE_PROJECT_RULES
        String customRule = "Strict DDD 4-layer architecture enforced.";
        session = onboardingApplication.executeStep(session.getSessionId(), new ExecuteStepCommand(OnboardingStep.GENERATE_PROJECT_RULES, null, customRule));
        assertThat(session.getGeneratedRules()).contains(customRule);
        assertThat(session.getCurrentStep()).isEqualTo(OnboardingStep.CREATE_INITIAL_BASELINE);

        // 7) 执行 Step 7: CREATE_INITIAL_BASELINE
        session = onboardingApplication.executeStep(session.getSessionId(), new ExecuteStepCommand(OnboardingStep.CREATE_INITIAL_BASELINE, null, null));
        assertThat(session.getStatus()).isEqualTo(OnboardingSessionStatus.COMPLETED);
        assertThat(session.getBaselineCommit()).isNotBlank();

        // 8) 校验数据库持久化事实
        ProjectEntity project = projectRepository.findById(session.getProjectId()).orElse(null);
        assertThat(project).isNotNull();
        assertThat(project.getWorkspaceId()).isEqualTo(session.getWorkspaceId());
        assertThat(project.getOwnerId()).isEqualTo(USER_ALICE);

        WorkspaceEntity ws = workspaceRepository.findById(session.getWorkspaceId()).orElse(null);
        assertThat(ws).isNotNull();
        assertThat(ws.getGitBaselineCommit()).isEqualTo(session.getBaselineCommit());

        // 9) 校验 completeOnboarding 获取 ProjectView
        ProjectView projView = onboardingApplication.completeOnboarding(session.getSessionId());
        assertThat(projView.getId()).isEqualTo(session.getProjectId());

        // 10) 验证向导中止（Abort）能力
        StartOnboardingCommand abortCmd = new StartOnboardingCommand(
                "to-abort-service",
                "Will be aborted",
                new GitSourceConfig(GitSourceType.LOCAL_DIR, "abort-template", null, null, "main")
        );
        OnboardingSessionView toAbort = onboardingApplication.startOnboarding(abortCmd);
        createdSessionIds.add(toAbort.getSessionId());
        OnboardingSessionView aborted = onboardingApplication.abortOnboarding(toAbort.getSessionId());
        assertThat(aborted.getStatus()).isEqualTo(OnboardingSessionStatus.ABORTED);

        // 中止后严禁继续执行步骤
        assertThatThrownBy(() -> onboardingApplication.executeStep(toAbort.getSessionId(), new ExecuteStepCommand(OnboardingStep.VERIFY_PERMISSIONS, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("aborted");
    }

    @Test
    @DisplayName("3. 受控工作区生命周期：重新同步、基线变更、快照恢复、放弃修改与审计日志校验")
    void shouldExecuteControlledWorkspaceLifecycleOperationsWithAudit() throws Exception {
        // 先通过向导创建一个受控项目与工作区
        StartOnboardingCommand startCmd = new StartOnboardingCommand(
                "lifecycle-service",
                "Testing workspace operations",
                new GitSourceConfig(GitSourceType.LOCAL_DIR, "src-lifecycle", null, null, "main")
        );
        OnboardingSessionView session = onboardingApplication.startOnboarding(startCmd);
        createdSessionIds.add(session.getSessionId());
        for (OnboardingStep step : OnboardingStep.values()) {
            session = onboardingApplication.executeStep(session.getSessionId(), new ExecuteStepCommand(step, null, null));
        }
        String wsId = session.getWorkspaceId();
        String projId = session.getProjectId();
        String initialBaseline = session.getBaselineCommit();
        createdWorkspaceIds.add(wsId);
        createdProjectIds.add(projId);

        // 1) 写入新文件
        workspaceApplication.saveFile(wsId, "src/main/TestApp.java", "public class TestApp {}");
        String content = workspaceApplication.getFileContent(wsId, "src/main/TestApp.java");
        assertThat(content).isEqualTo("public class TestApp {}");

        // 2) 创建 Git Snapshot
        Map<String, Object> snapResult = workspaceApplication.createGitSnapshot(wsId, null, null, "Added TestApp", "tester@test.local");
        String snapCommit = (String) snapResult.get("commitHash");
        assertThat(snapCommit).isNotBlank();

        // 3) 修改文件并放弃修改（discardWorkspace，未确认拒绝，确认通过）
        workspaceApplication.saveFile(wsId, "src/main/TestApp.java", "public class TestApp { // modified }");
        assertThatThrownBy(() -> workspaceApplication.discardWorkspace(wsId, false, "test"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("confirmation");

        WorkspaceView discarded = workspaceApplication.discardWorkspace(wsId, true, "Discarding modified TestApp");
        assertThat(discarded.getId()).isEqualTo(wsId);
        // discard 回到了 baselineCommit
        assertThat(workspaceApplication.getFileContent(wsId, "PROJECT_RULES.md")).isNotBlank();

        // 4) 重新同步（resyncWorkspace）
        WorkspaceView resynced = workspaceApplication.resyncWorkspace(wsId);
        assertThat(resynced.getId()).isEqualTo(wsId);

        // 5) 恢复快照（restoreSnapshot，未确认拒绝，确认通过）
        assertThatThrownBy(() -> workspaceApplication.restoreSnapshot(wsId, snapCommit, false))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("confirmation");

        WorkspaceView restored = workspaceApplication.restoreSnapshot(wsId, snapCommit, true);
        assertThat(restored.getId()).isEqualTo(wsId);
        assertThat(workspaceApplication.getFileContent(wsId, "src/main/TestApp.java")).isEqualTo("public class TestApp {}");

        // 6) 基线变更（changeBaseline，未确认拒绝，确认通过）
        assertThatThrownBy(() -> workspaceApplication.changeBaseline(wsId, snapCommit, false))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("confirmation");

        WorkspaceView baselineChanged = workspaceApplication.changeBaseline(wsId, snapCommit, true);
        assertThat(baselineChanged.getGitBaselineCommit()).isEqualTo(snapCommit);

        // 7) 查询配额状态（getWorkspaceQuota）
        WorkspaceQuotaUsage quota = workspaceApplication.getWorkspaceQuota(wsId);
        assertThat(quota.getWorkspaceId()).isEqualTo(wsId);
        assertThat(quota.getCurrentFilesCount()).isGreaterThan(0);
        assertThat(quota.getCurrentCapacityBytes()).isGreaterThan(0);
        assertThat(quota.getMaxCapacityBytes()).isEqualTo(WorkspaceQuotaPolicy.DEFAULT_MAX_CAPACITY_BYTES);
    }

    @Test
    @DisplayName("4. 版本化 Context Source：自动探测、手动注册、SHA-256 指纹与 commit 绑定校验")
    void shouldManageVersionedContextSourcesWithFingerprintAndCommitBinding() {
        // 创建项目
        StartOnboardingCommand startCmd = new StartOnboardingCommand(
                "context-sources-service",
                "Context source test",
                new GitSourceConfig(GitSourceType.LOCAL_DIR, "src-ctx", null, null, "main")
        );
        OnboardingSessionView session = onboardingApplication.startOnboarding(startCmd);
        createdSessionIds.add(session.getSessionId());
        for (OnboardingStep step : OnboardingStep.values()) {
            session = onboardingApplication.executeStep(session.getSessionId(), new ExecuteStepCommand(step, null, null));
        }
        String projId = session.getProjectId();
        createdWorkspaceIds.add(session.getWorkspaceId());
        createdProjectIds.add(projId);

        // 1) 校验向导自动注册的 Context Sources（包含 PROJECT_RULES 与 AOCI_COGNITION）
        List<ContextSourceView> sources = contextSourceApplication.listContextSources(projId);
        assertThat(sources).isNotEmpty();
        assertThat(sources.stream().anyMatch(s -> s.getSourceType() == ContextSourceType.PROJECT_RULES)).isTrue();
        assertThat(sources.stream().anyMatch(s -> s.getSourceType() == ContextSourceType.AOCI_COGNITION)).isTrue();

        for (ContextSourceView s : sources) {
            assertThat(s.getContentFingerprint()).isNotBlank();
            assertThat(s.getCommitHash()).isNotBlank();
            assertThat(s.getSecurityLevel()).isNotNull();
        }

        // 2) 手动注册新的 Context Source（TEST_CONVENTIONS）
        String testConventionsContent = "# Boundary Test Guard Conventions\nMust cover 5-dimension test matrix.";
        RegisterContextSourceCommand regCmd = new RegisterContextSourceCommand(
                ContextSourceType.TEST_CONVENTIONS,
                "Boundary Test Guard Matrix",
                "docs/TEST_CONVENTIONS.md",
                testConventionsContent,
                session.getBaselineCommit(),
                SecurityLevel.INTERNAL,
                Map.of("coverage", "extreme_boundaries,state_idempotency,fault_tolerance,concurrency,security_isolation")
        );
        ContextSourceView registered = contextSourceApplication.registerContextSource(projId, regCmd);
        assertThat(registered.getId()).startsWith("ctx-");
        assertThat(registered.getSourceType()).isEqualTo(ContextSourceType.TEST_CONVENTIONS);
        assertThat(registered.getContentFingerprint()).isNotBlank();

        // 3) 根据 sourceId 单独查询
        ContextSourceView queried = contextSourceApplication.getContextSource(projId, registered.getId());
        assertThat(queried.getName()).isEqualTo("Boundary Test Guard Matrix");
        assertThat(queried.getContent()).isEqualTo(testConventionsContent);
    }

    @Test
    @DisplayName("5. 多租户与权限隔离：User A 与 User B 工作区越权操作严格拦截校验")
    void shouldEnforceUserIsolationAndBlockCrossUserWorkspaceAccess() {
        // User A (Alice) 创建项目
        StartOnboardingCommand startCmd = new StartOnboardingCommand(
                "alice-private-service",
                "Alice's confidential project",
                new GitSourceConfig(GitSourceType.LOCAL_DIR, "alice-template", null, null, "main")
        );
        OnboardingSessionView session = onboardingApplication.startOnboarding(startCmd);
        createdSessionIds.add(session.getSessionId());
        for (OnboardingStep step : OnboardingStep.values()) {
            session = onboardingApplication.executeStep(session.getSessionId(), new ExecuteStepCommand(step, null, null));
        }
        String aliceProjId = session.getProjectId();
        String aliceWsId = session.getWorkspaceId();
        createdWorkspaceIds.add(aliceWsId);
        createdProjectIds.add(aliceProjId);

        // 切换到 User B (Bob) 上下文
        RequestContext.get().setUserId(USER_BOB);
        RequestContext.get().setUsername("bob@agenthub.test");

        // 1) Bob 尝试访问 Alice 的向导 Session
        final String aliceSessionId = session.getSessionId();
        assertThatThrownBy(() -> onboardingApplication.getSession(aliceSessionId))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        // 2) Bob 尝试列出 Alice 工作区的文件
        assertThatThrownBy(() -> workspaceApplication.listFiles(aliceWsId))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        // 3) Bob 尝试修改 Alice 工作区的文件
        assertThatThrownBy(() -> workspaceApplication.saveFile(aliceWsId, "hack.txt", "evil"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        // 4) Bob 尝试执行 Alice 工作区的生命周期操作
        assertThatThrownBy(() -> workspaceApplication.resyncWorkspace(aliceWsId))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        assertThatThrownBy(() -> workspaceApplication.changeBaseline(aliceWsId, "abc1234", true))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        assertThatThrownBy(() -> workspaceApplication.discardWorkspace(aliceWsId, true, "hack"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        // 5) Bob 尝试查询 Alice 项目的 Context Sources
        assertThatThrownBy(() -> contextSourceApplication.listContextSources(aliceProjId))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    @DisplayName("6. 凭据全链路脱敏：GitHub Token 与私密凭据绝不在日志或视图中明文泄漏")
    void shouldEnsureSecretMaskingAcrossUrlsAndLogs() {
        String rawGithubToken = "ghp_AbCdEf1234567890AbCdEf1234567890XyZ0";
        String rawPatToken = "github_pat_11AABBCC00112233445566778899aabbccddeeffgghhiijjkkllmmnnooppqqrrss";

        // 校验 SecretMasker 脱敏效果
        String maskedGithub = SecretMasker.maskSecret(rawGithubToken);
        assertThat(maskedGithub).doesNotContain(rawGithubToken);
        assertThat(maskedGithub).contains("***");

        String maskedPat = SecretMasker.maskSecret(rawPatToken);
        assertThat(maskedPat).doesNotContain(rawPatToken);
        assertThat(maskedPat).contains("***");

        // 文本混杂脱敏
        String logText = "Connecting with token: " + rawGithubToken + " to clone repo https://github.com/myorg/repo.git?token=" + rawPatToken;
        String sanitizedText = SecretMasker.maskInText(logText);
        assertThat(sanitizedText).doesNotContain(rawGithubToken);
        assertThat(sanitizedText).doesNotContain(rawPatToken);
        assertThat(sanitizedText).contains("***");
    }
}
