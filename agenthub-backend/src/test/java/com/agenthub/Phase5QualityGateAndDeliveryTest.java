package com.agenthub;

import com.agenthub.audit.application.ArtifactApplication;
import com.agenthub.audit.domain.quality.*;
import com.agenthub.audit.dto.ArtifactView;
import com.agenthub.audit.dto.PullRequestDraftView;
import com.agenthub.audit.infrastructure.entity.ArtifactEntity;
import com.agenthub.audit.infrastructure.repository.ArtifactRepository;
import com.agenthub.domain.workspace.model.FileDiffEntry;
import com.agenthub.domain.workspace.model.StructuredDiff;
import com.agenthub.domain.workspace.service.JGitWorkspaceManager;
import com.agenthub.domain.workspace.service.WorkspaceResolver;
import com.agenthub.execution.application.ExecutionApplication;
import com.agenthub.execution.domain.model.StepRunStatus;
import com.agenthub.execution.dto.RunEvidenceBundleView;
import com.agenthub.execution.dto.StepRunView;
import com.agenthub.execution.dto.WorkflowRunView;
import com.agenthub.execution.infrastructure.entity.RunEventEntity;
import com.agenthub.execution.infrastructure.entity.StepRunEntity;
import com.agenthub.execution.infrastructure.entity.WorkflowRunEntity;
import com.agenthub.execution.infrastructure.repository.RunEventRepository;
import com.agenthub.execution.infrastructure.repository.StepRunRepository;
import com.agenthub.execution.infrastructure.repository.WorkflowRunRepository;
import com.agenthub.identity.infrastructure.entity.UserEntity;
import com.agenthub.identity.infrastructure.repository.UserRepository;
import com.agenthub.project.application.ProjectApplication;
import com.agenthub.project.dto.CreateProjectCommand;
import com.agenthub.project.dto.ProjectView;
import com.agenthub.project.infrastructure.entity.WorkspaceEntity;
import com.agenthub.project.infrastructure.repository.ProjectRepository;
import com.agenthub.project.infrastructure.repository.WorkspaceRepository;
import com.agenthub.sandbox.api.dto.CreateDeploymentRequest;
import com.agenthub.sandbox.api.dto.DeploymentResponse;
import com.agenthub.sandbox.application.DeploymentApplication;
import com.agenthub.sandbox.domain.model.DeploymentStatus;
import com.agenthub.sandbox.infrastructure.entity.DeploymentEntity;
import com.agenthub.sandbox.infrastructure.repository.DeploymentRepository;
import com.agenthub.shared.context.RequestContext;
import com.agenthub.shared.exception.BusinessException;
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
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 5 质量门禁、审查与受控交付自动化测试套件：
 * 1. 质量门禁多维策略评估（QualityGateEngine）与不可伪造 SHA-256 签名；
 * 2. 秘密凭据泄露与危险代码模式静态扫描一票否决防御；
 * 3. 质量门禁防伪造门禁：未通过门禁的产物坚决阻断验收并标记为 GATE_FAILED；
 * 4. 受控沙箱预览部署严格准入与主动健康探针探测；
 * 5. JGit 单文件非破坏性精准回滚（Single-File Revert）；
 * 6. Step 级审查重做与用户反馈意见在上下文中的传递；
 * 7. 标准 Pull Request 草稿规范（PullRequestDraftView）与安全交付摘要生成；
 * 8. 离线可审计证据包完整性导出与防篡改校验。
 */
@SpringBootTest
@ActiveProfiles("test")
public class Phase5QualityGateAndDeliveryTest {

    @Autowired
    private ArtifactApplication artifactApplication;

    @Autowired
    private ExecutionApplication executionApplication;

    @Autowired
    private DeploymentApplication deploymentApplication;

    @Autowired
    private ProjectApplication projectApplication;

    @Autowired
    private QualityGateEngine qualityGateEngine;

    @Autowired
    private JGitWorkspaceManager gitManager;

    @Autowired
    private WorkspaceResolver workspaceResolver;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private WorkflowRunRepository workflowRunRepository;

    @Autowired
    private StepRunRepository stepRunRepository;

    @Autowired
    private ArtifactRepository artifactRepository;

    @Autowired
    private RunEventRepository runEventRepository;

    @Autowired
    private DeploymentRepository deploymentRepository;

    private String testUserId;
    private String testProjectId;
    private String testWorkspaceId;
    private Path testWorkspacePath;
    private final List<String> createdRunIds = new ArrayList<>();
    private final List<String> createdArtifactIds = new ArrayList<>();
    private final List<String> createdDeploymentIds = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        testUserId = "usr-p5-" + UUID.randomUUID().toString().substring(0, 8);
        UserEntity user = new UserEntity(testUserId, "Tester P5", testUserId + "@test.com", "secret", "DEVELOPER");
        userRepository.save(user);
        RequestContext.get().setUserId(testUserId);

        CreateProjectCommand cmd = new CreateProjectCommand("Proj P5 " + testUserId, "Phase 5 Delivery Test", "REPO");
        ProjectView proj = projectApplication.createProject(cmd);
        testProjectId = proj.getId();

        WorkspaceEntity ws = workspaceRepository.findByProjectId(testProjectId).orElseThrow();
        testWorkspaceId = ws.getId();
        testWorkspacePath = workspaceResolver.getWorkspaceRoot(testWorkspaceId);

        gitManager.initWorkspace(testWorkspacePath.toFile());
    }

    @AfterEach
    void tearDown() {
        // Cascade clean up all entities and sandbox deployments to prevent test pollution
        for (String depId : createdDeploymentIds) {
            try {
                deploymentApplication.stopDeployment(depId);
            } catch (Exception ignored) {}
            deploymentRepository.deleteById(depId);
        }

        for (String artId : createdArtifactIds) {
            artifactRepository.deleteById(artId);
        }

        for (String runId : createdRunIds) {
            stepRunRepository.deleteAll(stepRunRepository.findByRunIdOrderByCreatedAtAsc(runId));
            runEventRepository.deleteAll(runEventRepository.findByRunIdOrderBySequenceNumAsc(runId));
            artifactRepository.deleteAll(artifactRepository.findByRunIdOrderByCreatedAtDesc(runId));
            workflowRunRepository.deleteById(runId);
        }

        if (testWorkspaceId != null) {
            workspaceRepository.deleteById(testWorkspaceId);
        }
        if (testProjectId != null) {
            projectRepository.deleteById(testProjectId);
        }
        if (testUserId != null) {
            userRepository.deleteById(testUserId);
        }

        if (testWorkspacePath != null && Files.exists(testWorkspacePath)) {
            try {
                org.springframework.util.FileSystemUtils.deleteRecursively(testWorkspacePath);
            } catch (Exception ignored) {}
        }
        RequestContext.clear();
    }

    @Test
    @DisplayName("测试 1：质量门禁全项评测与确定性指纹计算（5 大维度全绿灯）")
    void testQualityGateAllRulesPassed() {
        String runId = "run-qg-pass-" + UUID.randomUUID().toString().substring(0, 8);
        createdRunIds.add(runId);

        List<FileDiffEntry> entries = List.of(
                new FileDiffEntry("src/main/Service.java", "src/main/Service.java",
                        FileDiffEntry.ChangeType.MODIFY,
                        "@@ -1,5 +1,7 @@\n public class Service {\n+    public String execute() {\n+        return \"safe\";\n+    }\n }",
                        4, 0)
        );
        StructuredDiff diff = new StructuredDiff(testWorkspaceId, "base-1", "head-1", 1, 4, 0, entries);

        StepRunView testStep = new StepRunView("step-test-1", runId, "node-qa-unit-test", "SUCCEEDED", 1, null, null, null, LocalDateTime.now(), LocalDateTime.now());
        StepRunView buildStep = new StepRunView("step-build-1", runId, "node-compile-build", "SUCCEEDED", 1, null, null, null, LocalDateTime.now(), LocalDateTime.now());

        QualityGateReport report = qualityGateEngine.evaluate("art-1", runId, diff, List.of(testStep, buildStep));

        assertThat(report).isNotNull();
        assertThat(report.getOverallStatus()).isEqualTo(QualityGateStatus.PASSED);
        assertThat(report.getTotalRules()).isEqualTo(5);
        assertThat(report.getPassedRules()).isEqualTo(5);
        assertThat(report.getFailedRules()).isEqualTo(0);
        assertThat(report.getCompositeScore()).isGreaterThanOrEqualTo(90);
        assertThat(report.getSha256Fingerprint()).isNotBlank().hasSize(64);
    }

    @Test
    @DisplayName("测试 2：硬编码敏感凭据与违规函数一票否决防御")
    void testQualityGateSecretAndStaticViolationsRejected() {
        String runId = "run-qg-fail-" + UUID.randomUUID().toString().substring(0, 8);
        createdRunIds.add(runId);

        // 1. Secret leakage violation
        List<FileDiffEntry> secretEntries = List.of(
                new FileDiffEntry("src/config/AppConfig.java", "src/config/AppConfig.java",
                        FileDiffEntry.ChangeType.MODIFY,
                        "+ String apiKey = \"sk-live-12345678901234567890\";",
                        1, 0)
        );
        StructuredDiff secretDiff = new StructuredDiff(testWorkspaceId, "base-1", "head-1", 1, 1, 0, secretEntries);
        QualityGateReport secretReport = qualityGateEngine.evaluate("art-secret", runId, secretDiff, List.of());

        assertThat(secretReport.getOverallStatus()).isEqualTo(QualityGateStatus.FAILED);
        assertThat(secretReport.getCheckItems().stream()
                .filter(i -> i.getRuleType() == QualityGateRuleType.SECRET_LEAK_SCAN)
                .findFirst().orElseThrow().getStatus()).isEqualTo(QualityGateStatus.FAILED);

        // 2. Dangerous static call violation
        List<FileDiffEntry> staticEntries = List.of(
                new FileDiffEntry("src/main/BadClass.java", "src/main/BadClass.java",
                        FileDiffEntry.ChangeType.ADD,
                        "+ public void crash() {\n+     System.exit(1);\n+ }",
                        3, 0)
        );
        StructuredDiff staticDiff = new StructuredDiff(testWorkspaceId, "base-1", "head-2", 1, 3, 0, staticEntries);
        QualityGateReport staticReport = qualityGateEngine.evaluate("art-static", runId, staticDiff, List.of());

        assertThat(staticReport.getOverallStatus()).isEqualTo(QualityGateStatus.FAILED);
        assertThat(staticReport.getCheckItems().stream()
                .filter(i -> i.getRuleType() == QualityGateRuleType.STATIC_ANALYSIS_GUARD)
                .findFirst().orElseThrow().getStatus()).isEqualTo(QualityGateStatus.FAILED);
    }

    @Test
    @DisplayName("测试 3：质量门禁防伪造：未通过门禁坚决阻断验收并标记为 GATE_FAILED")
    void testAntiForgeryQualityGateRejectsAcceptance() throws Exception {
        String runId = "run-antiforgery-" + UUID.randomUUID().toString().substring(0, 8);
        createdRunIds.add(runId);

        WorkflowRunEntity run = new WorkflowRunEntity(runId, testProjectId, "def-1", "WAITING_APPROVAL", "key-1");
        workflowRunRepository.save(run);

        // Establish git baseline
        Path fileA = testWorkspacePath.resolve("Main.java");
        Files.writeString(fileA, "public class Main {}");
        String baselineCommit = gitManager.createBaseline(testWorkspacePath.toFile(), runId, testUserId);

        // Commit a leaked secret change into git
        Files.writeString(fileA, "public class Main {\n    String token = \"ghp_123456789012345678901234567890123456\";\n}");
        String headCommit = gitManager.createStepSnapshot(testWorkspacePath.toFile(), runId, "step-1", "Commit with secret", testUserId).getCommitHash();

        String artifactId = "art-bad-" + UUID.randomUUID().toString().substring(0, 8);
        createdArtifactIds.add(artifactId);
        ArtifactEntity art = new ArtifactEntity(
                artifactId, runId, "step-1", "CODE_DIFF", headCommit, headCommit,
                "{\"changedFiles\":[\"Main.java\"],\"baselineCommit\":\"" + baselineCommit + "\"}"
        );
        art.setReviewStatus("PENDING");
        artifactRepository.save(art);

        // Attempting to accept artifact MUST throw BusinessException due to quality gate failure
        assertThatThrownBy(() -> artifactApplication.acceptArtifact(artifactId, testUserId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("failed quality gate checks");

        // Verify artifact is marked GATE_FAILED in database
        ArtifactEntity updated = artifactRepository.findById(artifactId).orElseThrow();
        assertThat(updated.getReviewStatus()).isEqualTo("GATE_FAILED");
    }

    @Test
    @DisplayName("测试 4：受控沙箱预览部署准入（拦截 GATE_FAILED）与主动健康探针探测")
    void testControlledDeploymentAdmissionAndHealthProbe() throws Exception {
        String runId = "run-deploy-" + UUID.randomUUID().toString().substring(0, 8);
        createdRunIds.add(runId);

        WorkflowRunEntity run = new WorkflowRunEntity(runId, testProjectId, "def-1", "SUCCEEDED", "key-dep");
        workflowRunRepository.save(run);

        // 1. Verify GATE_FAILED artifact is strictly blocked from deployment
        String badArtId = "art-deploy-bad-" + UUID.randomUUID().toString().substring(0, 8);
        createdArtifactIds.add(badArtId);
        ArtifactEntity badArt = new ArtifactEntity(
                badArtId, runId, "step-1", "SNAPSHOT", "commit-bad", "commit-bad", "{}"
        );
        badArt.setReviewStatus("GATE_FAILED");
        artifactRepository.save(badArt);

        CreateDeploymentRequest badReq = new CreateDeploymentRequest();
        badReq.setProjectId(testProjectId);
        badReq.setArtifactId(badArtId);
        badReq.setTarget("STATIC_PREVIEW");

        assertThatThrownBy(() -> deploymentApplication.createAndDeploy(badReq))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("cannot be deployed because its quality gate status is GATE_FAILED");

        // 2. Deploy an ACCEPTED artifact with static preview and test probe
        String goodArtId = "art-deploy-good-" + UUID.randomUUID().toString().substring(0, 8);
        createdArtifactIds.add(goodArtId);
        ArtifactEntity goodArt = new ArtifactEntity(
                goodArtId, runId, "step-2", "SNAPSHOT", "commit-good", "commit-good", "{}"
        );
        goodArt.setReviewStatus("ACCEPTED");
        artifactRepository.save(goodArt);

        // Write index.html for static preview in workspace
        Path indexPath = testWorkspacePath.resolve("index.html");
        Files.writeString(indexPath, "<html><body><h1>AgentHub Preview</h1></body></html>");

        CreateDeploymentRequest goodReq = new CreateDeploymentRequest();
        goodReq.setProjectId(testProjectId);
        goodReq.setArtifactId(goodArtId);
        goodReq.setTarget("STATIC_PREVIEW");
        goodReq.setTemplateType("STATIC_WEB_TEMPLATE");
        goodReq.setSandboxType("LOCAL_PROCESS");

        DeploymentResponse depResp = deploymentApplication.createAndDeploy(goodReq);
        createdDeploymentIds.add(depResp.getId());

        assertThat(depResp).isNotNull();
        assertThat(depResp.getStatus()).isEqualTo(DeploymentStatus.RUNNING.name());
        assertThat(depResp.getUrl()).contains("http://");

        // Execute active health probe endpoint
        DeploymentResponse probeResp = deploymentApplication.probeDeployment(depResp.getId());
        assertThat(probeResp).isNotNull();
        assertThat(probeResp.getStatus()).isEqualTo(DeploymentStatus.RUNNING.name());
        assertThat(probeResp.isHasLogs()).isTrue();
    }

    @Test
    @DisplayName("测试 5：JGit 单文件非破坏性精准回滚（Single File Revert）")
    void testSingleFileNonDestructiveRevert() throws Exception {
        String runId = "run-single-revert-" + UUID.randomUUID().toString().substring(0, 8);
        createdRunIds.add(runId);

        WorkflowRunEntity run = new WorkflowRunEntity(runId, testProjectId, "def-1", "WAITING_APPROVAL", "key-srev");
        workflowRunRepository.save(run);

        // Establish workspace baseline with two files
        Path fileA = testWorkspacePath.resolve("fileA.txt");
        Path fileB = testWorkspacePath.resolve("fileB.txt");
        Files.writeString(fileA, "Baseline A content\n");
        Files.writeString(fileB, "Baseline B content\n");
        String baselineHash = gitManager.createBaseline(testWorkspacePath.toFile(), runId, testUserId);

        WorkspaceEntity ws = workspaceRepository.findById(testWorkspaceId).orElseThrow();
        ws.setGitBaselineCommit(baselineHash);
        workspaceRepository.save(ws);

        // Modify both files in Step 1
        Files.writeString(fileA, "Modified A in step\n");
        Files.writeString(fileB, "Modified B in step\n");
        String commitHash = gitManager.createStepSnapshot(testWorkspacePath.toFile(), runId, "step-1", "Modified both files", testUserId).getCommitHash();

        String artId = "art-revert-" + UUID.randomUUID().toString().substring(0, 8);
        createdArtifactIds.add(artId);
        ArtifactEntity art = new ArtifactEntity(
                artId, runId, "step-1", "CODE_DIFF", commitHash, commitHash,
                "{\"changedFiles\":[\"fileA.txt\",\"fileB.txt\"],\"baselineCommit\":\"" + baselineHash + "\"}"
        );
        art.setReviewStatus("PENDING");
        artifactRepository.save(art);

        // Revert ONLY fileA.txt
        ArtifactView result = artifactApplication.revertSingleFile(artId, "fileA.txt", testUserId);
        assertThat(result).isNotNull();

        // Verification: fileA.txt is restored to baseline, fileB.txt retains its step modification!
        String contentA = Files.readString(fileA);
        String contentB = Files.readString(fileB);

        assertThat(contentA).isEqualTo("Baseline A content\n");
        assertThat(contentB).isEqualTo("Modified B in step\n");

        // Verify audit event recorded
        List<RunEventEntity> events = runEventRepository.findByRunIdOrderBySequenceNumAsc(runId);
        assertThat(events.stream().anyMatch(e -> "FILE_REVERTED".equals(e.getEventType()))).isTrue();
    }

    @Test
    @DisplayName("测试 6：Step 级审查重做与用户反馈意见在上下文中的传递")
    void testStepReworkWithReviewerFeedback() {
        String runId = "run-rework-" + UUID.randomUUID().toString().substring(0, 8);
        createdRunIds.add(runId);

        WorkflowRunEntity run = new WorkflowRunEntity(runId, testProjectId, "def-1", "RUNNING", "key-rw");
        workflowRunRepository.save(run);

        String stepId = "step-rw-1";
        StepRunEntity step = new StepRunEntity(stepId, runId, "node-developer", "FAILED");
        step.setAttempt(1);
        step.setInputRef("Initial prompt");
        stepRunRepository.save(step);

        // Request step rework with review feedback
        String feedback = "Please add null check and defensive boundary validation";
        StepRunView reworked = executionApplication.reworkStep(runId, stepId, feedback);

        assertThat(reworked).isNotNull();
        assertThat(reworked.getStatus()).isEqualTo(StepRunStatus.PENDING.name());
        assertThat(reworked.getAttempt()).isEqualTo(2);
        assertThat(reworked.getOutputRef()).contains("Rework feedback: " + feedback);

        // Verify event published
        List<RunEventEntity> events = runEventRepository.findByRunIdOrderBySequenceNumAsc(runId);
        assertThat(events.stream().anyMatch(e -> "STEP_REWORK_REQUESTED".equals(e.getEventType()))).isTrue();
    }

    @Test
    @DisplayName("测试 7：标准 Pull Request 草稿规范（PullRequestDraftView）与安全交付摘要生成")
    void testGeneratePullRequestDraft() throws Exception {
        String runId = "run-pr-draft-" + UUID.randomUUID().toString().substring(0, 8);
        createdRunIds.add(runId);

        WorkflowRunEntity run = new WorkflowRunEntity(runId, testProjectId, "def-1", "SUCCEEDED", "key-pr");
        workflowRunRepository.save(run);

        Path codeFile = testWorkspacePath.resolve("Calculator.java");
        Files.writeString(codeFile, "public class Calculator { public int add(int a, int b) { return a + b; } }");
        String baselineHash = gitManager.createBaseline(testWorkspacePath.toFile(), runId, testUserId);

        WorkspaceEntity ws = workspaceRepository.findById(testWorkspaceId).orElseThrow();
        ws.setGitBaselineCommit(baselineHash);
        workspaceRepository.save(ws);

        Files.writeString(codeFile, "public class Calculator {\n    public int add(int a, int b) { return a + b; }\n    public int sub(int a, int b) { return a - b; }\n}");
        String commitHash = gitManager.createStepSnapshot(testWorkspacePath.toFile(), runId, "step-calc", "Add sub method", testUserId).getCommitHash();

        String artId = "art-pr-" + UUID.randomUUID().toString().substring(0, 8);
        createdArtifactIds.add(artId);
        ArtifactEntity art = new ArtifactEntity(
                artId, runId, "step-calc", "CODE_DIFF", commitHash, commitHash,
                "{\"changedFiles\":[\"Calculator.java\"],\"baselineCommit\":\"" + baselineHash + "\"}"
        );
        art.setReviewStatus("ACCEPTED");
        artifactRepository.save(art);

        PullRequestDraftView draft = artifactApplication.generatePullRequestDraft(artId);

        assertThat(draft).isNotNull();
        assertThat(draft.getTitle()).contains("Calculator");
        assertThat(draft.getSourceBranch()).isEqualTo("feature/run-" + runId);
        assertThat(draft.getTargetBranch()).isEqualTo("main");
        assertThat(draft.getQualityGateReport()).isNotNull();
        assertThat(draft.getSummaryMarkdown()).contains("Automated Change Delivery Summary");
        assertThat(draft.getSummaryMarkdown()).contains("Quality Gate Check Matrix");
        assertThat(draft.isCanMergeSafely()).isTrue();
        assertThat(draft.getAuditChecksum()).isNotBlank();
    }

    @Test
    @DisplayName("测试 8：离线可审计证据包完整性导出与全局 SHA-256 校验")
    void testExportOfflineAuditRunEvidenceBundle() throws Exception {
        String runId = "run-evidence-" + UUID.randomUUID().toString().substring(0, 8);
        createdRunIds.add(runId);

        WorkflowRunEntity run = new WorkflowRunEntity(runId, testProjectId, "def-1", "SUCCEEDED", "key-ev");
        workflowRunRepository.save(run);

        Path testFile = testWorkspacePath.resolve("EvidenceTest.java");
        Files.writeString(testFile, "class EvidenceTest {}");
        String commitHash = gitManager.createBaseline(testWorkspacePath.toFile(), runId, testUserId);

        String artId = "art-ev-" + UUID.randomUUID().toString().substring(0, 8);
        createdArtifactIds.add(artId);
        ArtifactEntity art = new ArtifactEntity(artId, runId, null, "SNAPSHOT", commitHash, commitHash, "{}");
        art.setReviewStatus("ACCEPTED");
        artifactRepository.save(art);

        RunEvidenceBundleView bundle = executionApplication.exportRunEvidenceBundle(runId);

        assertThat(bundle).isNotNull();
        assertThat(bundle.getRun().getId()).isEqualTo(runId);
        assertThat(bundle.getProject().getId()).isEqualTo(testProjectId);
        assertThat(bundle.getChecksum()).isNotBlank().hasSize(64);
        assertThat(executionApplication.verifyEvidenceBundleChecksum(bundle)).isTrue();
    }
}
