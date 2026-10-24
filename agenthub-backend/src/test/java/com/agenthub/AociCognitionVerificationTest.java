package com.agenthub;

import com.agenthub.domain.workspace.service.JGitWorkspaceManager;
import com.agenthub.domain.workspace.service.WorkspaceResolver;
import com.agenthub.execution.application.ExecutionApplication;
import com.agenthub.execution.domain.memory.ContextMemoryCompressor;
import com.agenthub.execution.dto.RunOperationsView;
import com.agenthub.execution.infrastructure.entity.StepRunEntity;
import com.agenthub.execution.infrastructure.entity.WorkflowRunEntity;
import com.agenthub.execution.infrastructure.repository.StepRunRepository;
import com.agenthub.execution.infrastructure.repository.WorkflowRunRepository;
import com.agenthub.shared.context.RequestContext;
import com.agenthub.identity.infrastructure.entity.UserEntity;
import com.agenthub.identity.infrastructure.repository.UserRepository;
import com.agenthub.project.application.ContextSourceApplication;
import com.agenthub.project.application.ProjectApplication;
import com.agenthub.project.domain.context.ContextSourceType;
import com.agenthub.project.dto.context.ContextSourceView;
import com.agenthub.project.dto.CreateProjectCommand;
import com.agenthub.project.dto.ProjectView;
import com.agenthub.project.infrastructure.entity.WorkspaceEntity;
import com.agenthub.project.infrastructure.repository.WorkspaceRepository;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.FileSystemUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AOCI 认知状态机本地行为验证套件。
 * 覆盖：
 * 1. 消除无凭据时默认 ALIGNED 的行为，验证未配置时返回 NOT_CONFIGURED；
 * 2. 验证非 Git 目录或空凭据文件时的 UNAVAILABLE 防御；
 * 3. 验证本地存在凭据且工作区干净/漂移时的状态机响应；
 * 注意：本测试使用本地测试文件验证状态机逻辑，不替代官方 AOCI 工具链生成与 verify/check 的官方对齐证明。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AociCognitionVerificationTest {

    @Autowired
    private ContextSourceApplication contextSourceApplication;

    @Autowired
    private ExecutionApplication executionApplication;

    @Autowired
    private ProjectApplication projectApplication;

    @Autowired
    private com.agenthub.project.infrastructure.repository.ProjectRepository projectRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private WorkspaceResolver workspaceResolver;

    @Autowired
    private JGitWorkspaceManager gitManager;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WorkflowRunRepository workflowRunRepository;

    @Autowired
    private StepRunRepository stepRunRepository;

    private String testUserId;
    private final List<String> createdProjectIds = new ArrayList<>();
    private final List<String> createdRunIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        testUserId = "usr-aoci-" + UUID.randomUUID().toString().substring(0, 8);
        userRepository.save(new UserEntity(
                testUserId,
                testUserId + "@agenthub.test",
                "HashPassword123!",
                "AociTester",
                "ACTIVE"
        ));
        RequestContext.get().setUserId(testUserId);
        RequestContext.get().setUsername(testUserId + "@agenthub.test");
    }

    @AfterEach
    void tearDown() {
        RequestContext.clear();
        for (String runId : createdRunIds) {
            try {
                stepRunRepository.deleteAll(stepRunRepository.findByRunIdOrderByCreatedAtAsc(runId));
                workflowRunRepository.deleteById(runId);
            } catch (Exception ignored) {}
        }
        createdRunIds.clear();

        for (String projId : createdProjectIds) {
            try {
                workspaceRepository.findByProjectId(projId).ifPresent(ws -> {
                    workspaceRepository.delete(ws);
                    try {
                        Path root = workspaceResolver.getWorkspaceRoot(ws.getId());
                        if (Files.exists(root)) {
                            FileSystemUtils.deleteRecursively(root);
                        }
                    } catch (Exception ignored) {}
                });
                projectRepository.deleteById(projId);
            } catch (Exception ignored) {}
        }
        createdProjectIds.clear();

        if (testUserId != null) {
            try {
                userRepository.deleteById(testUserId);
            } catch (Exception ignored) {}
        }
    }

    @Test
    @DisplayName("1. determineAociCognitionStatus 纯内存与文件系统状态矩阵：NOT_CONFIGURED / UNAVAILABLE / ALIGNED / REPAIR_REQUIRED")
    void testAociCognitionStatusMatrix(@TempDir Path tempDir) throws Exception {
        // Case A: null or non-existent
        assertThat(ContextMemoryCompressor.determineAociCognitionStatus(null, "HEAD")).isEqualTo("NOT_CONFIGURED");
        assertThat(ContextMemoryCompressor.determineAociCognitionStatus(tempDir.resolve("non_existent").toFile(), "HEAD")).isEqualTo("NOT_CONFIGURED");

        // Case B: empty workspace (no .aoci, no receipt) -> NOT_CONFIGURED
        assertThat(ContextMemoryCompressor.determineAociCognitionStatus(tempDir.toFile(), "HEAD")).isEqualTo("NOT_CONFIGURED");

        // Case C: .aoci directory exists but no receipt -> UNAVAILABLE
        Path aociDir = tempDir.resolve(".aoci");
        Files.createDirectories(aociDir);
        assertThat(ContextMemoryCompressor.determineAociCognitionStatus(tempDir.toFile(), "HEAD")).isEqualTo("UNAVAILABLE");

        // Case C2: receipt exists but NO .git repository -> UNAVAILABLE
        Path receiptFile = aociDir.resolve("receipt.json");
        Files.writeString(receiptFile, "{\"status\":\"ALIGNED\"}", StandardCharsets.UTF_8);
        assertThat(ContextMemoryCompressor.determineAociCognitionStatus(tempDir.toFile(), "HEAD")).isEqualTo("UNAVAILABLE");
        Files.deleteIfExists(receiptFile);

        // Initialize git in tempDir
        try (Git git = Git.init().setDirectory(tempDir.toFile()).call()) {
            Path fileA = tempDir.resolve("README.md");
            Files.writeString(fileA, "# Init", StandardCharsets.UTF_8);
            git.add().addFilepattern(".").call();
            var commit = git.commit().setMessage("Initial commit").call();
            String commitHash = commit.getName();

            // Case C3: 0-byte receipt file -> UNAVAILABLE
            Files.writeString(receiptFile, "", StandardCharsets.UTF_8);
            assertThat(ContextMemoryCompressor.determineAociCognitionStatus(tempDir.toFile(), commitHash)).isEqualTo("UNAVAILABLE");

            // Case D: official non-empty receipt present, but untracked files -> REPAIR_REQUIRED
            Files.writeString(receiptFile, "{\"status\":\"ALIGNED\"}", StandardCharsets.UTF_8);
            // receipt.json is currently untracked in git
            assertThat(ContextMemoryCompressor.determineAociCognitionStatus(tempDir.toFile(), commitHash)).isEqualTo("REPAIR_REQUIRED");

            // Case E: official receipt committed and working tree clean -> ALIGNED
            git.add().addFilepattern(".").call();
            var commit2 = git.commit().setMessage("Add AOCI receipt").call();
            assertThat(ContextMemoryCompressor.determineAociCognitionStatus(tempDir.toFile(), commit2.getName())).isEqualTo("ALIGNED");

            // Case F: modify a file -> REPAIR_REQUIRED
            Files.writeString(fileA, "# Modified", StandardCharsets.UTF_8);
            assertThat(ContextMemoryCompressor.determineAociCognitionStatus(tempDir.toFile(), commit2.getName())).isEqualTo("REPAIR_REQUIRED");
        }
    }

    @Test
    @DisplayName("2. ContextSourceApplicationService 自动发现：未配置 AOCI 时诚实返回 NOT_CONFIGURED 与 UNAVAILABLE")
    void testContextSourceAutoDiscoveryAociStatus() throws Exception {
        String wsRel = "ws-ctx-" + UUID.randomUUID().toString().substring(0, 8);
        ProjectView proj = projectApplication.createProject(new CreateProjectCommand(
                "p-aoci-" + UUID.randomUUID().toString().substring(0, 6),
                "AOCI Test Project",
                wsRel
        ));
        createdProjectIds.add(proj.getId());
        WorkspaceEntity ws = workspaceRepository.findByProjectId(proj.getId()).orElseThrow();
        Path wsRoot = workspaceResolver.getWorkspaceRoot(ws.getId());
        Files.createDirectories(wsRoot);

        // Initialize git workspace without AOCI
        try (Git git = Git.init().setDirectory(wsRoot.toFile()).call()) {
            Files.writeString(wsRoot.resolve("AGENTS.md"), "# Project Rules\n", StandardCharsets.UTF_8);
            git.add().addFilepattern(".").call();
            var commit = git.commit().setMessage("Initial").call();

            // Auto-discover
            List<ContextSourceView> sources = contextSourceApplication.autoDiscoverContextSources(
                    proj.getId(), ws.getId(), commit.getName()
            );

            // AOCI source should exist, but have status NOT_CONFIGURED
            ContextSourceView aociSource = sources.stream()
                    .filter(s -> s.getSourceType() == ContextSourceType.AOCI_COGNITION)
                    .findFirst()
                    .orElseThrow();

            assertThat(aociSource.getMetadata().get("status")).isEqualTo("NOT_CONFIGURED");
            assertThat(aociSource.getMetadata().get("scope")).isEqualTo("none");
            assertThat(aociSource.getContent()).contains("not configured");

            // Now create .aoci dir without receipt
            Files.createDirectories(wsRoot.resolve(".aoci"));
            List<ContextSourceView> sourcesUnavailable = contextSourceApplication.autoDiscoverContextSources(
                    proj.getId(), ws.getId(), commit.getName()
            );
            ContextSourceView aociSourceUnavailable = sourcesUnavailable.stream()
                    .filter(s -> s.getSourceType() == ContextSourceType.AOCI_COGNITION)
                    .findFirst()
                    .orElseThrow();
            assertThat(aociSourceUnavailable.getMetadata().get("status")).isEqualTo("UNAVAILABLE");

            // Now create official receipt and commit
            Files.writeString(wsRoot.resolve(".aoci/receipt.json"), "{\"status\":\"ALIGNED\"}", StandardCharsets.UTF_8);
            git.add().addFilepattern(".").call();
            var commit2 = git.commit().setMessage("Add official receipt").call();

            List<ContextSourceView> sourcesAligned = contextSourceApplication.autoDiscoverContextSources(
                    proj.getId(), ws.getId(), commit2.getName()
            );
            ContextSourceView aociSourceAligned = sourcesAligned.stream()
                    .filter(s -> s.getSourceType() == ContextSourceType.AOCI_COGNITION)
                    .findFirst()
                    .orElseThrow();
            assertThat(aociSourceAligned.getMetadata().get("status")).isEqualTo("ALIGNED");
            assertThat(aociSourceAligned.getMetadata().get("scope")).isEqualTo("backend,frontend,schema");
        }
    }

    @Test
    @DisplayName("3. RunQueryModule 在没有官方 Receipt 时返回 NOT_CONFIGURED，有 Receipt 且 clean 时返回 ALIGNED")
    void testRunQueryModuleAociStatusHonesty() throws Exception {
        String wsRel = "ws-run-" + UUID.randomUUID().toString().substring(0, 8);
        ProjectView proj = projectApplication.createProject(new CreateProjectCommand(
                "p-run-aoci-" + UUID.randomUUID().toString().substring(0, 6),
                "Run AOCI Test Project",
                wsRel
        ));
        createdProjectIds.add(proj.getId());
        WorkspaceEntity ws = workspaceRepository.findByProjectId(proj.getId()).orElseThrow();
        Path wsRoot = workspaceResolver.getWorkspaceRoot(ws.getId());
        Files.createDirectories(wsRoot);
        Files.writeString(wsRoot.resolve("README.md"), "# Initial\n", StandardCharsets.UTF_8);
        String baselineHash = gitManager.createBaseline(wsRoot.toFile(), "init-test", "test@agenthub.local");
        ws.setGitBaselineCommit(baselineHash);
        workspaceRepository.save(ws);

        String runId = "run-aoci-" + UUID.randomUUID().toString().substring(0, 8);
        createdRunIds.add(runId);
        WorkflowRunEntity runEntity = new WorkflowRunEntity(
                runId,
                proj.getId(),
                "wf-aoci-test",
                "WAITING_APPROVAL",
                "idem-" + UUID.randomUUID()
        );
        workflowRunRepository.save(runEntity);

        StepRunEntity stepEntity = new StepRunEntity(
                "step-aoci-1",
                runId,
                "node-gate",
                "WAITING_APPROVAL"
        );
        stepRunRepository.save(stepEntity);

        // Workspace has no AOCI files -> NOT_CONFIGURED
        RunOperationsView ops = executionApplication.getRunOperations(runId);
        assertThat(ops.getMemorySummary().getAociCognitionStatus()).isEqualTo("NOT_CONFIGURED");

        // Add official receipt and commit to git
        Files.createDirectories(wsRoot.resolve(".aoci"));
        Files.writeString(wsRoot.resolve(".aoci/receipt.json"), "{\"status\":\"ALIGNED\"}\n", StandardCharsets.UTF_8);
        String newCommit = gitManager.createStepSnapshot(wsRoot.toFile(), runId, "step-aoci-1", "Add official receipt", "test@agenthub.local").getCommitHash();
        ws.setGitBaselineCommit(newCommit);
        workspaceRepository.save(ws);

        RunOperationsView opsAligned = executionApplication.getRunOperations(runId);
        assertThat(opsAligned.getMemorySummary().getAociCognitionStatus()).isEqualTo("ALIGNED");
    }
}
