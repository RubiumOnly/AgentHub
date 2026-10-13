package com.agenthub.project.application;

import com.agenthub.adapter.common.BusinessException;
import com.agenthub.adapter.common.ErrorCode;
import com.agenthub.audit.application.ArtifactApplication;
import com.agenthub.audit.dto.ArtifactView;
import com.agenthub.domain.workflow.model.WorkflowModels.WorkflowDefinition;
import com.agenthub.domain.workflow.model.WorkflowModels.WorkflowExecutionResult;
import com.agenthub.domain.workflow.service.WorkflowEngineService;
import com.agenthub.domain.workspace.model.FileDiffEntry;
import com.agenthub.domain.workspace.model.StructuredDiff;
import com.agenthub.domain.workspace.service.JGitWorkspaceManager;
import com.agenthub.domain.workspace.service.WorkspaceResolver;
import com.agenthub.identity.infrastructure.security.ResourceAccessGuard;
import com.agenthub.identity.infrastructure.security.ResourceOwnershipVerifier;
import com.agenthub.infrastructure.concurrency.WorkspaceLockManager;
import com.agenthub.project.dto.WorkspaceFileDetailView;
import com.agenthub.project.dto.WorkspaceFileNode;
import com.agenthub.project.dto.WorkspaceLockStatusView;
import com.agenthub.project.infrastructure.entity.ProjectEntity;
import com.agenthub.project.infrastructure.entity.WorkspaceEntity;
import com.agenthub.project.infrastructure.repository.ProjectRepository;
import com.agenthub.project.infrastructure.repository.WorkspaceRepository;
import com.agenthub.shared.context.RequestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class WorkspaceApplicationService implements WorkspaceApplication {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceApplicationService.class);
    private static final long MAX_WRITE_BYTES = 10 * 1024 * 1024L; // 10 MB
    private static final long MAX_PREVIEW_BYTES = 2 * 1024 * 1024L; // 2 MB

    @Value("${agenthub.workspace.base-dir:./data/workspaces}")
    private String workspaceBaseDir;

    private final JGitWorkspaceManager gitManager;
    private final WorkflowEngineService workflowEngineService;
    private final WorkspaceResolver workspaceResolver;
    private final WorkspaceRepository workspaceRepository;
    private final ProjectRepository projectRepository;
    private final ResourceAccessGuard accessGuard;
    private final WorkspaceLockManager lockManager;
    private final ArtifactApplication artifactApplication;
    private final ResourceOwnershipVerifier ownershipVerifier;

    public WorkspaceApplicationService(JGitWorkspaceManager gitManager,
                                       WorkflowEngineService workflowEngineService,
                                       WorkspaceResolver workspaceResolver) {
        this(gitManager, workflowEngineService, workspaceResolver, null, null, null, null, null, null);
    }

    public WorkspaceApplicationService(JGitWorkspaceManager gitManager,
                                       WorkflowEngineService workflowEngineService,
                                       WorkspaceResolver workspaceResolver,
                                       WorkspaceRepository workspaceRepository,
                                       ProjectRepository projectRepository,
                                       ResourceAccessGuard accessGuard) {
        this(gitManager, workflowEngineService, workspaceResolver, workspaceRepository, projectRepository, accessGuard, null, null, null);
    }

    public WorkspaceApplicationService(JGitWorkspaceManager gitManager,
                                       WorkflowEngineService workflowEngineService,
                                       WorkspaceResolver workspaceResolver,
                                       WorkspaceRepository workspaceRepository,
                                       ProjectRepository projectRepository,
                                       ResourceAccessGuard accessGuard,
                                       WorkspaceLockManager lockManager,
                                       ArtifactApplication artifactApplication) {
        this(gitManager, workflowEngineService, workspaceResolver, workspaceRepository, projectRepository, accessGuard, lockManager, artifactApplication, null);
    }

    @Autowired
    public WorkspaceApplicationService(JGitWorkspaceManager gitManager,
                                       WorkflowEngineService workflowEngineService,
                                       WorkspaceResolver workspaceResolver,
                                       @Autowired(required = false) WorkspaceRepository workspaceRepository,
                                       @Autowired(required = false) ProjectRepository projectRepository,
                                       @Autowired(required = false) ResourceAccessGuard accessGuard,
                                       @Autowired(required = false) WorkspaceLockManager lockManager,
                                       @Autowired(required = false) ArtifactApplication artifactApplication,
                                       @Autowired(required = false) ResourceOwnershipVerifier ownershipVerifier) {
        this.gitManager = gitManager;
        this.workflowEngineService = workflowEngineService;
        this.workspaceResolver = workspaceResolver;
        this.workspaceRepository = workspaceRepository;
        this.projectRepository = projectRepository;
        this.accessGuard = accessGuard;
        this.lockManager = lockManager;
        this.artifactApplication = artifactApplication;
        this.ownershipVerifier = ownershipVerifier != null ? ownershipVerifier
                : (accessGuard != null ? new ResourceOwnershipVerifier(accessGuard, projectRepository, workspaceRepository, null, null, null, null) : null);
    }

    @Override
    public void checkWorkspaceAccess(String workspaceIdOrPath) {
        if (RequestContext.get().isSystem()) {
            return;
        }
        if (workspaceRepository != null && projectRepository != null && accessGuard != null) {
            String currentUserId = RequestContext.get().getUserId();
            Optional<WorkspaceEntity> wsOpt = workspaceRepository.findById(workspaceIdOrPath);
            if (wsOpt.isEmpty()) {
                wsOpt = workspaceRepository.findByProjectId(workspaceIdOrPath);
            }
            if (wsOpt.isPresent()) {
                if (ownershipVerifier != null) {
                    ownershipVerifier.verifyWorkspaceAccess(wsOpt.get().getId(), currentUserId);
                    return;
                }
                ProjectEntity project = projectRepository.findById(wsOpt.get().getProjectId()).orElse(null);
                if (project != null) {
                    accessGuard.checkOwnership(project.getOwnerId(), currentUserId);
                    return;
                }
            }
            Optional<ProjectEntity> projOpt = projectRepository.findById(workspaceIdOrPath);
            if (projOpt.isPresent()) {
                if (ownershipVerifier != null) {
                    ownershipVerifier.verifyProjectAccess(projOpt.get().getId(), currentUserId);
                    return;
                }
                accessGuard.checkOwnership(projOpt.get().getOwnerId(), currentUserId);
                return;
            }
            if (currentUserId == null || currentUserId.isBlank()) {
                accessGuard.checkOwnership("unauthenticated-resource", null);
            }
        }
    }

    @Override
    public StructuredDiff computeStructuredDiff(String workspaceIdOrPath) throws Exception {
        checkWorkspaceAccess(workspaceIdOrPath);
        Path safeDir = workspaceResolver.getWorkspaceRoot(workspaceIdOrPath);
        return gitManager.computeStructuredDiff(safeDir.toFile());
    }

    @Override
    public List<FileDiffEntry> computeDiff(String path) throws Exception {
        return computeStructuredDiff(path).getEntries();
    }

    @Override
    public List<WorkspaceFileNode> listFiles(String workspaceIdOrPath) {
        return listFiles(workspaceIdOrPath, "");
    }

    @Override
    public List<WorkspaceFileNode> listFiles(String workspaceIdOrPath, String relativeDirectoryPath) {
        checkWorkspaceAccess(workspaceIdOrPath);
        Path root = workspaceResolver.getWorkspaceRoot(workspaceIdOrPath);
        Path targetDir = root;
        if (relativeDirectoryPath != null && !relativeDirectoryPath.isBlank()) {
            targetDir = workspaceResolver.resolvePathForRead(workspaceIdOrPath, relativeDirectoryPath);
        }

        File current = targetDir.toFile();
        List<WorkspaceFileNode> fileNodes = new ArrayList<>();
        if (!current.exists() || !current.isDirectory()) {
            return fileNodes;
        }

        scanFiles(current, root.toFile(), fileNodes);
        return fileNodes;
    }

    @Override
    public String getFileContent(String filePath) throws Exception {
        Path safeFile = workspaceResolver.resolveLegacyPath(filePath, false);
        File file = safeFile.toFile();
        if (!file.exists() || file.isDirectory()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "File not found: " + filePath);
        }
        if (file.length() > MAX_PREVIEW_BYTES) {
            throw new BusinessException(ErrorCode.WORKSPACE_FILE_TOO_LARGE,
                    "File size exceeds maximum preview limit of 2MB: " + file.length() + " bytes");
        }
        if (JGitWorkspaceManager.isBinaryFile(file)) {
            throw new BusinessException(ErrorCode.WORKSPACE_BINARY_PREVIEW_DENIED,
                    "Binary file preview is not supported: " + file.getName());
        }
        return Files.readString(safeFile, StandardCharsets.UTF_8);
    }

    @Override
    public String getFileContent(String workspaceIdOrPath, String relativePath) throws Exception {
        checkWorkspaceAccess(workspaceIdOrPath);
        Path safeFile = workspaceResolver.resolvePathForRead(workspaceIdOrPath, relativePath);
        File file = safeFile.toFile();
        if (!file.exists() || file.isDirectory()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "File not found: " + relativePath);
        }
        if (file.length() > MAX_PREVIEW_BYTES) {
            throw new BusinessException(ErrorCode.WORKSPACE_FILE_TOO_LARGE,
                    "File size exceeds maximum preview limit of 2MB: " + file.length() + " bytes");
        }
        if (JGitWorkspaceManager.isBinaryFile(file)) {
            throw new BusinessException(ErrorCode.WORKSPACE_BINARY_PREVIEW_DENIED,
                    "Binary file preview is not supported: " + file.getName());
        }
        return Files.readString(safeFile, StandardCharsets.UTF_8);
    }

    @Override
    public WorkspaceFileDetailView getFileDetail(String workspaceIdOrPath, String relativePath) throws Exception {
        checkWorkspaceAccess(workspaceIdOrPath);
        Path safeFile = workspaceResolver.resolvePathForRead(workspaceIdOrPath, relativePath);
        File file = safeFile.toFile();
        if (!file.exists() || file.isDirectory()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "File not found: " + relativePath);
        }

        boolean isBinary = JGitWorkspaceManager.isBinaryFile(file);
        long size = file.length();
        long lastModified = file.lastModified();
        String name = file.getName();

        if (isBinary) {
            return new WorkspaceFileDetailView(name, relativePath, null, true, size, false, lastModified);
        }

        if (size > MAX_PREVIEW_BYTES) {
            byte[] bytes = new byte[(int) MAX_PREVIEW_BYTES];
            try (var in = Files.newInputStream(safeFile)) {
                int read = in.read(bytes);
                String truncatedContent = new String(bytes, 0, Math.max(0, read), StandardCharsets.UTF_8);
                return new WorkspaceFileDetailView(name, relativePath, truncatedContent, false, size, true, lastModified);
            }
        }

        String content = Files.readString(safeFile, StandardCharsets.UTF_8);
        return new WorkspaceFileDetailView(name, relativePath, content, false, size, false, lastModified);
    }

    @Override
    public void saveFile(String filePath, String content) throws Exception {
        validateWriteSize(content);
        Path safeFile = workspaceResolver.resolveLegacyPath(filePath, true);
        if (safeFile.getParent() != null && !Files.exists(safeFile.getParent())) {
            Files.createDirectories(safeFile.getParent());
        }
        Files.writeString(safeFile, content != null ? content : "", StandardCharsets.UTF_8);
    }

    @Override
    public void saveFile(String workspaceIdOrPath, String relativePath, String content) throws Exception {
        checkWorkspaceAccess(workspaceIdOrPath);
        validateWriteSize(content);
        Path safeFile = workspaceResolver.resolvePathForWrite(workspaceIdOrPath, relativePath);
        if (safeFile.getParent() != null && !Files.exists(safeFile.getParent())) {
            Files.createDirectories(safeFile.getParent());
        }
        Files.writeString(safeFile, content != null ? content : "", StandardCharsets.UTF_8);
    }

    @Override
    public void renameFile(String workspaceIdOrPath, String oldRelativePath, String newRelativePath) throws Exception {
        checkWorkspaceAccess(workspaceIdOrPath);
        Path src = workspaceResolver.resolvePathForRead(workspaceIdOrPath, oldRelativePath);
        Path dest = workspaceResolver.resolvePathForWrite(workspaceIdOrPath, newRelativePath);

        if (!Files.exists(src)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Source file not found: " + oldRelativePath);
        }
        if (Files.exists(dest)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Target file already exists: " + newRelativePath);
        }
        if (dest.getParent() != null && !Files.exists(dest.getParent())) {
            Files.createDirectories(dest.getParent());
        }
        Files.move(src, dest);
        log.info("Renamed file in workspace [{}] from [{}] to [{}]", workspaceIdOrPath, oldRelativePath, newRelativePath);
    }

    @Override
    public void deleteFile(String workspaceIdOrPath, String relativePath) throws Exception {
        checkWorkspaceAccess(workspaceIdOrPath);
        Path target = workspaceResolver.resolvePathForWrite(workspaceIdOrPath, relativePath);
        if (!Files.exists(target)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "File not found: " + relativePath);
        }

        if (Files.isDirectory(target)) {
            try (var stream = Files.walk(target)) {
                stream.sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            }
        } else {
            Files.delete(target);
        }
        log.info("Deleted file in workspace [{}]: [{}]", workspaceIdOrPath, relativePath);
    }

    @Override
    public byte[] downloadFile(String workspaceIdOrPath, String relativePath) throws Exception {
        checkWorkspaceAccess(workspaceIdOrPath);
        Path safeFile = workspaceResolver.resolvePathForRead(workspaceIdOrPath, relativePath);
        File file = safeFile.toFile();
        if (!file.exists() || file.isDirectory()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "File not found: " + relativePath);
        }
        if (file.length() > MAX_WRITE_BYTES) {
            throw new BusinessException(ErrorCode.WORKSPACE_FILE_TOO_LARGE,
                    "File size exceeds maximum download limit of 10MB: " + file.length() + " bytes");
        }
        return Files.readAllBytes(safeFile);
    }

    @Override
    public String extractFilename(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return "download.bin";
        }
        Path fileName = Path.of(relativePath).getFileName();
        return fileName != null ? fileName.toString() : "download.bin";
    }

    @Override
    public byte[] archiveWorkspace(String workspaceIdOrPath) throws Exception {
        checkWorkspaceAccess(workspaceIdOrPath);
        Path root = workspaceResolver.getWorkspaceRoot(workspaceIdOrPath);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            try (var stream = Files.walk(root)) {
                for (Path path : stream.toList()) {
                    if (Files.isDirectory(path)) continue;
                    Path rel = root.relativize(path);
                    String relStr = rel.toString().replace('\\', '/');
                    if (relStr.startsWith(".git/") || relStr.equals(".git")) continue;
                    zos.putNextEntry(new ZipEntry(relStr));
                    Files.copy(path, zos);
                    zos.closeEntry();
                }
            }
        }
        return baos.toByteArray();
    }

    @Override
    public Map<String, Object> createGitSnapshot(String workspaceId, String runId, String stepRunId, String message, String author) throws Exception {
        checkWorkspaceAccess(workspaceId);
        if (gitManager == null || workspaceResolver == null) {
            throw new IllegalStateException("JGitWorkspaceManager or WorkspaceResolver is not configured");
        }
        if (runId != null && !runId.isBlank() && artifactApplication != null) {
            artifactApplication.validateRunWorkspaceBinding(runId, workspaceId);
        }
        Path wsRoot = workspaceResolver.getWorkspaceRoot(workspaceId);
        String effectiveRunId = (runId != null && !runId.isBlank()) ? runId : "manual-" + workspaceId;
        String effectiveStepId = (stepRunId != null && !stepRunId.isBlank()) ? stepRunId : "step-" + System.currentTimeMillis();
        String effectiveMessage = (message != null && !message.isBlank()) ? message : "Workspace snapshot commit";
        String effectiveAuthor = (author != null && !author.isBlank()) ? author : "operator@agenthub.local";

        JGitWorkspaceManager.StepSnapshotResult snap = gitManager.createStepSnapshot(
                wsRoot.toFile(), effectiveRunId, effectiveStepId, effectiveMessage, effectiveAuthor);

        ArtifactView recordedArtifact = null;
        if (runId != null && !runId.isBlank() && artifactApplication != null) {
            recordedArtifact = artifactApplication.recordStepSnapshot(
                    runId, effectiveStepId, workspaceId,
                    snap.getCommitHash(), snap.getTagName(),
                    snap.getChangedFiles(), snap.getFileChecksums(), effectiveAuthor);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("commitHash", snap.getCommitHash());
        result.put("tagName", snap.getTagName());
        result.put("changedFiles", snap.getChangedFiles());
        result.put("fileChecksums", snap.getFileChecksums());
        if (recordedArtifact != null) {
            result.put("artifactId", recordedArtifact.getId());
        }
        return result;
    }

    @Override
    public boolean acquireLock(String workspaceId, String requestedOwnerId, long waitTimeoutMs, long leaseTtlMs) {
        checkWorkspaceAccess(workspaceId);
        if (lockManager == null) {
            return false;
        }
        String lockOwner = resolveLockOwner(requestedOwnerId);
        long waitTimeout = waitTimeoutMs > 0 ? waitTimeoutMs : 3000;
        long leaseTtl = leaseTtlMs > 0 ? leaseTtlMs : 60000;
        return lockManager.tryAcquireLock(workspaceId, lockOwner, waitTimeout, leaseTtl);
    }

    @Override
    public boolean renewLock(String workspaceId, String requestedOwnerId, long additionalTtlMs) {
        checkWorkspaceAccess(workspaceId);
        if (lockManager == null) {
            return false;
        }
        String lockOwner = resolveLockOwner(requestedOwnerId);
        long additionalTtl = additionalTtlMs > 0 ? additionalTtlMs : 60000;
        return lockManager.renewLease(workspaceId, lockOwner, additionalTtl);
    }

    @Override
    public boolean releaseLock(String workspaceId, String requestedOwnerId) {
        checkWorkspaceAccess(workspaceId);
        if (lockManager == null) {
            return false;
        }
        String lockOwner = resolveLockOwner(requestedOwnerId);
        return lockManager.releaseLock(workspaceId, lockOwner);
    }

    @Override
    public WorkspaceLockStatusView getLockStatus(String workspaceId) {
        checkWorkspaceAccess(workspaceId);
        if (lockManager == null) {
            return new WorkspaceLockStatusView(workspaceId, workspaceId, null, false, 0, 0, 0);
        }
        return lockManager.getLockInfo(workspaceId)
                .map(info -> new WorkspaceLockStatusView(
                        workspaceId,
                        info.getWorkspaceKey(),
                        info.getOwnerId(),
                        info.isLocked(),
                        info.getAcquiredAt(),
                        info.getExpiresAt(),
                        info.getRemainingTtlMs()
                ))
                .orElse(new WorkspaceLockStatusView(workspaceId, workspaceId, null, false, 0, 0, 0));
    }

    private String resolveLockOwner(String requestedOwnerId) {
        String currentUserId = RequestContext.get().getUserId();
        if (currentUserId != null && !currentUserId.isBlank()) {
            return currentUserId;
        }
        return (requestedOwnerId != null && !requestedOwnerId.isBlank()) ? requestedOwnerId : "anonymous";
    }

    @Override
    public WorkflowExecutionResult executeWorkflow(WorkflowDefinition workflow, String workspacePath, String taskPrompt) {
        String targetPath = (workspacePath != null && !workspacePath.isBlank())
                ? workspacePath : workspaceBaseDir + "/default";
        Path safeWorkspace = workspaceResolver.resolveLegacyPath(targetPath, false);
        return workflowEngineService.executeWorkflow(workflow, safeWorkspace.toString(), taskPrompt);
    }

    private void validateWriteSize(String content) {
        if (content != null) {
            byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
            if (bytes.length > MAX_WRITE_BYTES) {
                throw new BusinessException(ErrorCode.WORKSPACE_FILE_TOO_LARGE,
                        "File content exceeds maximum write limit of 10MB: " + bytes.length + " bytes");
            }
        }
    }

    private void scanFiles(File current, File root, List<WorkspaceFileNode> nodes) {
        File[] files = current.listFiles();
        if (files == null) return;

        for (File f : files) {
            if (f.getName().equalsIgnoreCase(".git")) continue;
            String relative = root.toPath().relativize(f.toPath()).toString().replace("\\", "/");
            boolean isBinary = !f.isDirectory() && JGitWorkspaceManager.isBinaryFile(f);
            nodes.add(new WorkspaceFileNode(f.getName(), relative, f.isDirectory(), f.length(), isBinary, f.lastModified()));
            if (f.isDirectory()) {
                scanFiles(f, root, nodes);
            }
        }
    }
}
