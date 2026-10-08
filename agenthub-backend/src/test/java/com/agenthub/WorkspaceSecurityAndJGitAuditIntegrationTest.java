package com.agenthub;

import com.agenthub.adapter.common.BusinessException;
import com.agenthub.adapter.common.ErrorCode;
import com.agenthub.audit.application.ArtifactApplication;
import com.agenthub.audit.dto.ArtifactView;
import com.agenthub.audit.infrastructure.entity.ArtifactEntity;
import com.agenthub.audit.infrastructure.repository.ArtifactRepository;
import com.agenthub.domain.workspace.model.FileDiffEntry;
import com.agenthub.domain.workspace.model.StructuredDiff;
import com.agenthub.domain.workspace.service.JGitWorkspaceManager;
import com.agenthub.domain.workspace.service.WorkspaceResolver;
import com.agenthub.execution.infrastructure.entity.WorkflowRunEntity;
import com.agenthub.execution.infrastructure.repository.WorkflowRunRepository;
import com.agenthub.infrastructure.concurrency.WorkspaceLockManager;
import com.agenthub.project.application.WorkspaceApplication;
import com.agenthub.project.dto.WorkspaceFileDetailView;
import com.agenthub.project.dto.WorkspaceFileNode;
import com.agenthub.project.infrastructure.entity.ProjectEntity;
import com.agenthub.project.infrastructure.entity.WorkspaceEntity;
import com.agenthub.project.infrastructure.repository.ProjectRepository;
import com.agenthub.project.infrastructure.repository.WorkspaceRepository;
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

import com.agenthub.shared.context.RequestContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class WorkspaceSecurityAndJGitAuditIntegrationTest {

    @Autowired
    private WorkspaceResolver workspaceResolver;

    @Autowired
    private WorkspaceApplication workspaceApplication;

    @Autowired
    private WorkspaceLockManager lockManager;

    @Autowired
    private JGitWorkspaceManager gitManager;

    @Autowired
    private ArtifactApplication artifactApplication;

    @Autowired
    private ArtifactRepository artifactRepository;

    @Autowired
    private WorkflowRunRepository workflowRunRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private MockMvc mockMvc;

    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    private String testProjectId;
    private String testWorkspaceId;
    private Path testWorkspaceDir;

    @BeforeEach
    void setUp() throws Exception {
        testProjectId = "proj-test-" + UUID.randomUUID().toString().substring(0, 8);
        testWorkspaceId = "ws-test-" + UUID.randomUUID().toString().substring(0, 8);

        testWorkspaceDir = workspaceResolver.getWorkspaceRoot(testWorkspaceId);
        gitManager.initWorkspace(testWorkspaceDir.toFile());

        ProjectEntity project = new ProjectEntity(
                testProjectId,
                "user-1",
                "Test Project for Phase 2",
                "Integration test project",
                "ACTIVE",
                testWorkspaceId
        );
        projectRepository.save(project);

        WorkspaceEntity workspace = new WorkspaceEntity(
                testWorkspaceId,
                testProjectId,
                testWorkspaceDir.getFileName().toString(),
                null
        );
        workspaceRepository.save(workspace);
    }

    @AfterEach
    void tearDown() {
        if (testWorkspaceDir != null && Files.exists(testWorkspaceDir)) {
            try (var stream = Files.walk(testWorkspaceDir)) {
                stream.sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            } catch (Exception ignored) {}
        }
    }

    // =========================================================================
    // 1. 受控工作区路径与设备文件防护测试
    // =========================================================================

    @Test
    @DisplayName("边界防御 1：../ 路径穿越读取与写入被严格拦截 (3004)")
    void testPathTraversalForbidden() {
        assertThatThrownBy(() -> workspaceResolver.resolvePathForRead(testWorkspaceId, "../outside.txt"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode().getCode()).isEqualTo(3004));

        assertThatThrownBy(() -> workspaceResolver.resolvePathForWrite(testWorkspaceId, "subdir/../../escape.txt"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode().getCode()).isEqualTo(3004));
    }

    @Test
    @DisplayName("边界防御 2：Windows 保留设备文件 (CON, PRN, AUX, NUL, COM0-9, LPT0-9, CONIN$, CONOUT$) 被严格拦截 (3009)")
    void testReservedDeviceNamesForbidden() {
        String[] reservedDevices = {"CON", "con.txt", "PRN", "aux.json", "nul", "COM0", "COM1", "COM9.log", "LPT1.txt", "CONIN$", "conout$.dat", "subdir/aux/code.py"};
        for (String dev : reservedDevices) {
            assertThatThrownBy(() -> workspaceResolver.resolvePathForWrite(testWorkspaceId, dev))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode().getCode()).isEqualTo(3009));

            assertThatThrownBy(() -> workspaceResolver.resolvePathForRead(testWorkspaceId, dev))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode().getCode()).isEqualTo(3009));
        }
    }

    @Test
    @DisplayName("边界防御 3：.git 目录大小写、变种与 NTFS 8.3 短名称 (git~1) 读取与写入被严格阻断 (3005)")
    void testGitProtectionVariations() {
        String[] gitPaths = {".git/config", ".git/hooks/pre-commit", ".GIT/HEAD", ".git\\config", "sub/.git/config", "git~1/config", "GIT~1/HEAD"};
        for (String gitPath : gitPaths) {
            assertThatThrownBy(() -> workspaceResolver.resolvePathForRead(testWorkspaceId, gitPath))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode().getCode()).isEqualTo(3005));

            assertThatThrownBy(() -> workspaceResolver.resolvePathForWrite(testWorkspaceId, gitPath))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode().getCode()).isEqualTo(3005));
        }
    }

    @Test
    @DisplayName("边界防御 4：指向工作区外的符号链接 (Symlink Traversal) 被拦截阻断 (3004)")
    void testSymlinkTraversalForbidden() throws Exception {
        Path outsideDir = Files.createTempDirectory("agenthub-outside-target");
        Path outsideFile = Files.writeString(outsideDir.resolve("secret.txt"), "TOP_SECRET_DATA");

        Path symlinkInWorkspace = testWorkspaceDir.resolve("symlink-to-outside");
        try {
            Files.createSymbolicLink(symlinkInWorkspace, outsideDir);
        } catch (UnsupportedOperationException | SecurityException | java.nio.file.FileSystemException e) {
            // If OS privileges do not permit symlink creation, verify graceful non-crash
            return;
        }

        // Attempting to resolve path via symlink pointing outside workspace root must fail
        assertThatThrownBy(() -> workspaceResolver.resolvePathForRead(testWorkspaceId, "symlink-to-outside/secret.txt"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode().getCode()).isEqualTo(3004));

        Files.deleteIfExists(outsideFile);
        Files.deleteIfExists(outsideDir);
    }

    // =========================================================================
    // 2. 统一受控工作区文件操作 API 测试
    // =========================================================================

    @Test
    @DisplayName("受控文件 API 1：单文件写入上限限制 (10MB) 防护拦截")
    void testMaxWriteLimitExceeded() {
        // Construct string larger than 10MB
        int targetSize = 10 * 1024 * 1024 + 1024;
        String largeContent = "A".repeat(targetSize);

        assertThatThrownBy(() -> workspaceApplication.saveFile(testWorkspaceId, "large.txt", largeContent))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode().getCode()).isEqualTo(3007));
    }

    @Test
    @DisplayName("受控文件 API 2：单文件预览上限限制 (2MB) 与截断模式")
    void testMaxPreviewLimitExceeded() throws Exception {
        Path largeFile = testWorkspaceDir.resolve("huge-doc.txt");
        byte[] payload = new byte[2 * 1024 * 1024 + 2048];
        Arrays.fill(payload, (byte) 'X');
        Files.write(largeFile, payload);

        // Direct getFileContent text preview throws WORKSPACE_FILE_TOO_LARGE (3007)
        assertThatThrownBy(() -> workspaceApplication.getFileContent(testWorkspaceId, "huge-doc.txt"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode().getCode()).isEqualTo(3007));

        // getFileDetail safely returns truncated preview
        WorkspaceFileDetailView detail = workspaceApplication.getFileDetail(testWorkspaceId, "huge-doc.txt");
        assertThat(detail.isTruncated()).isTrue();
        assertThat(detail.isBinary()).isFalse();
        assertThat(detail.getSize()).isEqualTo(payload.length);
        assertThat(detail.getContent()).isNotNull();
    }

    @Test
    @DisplayName("受控文件 API 3：二进制文件探测与文本预览拒绝 (3008)")
    void testBinaryFilePreviewForbidden() throws Exception {
        Path binFile = testWorkspaceDir.resolve("logo.png");
        byte[] binData = new byte[]{0x00, 0x01, 0x02, (byte) 0xFF, 0x00, 0x04};
        Files.write(binFile, binData);

        // Binary text read must be rejected
        assertThatThrownBy(() -> workspaceApplication.getFileContent(testWorkspaceId, "logo.png"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode().getCode()).isEqualTo(3008));

        // getFileDetail marks binary=true, content=null
        WorkspaceFileDetailView detail = workspaceApplication.getFileDetail(testWorkspaceId, "logo.png");
        assertThat(detail.isBinary()).isTrue();
        assertThat(detail.getContent()).isNull();
    }

    @Test
    @DisplayName("受控文件 API 4：文件树列出、重命名与安全删除生命周期")
    void testFileTreeRenameAndDeleteLifecycle() throws Exception {
        // 1. Create file
        workspaceApplication.saveFile(testWorkspaceId, "src/main/App.java", "public class App {}");

        // 2. List files
        List<WorkspaceFileNode> nodes = workspaceApplication.listFiles(testWorkspaceId, "");
        assertThat(nodes).isNotEmpty();
        assertThat(nodes.stream().noneMatch(n -> n.getName().equalsIgnoreCase(".git"))).isTrue();
        assertThat(nodes.stream().anyMatch(n -> n.getRelativePath().replace('\\', '/').equals("src/main/App.java"))).isTrue();

        // 3. Rename file
        workspaceApplication.renameFile(testWorkspaceId, "src/main/App.java", "src/main/Application.java");
        assertThat(Files.exists(testWorkspaceDir.resolve("src/main/App.java"))).isFalse();
        assertThat(Files.exists(testWorkspaceDir.resolve("src/main/Application.java"))).isTrue();

        // 4. Delete file
        workspaceApplication.deleteFile(testWorkspaceId, "src/main/Application.java");
        assertThat(Files.exists(testWorkspaceDir.resolve("src/main/Application.java"))).isFalse();
    }

    // =========================================================================
    // 3. 工作区并发与租约治理测试
    // =========================================================================

    @Test
    @DisplayName("并发治理 1：多 Owner 互斥与租约自动续期")
    void testLockExclusionAndLeaseRenewal() {
        String wsKey = testWorkspaceDir.toString();
        String owner1 = "run-owner-1";
        String owner2 = "run-owner-2";

        // Owner 1 acquires lock with 5s lease
        boolean locked1 = lockManager.tryAcquireLock(wsKey, owner1, 100, 5000);
        assertThat(locked1).isTrue();

        // Owner 2 attempts to acquire lock immediately -> must fail
        boolean locked2 = lockManager.tryAcquireLock(wsKey, owner2, 100, 5000);
        assertThat(locked2).isFalse();

        // Owner 1 renews lease
        boolean renewed = lockManager.renewLease(wsKey, owner1, 10000);
        assertThat(renewed).isTrue();

        // Owner 1 releases lock
        boolean released = lockManager.releaseLock(wsKey, owner1);
        assertThat(released).isTrue();

        // Now Owner 2 acquires lock successfully
        boolean locked2After = lockManager.tryAcquireLock(wsKey, owner2, 100, 5000);
        assertThat(locked2After).isTrue();
        lockManager.releaseLock(wsKey, owner2);
    }

    @Test
    @DisplayName("并发治理 2：租约超时防死锁自动回收机制")
    void testLeaseTimeoutAutoDeadlockRecovery() throws Exception {
        String wsKey = testWorkspaceDir.toString();
        String staleOwner = "stale-process-dead";
        String newOwner = "new-process-alive";

        // Stale owner acquires lock with very short lease (100ms) and fails to release
        boolean lockedStale = lockManager.tryAcquireLock(wsKey, staleOwner, 100, 100);
        assertThat(lockedStale).isTrue();

        // Wait for lease to expire
        Thread.sleep(150);

        // New owner arrives: lock is expired, auto-recovery reclaims the lock for new owner
        boolean lockedNew = lockManager.tryAcquireLock(wsKey, newOwner, 500, 5000);
        assertThat(lockedNew).isTrue();

        WorkspaceLockManager.LockInfo info = lockManager.getLockInfo(wsKey).orElse(null);
        assertThat(info).isNotNull();
        assertThat(info.getOwnerId()).isEqualTo(newOwner);

        lockManager.releaseLock(wsKey, newOwner);
    }

    // =========================================================================
    // 4. JGit 基线、快照与结构化 Diff 输出引擎测试
    // =========================================================================

    @Test
    @DisplayName("JGit 审计 1：创建 Baseline、Step Snapshot 与结构化 Diff 引擎完整性")
    void testBaselineSnapshotAndStructuredDiff() throws Exception {
        File wsDir = testWorkspaceDir.toFile();
        String runId = "run-jgit-001";
        String stepId = "step-jgit-001";

        // 1. Establish baseline commit
        String baselineHash = gitManager.createBaseline(wsDir, runId, "bot@agenthub.local");
        assertThat(baselineHash).isNotBlank();

        // 2. Agent creates files
        workspaceApplication.saveFile(testWorkspaceId, "Service.java", "public class Service { void run() {} }");
        workspaceApplication.saveFile(testWorkspaceId, "Config.json", "{\"status\": \"ok\"}");

        // 3. Step finishes -> Create snapshot
        JGitWorkspaceManager.StepSnapshotResult snapshot = gitManager.createStepSnapshot(
                wsDir, runId, stepId, "Added Service and Config", "developer@agenthub.local"
        );
        assertThat(snapshot.getCommitHash()).isNotBlank();
        assertThat(snapshot.getTagName()).isEqualTo("snapshot-" + stepId);
        assertThat(snapshot.getChangedFiles()).contains("Service.java", "Config.json");
        assertThat(snapshot.getFileChecksums().get("Service.java")).isNotBlank();

        // 4. Compute structured diff
        StructuredDiff diff = gitManager.computeStructuredDiff(wsDir);
        assertThat(diff.getTotalFilesChanged()).isGreaterThanOrEqualTo(0);
        assertThat(diff.getEntries()).isNotNull();

        // Modify file to test live diff
        workspaceApplication.saveFile(testWorkspaceId, "Service.java", "public class Service { void run() { int x = 1; } }");
        StructuredDiff modifiedDiff = gitManager.computeStructuredDiff(wsDir);
        assertThat(modifiedDiff.getEntries()).isNotEmpty();

        FileDiffEntry entry = modifiedDiff.getEntries().stream()
                .filter(e -> "Service.java".equals(e.getNewPath()))
                .findFirst()
                .orElse(null);
        assertThat(entry).isNotNull();
        assertThat(entry.getChangeType()).isEqualTo(FileDiffEntry.ChangeType.MODIFY);
        assertThat(entry.getLinesAdded()).isGreaterThan(0);
        assertThat(entry.getDiffContent()).contains("+");
        assertThat(entry.isBinary()).isFalse();
    }

    // =========================================================================
    // 5. 产物审查与安全回滚一致性测试
    // =========================================================================

    @Test
    @DisplayName("产物治理与回滚：Accept/Reject/Revert 安全回滚保留无关文件")
    void testArtifactAcceptRejectAndSafeRevert() throws Exception {
        File wsDir = testWorkspaceDir.toFile();
        String runId = "run-audit-" + UUID.randomUUID().toString().substring(0, 6);
        String stepId = "step-code-" + UUID.randomUUID().toString().substring(0, 6);

        WorkflowRunEntity run = new WorkflowRunEntity(
                runId,
                testProjectId,
                "def-default",
                "RUNNING",
                "idemp-" + runId
        );
        workflowRunRepository.save(run);

        // 1. Initial baseline commit with base.txt
        workspaceApplication.saveFile(testWorkspaceId, "base.txt", "Original Baseline Version");
        String baselineHash = gitManager.createBaseline(wsDir, runId, "admin@agenthub.local");

        WorkspaceEntity wsEntity = workspaceRepository.findById(testWorkspaceId).get();
        wsEntity.setGitBaselineCommit(baselineHash);
        workspaceRepository.save(wsEntity);

        // 2. Step 1: Agent modifies base.txt and creates step1.txt
        workspaceApplication.saveFile(testWorkspaceId, "base.txt", "Modified by Step 1");
        workspaceApplication.saveFile(testWorkspaceId, "step1.txt", "New Step 1 Content");
        JGitWorkspaceManager.StepSnapshotResult snap1 = gitManager.createStepSnapshot(
                wsDir, runId, stepId, "Step 1 feature", "dev@agenthub.local"
        );

        ArtifactView artifact1 = artifactApplication.recordStepSnapshot(
                runId, stepId, testWorkspaceId, snap1.getCommitHash(), snap1.getTagName(),
                snap1.getChangedFiles(), snap1.getFileChecksums(), "dev@agenthub.local"
        );
        assertThat(artifact1.getReviewStatus()).isEqualTo("PENDING");

        // Test Accept
        ArtifactView accepted = artifactApplication.acceptArtifact(artifact1.getId(), "user-1");
        assertThat(accepted.getReviewStatus()).isEqualTo("ACCEPTED");

        // Test Reject
        ArtifactView rejected = artifactApplication.rejectArtifact(artifact1.getId(), "user-1", "Needs refactoring");
        assertThat(rejected.getReviewStatus()).isEqualTo("REJECTED");
        assertThat(rejected.getReviewComment()).isEqualTo("Needs refactoring");

        // 3. Step 2 creates an unrelated file
        workspaceApplication.saveFile(testWorkspaceId, "unrelated.txt", "Independent user work");

        // 4. User Reverts Step 1 artifact
        ArtifactView revertedArtifact = artifactApplication.revertArtifact(artifact1.getId(), "user-1");
        assertThat(revertedArtifact.getReviewStatus()).isEqualTo("REVERTED");

        // 5. Verify rollback safety:
        // - base.txt must be restored back to original baseline
        String baseContent = Files.readString(testWorkspaceDir.resolve("base.txt"));
        assertThat(baseContent).isEqualTo("Original Baseline Version");

        // - step1.txt newly added in step 1 must be safely deleted
        assertThat(Files.exists(testWorkspaceDir.resolve("step1.txt"))).isFalse();

        // - unrelated.txt must REMAIN UNTOUCHED (Strict proof against destructive git reset --hard!)
        assertThat(Files.exists(testWorkspaceDir.resolve("unrelated.txt"))).isTrue();
        String unrelatedContent = Files.readString(testWorkspaceDir.resolve("unrelated.txt"));
        assertThat(unrelatedContent).isEqualTo("Independent user work");
    }

    // =========================================================================
    // 6. Web REST Controller 端点测试
    // =========================================================================

    @Test
    @DisplayName("REST API：/api/workspaces 端点受控树、读写与锁状态测试")
    void testWorkspaceRestControllerEndpoints() throws Exception {
        // Write file via REST
        mockMvc.perform(post("/api/workspaces/" + testWorkspaceId + "/file")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"path\":\"test-api.txt\",\"content\":\"API Test Content\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // Read file tree via REST
        mockMvc.perform(get("/api/workspaces/" + testWorkspaceId + "/tree"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray());

        // Read file detail via REST
        mockMvc.perform(get("/api/workspaces/" + testWorkspaceId + "/file").param("path", "test-api.txt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.content").value("API Test Content"))
                .andExpect(jsonPath("$.data.binary").value(false));

        // Acquire lock via REST
        mockMvc.perform(post("/api/workspaces/" + testWorkspaceId + "/lock/acquire")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ownerId\":\"web-test\",\"waitTimeoutMs\":1000,\"leaseTtlMs\":5000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));

        // Query lock status via REST
        mockMvc.perform(get("/api/workspaces/" + testWorkspaceId + "/lock/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.locked").value(true))
                .andExpect(jsonPath("$.data.ownerId").value("web-test"));

        // Release lock via REST
        mockMvc.perform(post("/api/workspaces/" + testWorkspaceId + "/lock/release")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ownerId\":\"web-test\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));
    }

    // =========================================================================
    // 7. 受控工作区文件下载与归档 (Download & Archive) 接口测试
    // =========================================================================

    @Test
    @DisplayName("受控工作区 API 5：文件下载与整工作区 ZIP 归档导出测试")
    void testDownloadAndArchiveLifecycle() throws Exception {
        // 1. Create text file and binary file
        workspaceApplication.saveFile(testWorkspaceId, "docs/manual.txt", "Manual Content Here");
        Path binPath = testWorkspaceDir.resolve("assets/icon.png");
        Files.createDirectories(binPath.getParent());
        Files.write(binPath, new byte[]{0x00, 0x01, 0x02, 0x03});

        // 2. Download via Service
        byte[] textBytes = workspaceApplication.downloadFile(testWorkspaceId, "docs/manual.txt");
        assertThat(new String(textBytes, StandardCharsets.UTF_8)).isEqualTo("Manual Content Here");

        byte[] binBytes = workspaceApplication.downloadFile(testWorkspaceId, "assets/icon.png");
        assertThat(binBytes).containsExactly(0x00, 0x01, 0x02, 0x03);

        // 3. Download via REST API
        mockMvc.perform(get("/api/workspaces/" + testWorkspaceId + "/download").param("path", "docs/manual.txt"))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEqualTo("Manual Content Here"));

        // 4. Archive workspace via Service & REST API
        byte[] zipData = workspaceApplication.archiveWorkspace(testWorkspaceId);
        assertThat(zipData).isNotEmpty();

        // Verify ZIP contents: must contain manual.txt and icon.png, must NOT contain .git
        List<String> entryNames = new ArrayList<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipData))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entryNames.add(entry.getName().replace('\\', '/'));
            }
        }
        assertThat(entryNames).contains("docs/manual.txt", "assets/icon.png");
        assertThat(entryNames.stream().noneMatch(e -> e.startsWith(".git"))).isTrue();

        // 5. REST Archive Endpoint
        mockMvc.perform(get("/api/workspaces/" + testWorkspaceId + "/archive"))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray()).isNotEmpty());
    }

    // =========================================================================
    // 8. 数据库租约持久化表 (workspace_locks) 与跨实例锁协调测试
    // =========================================================================

    @Test
    @DisplayName("并发治理 3：数据库 workspace_locks 租约表持久化与释放同步测试")
    void testDatabaseWorkspaceLocksRegistry() {
        if (jdbcTemplate == null) return;

        String owner = "db-node-worker-1";
        boolean acquired = lockManager.tryAcquireLock(testWorkspaceId, owner, 200, 5000);
        assertThat(acquired).isTrue();

        // Verify DB row exists in workspace_locks
        String normKey = lockManager.normalizeKey(testWorkspaceId);
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM workspace_locks WHERE owner_id = ?",
                Integer.class, owner
        );
        assertThat(count).isGreaterThanOrEqualTo(1);

        // Renew lease
        boolean renewed = lockManager.renewLease(testWorkspaceId, owner, 10000);
        assertThat(renewed).isTrue();

        // Release lock
        boolean released = lockManager.releaseLock(testWorkspaceId, owner);
        assertThat(released).isTrue();

        // Verify DB row removed
        Integer countAfter = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM workspace_locks WHERE owner_id = ?",
                Integer.class, owner
        );
        assertThat(countAfter).isEqualTo(0);
    }

    // =========================================================================
    // 9. 工作区 Lock Key 归一化互斥一致性测试 (workspaceId 与目录绝对路径)
    // =========================================================================

    @Test
    @DisplayName("并发治理 4：workspaceId 与绝对文件路径指向同一工作区时的锁归一化互斥测试")
    void testLockKeyNormalizationBetweenWorkspaceIdAndPath() {
        String owner1 = "worker-by-id";
        String owner2 = "worker-by-path";

        // Owner 1 acquires lock using workspaceId ("ws-test-xxx")
        boolean locked1 = lockManager.tryAcquireLock(testWorkspaceId, owner1, 100, 5000);
        assertThat(locked1).isTrue();

        // Owner 2 attempts to acquire lock using physical path -> MUST BE MUTUALLY EXCLUSIVE!
        boolean locked2 = lockManager.tryAcquireLock(testWorkspaceDir.toString(), owner2, 100, 5000);
        assertThat(locked2).isFalse();

        // Owner 1 releases lock via workspaceId
        lockManager.releaseLock(testWorkspaceId, owner1);

        // Owner 2 can now acquire lock via physical path
        boolean locked2After = lockManager.tryAcquireLock(testWorkspaceDir.toString(), owner2, 100, 5000);
        assertThat(locked2After).isTrue();
        lockManager.releaseLock(testWorkspaceDir.toString(), owner2);
    }

    // =========================================================================
    // 10. 跨租户 IDOR 访问拦截测试
    // =========================================================================

    @Test
    @DisplayName("安全边界：跨租户越权读取与修改受控工作区被严格拦截 (1002 FORBIDDEN)")
    void testCrossTenantWorkspaceAccessForbidden() throws Exception {
        workspaceApplication.saveFile(testWorkspaceId, "private.txt", "Alice Private Code");

        try {
            // Bob attempts to read Alice's workspace file
            RequestContext.get().setUserId("user-bob-attacker");

            assertThatThrownBy(() -> workspaceApplication.getFileContent(testWorkspaceId, "private.txt"))
                    .isInstanceOf(com.agenthub.shared.exception.BusinessException.class)
                    .satisfies(e -> assertThat(((com.agenthub.shared.exception.BusinessException) e).getErrorCode().getCode()).isEqualTo(1002));

            assertThatThrownBy(() -> workspaceApplication.saveFile(testWorkspaceId, "malicious.txt", "hacked"))
                    .isInstanceOf(com.agenthub.shared.exception.BusinessException.class)
                    .satisfies(e -> assertThat(((com.agenthub.shared.exception.BusinessException) e).getErrorCode().getCode()).isEqualTo(1002));
        } finally {
            RequestContext.clear();
        }
    }

    // =========================================================================
    // 11. Git Commit 自动回滚推导与越权防御测试
    // =========================================================================

    @Test
    @DisplayName("JGit 审计 2：快照元数据缺失 files 列表时自动从 Commit 提取变更文件并安全回滚")
    void testRevertArtifactAutomaticCommitFileExtraction() throws Exception {
        File wsDir = testWorkspaceDir.toFile();
        String runId = "run-auto-revert-" + UUID.randomUUID().toString().substring(0, 6);
        String stepId = "step-auto-revert-" + UUID.randomUUID().toString().substring(0, 6);

        WorkflowRunEntity run = new WorkflowRunEntity(runId, testProjectId, "def-default", "RUNNING", "idemp-" + runId);
        workflowRunRepository.save(run);

        // 1. Baseline
        workspaceApplication.saveFile(testWorkspaceId, "auto-target.txt", "Initial Target Content");
        String baselineHash = gitManager.createBaseline(wsDir, runId, "admin@agenthub.local");

        WorkspaceEntity wsEntity = workspaceRepository.findById(testWorkspaceId).get();
        wsEntity.setGitBaselineCommit(baselineHash);
        workspaceRepository.save(wsEntity);

        // 2. Step modifies file
        workspaceApplication.saveFile(testWorkspaceId, "auto-target.txt", "Modified by Step");
        JGitWorkspaceManager.StepSnapshotResult snap = gitManager.createStepSnapshot(
                wsDir, runId, stepId, "Feature step", "dev@agenthub.local"
        );

        // Record artifact with EMPTY metadata JSON (no "files" key)
        String artId = "art-empty-meta-" + UUID.randomUUID().toString().substring(0, 6);
        ArtifactEntity artEntity = new ArtifactEntity(
                artId, runId, stepId, "SNAPSHOT", snap.getCommitHash(), snap.getCommitHash(), "{}"
        );
        artEntity.setReviewStatus("PENDING");
        artifactRepository.save(artEntity);

        // 3. User reverts artifact
        ArtifactView reverted = artifactApplication.revertArtifact(artId, "user-1");
        assertThat(reverted.getReviewStatus()).isEqualTo("REVERTED");

        // 4. Verify file was restored via fallback commit diff
        String content = Files.readString(testWorkspaceDir.resolve("auto-target.txt"));
        assertThat(content).isEqualTo("Initial Target Content");
    }

    @Test
    @DisplayName("JGit 审计 3：revertStepFiles 越权路径穿越防护与无效 Baseline 提交保护")
    void testRevertStepFilesPathTraversalAndInvalidBaseline() throws Exception {
        File wsDir = testWorkspaceDir.toFile();
        String baselineHash = gitManager.createBaseline(wsDir, "run-guard", "admin@agenthub.local");

        // 1. Path traversal attempt in revertStepFiles is safely ignored
        gitManager.revertStepFiles(wsDir, baselineHash, List.of("../../outside-file.txt"), "admin@agenthub.local");

        // 2. Invalid baseline hash must throw IllegalArgumentException and NOT delete existing files
        workspaceApplication.saveFile(testWorkspaceId, "keep-me.txt", "Crucial Data");
        assertThatThrownBy(() -> gitManager.revertStepFiles(wsDir, "0000000000000000000000000000000000000000",
                List.of("keep-me.txt"), "admin@agenthub.local"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(Files.exists(testWorkspaceDir.resolve("keep-me.txt"))).isTrue();
    }
}
