package com.agenthub.project.application;

import com.agenthub.shared.exception.BusinessException;
import com.agenthub.shared.exception.ErrorCode;
import com.agenthub.audit.application.ArtifactApplication;
import com.agenthub.audit.dto.ArtifactView;
import com.agenthub.domain.workflow.model.WorkflowModels.WorkflowDefinition;
import com.agenthub.domain.workflow.model.WorkflowModels.WorkflowExecutionResult;
import com.agenthub.domain.workflow.service.WorkflowEngineService;
import com.agenthub.domain.workspace.model.FileDiffEntry;
import com.agenthub.domain.workspace.model.StructuredDiff;
import com.agenthub.domain.workspace.service.JGitWorkspaceManager;
import com.agenthub.domain.workspace.service.WorkspaceResolver;
import com.agenthub.identity.infrastructure.security.AuthAuditService;
import com.agenthub.identity.infrastructure.security.ResourceAccessGuard;
import com.agenthub.identity.infrastructure.security.ResourceOwnershipVerifier;
import com.agenthub.infrastructure.concurrency.WorkspaceLockManager;
import com.agenthub.project.domain.quota.WorkspaceQuotaPolicy;
import com.agenthub.project.domain.quota.WorkspaceQuotaUsage;
import com.agenthub.project.dto.WorkspaceFileDetailView;
import com.agenthub.project.dto.WorkspaceFileNode;
import com.agenthub.project.dto.WorkspaceLockStatusView;
import com.agenthub.project.dto.WorkspaceView;
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
import java.util.Map;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class WorkspaceApplicationService implements WorkspaceApplication {

    @Autowired
    private com.agenthub.project.application.workspace.WorkspaceFileOperations fileOperations;

    @Autowired
    private com.agenthub.project.application.workspace.WorkspaceLockLeaseService lockLeaseService;

    @Autowired
    private com.agenthub.project.application.workspace.WorkspaceGitOperations gitOperations;


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
    private final WorkspaceQuotaGuard quotaGuard;
    private final AuthAuditService auditService;

    public WorkspaceApplicationService(JGitWorkspaceManager gitManager,
                                       WorkflowEngineService workflowEngineService,
                                       WorkspaceResolver workspaceResolver) {
        this(gitManager, workflowEngineService, workspaceResolver, null, null, null, null, null, null, null, null);
    }

    public WorkspaceApplicationService(JGitWorkspaceManager gitManager,
                                       WorkflowEngineService workflowEngineService,
                                       WorkspaceResolver workspaceResolver,
                                       WorkspaceRepository workspaceRepository,
                                       ProjectRepository projectRepository,
                                       ResourceAccessGuard accessGuard) {
        this(gitManager, workflowEngineService, workspaceResolver, workspaceRepository, projectRepository, accessGuard, null, null, null, null, null);
    }

    public WorkspaceApplicationService(JGitWorkspaceManager gitManager,
                                       WorkflowEngineService workflowEngineService,
                                       WorkspaceResolver workspaceResolver,
                                       WorkspaceRepository workspaceRepository,
                                       ProjectRepository projectRepository,
                                       ResourceAccessGuard accessGuard,
                                       WorkspaceLockManager lockManager,
                                       ArtifactApplication artifactApplication) {
        this(gitManager, workflowEngineService, workspaceResolver, workspaceRepository, projectRepository, accessGuard, lockManager, artifactApplication, null, null, null);
    }

    public WorkspaceApplicationService(JGitWorkspaceManager gitManager,
                                       WorkflowEngineService workflowEngineService,
                                       WorkspaceResolver workspaceResolver,
                                       WorkspaceRepository workspaceRepository,
                                       ProjectRepository projectRepository,
                                       ResourceAccessGuard accessGuard,
                                       WorkspaceLockManager lockManager,
                                       ArtifactApplication artifactApplication,
                                       ResourceOwnershipVerifier ownershipVerifier) {
        this(gitManager, workflowEngineService, workspaceResolver, workspaceRepository, projectRepository, accessGuard, lockManager, artifactApplication, ownershipVerifier, null, null);
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
                                       @Autowired(required = false) ResourceOwnershipVerifier ownershipVerifier,
                                       @Autowired(required = false) WorkspaceQuotaGuard quotaGuard,
                                       @Autowired(required = false) AuthAuditService auditService) {
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
        this.quotaGuard = quotaGuard != null ? quotaGuard : new WorkspaceQuotaGuard();
        this.auditService = auditService;
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
    public void checkWorkspaceWriteAccess(String workspaceIdOrPath) {
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
                    ownershipVerifier.verifyWorkspaceWriteAccess(wsOpt.get().getId(), currentUserId);
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
                    ownershipVerifier.verifyProjectAccess(projOpt.get().getId(), currentUserId, com.agenthub.team.domain.rbac.ProjectPermission.WORKSPACE_WRITE);
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
        // Phase 10 R8 深模块拆分：JGit 操作由 WorkspaceGitOperations 承载
        return gitOperations.computeStructuredDiff(workspaceIdOrPath).diff();
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
        // Phase 10 R8 深模块拆分：文件读写由 WorkspaceFileOperations 承载
        return fileOperations.listFiles(workspaceIdOrPath, relativeDirectoryPath);
    }

    @Override
    public String getFileContent(String filePath) throws Exception {
        return fileOperations.getFileContentLegacy(filePath);
    }

    @Override
    public String getFileContent(String workspaceIdOrPath, String relativePath) throws Exception {
        checkWorkspaceAccess(workspaceIdOrPath);
        return fileOperations.getFileContent(workspaceIdOrPath, relativePath);
    }

    @Override
    public WorkspaceFileDetailView getFileDetail(String workspaceIdOrPath, String relativePath) throws Exception {
        checkWorkspaceAccess(workspaceIdOrPath);
        return fileOperations.getFileDetail(workspaceIdOrPath, relativePath);
    }

    @Override
    public void saveFile(String filePath, String content) throws Exception {
        fileOperations.saveFileLegacy(filePath, content);
    }

    @Override
    public void saveFile(String workspaceIdOrPath, String relativePath, String content) throws Exception {
        checkWorkspaceWriteAccess(workspaceIdOrPath);
        fileOperations.saveFile(workspaceIdOrPath, relativePath, content);
    }

    @Override
    public void renameFile(String workspaceIdOrPath, String oldRelativePath, String newRelativePath) throws Exception {
        checkWorkspaceWriteAccess(workspaceIdOrPath);
        fileOperations.renameFile(workspaceIdOrPath, oldRelativePath, newRelativePath);
    }

    @Override
    public void deleteFile(String workspaceIdOrPath, String relativePath) throws Exception {
        checkWorkspaceWriteAccess(workspaceIdOrPath);
        fileOperations.deleteFile(workspaceIdOrPath, relativePath);
    }

    @Override
    public byte[] downloadFile(String workspaceIdOrPath, String relativePath) throws Exception {
        checkWorkspaceAccess(workspaceIdOrPath);
        return fileOperations.downloadFile(workspaceIdOrPath, relativePath);
    }

    @Override
    public byte[] archiveWorkspace(String workspaceIdOrPath) throws Exception {
        checkWorkspaceAccess(workspaceIdOrPath);
        return fileOperations.archiveWorkspace(workspaceIdOrPath);
    }

    @Override
    public String extractFilename(String relativePath) {
        return fileOperations.extractFilename(relativePath);
    }

    @Override
    public boolean acquireLock(String workspaceId, String requestedOwnerId, long waitTimeoutMs, long leaseTtlMs) {
        checkWorkspaceAccess(workspaceId);
        return lockLeaseService.acquireLock(workspaceId, requestedOwnerId, waitTimeoutMs, leaseTtlMs);
    }

    @Override
    public boolean renewLock(String workspaceId, String requestedOwnerId, long additionalTtlMs) {
        checkWorkspaceAccess(workspaceId);
        return lockLeaseService.renewLock(workspaceId, requestedOwnerId, additionalTtlMs);
    }

    @Override
    public boolean releaseLock(String workspaceId, String requestedOwnerId) {
        checkWorkspaceAccess(workspaceId);
        return lockLeaseService.releaseLock(workspaceId, requestedOwnerId);
    }

    @Override
    public WorkspaceLockStatusView getLockStatus(String workspaceId) {
        checkWorkspaceAccess(workspaceId);
        return lockLeaseService.getLockStatus(workspaceId);
    }

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

    @Override
    public WorkspaceView resyncWorkspace(String workspaceId) throws Exception {
        checkWorkspaceWriteAccess(workspaceId);
        return gitOperations.resyncWorkspace(workspaceId);
    }

    @Override
    public WorkspaceView changeBaseline(String workspaceId, String targetCommitHash, boolean confirmed) throws Exception {
        checkWorkspaceWriteAccess(workspaceId);
        return gitOperations.changeBaseline(workspaceId, targetCommitHash, confirmed);
    }

    @Override
    public WorkspaceView discardWorkspace(String workspaceId, boolean confirmed, String reason) throws Exception {
        checkWorkspaceWriteAccess(workspaceId);
        return gitOperations.discardWorkspace(workspaceId, confirmed, reason);
    }

    @Override
    public WorkspaceView restoreSnapshot(String workspaceId, String snapshotCommitOrTag, boolean confirmed) throws Exception {
        checkWorkspaceWriteAccess(workspaceId);
        return gitOperations.restoreSnapshot(workspaceId, snapshotCommitOrTag, confirmed);
    }

    @Override
    public Map<String, Object> createGitSnapshot(String workspaceId, String runId, String stepRunId, String message, String author) throws Exception {
        checkWorkspaceAccess(workspaceId);
        // Phase 10 R8 深模块拆分：步骤快照与产物登记由 WorkspaceGitOperations 承载
        return gitOperations.createGitSnapshot(workspaceId, runId, stepRunId, message, author);
    }

    @Override
    public WorkspaceQuotaUsage getWorkspaceQuota(String workspaceId) {
        checkWorkspaceAccess(workspaceId);
        Path wsRoot = workspaceResolver.getWorkspaceRoot(workspaceId);
        int concurrentLocks = lockLeaseService.concurrentLockCount(workspaceId);
        if (quotaGuard != null) {
            return quotaGuard.computeUsage(workspaceId, wsRoot, WorkspaceQuotaPolicy.defaultPolicy(), concurrentLocks);
        }
        return new WorkspaceQuotaUsage(
                workspaceId, 0L, WorkspaceQuotaPolicy.DEFAULT_MAX_CAPACITY_BYTES,
                0, WorkspaceQuotaPolicy.DEFAULT_MAX_FILES_COUNT,
                WorkspaceQuotaPolicy.DEFAULT_MAX_SINGLE_FILE_SIZE_BYTES,
                0L, WorkspaceQuotaPolicy.DEFAULT_MAX_EXECUTION_DURATION_SECONDS,
                WorkspaceQuotaPolicy.DEFAULT_ALLOW_NETWORK_ACCESS,
                concurrentLocks, WorkspaceQuotaPolicy.DEFAULT_MAX_CONCURRENT_LOCKS
        );
    }
}
