package com.agenthub;

import com.agenthub.identity.dto.AuthAuditLogView;
import com.agenthub.identity.infrastructure.entity.UserEntity;
import com.agenthub.identity.infrastructure.repository.AuthAuditLogRepository;
import com.agenthub.identity.infrastructure.repository.UserRepository;
import com.agenthub.identity.infrastructure.security.ResourceOwnershipVerifier;
import com.agenthub.infrastructure.ops.DisasterRecoveryBackupService;
import com.agenthub.ops.application.OpsApplication;
import com.agenthub.ops.dto.BackupRestoreCommand;
import com.agenthub.ops.dto.BackupTriggerCommand;
import com.agenthub.ops.dto.BackupVerifyCommand;
import com.agenthub.ops.dto.OpsMetricsSummaryView;
import com.agenthub.project.application.ProjectApplication;
import com.agenthub.project.dto.CreateProjectCommand;
import com.agenthub.project.dto.ProjectView;
import com.agenthub.project.infrastructure.entity.WorkspaceEntity;
import com.agenthub.project.infrastructure.repository.ProjectRepository;
import com.agenthub.project.infrastructure.repository.WorkspaceRepository;
import com.agenthub.shared.context.RequestContext;
import com.agenthub.shared.contract.PageRequestQuery;
import com.agenthub.shared.contract.PageResultView;
import com.agenthub.shared.exception.BusinessException;
import com.agenthub.shared.exception.ErrorCode;
import com.agenthub.team.application.TeamApplication;
import com.agenthub.team.domain.rbac.ProjectPermission;
import com.agenthub.team.domain.rbac.ProjectRbacRole;
import com.agenthub.team.dto.AddProjectMemberCommand;
import com.agenthub.team.dto.ProjectMemberView;
import com.agenthub.team.dto.ProjectUserRoleView;
import com.agenthub.team.dto.UpdateMemberRoleCommand;
import com.agenthub.team.infrastructure.entity.TeamEntity;
import com.agenthub.team.infrastructure.entity.TeamMemberEntity;
import com.agenthub.team.infrastructure.repository.TeamMemberRepository;
import com.agenthub.team.infrastructure.repository.TeamRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.FileSystemUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 6: 团队协作、组织角色与长期运维体系 (Team Collaboration, RBAC & Operations Productization) 自动化测试套件。
 * 覆盖：
 * 1. 细粒度 RBAC 权限矩阵校验（OWNER, ADMIN, MAINTAINER, DEVELOPER, VIEWER, AUDITOR）；
 * 2. ResourceOwnershipVerifier 跨资源所有权与角色权限防越权拦截；
 * 3. 团队成员角色指派、升级、权限生效与移除收回全生命周期；
 * 4. 不可篡改安全审计日志查询与主体追踪；
 * 5. 灾备快照创建、加密校验与幂等还原验证；
 * 6. Prometheus/Micrometer 运维聚合指标与 SLO 健康状态评估；
 * 7. 严格的测试隔离与级联清理。
 */
@SpringBootTest
@ActiveProfiles("test")
public class Phase6TeamRbacAndOperationsTest {

    @Autowired
    private ProjectApplication projectApplication;

    @Autowired
    private TeamApplication teamApplication;

    @Autowired
    private OpsApplication opsApplication;

    @Autowired
    private ResourceOwnershipVerifier ownershipVerifier;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private TeamMemberRepository teamMemberRepository;

    @Autowired
    private AuthAuditLogRepository auditLogRepository;

    @Autowired
    private com.agenthub.execution.infrastructure.repository.WorkflowRunRepository workflowRunRepository;

    @Autowired
    private com.agenthub.sandbox.infrastructure.repository.DeploymentRepository deploymentRepository;

    private String ownerUserId;
    private String devUserId;
    private String viewerUserId;
    private String auditorUserId;

    private String testProjectId;
    private String testWorkspaceId;
    private String testRunId;
    private String testDeploymentId;
    private final List<String> createdUserIds = new ArrayList<>();
    private final List<String> createdRunIds = new ArrayList<>();
    private final List<String> createdDeploymentIds = new ArrayList<>();
    private final List<Path> createdTempDirs = new ArrayList<>();

    @BeforeEach
    void setUp() {
        ownerUserId = "usr-p6-owner-" + UUID.randomUUID().toString().substring(0, 8);
        devUserId = "usr-p6-dev-" + UUID.randomUUID().toString().substring(0, 8);
        viewerUserId = "usr-p6-view-" + UUID.randomUUID().toString().substring(0, 8);
        auditorUserId = "usr-p6-audit-" + UUID.randomUUID().toString().substring(0, 8);

        createUser(ownerUserId, "Owner", "DEVELOPER");
        createUser(devUserId, "Developer", "DEVELOPER");
        createUser(viewerUserId, "Viewer", "DEVELOPER");
        createUser(auditorUserId, "Auditor", "DEVELOPER");

        RequestContext.get().setUserId(ownerUserId);

        CreateProjectCommand cmd = new CreateProjectCommand("Phase 6 Proj " + ownerUserId, "Team RBAC and Ops Test", "REPO");
        ProjectView proj = projectApplication.createProject(cmd);
        testProjectId = proj.getId();

        WorkspaceEntity ws = workspaceRepository.findByProjectId(testProjectId).orElseThrow();
        testWorkspaceId = ws.getId();

        com.agenthub.execution.infrastructure.entity.WorkflowRunEntity run =
                new com.agenthub.execution.infrastructure.entity.WorkflowRunEntity(
                        "run-p6-" + UUID.randomUUID().toString().substring(0, 8), testProjectId, "def-p6", "RUNNING", null);
        workflowRunRepository.save(run);
        testRunId = run.getId();
        createdRunIds.add(testRunId);

        com.agenthub.sandbox.infrastructure.entity.DeploymentEntity deployment =
                new com.agenthub.sandbox.infrastructure.entity.DeploymentEntity(
                        "dep-p6-" + UUID.randomUUID().toString().substring(0, 8), testProjectId, "art-p6", "DEPLOYED", "LOCAL_PROCESS", "http://preview:8080");
        deploymentRepository.save(deployment);
        testDeploymentId = deployment.getId();
        createdDeploymentIds.add(testDeploymentId);
    }

    private void createUser(String userId, String username, String role) {
        UserEntity user = new UserEntity(userId, username, userId + "@test.org", "pwd123", role);
        userRepository.save(user);
        createdUserIds.add(userId);
    }

    @AfterEach
    void tearDown() {
        RequestContext.clear();

        for (String depId : createdDeploymentIds) {
            try {
                deploymentRepository.deleteById(depId);
            } catch (Exception ignored) {}
        }

        for (String runId : createdRunIds) {
            try {
                workflowRunRepository.deleteById(runId);
            } catch (Exception ignored) {}
        }

        if (testProjectId != null) {
            List<TeamEntity> teams = teamRepository.findByProjectId(testProjectId);
            for (TeamEntity t : teams) {
                teamMemberRepository.deleteAll(teamMemberRepository.findByTeamIdOrderBySortOrderAsc(t.getId()));
            }
            teamRepository.deleteAll(teams);
        }

        if (testWorkspaceId != null) {
            workspaceRepository.deleteById(testWorkspaceId);
        }
        if (testProjectId != null) {
            projectRepository.deleteById(testProjectId);
        }

        for (String uid : createdUserIds) {
            try {
                userRepository.deleteById(uid);
            } catch (Exception ignored) {}
        }

        for (Path tempDir : createdTempDirs) {
            try {
                if (Files.exists(tempDir)) {
                    FileSystemUtils.deleteRecursively(tempDir);
                }
            } catch (Exception ignored) {}
        }
    }

    @Test
    @DisplayName("测试 1：RBAC 角色模型与细粒度权限矩阵验证")
    void testRbacPermissionsMatrix() {
        assertThat(ProjectRbacRole.OWNER.getPermissions()).containsAll(List.of(ProjectPermission.values()));

        assertThat(ProjectRbacRole.ADMIN.hasPermission(ProjectPermission.PROJECT_MANAGE)).isTrue();
        assertThat(ProjectRbacRole.ADMIN.hasPermission(ProjectPermission.WORKSPACE_WRITE)).isTrue();
        assertThat(ProjectRbacRole.ADMIN.hasPermission(ProjectPermission.APPROVAL_DECIDE)).isTrue();
        assertThat(ProjectRbacRole.ADMIN.hasPermission(ProjectPermission.DISASTER_RECOVERY)).isTrue();

        assertThat(ProjectRbacRole.MAINTAINER.hasPermission(ProjectPermission.WORKSPACE_WRITE)).isTrue();
        assertThat(ProjectRbacRole.MAINTAINER.hasPermission(ProjectPermission.APPROVAL_DECIDE)).isTrue();
        assertThat(ProjectRbacRole.MAINTAINER.hasPermission(ProjectPermission.DEPLOYMENT_MANAGE)).isTrue();
        assertThat(ProjectRbacRole.MAINTAINER.hasPermission(ProjectPermission.PROJECT_MANAGE)).isFalse();
        assertThat(ProjectRbacRole.MAINTAINER.hasPermission(ProjectPermission.DISASTER_RECOVERY)).isFalse();

        assertThat(ProjectRbacRole.DEVELOPER.hasPermission(ProjectPermission.WORKSPACE_WRITE)).isTrue();
        assertThat(ProjectRbacRole.DEVELOPER.hasPermission(ProjectPermission.WORKFLOW_EXECUTE)).isTrue();
        assertThat(ProjectRbacRole.DEVELOPER.hasPermission(ProjectPermission.APPROVAL_DECIDE)).isFalse();
        assertThat(ProjectRbacRole.DEVELOPER.hasPermission(ProjectPermission.DEPLOYMENT_MANAGE)).isFalse();
        assertThat(ProjectRbacRole.DEVELOPER.hasPermission(ProjectPermission.PROJECT_MANAGE)).isFalse();

        assertThat(ProjectRbacRole.VIEWER.hasPermission(ProjectPermission.WORKSPACE_READ)).isTrue();
        assertThat(ProjectRbacRole.VIEWER.hasPermission(ProjectPermission.WORKSPACE_WRITE)).isFalse();
        assertThat(ProjectRbacRole.VIEWER.hasPermission(ProjectPermission.WORKFLOW_EXECUTE)).isFalse();
        assertThat(ProjectRbacRole.VIEWER.hasPermission(ProjectPermission.APPROVAL_DECIDE)).isFalse();

        assertThat(ProjectRbacRole.AUDITOR.hasPermission(ProjectPermission.AUDIT_VIEW)).isTrue();
        assertThat(ProjectRbacRole.AUDITOR.hasPermission(ProjectPermission.WORKSPACE_WRITE)).isFalse();
    }

    @Test
    @DisplayName("测试 2：ResourceOwnershipVerifier 跨资源所有权与角色防越权阻断")
    void testProjectResourceOwnershipAndRoleEnforcement() {
        // 1. 项目创建者自动具有 OWNER 权限
        ownershipVerifier.verifyWorkspaceWriteAccess(testWorkspaceId, ownerUserId);
        ownershipVerifier.verifyRunExecuteAccess(testRunId, ownerUserId);
        ownershipVerifier.verifyApprovalAccess(testRunId, ownerUserId);
        ownershipVerifier.verifyDeploymentAccess(testDeploymentId, ownerUserId);

        // 2. 添加 DEVELOPER 成员
        RequestContext.get().setUserId(ownerUserId);
        AddProjectMemberCommand addDevCmd = new AddProjectMemberCommand(devUserId, ProjectRbacRole.DEVELOPER, "Backend Engineer");
        teamApplication.addProjectMember(testProjectId, addDevCmd);

        // 开发者具有写工作区和执行 Run 权限
        ownershipVerifier.verifyWorkspaceWriteAccess(testWorkspaceId, devUserId);
        ownershipVerifier.verifyRunExecuteAccess(testRunId, devUserId);

        // 开发者禁止审批决策
        assertThatThrownBy(() -> ownershipVerifier.verifyApprovalAccess(testRunId, devUserId))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);

        // 开发者禁止部署沙箱
        assertThatThrownBy(() -> ownershipVerifier.verifyDeploymentAccess(testDeploymentId, devUserId))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);

        // 3. 添加 VIEWER 成员
        AddProjectMemberCommand addViewCmd = new AddProjectMemberCommand(viewerUserId, ProjectRbacRole.VIEWER, "Guest Reviewer");
        teamApplication.addProjectMember(testProjectId, addViewCmd);

        // 观察者允许读工作区
        ownershipVerifier.verifyWorkspaceAccess(testWorkspaceId, viewerUserId);

        // 观察者禁止写工作区
        assertThatThrownBy(() -> ownershipVerifier.verifyWorkspaceWriteAccess(testWorkspaceId, viewerUserId))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);

        // 观察者禁止执行 Run
        assertThatThrownBy(() -> ownershipVerifier.verifyRunExecuteAccess(testRunId, viewerUserId))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("测试 3：团队成员角色管理全流程（查询、增加、升级、收回权限）")
    void testTeamMemberLifecycleAndRoleElevation() {
        RequestContext.get().setUserId(ownerUserId);

        // 1. 获取当前用户权限视图
        ProjectUserRoleView ownerRole = teamApplication.getCurrentUserRole(testProjectId);
        assertThat(ownerRole.getRole()).isEqualTo(ProjectRbacRole.OWNER);
        assertThat(ownerRole.isOwner()).isTrue();
        assertThat(ownerRole.getPermissions()).contains(ProjectPermission.PROJECT_MANAGE.name());

        // 2. 添加开发者成员
        ProjectMemberView devMember = teamApplication.addProjectMember(
                testProjectId, new AddProjectMemberCommand(devUserId, ProjectRbacRole.DEVELOPER, "Dev Lead"));
        assertThat(devMember.getRbacRole()).isEqualTo(ProjectRbacRole.DEVELOPER);

        List<ProjectMemberView> members = teamApplication.listProjectMembers(testProjectId);
        assertThat(members).hasSizeGreaterThanOrEqualTo(2);

        // 3. 角色升级：从 DEVELOPER 提升为 MAINTAINER
        UpdateMemberRoleCommand updateCmd = new UpdateMemberRoleCommand(ProjectRbacRole.MAINTAINER);
        ProjectMemberView updated = teamApplication.updateMemberRole(testProjectId, devMember.getId(), updateCmd);
        assertThat(updated.getRbacRole()).isEqualTo(ProjectRbacRole.MAINTAINER);

        // 提升为 MAINTAINER 后，审批权限与部署权限立即生效
        ownershipVerifier.verifyApprovalAccess(testRunId, devUserId);
        ownershipVerifier.verifyDeploymentAccess(testDeploymentId, devUserId);

        // 4. 移除成员，权限立即被收回
        teamApplication.removeProjectMember(testProjectId, devMember.getId());
        assertThatThrownBy(() -> ownershipVerifier.verifyProjectAccess(testProjectId, devUserId))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("测试 4：不可篡改安全审计日志查询与主体追踪")
    void testAuditLogRecordingAndQuerying() {
        RequestContext.get().setUserId(ownerUserId);

        // 触发操作产生审计记录
        teamApplication.addProjectMember(testProjectId, new AddProjectMemberCommand(devUserId, ProjectRbacRole.DEVELOPER, "Dev"));

        PageResultView<AuthAuditLogView> auditLogs = opsApplication.queryAuditLogs(
                "RBAC_ROLE_ASSIGNED", ownerUserId, PageRequestQuery.of(1, 10));

        assertThat(auditLogs).isNotNull();
        assertThat(auditLogs.getItems()).isNotEmpty();
        AuthAuditLogView entry = auditLogs.getItems().get(0);
        assertThat(entry.getEventType()).isEqualTo("RBAC_ROLE_ASSIGNED");
        assertThat(entry.getUserId()).isEqualTo(ownerUserId);
        assertThat(entry.getDetails()).contains(devUserId);
    }

    @Test
    @DisplayName("测试 5：灾备快照创建、加密完整性校验与幂等还原")
    void testDisasterRecoveryBackupAndVerifyAndRestore() throws IOException {
        Path tempBackupDir = Files.createTempDirectory("agenthub-test-backup-");
        Path tempRestoreDir = Files.createTempDirectory("agenthub-test-restore-");
        createdTempDirs.add(tempBackupDir);
        createdTempDirs.add(tempRestoreDir);

        RequestContext.get().setUserId(ownerUserId);

        // 1. 触发备份
        BackupTriggerCommand triggerCmd = new BackupTriggerCommand(tempBackupDir.toString(), "TestSecretPassphrase123!");
        DisasterRecoveryBackupService.BackupManifest manifest = opsApplication.triggerBackup(triggerCmd);

        assertThat(manifest).isNotNull();
        assertThat(manifest.backupId).isNotBlank();
        assertThat(manifest.dbSnapshotSha256).isNotBlank();
        assertThat(manifest.encrypted).isTrue();

        // 2. 校验备份完整性与指纹
        BackupVerifyCommand verifyCmd = new BackupVerifyCommand(tempBackupDir.toString(), "TestSecretPassphrase123!");
        DisasterRecoveryBackupService.BackupManifest verified = opsApplication.verifyBackup(verifyCmd);
        assertThat(verified.backupId).isEqualTo(manifest.backupId);
        assertThat(verified.dbSnapshotSha256).isEqualTo(manifest.dbSnapshotSha256);

        // 3. 错误密码校验必须失败
        BackupVerifyCommand wrongPassCmd = new BackupVerifyCommand(tempBackupDir.toString(), "WrongPassphrase!");
        assertThatThrownBy(() -> opsApplication.verifyBackup(wrongPassCmd))
                .isInstanceOf(BusinessException.class);

        // 4. 幂等还原验证
        BackupRestoreCommand restoreCmd = new BackupRestoreCommand(
                tempBackupDir.toString(), tempRestoreDir.toString(), "TestSecretPassphrase123!");
        DisasterRecoveryBackupService.RestoreVerificationReport report = opsApplication.restoreBackup(restoreCmd);

        assertThat(report).isNotNull();
        assertThat(report.backupId()).isEqualTo(manifest.backupId);
        assertThat(report.checksumVerified()).isTrue();
        assertThat(report.schemaVersionVerified()).isTrue();
        assertThat(report.restoredWorkspaceFilesCount()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("测试 6：运维聚合指标与 SLO 健康状态评估")
    void testOpsMetricsSummaryAndSloCalculation() {
        OpsMetricsSummaryView summary = opsApplication.getMetricsSummary();

        assertThat(summary).isNotNull();
        assertThat(summary.getSystemHealth()).isEqualTo("UP");
        assertThat(summary.getSloStatus()).isIn("HEALTHY", "WARNING", "CRITICAL");
        assertThat(summary.getUptimeSeconds()).isGreaterThan(0L);
        assertThat(summary.getJvmMemoryTotalMb()).isGreaterThan(0L);
        assertThat(summary.getRbacEvaluations()).isNotNull();
        assertThat(summary.getWorkspaceDiskAvailableRatio()).isGreaterThan(0.0);
    }
}
