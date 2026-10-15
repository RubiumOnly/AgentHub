package com.agenthub;

import com.agenthub.audit.application.ArtifactApplication;
import com.agenthub.audit.domain.quality.QualityGateEngine;
import com.agenthub.audit.domain.quality.QualityGateReport;
import com.agenthub.audit.domain.quality.QualityGateStatus;
import com.agenthub.audit.dto.ArtifactLineageView;
import com.agenthub.audit.dto.PullRequestDraftView;
import com.agenthub.audit.infrastructure.entity.ArtifactEntity;
import com.agenthub.audit.infrastructure.repository.ArtifactRepository;
import com.agenthub.domain.workspace.service.JGitWorkspaceManager;
import com.agenthub.domain.workspace.service.WorkspaceResolver;
import com.agenthub.execution.application.ExecutionApplication;
import com.agenthub.execution.domain.memory.ContextMemoryCompressor;
import com.agenthub.execution.domain.memory.HierarchicalContextMemory;
import com.agenthub.execution.domain.model.CancelTokenRegistry;
import com.agenthub.execution.domain.model.StepRunStatus;
import com.agenthub.execution.domain.model.WorkflowRunStatus;
import com.agenthub.execution.dto.RunEvidenceBundleView;
import com.agenthub.execution.dto.StartRunCommand;
import com.agenthub.execution.dto.WorkflowRunView;
import com.agenthub.execution.infrastructure.entity.RunEventEntity;
import com.agenthub.execution.infrastructure.entity.StepRunEntity;
import com.agenthub.execution.infrastructure.entity.WorkflowRunEntity;
import com.agenthub.execution.infrastructure.repository.RunEventRepository;
import com.agenthub.execution.infrastructure.repository.StepRunRepository;
import com.agenthub.execution.infrastructure.repository.WorkflowRunRepository;
import com.agenthub.execution.service.ExecutionStateMachine;
import com.agenthub.execution.service.RunEventBroadcaster;
import com.agenthub.identity.infrastructure.entity.UserEntity;
import com.agenthub.identity.infrastructure.repository.AuthAuditLogRepository;
import com.agenthub.identity.infrastructure.repository.UserRepository;
import com.agenthub.ops.application.OpsApplication;
import com.agenthub.orchestration.application.ApprovalApplication;
import com.agenthub.orchestration.application.OrchestratorPlanApplication;
import com.agenthub.orchestration.application.WorkflowOrchestrationApplication;
import com.agenthub.orchestration.domain.plan.OrchestrationPlanStatus;
import com.agenthub.orchestration.domain.template.WorkflowTemplateType;
import com.agenthub.orchestration.domain.template.WorkflowTemplateView;
import com.agenthub.orchestration.dto.ApprovalDecisionCommand;
import com.agenthub.orchestration.dto.ApprovalView;
import com.agenthub.orchestration.infrastructure.entity.ApprovalEntity;
import com.agenthub.orchestration.dto.ConfirmPlanCommand;
import com.agenthub.orchestration.dto.GeneratePlanCommand;
import com.agenthub.orchestration.dto.OrchestrationPlanView;
import com.agenthub.orchestration.infrastructure.repository.ApprovalRepository;
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
import com.agenthub.shared.contract.ApiContractHeaders;
import com.agenthub.shared.contract.sdk.AgentHubReleaseManifest;
import com.agenthub.shared.contract.webhook.WebhookEventType;
import com.agenthub.shared.contract.webhook.WebhookPayload;
import com.agenthub.shared.contract.webhook.WebhookSignatureVerifier;
import com.agenthub.shared.exception.BusinessException;
import com.agenthub.shared.exception.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 8: 端到端验收、性能基准与开源/交付就绪（E2E Verification, Benchmarks & Release Readiness）
 * 自动化测试套件。
 * 覆盖：
 * 1. 软件交付全生命周期端到端集成校验（10 阶段闭环）：
 *    Project Onboarding -> Workspace JGit Baseline -> WorkflowTemplate -> Orchestrator Plan & Confirmation
 *    -> Agent Invocation & DAG Execution -> Quality Gate 5-Dimension Evaluation -> Pull Request Draft
 *    -> Controlled Deployment Admission & Active Health Probe -> Audit Log & Lineage -> Evidence Bundle Export;
 * 2. 端到端性能基准（Execution Throughput, Concurrency Latency P50/P95/P99, 防御性回归断言）；
 * 3. SSE 实时事件流广播与游标重放性能基准（Monotonic Sequence, Last-Event-ID Replay, P95 广播延迟）；
 * 4. 负载下 JVM 堆内存与资源边界防御性评测（Zero Runaway Memory Leaks, Bounded Context Assembly）；
 * 5. Webhook 交付合同与 HMAC-SHA256 防篡改/防重放签名验证（X-AgentHub-Signature）；
 * 6. 产品稳定版 Release Manifest 与最小 SDK 客户端合同一致性校验；
 * 7. 受控沙箱准入门禁对 GATE_FAILED / REJECTED 产物的严格阻断防御；
 * 8. 测试隔离与级联清理铁律（Zero Test Pollution）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class Phase8PerformanceBenchmarkAndE2ETest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProjectApplication projectApplication;

    @Autowired
    private ExecutionApplication executionApplication;

    @Autowired
    private ArtifactApplication artifactApplication;

    @Autowired
    private ApprovalApplication approvalApplication;

    @Autowired
    private DeploymentApplication deploymentApplication;

    @Autowired
    private OpsApplication opsApplication;

    @Autowired
    private WorkflowOrchestrationApplication workflowApplication;

    @Autowired
    private OrchestratorPlanApplication planApplication;

    @Autowired
    private ExecutionStateMachine executionStateMachine;

    @Autowired
    private JGitWorkspaceManager gitManager;

    @Autowired
    private WorkspaceResolver workspaceResolver;

    @Autowired
    private RunEventBroadcaster runEventBroadcaster;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private WorkflowRunRepository workflowRunRepository;

    @Autowired
    private CancelTokenRegistry cancelTokenRegistry;

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

    @Autowired
    private AuthAuditLogRepository authAuditLogRepository;

    private final List<String> createdUserIds = new CopyOnWriteArrayList<>();
    private final List<String> createdProjectIds = new CopyOnWriteArrayList<>();
    private final List<String> createdWorkspaceIds = new CopyOnWriteArrayList<>();
    private final List<String> createdRunIds = new CopyOnWriteArrayList<>();
    private final List<String> createdArtifactIds = new CopyOnWriteArrayList<>();
    private final List<String> createdDeploymentIds = new CopyOnWriteArrayList<>();
    private final List<Path> createdWorkspacePaths = new CopyOnWriteArrayList<>();
    private final List<CompletableFuture<?>> activeFutures = new CopyOnWriteArrayList<>();

    private String testUserId;
    private String testProjectId;
    private String testWorkspaceId;
    private Path testWorkspacePath;

    @BeforeEach
    void setUp() throws Exception {
        testUserId = "usr-p8-" + UUID.randomUUID().toString().substring(0, 8);
        createdUserIds.add(testUserId);
        UserEntity user = new UserEntity(testUserId, "Tester Phase8", testUserId + "@test.local", "secret-p8", "ADMIN");
        userRepository.save(user);
        RequestContext.get().setUserId(testUserId);

        CreateProjectCommand cmd = new CreateProjectCommand("Proj P8 " + testUserId, "Phase 8 Release Readiness", null);
        ProjectView proj = projectApplication.createProject(cmd);
        testProjectId = proj.getId();
        createdProjectIds.add(testProjectId);

        WorkspaceEntity ws = workspaceRepository.findByProjectId(testProjectId).orElseThrow();
        testWorkspaceId = ws.getId();
        createdWorkspaceIds.add(testWorkspaceId);

        testWorkspacePath = workspaceResolver.getWorkspaceRoot(testWorkspaceId);
        createdWorkspacePaths.add(testWorkspacePath);
        gitManager.initWorkspace(testWorkspacePath.toFile());
    }

    @AfterEach
    void tearDown() {
        // 1. Cancel all active run tokens and futures
        for (String runId : createdRunIds) {
            try {
                cancelTokenRegistry.get(runId).ifPresent(token -> token.cancel("Test teardown cleanup"));
            } catch (Exception ignored) {}
        }
        for (CompletableFuture<?> future : activeFutures) {
            try {
                if (future != null && !future.isDone()) {
                    future.cancel(true);
                }
            } catch (Exception ignored) {}
        }
        activeFutures.clear();

        // 2. Cascade cleanup deployments
        for (String depId : createdDeploymentIds) {
            try {
                deploymentApplication.stopDeployment(depId);
            } catch (Exception ignored) {}
            try {
                deploymentRepository.deleteById(depId);
            } catch (Exception ignored) {}
        }
        createdDeploymentIds.clear();

        // 3. Cascade cleanup artifacts
        for (String artId : createdArtifactIds) {
            try {
                artifactRepository.deleteById(artId);
            } catch (Exception ignored) {}
        }
        createdArtifactIds.clear();

        // 4. Cascade cleanup runs, steps, events, and approvals
        for (String runId : createdRunIds) {
            try {
                approvalRepository.deleteAll(approvalRepository.findByRunId(runId));
                stepRunRepository.deleteAll(stepRunRepository.findByRunIdOrderByCreatedAtAsc(runId));
                runEventRepository.deleteAll(runEventRepository.findByRunIdOrderBySequenceNumAsc(runId));
                artifactRepository.deleteAll(artifactRepository.findByRunIdOrderByCreatedAtDesc(runId));
                workflowRunRepository.deleteById(runId);
            } catch (Exception ignored) {}
        }
        createdRunIds.clear();

        // 5. Cascade cleanup workspaces and projects
        for (String wsId : createdWorkspaceIds) {
            try {
                workspaceRepository.deleteById(wsId);
            } catch (Exception ignored) {}
        }
        createdWorkspaceIds.clear();

        for (String projId : createdProjectIds) {
            try {
                projectRepository.deleteById(projId);
            } catch (Exception ignored) {}
        }
        createdProjectIds.clear();

        // 6. Cascade cleanup users
        for (String uId : createdUserIds) {
            try {
                userRepository.deleteById(uId);
            } catch (Exception ignored) {}
        }
        createdUserIds.clear();

        // 7. Cleanup physical workspace directories
        for (Path p : createdWorkspacePaths) {
            if (p != null && Files.exists(p)) {
                try {
                    org.springframework.util.FileSystemUtils.deleteRecursively(p);
                } catch (Exception ignored) {}
            }
        }
        createdWorkspacePaths.clear();

        RequestContext.clear();
        org.eclipse.jgit.lib.RepositoryCache.clear();
    }

    @Test
    @DisplayName("测试 1：软件交付全生命周期自动化 E2E 验证（10 阶段全流程闭环）")
    void test1_fullProductLifecycleAutomatedE2E() throws Exception {
        // -------------------------------------------------------------
        // Stage 1: Project Onboarding & Workspace Provisioning
        // -------------------------------------------------------------
        assertThat(testProjectId).isNotBlank();
        assertThat(testWorkspaceId).isNotBlank();
        assertThat(Files.isDirectory(testWorkspacePath)).isTrue();

        // -------------------------------------------------------------
        // Stage 2: Workspace JGit Baseline Verification
        // -------------------------------------------------------------
        Path readmeFile = testWorkspacePath.resolve("README.md");
        assertThat(Files.exists(readmeFile)).isTrue();
        String readmeContent = Files.readString(readmeFile);
        assertThat(readmeContent).contains("# AgentHub Project Workspace");

        Path rulesDir = testWorkspacePath.resolve(".agenthub");
        Files.createDirectories(rulesDir);
        Files.writeString(rulesDir.resolve("rules.md"), "# AgentHub Project System Rules\nStrict safety invariants apply.");

        String baselineCommit = gitManager.createBaseline(testWorkspacePath.toFile(), "run-init-baseline", testUserId);
        assertThat(baselineCommit).isNotBlank().hasSize(40);

        WorkspaceEntity wsEntity = workspaceRepository.findById(testWorkspaceId).orElseThrow();
        wsEntity.setGitBaselineCommit(baselineCommit);
        workspaceRepository.save(wsEntity);

        // -------------------------------------------------------------
        // Stage 3: WorkflowTemplate Instantiation (BUG_FIX_WITH_TESTS)
        // -------------------------------------------------------------
        List<WorkflowTemplateView> templates = workflowApplication.listTemplates();
        assertThat(templates).isNotEmpty();
        WorkflowTemplateView bugFixTemplate = templates.stream()
                .filter(t -> t.getTemplateType() == WorkflowTemplateType.BUG_FIX_WITH_TESTS)
                .findFirst()
                .orElseThrow();
        assertThat(bugFixTemplate.getDsl().getNodes()).hasSize(7);
        assertThat(bugFixTemplate.getStages()).isNotEmpty();

        // -------------------------------------------------------------
        // Stage 4: Orchestrator Plan Generation & User Confirmation
        // -------------------------------------------------------------
        GeneratePlanCommand genPlanCmd = new GeneratePlanCommand(
                testProjectId,
                testWorkspaceId,
                WorkflowTemplateType.BUG_FIX_WITH_TESTS.name(),
                "Fix high-concurrency race condition in state machine with unit test verification"
        );
        OrchestrationPlanView plan = planApplication.generatePlan(genPlanCmd);
        assertThat(plan.getPlanId()).startsWith("plan-");
        assertThat(plan.getStatus()).isEqualTo(OrchestrationPlanStatus.GENERATED);
        assertThat(plan.isUserConfirmed()).isFalse();

        // Confirm plan with required tool permissions
        ConfirmPlanCommand confirmPlanCmd = new ConfirmPlanCommand(
                true,
                List.of("file_read", "file_tree", "file_write", "command_exec", "git_operation", "code_analyze"),
                "Authorized by project tech lead for Phase 8 E2E verification"
        );
        OrchestrationPlanView confirmedPlan = planApplication.confirmPlan(plan.getPlanId(), confirmPlanCmd);
        assertThat(confirmedPlan.isUserConfirmed()).isTrue();
        assertThat(confirmedPlan.getStatus()).isEqualTo(OrchestrationPlanStatus.CONFIRMED);

        // -------------------------------------------------------------
        // Stage 5: Agent Invocation & WorkflowRun Execution
        // -------------------------------------------------------------
        StartRunCommand startRunCmd = new StartRunCommand(
                testProjectId, null, "idem-e2e-" + UUID.randomUUID()
        );
        WorkflowRunView runView = executionApplication.startRun(startRunCmd);
        assertThat(runView.getId()).isNotBlank();
        String actualRunId = runView.getId();
        createdRunIds.add(actualRunId);

        // Simulate code changes in workspace during agent execution
        Path srcFile = testWorkspacePath.resolve("src/main/ConcurrentGuard.java");
        Files.createDirectories(srcFile.getParent());
        Files.writeString(srcFile, "package com.example;\npublic class ConcurrentGuard {\n    public boolean safe() { return true; }\n}\n");

        Path testFile = testWorkspacePath.resolve("src/test/ConcurrentGuardTest.java");
        Files.createDirectories(testFile.getParent());
        Files.writeString(testFile, "package com.example;\npublic class ConcurrentGuardTest {\n    public void testSafe() {}\n}\n");

        String snapshotCommit = gitManager.createStepSnapshot(
                testWorkspacePath.toFile(), actualRunId, "step-impl-1", "Implement ConcurrentGuard and unit tests", testUserId
        ).getCommitHash();
        assertThat(snapshotCommit).isNotBlank().hasSize(40);

        // Transition run to WAITING_APPROVAL
        WorkflowRunEntity runEntity = workflowRunRepository.findById(actualRunId).orElseThrow();
        runEntity.setStatus(WorkflowRunStatus.WAITING_APPROVAL.name());
        workflowRunRepository.save(runEntity);

        // Create approval request
        String apprId = "appr-e2e-" + UUID.randomUUID().toString().substring(0, 8);
        ApprovalEntity approvalEntity = new ApprovalEntity(apprId, actualRunId, "step-impl-1", "PENDING", testUserId);
        approvalRepository.save(approvalEntity);

        // Approve
        ApprovalView approved = approvalApplication.decide(apprId, new ApprovalDecisionCommand("APPROVED", "Verified test coverage"));
        assertThat(approved.getStatus()).isEqualTo("APPROVED");

        // Mark run SUCCEEDED
        runEntity.setStatus(WorkflowRunStatus.SUCCEEDED.name());
        workflowRunRepository.save(runEntity);

        // -------------------------------------------------------------
        // Stage 6: Quality Gate 5-Dimension Verification
        // -------------------------------------------------------------
        String artId = "art-e2e-" + UUID.randomUUID().toString().substring(0, 8);
        createdArtifactIds.add(artId);
        ArtifactEntity artEntity = new ArtifactEntity(
                artId, actualRunId, "step-impl-1", "CODE_DIFF", snapshotCommit, snapshotCommit,
                "{\"changedFiles\":[\"src/main/ConcurrentGuard.java\",\"src/test/ConcurrentGuardTest.java\"],\"baselineCommit\":\"" + baselineCommit + "\"}"
        );
        artEntity.setReviewStatus("PENDING");
        artifactRepository.save(artEntity);

        QualityGateReport qgReport = artifactApplication.evaluateQualityGate(artId);
        assertThat(qgReport).isNotNull();
        assertThat(qgReport.getOverallStatus()).isEqualTo(QualityGateStatus.PASSED);
        assertThat(qgReport.getCompositeScore()).isGreaterThanOrEqualTo(85);
        assertThat(qgReport.getSha256Fingerprint()).isNotBlank().hasSize(64);

        // -------------------------------------------------------------
        // Stage 7: Pull Request Draft Generation
        // -------------------------------------------------------------
        PullRequestDraftView prDraft = artifactApplication.generatePullRequestDraft(artId);
        assertThat(prDraft).isNotNull();
        assertThat(prDraft.getTitle()).contains("ConcurrentGuard");
        assertThat(prDraft.getChangedFiles()).contains("src/main/ConcurrentGuard.java", "src/test/ConcurrentGuardTest.java");
        assertThat(prDraft.getQualityGateReport().getOverallStatus()).isEqualTo(QualityGateStatus.PASSED);
        assertThat(prDraft.getSummaryMarkdown()).contains("### Quality Gate Check Matrix");

        // -------------------------------------------------------------
        // Stage 8: Controlled Deployment Admission & Active Health Probe
        // -------------------------------------------------------------
        Path indexPath = testWorkspacePath.resolve("index.html");
        Files.writeString(indexPath, "<!DOCTYPE html><html><body>AgentHub Phase 8 E2E Live Preview</body></html>");

        CreateDeploymentRequest depReq = new CreateDeploymentRequest();
        depReq.setProjectId(testProjectId);
        depReq.setArtifactId(artId);
        depReq.setTarget("STATIC_PREVIEW");
        depReq.setTemplateType("STATIC_WEB_TEMPLATE");
        depReq.setSandboxType("LOCAL_PROCESS");

        DeploymentResponse depResp = deploymentApplication.createAndDeploy(depReq);
        assertThat(depResp).isNotNull();
        createdDeploymentIds.add(depResp.getId());
        assertThat(depResp.getStatus()).isEqualTo(DeploymentStatus.RUNNING.name());

        DeploymentResponse probedResp = deploymentApplication.probeDeployment(depResp.getId());
        assertThat(probedResp).isNotNull();
        assertThat(probedResp.isHasLogs()).isTrue();
        assertThat(probedResp.getProbeLatencyMs()).isNotNull();

        // -------------------------------------------------------------
        // Stage 9: Security Audit Log & Artifact Lineage Verification
        // -------------------------------------------------------------
        ArtifactLineageView lineage = artifactApplication.getArtifactLineageByRunId(actualRunId);
        assertThat(lineage).isNotNull();
        assertThat(lineage.getRunId()).isEqualTo(actualRunId);
        assertThat(lineage.getBaselineCommitHash()).isEqualTo(baselineCommit);
        assertThat(lineage.getArtifacts()).isNotEmpty();

        // -------------------------------------------------------------
        // Stage 10: Run Evidence Bundle Export & Checksum Verification
        // -------------------------------------------------------------
        RunEvidenceBundleView evidence = executionApplication.exportRunEvidenceBundle(actualRunId);
        assertThat(evidence).isNotNull();
        assertThat(evidence.getBundleId()).isNotBlank();
        assertThat(evidence.getChecksum()).isNotBlank().hasSize(64);
        assertThat(executionApplication.verifyEvidenceBundleChecksum(evidence)).isTrue();
    }

    @Test
    @DisplayName("测试 2：执行吞吐量与并发延迟基准（Execution Throughput & Concurrency Latency P50/P95/P99）")
    void test2_executionThroughputAndConcurrencyLatencyBenchmark() throws Exception {
        int concurrency = 12;
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(concurrency);

        List<Long> latenciesMs = new CopyOnWriteArrayList<>();
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < concurrency; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    long start = System.nanoTime();

                    // Perform atomic state machine transitions and persistence under concurrency
                    String rId = "run-bench-" + idx + "-" + UUID.randomUUID().toString().substring(0, 6);
                    WorkflowRunEntity run = new WorkflowRunEntity(rId, testProjectId, "def-bench", "QUEUED", "idem-" + rId);
                    workflowRunRepository.save(run);
                    createdRunIds.add(rId);

                    executionStateMachine.transitionRun(rId, WorkflowRunStatus.RUNNING, "Bench start");

                    String sId = "step-" + rId;
                    StepRunEntity step = new StepRunEntity(sId, rId, "node-bench", "PENDING");
                    stepRunRepository.save(step);

                    executionStateMachine.transitionStep(sId, StepRunStatus.RUNNING, "Bench step");
                    executionStateMachine.transitionStep(sId, StepRunStatus.SUCCEEDED, "Bench complete");

                    executionStateMachine.transitionRun(rId, WorkflowRunStatus.SUCCEEDED, "Bench finish");

                    long durationNs = System.nanoTime() - start;
                    latenciesMs.add(durationNs / 1_000_000);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        long benchStart = System.currentTimeMillis();
        startLatch.countDown();
        boolean completed = endLatch.await(15, TimeUnit.SECONDS);
        long benchTotalMs = Math.max(1, System.currentTimeMillis() - benchStart);
        executor.shutdownNow();

        assertThat(completed).isTrue();
        assertThat(successCount.get()).isEqualTo(concurrency);

        Collections.sort(latenciesMs);
        long p50 = latenciesMs.get(latenciesMs.size() / 2);
        long p95 = latenciesMs.get((int) (latenciesMs.size() * 0.95));
        long p99 = latenciesMs.get(latenciesMs.size() - 1);

        double throughputOpsPerSec = (successCount.get() * 1000.0) / benchTotalMs;

        // Defensive regression assertions
        assertThat(throughputOpsPerSec)
                .as("Execution throughput must exceed 10 operations per second")
                .isGreaterThanOrEqualTo(10.0);
        assertThat(p95)
                .as("P95 state machine transition latency under concurrency must remain under 1000ms")
                .isLessThan(1000L);
    }

    @Test
    @DisplayName("测试 3：SSE 实时事件流广播与游标重放基准（Monotonic Sequence & Low Latency Replay）")
    void test3_sseEventStreamLatencyAndMonotonicReplayBenchmark() throws Exception {
        String streamRunId = "run-sse-bench-" + UUID.randomUUID().toString().substring(0, 8);
        createdRunIds.add(streamRunId);

        WorkflowRunEntity run = new WorkflowRunEntity(streamRunId, testProjectId, "def-sse", "RUNNING", "idem-sse");
        workflowRunRepository.save(run);

        // 1. Establish 3 concurrent subscribers
        List<SseEmitter> emitters = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            SseEmitter emitter = runEventBroadcaster.subscribe(streamRunId, null);
            emitters.add(emitter);
        }

        // 2. Broadcast 20 events rapidly and measure dispatch latency
        int totalEvents = 20;
        List<Long> broadcastLatencies = new ArrayList<>();
        for (int i = 1; i <= totalEvents; i++) {
            long start = System.nanoTime();
            runEventBroadcaster.publishEvent(streamRunId, "STEP_PROGRESS", "{\"progress\":" + (i * 5) + "}");
            long elapsedNs = System.nanoTime() - start;
            broadcastLatencies.add(elapsedNs / 1_000_000);
        }

        Collections.sort(broadcastLatencies);
        long p95Latency = broadcastLatencies.get((int) (broadcastLatencies.size() * 0.95));

        // Defensive regression assertion: P95 broadcast dispatch latency strictly < 60ms
        assertThat(p95Latency)
                .as("P95 SSE broadcast latency must remain under 60ms")
                .isLessThan(60L);

        // 3. Replay from cursor Last-Event-ID = 10
        SseEmitter replayEmitter = runEventBroadcaster.subscribe(streamRunId, 10L);
        assertThat(replayEmitter).isNotNull();

        List<RunEventEntity> storedEvents = runEventRepository.findByRunIdOrderBySequenceNumAsc(streamRunId);
        assertThat(storedEvents).hasSize(totalEvents);
        for (int i = 0; i < storedEvents.size(); i++) {
            assertThat(storedEvents.get(i).getSequenceNum()).isEqualTo((long) (i + 1));
        }
    }

    @Test
    @DisplayName("测试 4：高频上下文组装与记忆压缩下的内存资源边界评测（Bounded Memory Footprint）")
    void test4_memoryFootprintAndBoundednessUnderLoad() {
        System.gc();
        Runtime runtime = Runtime.getRuntime();
        long initialUsedMemory = runtime.totalMemory() - runtime.freeMemory();

        int iterations = 40;
        for (int i = 0; i < iterations; i++) {
            HierarchicalContextMemory memory = new HierarchicalContextMemory(
                    new HierarchicalContextMemory.ProjectMemory("proj-1", "Project Rules: strict safety invariants apply.", "commit-1", "sha-1", "ALIGNED"),
                    new HierarchicalContextMemory.RunMemory("run-1", "Goal: fix issue", "def-1", "plan-1", 1000, 100)
            );
            HierarchicalContextMemory.StepMemoryItem item = new HierarchicalContextMemory.StepMemoryItem("step-" + i, "node-1", "SUCCEEDED", 1, 10);
            item.setRawOutput("Very long output repeated for memory pressure... ".repeat(30));
            item.setModifiedFiles(List.of("src/main/A.java"));
            memory.addStepMemory(item);

            ContextMemoryCompressor.CompressionReport report = ContextMemoryCompressor.compressIfNeeded(memory, 50);
            assertThat(report).isNotNull();
            assertThat(report.isBudgetMet()).isTrue();
        }

        System.gc();
        long finalUsedMemory = runtime.totalMemory() - runtime.freeMemory();
        long memoryDeltaMb = Math.max(0, (finalUsedMemory - initialUsedMemory) / (1024 * 1024));

        // Defensive regression assertion: heap delta after 40 compressions and GC must remain bounded (< 100MB)
        assertThat(memoryDeltaMb)
                .as("Memory footprint delta after load must remain bounded under 100MB")
                .isLessThan(100L);
    }

    @Test
    @DisplayName("测试 5：Webhook 标准合同与 HMAC-SHA256 防篡改/防重放签名验证（含边界与容错）")
    void test5_webhookContractAndHmacSha256Verification() throws Exception {
        String secret = "ProdHardenedWebhookSecretKey_2026_SecureKey!";
        Map<String, Object> data = Map.of(
                "runId", "run-wh-100",
                "status", "SUCCEEDED",
                "durationMs", 1420,
                "message", "你好世界🚀 Unicode UTF-8 test"
        );

        WebhookPayload payload = WebhookPayload.create(
                WebhookEventType.RUN_COMPLETED,
                "run-wh-100",
                testProjectId,
                data
        );
        assertThat(payload.getEventId()).isNotBlank();
        assertThat(payload.getEventType()).isEqualTo("run.completed");
        assertThat(payload.getResourceType()).isEqualTo("RUN");
        assertThat(payload.getData()).containsKey("message");

        // Verify JSON serialization roundtrip
        String jsonFromObj = objectMapper.writeValueAsString(payload);
        WebhookPayload deserializedPayload = objectMapper.readValue(jsonFromObj, WebhookPayload.class);
        assertThat(deserializedPayload.getEventId()).isEqualTo(payload.getEventId());
        assertThat(deserializedPayload.getEventType()).isEqualTo("run.completed");
        assertThat(deserializedPayload.getData().get("message")).isEqualTo("你好世界🚀 Unicode UTF-8 test");

        String payloadJson = "{\"eventId\":\"" + payload.getEventId() + "\",\"eventType\":\"run.completed\",\"resourceId\":\"run-wh-100\",\"unicode\":\"你好世界🚀\"}";

        // 1. Generate signature header
        long nowTs = System.currentTimeMillis() / 1000;
        String signatureHeader = WebhookSignatureVerifier.generateSignatureHeader(payloadJson, secret, nowTs);
        assertThat(signatureHeader).startsWith("t=").contains(",v1=");

        // 2. Valid signature verification
        boolean valid = WebhookSignatureVerifier.verifySignature(signatureHeader, payloadJson, secret, 300L);
        assertThat(valid).isTrue();

        // 3. Header spacing resilience: standard HTTP header spacing around commas and '=' delimiters
        String rawHmac = signatureHeader.substring(signatureHeader.indexOf(",v1=") + 4);
        String spacedHeader = "t = " + nowTs + " , v1 = " + rawHmac;
        assertThat(WebhookSignatureVerifier.verifySignature(spacedHeader, payloadJson, secret, 300L)).isTrue();

        // 4. Case-insensitivity defense: uppercase hex signature must verify correctly
        String upperCaseHeader = "t=" + nowTs + ",v1=" + rawHmac.toUpperCase(Locale.ROOT);
        assertThat(WebhookSignatureVerifier.verifySignature(upperCaseHeader, payloadJson, secret, 300L)).isTrue();

        // 5. Anti-tampering check: modified payload fails
        String tamperedJson = payloadJson.replace("run-wh-100", "run-wh-attacker");
        boolean tamperedValid = WebhookSignatureVerifier.verifySignature(signatureHeader, tamperedJson, secret, 300L);
        assertThat(tamperedValid).isFalse();

        // 6. Anti-replay check: expired timestamp in the past (> 300s tolerance)
        long pastExpiredTs = nowTs - 500L;
        String pastExpiredHeader = WebhookSignatureVerifier.generateSignatureHeader(payloadJson, secret, pastExpiredTs);
        assertThat(WebhookSignatureVerifier.verifySignature(pastExpiredHeader, payloadJson, secret, 300L)).isFalse();

        // 7. Anti-replay check: clock skew in the future beyond tolerance window (> 300s)
        long futureExpiredTs = nowTs + 500L;
        String futureExpiredHeader = WebhookSignatureVerifier.generateSignatureHeader(payloadJson, secret, futureExpiredTs);
        assertThat(WebhookSignatureVerifier.verifySignature(futureExpiredHeader, payloadJson, secret, 300L)).isFalse();

        // 8. Acceptable clock skew in future within tolerance window (<= 300s)
        long futureValidTs = nowTs + 60L;
        String futureValidHeader = WebhookSignatureVerifier.generateSignatureHeader(payloadJson, secret, futureValidTs);
        assertThat(WebhookSignatureVerifier.verifySignature(futureValidHeader, payloadJson, secret, 300L)).isTrue();

        // 9. Wrong secret check fails
        boolean wrongSecretValid = WebhookSignatureVerifier.verifySignature(signatureHeader, payloadJson, "wrong-secret", 300L);
        assertThat(wrongSecretValid).isFalse();

        // 10. Malformed header checks
        assertThat(WebhookSignatureVerifier.verifySignature("invalid-format", payloadJson, secret, 300L)).isFalse();
        assertThat(WebhookSignatureVerifier.verifySignature("t=" + nowTs, payloadJson, secret, 300L)).isFalse();
        assertThat(WebhookSignatureVerifier.verifySignature("v1=" + rawHmac, payloadJson, secret, 300L)).isFalse();
        assertThat(WebhookSignatureVerifier.verifySignature("t=not-a-number,v1=" + rawHmac, payloadJson, secret, 300L)).isFalse();
        assertThat(WebhookSignatureVerifier.verifySignature(null, payloadJson, secret, 300L)).isFalse();
        assertThat(WebhookSignatureVerifier.verifySignature("", payloadJson, secret, 300L)).isFalse();
        assertThat(WebhookSignatureVerifier.verifySignature(signatureHeader, null, secret, 300L)).isFalse();
        assertThat(WebhookSignatureVerifier.verifySignature(signatureHeader, payloadJson, null, 300L)).isFalse();
        assertThat(WebhookSignatureVerifier.verifySignature(signatureHeader, payloadJson, "   ", 300L)).isFalse();
        assertThat(WebhookSignatureVerifier.verifySignature(signatureHeader, payloadJson, secret, -1L)).isFalse();

        // 11. Parameter validation for computeHmacSha256
        assertThatThrownBy(() -> WebhookSignatureVerifier.computeHmacSha256(null, secret))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WebhookSignatureVerifier.computeHmacSha256(payloadJson, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("测试 6：产品发布清单（Release Manifest）与 SDK 客户端合同一致性校验")
    void test6_releaseManifestAndSdkContractParity() throws Exception {
        AgentHubReleaseManifest manifest = AgentHubReleaseManifest.createDefault();
        assertThat(manifest.releaseVersion()).isEqualTo("1.0.0-RELEASE");
        assertThat(manifest.apiVersion()).isEqualTo("v1");
        assertThat(manifest.supportedExecutionModes()).containsExactly("DEMO_SIMULATED", "LIVE_SELF_HOSTED", "LIVE_TEAM");
        assertThat(manifest.stableContexts()).hasSize(8);
        assertThat(manifest.stableResources()).contains(
                "Project", "Workspace", "WorkflowDefinition", "WorkflowRun",
                "StepRun", "Artifact", "QualityGateReport", "Approval",
                "Deployment", "TokenUsage", "AuditLog"
        );
        assertThat(manifest.sdkClientContracts()).contains(
                "AgentHubProjectClient",
                "AgentHubRunClient",
                "AgentHubArtifactClient",
                "AgentHubApprovalClient",
                "AgentHubDeploymentClient"
        );
        assertThat(manifest.extensionContracts()).contains(
                "ProviderExtensionContract",
                "ToolExtensionContract",
                "AgentDefinitionExtensionContract",
                "WorkflowTemplateExtensionContract",
                "DeploymentTemplateExtensionContract"
        );

        // Verify JSON roundtrip of ReleaseManifest
        String manifestJson = objectMapper.writeValueAsString(manifest);
        AgentHubReleaseManifest roundtripped = objectMapper.readValue(manifestJson, AgentHubReleaseManifest.class);
        assertThat(roundtripped.releaseVersion()).isEqualTo("1.0.0-RELEASE");
        assertThat(roundtripped.apiVersion()).isEqualTo("v1");
        assertThat(roundtripped.stableResources()).hasSameElementsAs(manifest.stableResources());

        // Verify via /api/system/contracts/v1 REST endpoint
        MvcResult result = mockMvc.perform(get("/api/system/contracts/v1")
                        .header("Authorization", "Bearer dummy-token")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string(ApiContractHeaders.HEADER_API_VERSION, "v1"))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.releaseManifest.releaseVersion").value("1.0.0-RELEASE"))
                .andExpect(jsonPath("$.data.webhookSpecification.signatureHeader").value("X-AgentHub-Signature"))
                .andExpect(jsonPath("$.data.webhookSpecification.algorithm").value("HmacSHA256"))
                .andExpect(jsonPath("$.data.sdkSpecification.clientContracts").isArray())
                .andExpect(jsonPath("$.data.extensionContracts").isArray())
                .andReturn();

        JsonNode responseJson = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(responseJson.path("data").path("webhookSpecification").path("supportedEvents").size())
                .isEqualTo(WebhookEventType.values().length);
    }

    @Test
    @DisplayName("测试 7：受控沙箱准入门禁对未通过门禁（GATE_FAILED / REJECTED）产物的防御性阻断")
    void test7_deploymentAdmissionDefensiveBlockingForRejectedArtifact() {
        String failedArtId = "art-failed-" + UUID.randomUUID().toString().substring(0, 8);
        createdArtifactIds.add(failedArtId);

        WorkflowRunEntity run = new WorkflowRunEntity("run-failed-deploy", testProjectId, "def-failed", "SUCCEEDED", "idem-fail");
        workflowRunRepository.save(run);
        createdRunIds.add("run-failed-deploy");

        ArtifactEntity failedArt = new ArtifactEntity(
                failedArtId, "run-failed-deploy", "step-fail", "CODE_DIFF", "hash-fail", "hash-fail",
                "{\"changedFiles\":[\"compromised.py\"]}"
        );
        failedArt.setReviewStatus("GATE_FAILED");
        artifactRepository.save(failedArt);

        CreateDeploymentRequest req = new CreateDeploymentRequest();
        req.setProjectId(testProjectId);
        req.setArtifactId(failedArtId);
        req.setTarget("STATIC_PREVIEW");

        assertThatThrownBy(() -> deploymentApplication.createAndDeploy(req))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PARAM_ERROR);

        // Reverted artifact is also blocked
        failedArt.setReviewStatus("REVERTED");
        artifactRepository.save(failedArt);
        assertThatThrownBy(() -> deploymentApplication.createAndDeploy(req))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PARAM_ERROR);
    }
}
