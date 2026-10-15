package com.agenthub;

import com.agenthub.audit.application.ArtifactApplication;
import com.agenthub.audit.dto.ArtifactLineageView;
import com.agenthub.audit.dto.ArtifactView;
import com.agenthub.audit.dto.StepLineageNode;
import com.agenthub.audit.infrastructure.entity.ArtifactEntity;
import com.agenthub.audit.infrastructure.repository.ArtifactRepository;
import com.agenthub.domain.workspace.model.StructuredDiff;
import com.agenthub.domain.workspace.service.JGitWorkspaceManager;
import com.agenthub.domain.workspace.service.WorkspaceResolver;
import com.agenthub.execution.application.ExecutionApplication;
import com.agenthub.execution.domain.memory.ContextMemoryCompressor;
import com.agenthub.execution.domain.memory.HierarchicalContextMemory;
import com.agenthub.execution.domain.model.CancelTokenRegistry;
import com.agenthub.execution.domain.model.StepRunStatus;
import com.agenthub.execution.domain.model.WorkflowRunStatus;
import com.agenthub.execution.domain.retry.ExecutionErrorClassifier;
import com.agenthub.execution.domain.retry.RetryCategory;
import com.agenthub.execution.domain.state.UnifiedExecutionStateGraph;
import com.agenthub.execution.dto.*;
import com.agenthub.execution.infrastructure.entity.RunEventEntity;
import com.agenthub.execution.infrastructure.entity.StepRunEntity;
import com.agenthub.execution.infrastructure.entity.WorkflowRunEntity;
import com.agenthub.execution.infrastructure.repository.RunEventRepository;
import com.agenthub.execution.infrastructure.repository.StepRunRepository;
import com.agenthub.execution.infrastructure.repository.WorkflowRunRepository;
import com.agenthub.execution.service.ExecutionStateMachine;
import com.agenthub.execution.service.RunEventBroadcaster;
import com.agenthub.identity.infrastructure.entity.UserEntity;
import com.agenthub.identity.infrastructure.repository.UserRepository;
import com.agenthub.orchestration.infrastructure.entity.ApprovalEntity;
import com.agenthub.orchestration.infrastructure.repository.ApprovalRepository;
import com.agenthub.project.application.ProjectApplication;
import com.agenthub.project.application.WorkspaceApplication;
import com.agenthub.project.dto.CreateProjectCommand;
import com.agenthub.project.dto.ProjectView;
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
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 4 验收与防御测试套件：
 * 1. 单一状态图校验（UnifiedExecutionStateGraph）与合法状态跃迁防循环盲退；
 * 2. At-least-once 执行语义、幂等防重入与三级异常分类（ExecutionErrorClassifier）；
 * 3. 事务性发件箱（Transactional Outbox）与严格单调序列 SSE 恢复重放；
 * 4. 三层拓扑分层上下文记忆（HierarchicalContextMemory）与关键不变量保留压缩；
 * 5. 产物全生命周期血缘图谱（Artifact Lineage）、JGit 结构化 Diff 审查与无损回滚防御；
 * 6. 运行运维全景视图（RunOperationsView）与下一步调度建议。
 */
@SpringBootTest
@ActiveProfiles("test")
public class Phase4RunOperationsAndArtifactLineageTest {

    @Autowired
    private ExecutionApplication executionApplication;

    @Autowired
    private ArtifactApplication artifactApplication;

    @Autowired
    private ProjectApplication projectApplication;

    @Autowired
    private WorkspaceApplication workspaceApplication;

    @Autowired
    private RunEventBroadcaster runEventBroadcaster;

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
    private ProjectRepository projectRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WorkspaceResolver workspaceResolver;

    @Autowired
    private JGitWorkspaceManager gitManager;

    @Autowired
    private CancelTokenRegistry cancelTokenRegistry;

    @Autowired
    private ExecutionStateMachine executionStateMachine;

    private String testUserId;
    private String testProjectId;
    private String testWorkspaceId;
    private Path testWorkspaceDir;
    private final List<String> testRunIds = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        testUserId = "usr-p4-" + UUID.randomUUID().toString().substring(0, 8);
        userRepository.save(new UserEntity(
                testUserId,
                testUserId + "@agenthub.test",
                "HashPassword123!",
                "Phase4Operator",
                "ACTIVE"
        ));

        RequestContext.get().setUserId(testUserId);
        RequestContext.get().setUsername(testUserId + "@agenthub.test");

        ProjectView proj = projectApplication.createProject(new CreateProjectCommand(
                "p4-project-" + UUID.randomUUID().toString().substring(0, 6),
                "Phase 4 Delivery Project",
                "."
        ));
        testProjectId = proj.getId();

        WorkspaceEntity ws = workspaceRepository.findByProjectId(testProjectId).orElseThrow();
        testWorkspaceId = ws.getId();
        testWorkspaceDir = workspaceResolver.getWorkspaceRoot(testWorkspaceId);

        // Ensure workspace directory exists and has a git baseline commit
        Files.createDirectories(testWorkspaceDir);
        File initFile = new File(testWorkspaceDir.toFile(), "README.md");
        Files.writeString(initFile.toPath(), "# Phase 4 Baseline\n", StandardCharsets.UTF_8);
        String baselineHash = gitManager.createBaseline(testWorkspaceDir.toFile(), "init-p4", "test@agenthub.local");
        ws.setGitBaselineCommit(baselineHash);
        workspaceRepository.save(ws);
    }

    @AfterEach
    void tearDown() {
        RequestContext.clear();
        for (String rId : testRunIds) {
            try {
                cancelTokenRegistry.get(rId).ifPresent(token -> token.cancel("Test teardown cleanup"));
            } catch (Exception ignored) {}
        }
        try {
            Thread.sleep(100);
        } catch (InterruptedException ignored) {}
        for (String rId : testRunIds) {
            try {
                runEventRepository.deleteAll(runEventRepository.findByRunIdOrderBySequenceNumAsc(rId));
                stepRunRepository.deleteAll(stepRunRepository.findByRunIdOrderByCreatedAtAsc(rId));
                artifactRepository.deleteAll(artifactRepository.findByRunIdOrderByCreatedAtDesc(rId));
                approvalRepository.deleteAll(approvalRepository.findByRunId(rId));
                workflowRunRepository.deleteById(rId);
            } catch (Exception ignored) {}
        }
        testRunIds.clear();

        try {
            if (testWorkspaceId != null) {
                workspaceRepository.deleteById(testWorkspaceId);
            }
            if (testProjectId != null) {
                projectRepository.deleteById(testProjectId);
            }
            if (testUserId != null) {
                userRepository.deleteById(testUserId);
            }
        } catch (Exception ignored) {}

        if (testWorkspaceDir != null && Files.exists(testWorkspaceDir)) {
            try (var stream = Files.walk(testWorkspaceDir)) {
                stream.sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            } catch (Exception ignored) {}
        }
    }

    @Test
    @DisplayName("1. 统一状态图校验与合法状态跃迁防循环盲退")
    void test01_UnifiedStateGraphLegalTransitionsAndBarriers() {
        // Valid transitions for WorkflowRunStatus
        UnifiedExecutionStateGraph.validateWorkflowRunTransition(WorkflowRunStatus.PENDING, WorkflowRunStatus.RUNNING);
        UnifiedExecutionStateGraph.validateWorkflowRunTransition(WorkflowRunStatus.RUNNING, WorkflowRunStatus.PAUSED);
        UnifiedExecutionStateGraph.validateWorkflowRunTransition(WorkflowRunStatus.PAUSED, WorkflowRunStatus.RUNNING);
        UnifiedExecutionStateGraph.validateWorkflowRunTransition(WorkflowRunStatus.RUNNING, WorkflowRunStatus.WAITING_APPROVAL);
        UnifiedExecutionStateGraph.validateWorkflowRunTransition(WorkflowRunStatus.WAITING_APPROVAL, WorkflowRunStatus.RUNNING);
        UnifiedExecutionStateGraph.validateWorkflowRunTransition(WorkflowRunStatus.RUNNING, WorkflowRunStatus.SUCCEEDED);
        UnifiedExecutionStateGraph.validateWorkflowRunTransition(WorkflowRunStatus.RUNNING, WorkflowRunStatus.FAILED);
        UnifiedExecutionStateGraph.validateWorkflowRunTransition(WorkflowRunStatus.RUNNING, WorkflowRunStatus.CANCELLED);
        UnifiedExecutionStateGraph.validateWorkflowRunTransition(WorkflowRunStatus.RUNNING, WorkflowRunStatus.TIMED_OUT);

        // Illegal transitions from terminal states must throw IllegalStateException
        assertThatThrownBy(() -> UnifiedExecutionStateGraph.validateWorkflowRunTransition(WorkflowRunStatus.SUCCEEDED, WorkflowRunStatus.RUNNING))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Illegal workflow run status transition");

        assertThatThrownBy(() -> UnifiedExecutionStateGraph.validateWorkflowRunTransition(WorkflowRunStatus.CANCELLED, WorkflowRunStatus.RUNNING))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Illegal workflow run status transition");

        assertThatThrownBy(() -> UnifiedExecutionStateGraph.validateWorkflowRunTransition(WorkflowRunStatus.FAILED, WorkflowRunStatus.PAUSED))
                .isInstanceOf(IllegalStateException.class);

        // StepRunStatus transitions
        UnifiedExecutionStateGraph.validateStepRunTransition(StepRunStatus.PENDING, StepRunStatus.RUNNING);
        UnifiedExecutionStateGraph.validateStepRunTransition(StepRunStatus.RUNNING, StepRunStatus.SUCCEEDED);
        UnifiedExecutionStateGraph.validateStepRunTransition(StepRunStatus.RUNNING, StepRunStatus.WAITING_APPROVAL);

        assertThatThrownBy(() -> UnifiedExecutionStateGraph.validateStepRunTransition(StepRunStatus.SUCCEEDED, StepRunStatus.RUNNING))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Illegal step run status transition");

        // NextAction advice logic
        ApprovalEntity mockAppr = new ApprovalEntity("appr-1", "dummy-run", "step-1", "PENDING", "system");
        UnifiedExecutionStateGraph.NextActionDetermination waDecision =
                UnifiedExecutionStateGraph.determineNextAction(WorkflowRunStatus.WAITING_APPROVAL, List.of(), List.of(mockAppr));
        assertThat(waDecision.getNextAction()).contains("Waiting for Reviewer");
        assertThat(waDecision.isRequiresHumanIntervention()).isTrue();
        assertThat(waDecision.getPendingDecisions()).isNotEmpty();

        UnifiedExecutionStateGraph.NextActionDetermination pausedDecision =
                UnifiedExecutionStateGraph.determineNextAction(WorkflowRunStatus.PAUSED, List.of(), List.of());
        assertThat(pausedDecision.getNextAction()).contains("resumeRun");
        assertThat(pausedDecision.isRequiresHumanIntervention()).isTrue();

        UnifiedExecutionStateGraph.NextActionDetermination runningDecision =
                UnifiedExecutionStateGraph.determineNextAction(WorkflowRunStatus.RUNNING, List.of(), List.of());
        assertThat(runningDecision.getNextAction()).contains("Active steps");
        assertThat(runningDecision.isRequiresHumanIntervention()).isFalse();

        UnifiedExecutionStateGraph.NextActionDetermination succeededDecision =
                UnifiedExecutionStateGraph.determineNextAction(WorkflowRunStatus.SUCCEEDED, List.of(), List.of());
        assertThat(succeededDecision.getNextAction()).contains("completed successfully");
    }

    @Test
    @DisplayName("2. At-Least-Once 执行语义、幂等防重入与三级异常分类")
    void test02_AtLeastOnceExecutionAndErrorClassification() {
        // A. ExecutionErrorClassifier three-tier categorization
        // 1. Transient / Auto-retryable
        var res1 = ExecutionErrorClassifier.classify(new SocketTimeoutException("connect timed out"));
        assertThat(res1.getCategory()).isEqualTo(RetryCategory.RETRYABLE_TRANSIENT);
        assertThat(res1.isAutoRetryable()).isTrue();

        var res2 = ExecutionErrorClassifier.classify(new IOException("Connection reset by peer"));
        assertThat(res2.getCategory()).isEqualTo(RetryCategory.RETRYABLE_TRANSIENT);

        var res3 = ExecutionErrorClassifier.classify(new RuntimeException("Rate limit 429: Too Many Requests"));
        assertThat(res3.getCategory()).isEqualTo(RetryCategory.RETRYABLE_TRANSIENT);

        // 2. Approval required
        var res4 = ExecutionErrorClassifier.classify(new RuntimeException("Unauthorized tool access: requires supervisor approval"));
        assertThat(res4.getCategory()).isEqualTo(RetryCategory.APPROVAL_REQUIRED);
        assertThat(res4.isAutoRetryable()).isFalse();

        // 3. Fatal non-retryable
        var res5 = ExecutionErrorClassifier.classify(new NullPointerException("Null dereference in business rule engine"));
        assertThat(res5.getCategory()).isEqualTo(RetryCategory.FATAL_NON_RETRYABLE);
        assertThat(res5.isAutoRetryable()).isFalse();

        var res6 = ExecutionErrorClassifier.classify(new IllegalArgumentException("Invalid node definition format"));
        assertThat(res6.getCategory()).isEqualTo(RetryCategory.FATAL_NON_RETRYABLE);

        // B. Idempotency test: duplicate startRun with identical key must return existing run
        String idempotencyKey = "idem-p4-test-" + UUID.randomUUID().toString();
        StartRunCommand cmd = new StartRunCommand(
                testProjectId,
                "wf-enterprise-auth-delivery",
                idempotencyKey,
                null,
                Map.of("testKey", "testVal")
        );

        WorkflowRunView run1 = executionApplication.startRun(cmd);
        testRunIds.add(run1.getId());
        assertThat(run1).isNotNull();
        assertThat(run1.getIdempotencyKey()).isEqualTo(idempotencyKey);

        // Call again with exact same key
        WorkflowRunView run2 = executionApplication.startRun(cmd);
        assertThat(run2.getId()).isEqualTo(run1.getId());

        // Database must have exactly 1 record for this idempotency key
        Optional<WorkflowRunEntity> dbRun = workflowRunRepository.findByIdempotencyKey(idempotencyKey);
        assertThat(dbRun).isPresent();
        assertThat(dbRun.get().getId()).isEqualTo(run1.getId());
    }

    @Test
    @DisplayName("3. 事务性发件箱（Transactional Outbox）与严格单调序列事件重放")
    void test03_TransactionalOutboxAndMonotonicReplay() {
        String runId = "run-outbox-" + UUID.randomUUID().toString().substring(0, 8);
        WorkflowRunEntity runEntity = new WorkflowRunEntity(
                runId,
                testProjectId,
                "wf-outbox-test",
                "RUNNING",
                "idem-outbox-" + UUID.randomUUID()
        );
        workflowRunRepository.save(runEntity);
        testRunIds.add(runId);

        // Broadcast sequential events through RunEventBroadcaster
        RunEventEntity evt1 = runEventBroadcaster.publishEvent(runId, "STEP_STARTED", "{\"nodeId\":\"node-1\"}");
        RunEventEntity evt2 = runEventBroadcaster.publishEvent(runId, "TOOL_INVOKED", "{\"tool\":\"git_commit\"}");
        RunEventEntity evt3 = runEventBroadcaster.publishEvent(runId, "STEP_COMPLETED", "{\"nodeId\":\"node-1\"}");

        // Assert monotonic sequence numbers
        assertThat(evt1.getSequenceNum()).isEqualTo(1L);
        assertThat(evt2.getSequenceNum()).isEqualTo(2L);
        assertThat(evt3.getSequenceNum()).isEqualTo(3L);

        // Verify database persistence
        List<RunEventEntity> allEvents = runEventRepository.findByRunIdOrderBySequenceNumAsc(runId);
        assertThat(allEvents).hasSize(3);
        assertThat(allEvents.get(0).getEventType()).isEqualTo("STEP_STARTED");
        assertThat(allEvents.get(1).getEventType()).isEqualTo("TOOL_INVOKED");
        assertThat(allEvents.get(2).getEventType()).isEqualTo("STEP_COMPLETED");

        // Verify Last-Event-ID replay via listEvents with cursor
        List<RunEventView> replayed = executionApplication.listEvents(runId, 1L);
        assertThat(replayed).hasSize(2);
        assertThat(replayed.get(0).getSequenceNum()).isEqualTo(2L);
        assertThat(replayed.get(1).getSequenceNum()).isEqualTo(3L);

        // Verify query for events after 2
        List<RunEventView> replayedFrom2 = executionApplication.listEvents(runId, 2L);
        assertThat(replayedFrom2).hasSize(1);
        assertThat(replayedFrom2.get(0).getSequenceNum()).isEqualTo(3L);
    }

    @Test
    @DisplayName("4. 三层拓扑分层上下文记忆与关键不变量保留压缩")
    void test04_HierarchicalMemoryAndLosslessCompression() throws Exception {
        String runId = "run-mem-" + UUID.randomUUID().toString().substring(0, 8);

        // Tier 1: Project Memory (Rules, DDD invariants, baseline)
        HierarchicalContextMemory.ProjectMemory projMem = new HierarchicalContextMemory.ProjectMemory(
                testProjectId,
                "RULE-1: Strict DDD four layers; RULE-2: No long RPC in @Transactional; RULE-3: Redisson watchdog lock",
                "commit-baseline-123456",
                "fp-aoci-v1",
                "ALIGNED"
        );

        // Tier 2: Run Memory (Goal, confirmed plan, token budget)
        HierarchicalContextMemory.RunMemory runMem = new HierarchicalContextMemory.RunMemory(
                runId,
                "Deliver production-grade OAuth2 and JGit Structured Diff",
                "wf-enterprise-auth-delivery",
                "plan-conf-999",
                500, // Token budget limit
                200
        );

        HierarchicalContextMemory memory = new HierarchicalContextMemory(projMem, runMem);

        // Tier 3: Step Items with large raw output and invariant metadata
        HierarchicalContextMemory.StepMemoryItem step1 = new HierarchicalContextMemory.StepMemoryItem(
                "step-1", "node-arch", "SUCCEEDED", 1, 10
        );
        step1.setToolsInvoked(List.of("read_file", "generate_dsl"));
        step1.setModifiedFiles(List.of("src/main/AuthArchitecture.java"));
        step1.setRawOutput("Extremely verbose log data that simulates hundreds of lines of raw terminal stream...".repeat(20));

        HierarchicalContextMemory.StepMemoryItem step2 = new HierarchicalContextMemory.StepMemoryItem(
                "step-2", "node-qa", "SUCCEEDED", 11, 20
        );
        step2.setToolsInvoked(List.of("run_tests", "jgit_diff"));
        step2.setModifiedFiles(List.of("src/test/AuthTest.java"));
        step2.setApprovalDecision("APPROVED_BY_LEAD");
        step2.setRawOutput("Verbose test runner stack traces and stdout snippets...".repeat(20));

        memory.addStepMemory(step1);
        memory.addStepMemory(step2);

        int initialTokens = ContextMemoryCompressor.estimateTokens(memory);
        assertThat(initialTokens).isGreaterThan(500);

        // Perform lossless compression when budget is exceeded
        ContextMemoryCompressor.CompressionReport report =
                ContextMemoryCompressor.compressIfNeeded(memory, 200);

        assertThat(report.getStepsCompressed()).isEqualTo(2);
        assertThat(report.getCompressedTokens()).isLessThan(initialTokens);

        // Verify invariant preservation: Project rules, tools, files, and approvals are NOT lost
        assertThat(projMem.getProjectRules()).contains("Strict DDD four layers");
        assertThat(step1.isCompressed()).isTrue();
        assertThat(step1.getSummary()).contains("src/main/AuthArchitecture.java");
        assertThat(step1.getRawOutput()).isNull(); // Verbose raw output dropped

        assertThat(step2.isCompressed()).isTrue();
        assertThat(step2.getApprovalDecision()).isEqualTo("APPROVED_BY_LEAD");
        assertThat(step2.getSummary()).contains("src/test/AuthTest.java");

        // Verify cognitive resumption receipt checking for both clean and drifted states
        Path tempGitDir = Files.createTempDirectory("receipt-test-git");
        try {
            gitManager.initWorkspace(tempGitDir.toFile());
            var cleanReceipt = ContextMemoryCompressor.verifyCognitionAndReceipt(tempGitDir.toFile(), "baseline-sha");
            assertThat(cleanReceipt).isNotNull();
            assertThat(cleanReceipt.getStatus()).isEqualTo("CLEAN");
            assertThat(cleanReceipt.isClean()).isTrue();

            // Introduce untracked drift
            Files.writeString(tempGitDir.resolve("untracked.txt"), "drift content");
            var driftedReceipt = ContextMemoryCompressor.verifyCognitionAndReceipt(tempGitDir.toFile(), "baseline-sha");
            assertThat(driftedReceipt).isNotNull();
            assertThat(driftedReceipt.getStatus()).isEqualTo("DRIFTED");
            assertThat(driftedReceipt.isClean()).isFalse();
            assertThat(driftedReceipt.getUntrackedFiles()).contains("untracked.txt");
        } finally {
            try (var stream = Files.walk(tempGitDir)) {
                stream.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
            } catch (Exception ignored) {}
        }
    }

    @Test
    @DisplayName("5. 产物全生命周期血缘图谱、JGit 结构化 Diff 审查与无损回滚防御")
    void test05_ArtifactLineageStructuredJGitDiffAndNonDestructiveRevert() throws Exception {
        String runId = "run-art-" + UUID.randomUUID().toString().substring(0, 8);
        WorkflowRunEntity runEntity = new WorkflowRunEntity(
                runId,
                testProjectId,
                "wf-artifact-lineage",
                "RUNNING",
                "idem-art-" + UUID.randomUUID()
        );
        workflowRunRepository.save(runEntity);
        testRunIds.add(runId);

        String stepRunId = "step-run-modify-file";
        StepRunEntity stepEntity = new StepRunEntity(
                stepRunId,
                runId,
                "node-coder",
                "SUCCEEDED"
        );
        stepEntity.setAttempt(1);
        stepRunRepository.save(stepEntity);

        // 1. Agent modifies a file in the workspace
        File modifiedFile = new File(testWorkspaceDir.toFile(), "SecurityConfig.java");
        Files.writeString(modifiedFile.toPath(), "public class SecurityConfig { /* V1 patch */ }\n", StandardCharsets.UTF_8);
        var snapResult = gitManager.createStepSnapshot(testWorkspaceDir.toFile(), runId, stepRunId, "Commit step 1: Add SecurityConfig", "test@agenthub.local");
        String step1CommitHash = snapResult.getCommitHash();

        // 2. Save Artifact representing this step's commit snapshot
        String artifactId = "art-snap-" + UUID.randomUUID().toString().substring(0, 8);
        ArtifactEntity artifact = new ArtifactEntity(
                artifactId,
                runId,
                stepRunId,
                "SNAPSHOT",
                step1CommitHash,
                "sha256-mock-checksum",
                "{\"commitHash\":\"" + step1CommitHash + "\",\"changedFiles\":[\"SecurityConfig.java\"]}"
        );
        artifactRepository.save(artifact);

        // 3. Query Artifact Lineage
        ArtifactLineageView lineage = artifactApplication.getArtifactLineageByRunId(runId);
        assertThat(lineage).isNotNull();
        assertThat(lineage.getRunId()).isEqualTo(runId);
        assertThat(lineage.getArtifacts()).hasSize(1);
        assertThat(lineage.getStepNodes()).isNotEmpty();
        StepLineageNode node = lineage.getStepNodes().get(0);
        assertThat(node.getArtifactId()).isEqualTo(artifactId);
        assertThat(node.getCommitHash()).isEqualTo(step1CommitHash);

        // 4. Query Structured JGit Diff
        StructuredDiff diff = artifactApplication.getArtifactDiff(artifactId);
        assertThat(diff).isNotNull();
        assertThat(diff.getTotalFilesChanged()).isGreaterThanOrEqualTo(1);
        assertThat(diff.getEntries()).anyMatch(e -> e.getNewPath().contains("SecurityConfig.java"));

        // 5. Create an independent file in the workspace before revert
        File unrelatedFile = new File(testWorkspaceDir.toFile(), "unrelated_user_work.txt");
        Files.writeString(unrelatedFile.toPath(), "Independent user manual work\n", StandardCharsets.UTF_8);

        // 6. Execute Non-Destructive Revert
        ArtifactView reverted = artifactApplication.revertArtifact(artifactId, testUserId);
        assertThat(reverted.getReviewStatus()).isEqualTo("REVERTED");

        // Verify non-destructive rollback:
        // - SecurityConfig.java created in this step is safely removed/reverted to baseline
        assertThat(Files.exists(modifiedFile.toPath())).isFalse();

        // - unrelated_user_work.txt MUST REMAIN UNTOUCHED (Strict proof against destructive git reset --hard!)
        assertThat(Files.exists(unrelatedFile.toPath())).isTrue();
        assertThat(Files.readString(unrelatedFile.toPath())).isEqualTo("Independent user manual work\n");

        // Verify audit event emitted in run_events
        List<RunEventEntity> auditEvents = runEventRepository.findByRunIdOrderBySequenceNumAsc(runId);
        assertThat(auditEvents).anyMatch(e -> "ARTIFACT_REVERTED".equalsIgnoreCase(e.getEventType()));
    }

    @Test
    @DisplayName("6. 运行运维全景视图（RunOperationsView）与决策审计建议")
    void test06_RunOperationsQueryEndpointAndDecisionAudit() {
        String runId = "run-ops-" + UUID.randomUUID().toString().substring(0, 8);
        WorkflowRunEntity runEntity = new WorkflowRunEntity(
                runId,
                testProjectId,
                "wf-ops-dashboard",
                "WAITING_APPROVAL",
                "idem-ops-" + UUID.randomUUID()
        );
        runEntity.setLeaseOwner("worker-node-1");
        runEntity.setLeaseUntil(LocalDateTime.now().plusMinutes(5));
        workflowRunRepository.save(runEntity);
        testRunIds.add(runId);

        String stepRunId = "step-gate-1";
        StepRunEntity stepEntity = new StepRunEntity(
                stepRunId,
                runId,
                "node-security-gate",
                "WAITING_APPROVAL"
        );
        stepEntity.setAttempt(1);
        stepEntity.setErrorMessage("Security architectural approval required for deploy");
        stepEntity.setOutputRef("art-ref-1");
        stepRunRepository.save(stepEntity);

        ApprovalEntity approval = new ApprovalEntity(
                "appr-1",
                runId,
                stepRunId,
                "PENDING",
                "OrchestratorKernel"
        );
        approval.setComments("Review before container launch");
        approvalRepository.save(approval);

        // Query RunOperationsView
        RunOperationsView ops = executionApplication.getRunOperations(runId);
        assertThat(ops).isNotNull();
        assertThat(ops.getWorkflowRun()).isNotNull();
        assertThat(ops.getWorkflowRun().getId()).isEqualTo(runId);
        assertThat(ops.getStepRuns()).hasSize(1);
        assertThat(ops.getUnifiedStatus()).isEqualTo("WAITING_APPROVAL");
        assertThat(ops.getNextAction()).contains("Waiting for Reviewer");
        assertThat(ops.isRequiresHumanIntervention()).isTrue();

        // Pending Decisions check
        assertThat(ops.getPendingDecisions()).hasSize(1);
        PendingDecisionItem decision = ops.getPendingDecisions().get(0);
        assertThat(decision.getNodeName()).isEqualTo("node-security-gate");
        assertThat(decision.getDecisionType()).isEqualTo("APPROVAL_GATE");

        // Memory Summary check
        assertThat(ops.getMemorySummary()).isNotNull();
        assertThat(ops.getMemorySummary().getTotalTiers()).isEqualTo(3);
        assertThat(ops.getMemorySummary().getAociCognitionStatus()).isEqualTo("ALIGNED");

        // Lease Status check
        assertThat(ops.getLeaseStatus()).isNotNull();
        assertThat(ops.getLeaseStatus().getLeaseOwner()).isEqualTo("worker-node-1");
        assertThat(ops.getLeaseStatus().isExpired()).isFalse();

        // Tool side effects view
        assertThat(ops.getToolSideEffects()).isNotEmpty();
        assertThat(ops.getToolSideEffects().get(0).getToolName()).isEqualTo("node-security-gate");
    }

    @Test
    @DisplayName("7. 状态机严格委托校验、工具授权异常分类归因与产物元数据鲁棒性")
    void test07_ExecutionStateMachineStrictEnforcementAndMetadataRobustness() {
        String runId = "run-sm-" + UUID.randomUUID().toString().substring(0, 8);
        WorkflowRunEntity runEntity = new WorkflowRunEntity(
                runId,
                testProjectId,
                "wf-state-machine-test",
                "PENDING",
                "idem-sm-" + UUID.randomUUID()
        );
        workflowRunRepository.save(runEntity);
        testRunIds.add(runId);

        String stepRunId = "step-sm-1";
        StepRunEntity stepEntity = new StepRunEntity(
                stepRunId,
                runId,
                "node-coder-sm",
                "PENDING"
        );
        stepRunRepository.save(stepEntity);

        // A. Verify legal WorkflowRun transitions via ExecutionStateMachine
        executionStateMachine.transitionRun(runId, WorkflowRunStatus.RUNNING, "Run started");
        assertThat(workflowRunRepository.findById(runId).orElseThrow().getStatus()).isEqualTo("RUNNING");

        executionStateMachine.transitionRun(runId, WorkflowRunStatus.WAITING_APPROVAL, "Approval needed");
        assertThat(workflowRunRepository.findById(runId).orElseThrow().getStatus()).isEqualTo("WAITING_APPROVAL");

        // Legal: WAITING_APPROVAL -> SUCCEEDED or WAITING_APPROVAL -> RUNNING
        executionStateMachine.transitionRun(runId, WorkflowRunStatus.RUNNING, "Approved to continue");
        assertThat(workflowRunRepository.findById(runId).orElseThrow().getStatus()).isEqualTo("RUNNING");

        executionStateMachine.transitionRun(runId, WorkflowRunStatus.SUCCEEDED, "Run finished");
        assertThat(workflowRunRepository.findById(runId).orElseThrow().getStatus()).isEqualTo("SUCCEEDED");

        // Illegal: SUCCEEDED -> RUNNING must throw BusinessException(ErrorCode.INVALID_STATE_TRANSITION)
        assertThatThrownBy(() -> executionStateMachine.transitionRun(runId, WorkflowRunStatus.RUNNING, "Illegal attempt"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Illegal state transition for run");

        // B. Verify legal StepRun transitions via ExecutionStateMachine
        executionStateMachine.transitionStep(stepRunId, StepRunStatus.RUNNING, "Step starting");
        assertThat(stepRunRepository.findById(stepRunId).orElseThrow().getStatus()).isEqualTo("RUNNING");

        // Legal: RUNNING -> SUCCEEDED
        executionStateMachine.transitionStep(stepRunId, StepRunStatus.SUCCEEDED, "Step finished");
        assertThat(stepRunRepository.findById(stepRunId).orElseThrow().getStatus()).isEqualTo("SUCCEEDED");

        // Illegal: SUCCEEDED is terminal -> RUNNING
        assertThatThrownBy(() -> executionStateMachine.transitionStep(stepRunId, StepRunStatus.RUNNING, "Illegal attempt"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Illegal state transition for step");

        // C. Verify ExecutionErrorClassifier for tool permission exceptions
        BusinessException toolDeniedEx = new BusinessException(ErrorCode.FORBIDDEN, "Plan requires user confirmation before invoking high-impact tool git_push");
        var classified = ExecutionErrorClassifier.classify(toolDeniedEx);
        assertThat(classified.getCategory()).isEqualTo(RetryCategory.APPROVAL_REQUIRED);
        assertThat(classified.isAutoRetryable()).isFalse();

        // D. Verify Artifact metadata extraction for changedFiles and checksums
        String artifactId = "art-meta-" + UUID.randomUUID().toString().substring(0, 8);
        ArtifactEntity artWithAltFields = new ArtifactEntity(
                artifactId,
                runId,
                stepRunId,
                "SNAPSHOT",
                "hash-alt-1234",
                "sha-alt-chk",
                "{\"commitHash\":\"hash-alt-1234\",\"changedFiles\":[\"src/Main.java\",\"config.yaml\"],\"checksums\":{\"src/Main.java\":\"abc1\",\"config.yaml\":\"def2\"}}"
        );
        artifactRepository.save(artWithAltFields);

        ArtifactLineageView lineage = artifactApplication.getArtifactLineageByRunId(runId);
        assertThat(lineage).isNotNull();
        StepLineageNode lineageNode = lineage.getStepNodes().stream()
                .filter(n -> stepRunId.equals(n.getStepRunId()))
                .findFirst().orElseThrow();
        assertThat(lineageNode.getChangedFiles()).containsExactlyInAnyOrder("src/Main.java", "config.yaml");
        assertThat(lineageNode.getFileChecksums()).containsEntry("src/Main.java", "abc1");
        assertThat(lineageNode.getFileChecksums()).containsEntry("config.yaml", "def2");
    }
}
