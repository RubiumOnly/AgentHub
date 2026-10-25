package com.agenthub;

import com.agenthub.identity.infrastructure.entity.UserEntity;
import com.agenthub.identity.infrastructure.repository.UserRepository;
import com.agenthub.project.application.GitConnectionApplication;
import com.agenthub.project.application.ProjectApplication;
import com.agenthub.project.domain.connection.GitConnectionStatus;
import com.agenthub.project.domain.connection.GitProviderConnection;
import com.agenthub.project.dto.CreateProjectCommand;
import com.agenthub.project.dto.ProjectView;
import com.agenthub.project.dto.connection.AuthorizeGitConnectionCommand;
import com.agenthub.project.dto.connection.GitConnectionView;
import com.agenthub.project.infrastructure.repository.GitConnectionRepository;
import com.agenthub.project.infrastructure.repository.ProjectRepository;
import com.agenthub.shared.context.RequestContext;
import com.agenthub.shared.exception.BusinessException;
import com.agenthub.shared.exception.ErrorCode;
import com.agenthub.team.application.TeamApplication;
import com.agenthub.team.domain.rbac.ProjectRbacRole;
import com.agenthub.team.dto.AddProjectMemberCommand;
import com.agenthub.team.infrastructure.entity.TeamEntity;
import com.agenthub.team.infrastructure.repository.TeamMemberRepository;
import com.agenthub.team.infrastructure.repository.TeamRepository;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.FileSystemUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 11 R1: Git 提供商授权连接生命周期自动化测试套件。
 * 覆盖：
 * 1. 凭据只保存引用（env:/prop:/secret:），裸令牌在持久化前被拒绝；
 * 2. 授权/测试/撤销全部走统一 RBAC 权限矩阵（SECRET_MANAGE / PROJECT_VIEW）；
 * 3. 连接测试经真实 JGit ls-remote 验证仓库可达性，失败记录掩码原因；
 * 4. 撤销幂等、清除凭据引用并阻断后续使用；重新授权生成新 ACTIVE 行；
 * 5. 授权、测试、撤销全程进入不可篡改安全审计；
 * 6. 跨项目连接 ID 隔离与严格级联清理。
 */
@SpringBootTest
@ActiveProfiles("test")
public class Phase11GitConnectionAuthorizationTest {

    @Autowired
    private ProjectApplication projectApplication;

    @Autowired
    private TeamApplication teamApplication;

    @Autowired
    private GitConnectionApplication gitConnectionApplication;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private TeamMemberRepository teamMemberRepository;

    @Autowired
    private GitConnectionRepository gitConnectionRepository;

    @Autowired
    private com.agenthub.project.infrastructure.repository.WorkspaceRepository workspaceRepository;

    @Autowired
    private com.agenthub.identity.infrastructure.repository.AuthAuditLogRepository auditLogRepository;

    private String ownerUserId;
    private String devUserId;
    private String testProjectId;
    private String otherProjectId;
    private Path bareRemoteDir;
    private String bareRemoteUri;

    private final List<String> createdUserIds = new ArrayList<>();
    private final List<String> createdProjectIds = new ArrayList<>();
    private final List<Path> createdTempDirs = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        ownerUserId = "usr-p11-owner-" + UUID.randomUUID().toString().substring(0, 8);
        devUserId = "usr-p11-dev-" + UUID.randomUUID().toString().substring(0, 8);
        createUser(ownerUserId, "p11owner");
        createUser(devUserId, "p11dev");

        RequestContext.get().setUserId(ownerUserId);
        ProjectView proj = projectApplication.createProject(
                new CreateProjectCommand("Phase 11 Git " + ownerUserId, "Git connection lifecycle test", null));
        testProjectId = proj.getId();
        createdProjectIds.add(testProjectId);
        ProjectView other = projectApplication.createProject(
                new CreateProjectCommand("Phase 11 Other " + ownerUserId, "Isolation fixture", null));
        otherProjectId = other.getId();
        createdProjectIds.add(otherProjectId);

        // 本地裸仓库作为 file:// 测试远端（test profile 显式开启 allow-local-test-remote）
        bareRemoteDir = Files.createTempDirectory("agenthub-p11-remote-");
        createdTempDirs.add(bareRemoteDir);
        Path workDir = Files.createTempDirectory("agenthub-p11-src-");
        createdTempDirs.add(workDir);
        try (Git src = Git.init().setDirectory(workDir.toFile()).setInitialBranch("main").call()) {
            Files.writeString(workDir.resolve("README.md"), "# phase11 fixture\n");
            src.add().addFilepattern(".").call();
            src.commit().setMessage("init").setAuthor("Phase11", "phase11@test.org").call();
        }
        try (Git ignored = Git.cloneRepository()
                .setURI(workDir.toUri().toString())
                .setDirectory(bareRemoteDir.toFile())
                .setBare(true)
                .call()) {
            // bare clone keeps refs/heads/main for ls-remote visibility assertions
        }
        bareRemoteUri = bareRemoteDir.toUri().toString();
    }

    private void createUser(String userId, String username) {
        userRepository.save(new UserEntity(userId, userId + "@test.org", "pwd123", username, "ACTIVE"));
        createdUserIds.add(userId);
    }

    @AfterEach
    void tearDown() {
        RequestContext.clear();
        for (String projectId : createdProjectIds) {
            try {
                gitConnectionRepository.deleteAll(
                        gitConnectionRepository.findByProjectIdOrderByAuthorizedAtDesc(projectId));
            } catch (Exception ignored) {}
            try {
                workspaceRepository.findByProjectId(projectId)
                        .ifPresent(ws -> workspaceRepository.deleteById(ws.getId()));
            } catch (Exception ignored) {}
            List<TeamEntity> teams = teamRepository.findByProjectId(projectId);
            for (TeamEntity t : teams) {
                teamMemberRepository.deleteAll(teamMemberRepository.findByTeamIdOrderBySortOrderAsc(t.getId()));
            }
            teamRepository.deleteAll(teams);
            try {
                projectRepository.deleteById(projectId);
            } catch (Exception ignored) {}
        }
        for (String uid : createdUserIds) {
            try {
                userRepository.deleteById(uid);
            } catch (Exception ignored) {}
        }
        for (Path dir : createdTempDirs) {
            try {
                if (Files.exists(dir)) {
                    FileSystemUtils.deleteRecursively(dir);
                }
            } catch (Exception ignored) {}
        }
    }

    private AuthorizeGitConnectionCommand command(String repoUrl, String credentialRef) {
        AuthorizeGitConnectionCommand cmd = new AuthorizeGitConnectionCommand();
        cmd.setRepoUrl(repoUrl);
        cmd.setCredentialRef(credentialRef);
        cmd.setDefaultBranch("main");
        return cmd;
    }

    @Test
    @DisplayName("测试 1：授权持久化凭据引用而非令牌值，重复 ACTIVE 授权冲突，全程审计")
    void testAuthorizePersistsReferenceOnlyAndAudits() {
        RequestContext.get().setUserId(ownerUserId);
        GitConnectionView view = gitConnectionApplication.authorize(
                testProjectId, command("https://github.com/example/phase11-repo.git", "env:PHASE11_GIT_TOKEN"));

        assertThat(view.getStatus()).isEqualTo("ACTIVE");
        assertThat(view.getProvider()).isEqualTo("GITHUB");
        assertThat(view.getAuthorizedBy()).isEqualTo(ownerUserId);
        assertThat(view.getCredentialRef()).isEqualTo("env:PHASE11_GIT_TOKEN");

        // 数据库行只存引用，绝不存解析后的令牌
        var entity = gitConnectionRepository.findById(view.getId()).orElseThrow();
        assertThat(entity.getCredentialRef()).isEqualTo("env:PHASE11_GIT_TOKEN");
        assertThat(entity.getRepoUrl()).isEqualTo("https://github.com/example/phase11-repo.git");

        // 审计：授权事件含掩码仓库地址与引用名
        var events = auditLogRepository.findByUserIdAndEventTypeOrderByCreatedAtDesc(ownerUserId, "GIT_CONNECTION_AUTHORIZED");
        assertThat(events).isNotEmpty();
        assertThat(events.get(0).getDetails()).contains(view.getId()).contains("env:PHASE11_GIT_TOKEN");

        // 同仓库重复 ACTIVE 授权被拒绝
        assertThatThrownBy(() -> gitConnectionApplication.authorize(
                testProjectId, command("HTTPS://GitHub.Com/Example/Phase11-Repo.GIT", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already exists");

        // 列表仅要求 PROJECT_VIEW，且归一化 URL 去重生效（只剩一条 ACTIVE）
        assertThat(gitConnectionApplication.list(testProjectId))
                .filteredOn(v -> "ACTIVE".equals(v.getStatus()))
                .hasSize(1);
    }

    @Test
    @DisplayName("测试 2：裸令牌与非引用凭据在持久化前被拒绝，生产口径强制 https")
    void testRawCredentialAndNonHttpsRejected() {
        RequestContext.get().setUserId(ownerUserId);

        assertThatThrownBy(() -> gitConnectionApplication.authorize(
                testProjectId, command("https://github.com/example/repo.git", "ghp_rawtoken1234567890")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PARAM_ERROR);

        assertThatThrownBy(() -> gitConnectionApplication.authorize(
                testProjectId, command("https://github.com/example/repo.git", "plaintext-token")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PARAM_ERROR);

        // 无任何凭据或裸令牌被写入数据库
        assertThat(gitConnectionRepository.findByProjectIdOrderByAuthorizedAtDesc(testProjectId)).isEmpty();

        // 域规则：关闭本地测试远端开关后 http/file 一律拒绝（生产行为）
        org.junit.jupiter.api.Assertions.assertThrows(BusinessException.class,
                () -> new GitProviderConnection(null, testProjectId, null, "http://insecure.example/repo.git",
                        null, "main", GitConnectionStatus.ACTIVE, ownerUserId).validateAuthorization(false));
        org.junit.jupiter.api.Assertions.assertThrows(BusinessException.class,
                () -> new GitProviderConnection(null, testProjectId, null, "file:///tmp/repo",
                        null, "main", GitConnectionStatus.ACTIVE, ownerUserId).validateAuthorization(false));
        // 开关打开时（test profile 语义）本地 file:// 远端可用于连接测试
        new GitProviderConnection(null, testProjectId, null, "file:///tmp/repo",
                null, "main", GitConnectionStatus.ACTIVE, ownerUserId).validateAuthorization(true);
    }

    @Test
    @DisplayName("测试 3：授权/测试/撤销要求 SECRET_MANAGE，DEVELOPER 与 VIEWER 被权限矩阵阻断")
    void testPermissionMatrixEnforcement() {
        RequestContext.get().setUserId(ownerUserId);
        GitConnectionView view = gitConnectionApplication.authorize(
                testProjectId, command(bareRemoteUri, null));

        RequestContext.get().setUserId(ownerUserId);
        teamApplication.addProjectMember(testProjectId,
                new AddProjectMemberCommand(devUserId, ProjectRbacRole.DEVELOPER, "Dev"));

        // DEVELOPER 可读连接列表（PROJECT_VIEW）但无权授权/测试/撤销（无 SECRET_MANAGE）
        RequestContext.get().setUserId(devUserId);
        assertThat(gitConnectionApplication.list(testProjectId)).hasSize(1);
        assertThatThrownBy(() -> gitConnectionApplication.authorize(
                testProjectId, command("https://github.com/example/another.git", null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
        assertThatThrownBy(() -> gitConnectionApplication.testConnection(testProjectId, view.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
        assertThatThrownBy(() -> gitConnectionApplication.revoke(testProjectId, view.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);

        // 越权尝试没有留下任何新行或状态变化
        assertThat(gitConnectionRepository.findByProjectIdOrderByAuthorizedAtDesc(testProjectId)).hasSize(1);
        assertThat(gitConnectionRepository.findById(view.getId()).orElseThrow().getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("测试 4：连接测试经真实 JGit ls-remote，成功与失败均记录掩码结果与审计")
    void testConnectionProbeRecordsMaskedResult() {
        RequestContext.get().setUserId(ownerUserId);
        GitConnectionView ok = gitConnectionApplication.authorize(
                testProjectId, command(bareRemoteUri, null));
        GitConnectionView tested = gitConnectionApplication.testConnection(testProjectId, ok.getId());
        assertThat(tested.getLastVerifyResult()).isEqualTo("SUCCESS");
        assertThat(tested.getLastVerifiedAt()).isNotNull();
        assertThat(tested.getLastVerifyDetail()).contains("refs/heads/main");
        assertThat(tested.getLastVerifyDetail()).doesNotContain(bareRemoteDir.toAbsolutePath().toString().replace('\\', '/'));

        // 不可达远端：记录 FAILED 与掩码原因，而非抛错（可恢复原因）
        GitConnectionView broken = gitConnectionApplication.authorize(
                testProjectId, command("file:///definitely/missing/repo-" + UUID.randomUUID(), null));
        GitConnectionView brokenTested = gitConnectionApplication.testConnection(testProjectId, broken.getId());
        assertThat(brokenTested.getLastVerifyResult()).isEqualTo("FAILED");
        assertThat(brokenTested.getLastVerifyDetail()).isNotBlank();

        var events = auditLogRepository.findByUserIdAndEventTypeOrderByCreatedAtDesc(ownerUserId, "GIT_CONNECTION_TESTED");
        assertThat(events).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("测试 5：撤销幂等并清除凭据引用，撤销后测试被阻断，重新授权生成新 ACTIVE 行")
    void testIdempotentRevokeClearsCredentialAndBlocksReuse() {
        RequestContext.get().setUserId(ownerUserId);
        GitConnectionView view = gitConnectionApplication.authorize(
                testProjectId, command(bareRemoteUri, "env:PHASE11_GIT_TOKEN"));

        GitConnectionView revoked = gitConnectionApplication.revoke(testProjectId, view.getId());
        assertThat(revoked.getStatus()).isEqualTo("REVOKED");
        assertThat(revoked.getRevokedBy()).isEqualTo(ownerUserId);
        assertThat(revoked.getRevokedAt()).isNotNull();
        assertThat(revoked.getCredentialRef()).as("revocation must clear the credential reference").isNull();

        // 幂等：重复撤销不抛错、不重复审计、状态不变
        GitConnectionView revokedAgain = gitConnectionApplication.revoke(testProjectId, view.getId());
        assertThat(revokedAgain.getStatus()).isEqualTo("REVOKED");
        assertThat(auditLogRepository.findByUserIdAndEventTypeOrderByCreatedAtDesc(ownerUserId, "GIT_CONNECTION_REVOKED"))
                .hasSize(1);

        // 撤销后的连接不能再测试，错误码明确可恢复
        assertThatThrownBy(() -> gitConnectionApplication.testConnection(testProjectId, view.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.GIT_CONNECTION_REVOKED);

        // 同仓库可重新授权，生成新的 ACTIVE 行，历史 REVOKED 行保留审计
        GitConnectionView reauthorized = gitConnectionApplication.authorize(
                testProjectId, command(bareRemoteUri, null));
        assertThat(reauthorized.getId()).isNotEqualTo(view.getId());
        assertThat(reauthorized.getStatus()).isEqualTo("ACTIVE");
        assertThat(gitConnectionApplication.list(testProjectId))
                .filteredOn(v -> "REVOKED".equals(v.getStatus()))
                .hasSize(1);
    }

    @Test
    @DisplayName("测试 6：连接 ID 跨项目隔离，越项目访问按未找到处理")
    void testCrossProjectIsolation() {
        RequestContext.get().setUserId(ownerUserId);
        GitConnectionView view = gitConnectionApplication.authorize(
                testProjectId, command(bareRemoteUri, null));

        assertThatThrownBy(() -> gitConnectionApplication.testConnection(otherProjectId, view.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.GIT_CONNECTION_NOT_FOUND);
        assertThatThrownBy(() -> gitConnectionApplication.revoke(otherProjectId, view.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.GIT_CONNECTION_NOT_FOUND);
    }
}
