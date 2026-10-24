package com.agenthub.project.application.workspace;

import com.agenthub.audit.dto.ArtifactView;
import com.agenthub.domain.workspace.service.JGitWorkspaceManager;
import com.agenthub.domain.workspace.service.WorkspaceResolver;
import com.agenthub.project.infrastructure.entity.WorkspaceEntity;
import com.agenthub.project.infrastructure.repository.WorkspaceRepository;
import com.agenthub.audit.application.ArtifactApplication;
import com.agenthub.project.dto.WorkspaceView;
import com.agenthub.shared.exception.BusinessException;
import com.agenthub.shared.exception.ErrorCode;
import com.agenthub.shared.context.RequestContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Map;

/**
 * 工作区 JGit 操作缝（Phase 10 R8 自 WorkspaceApplicationService 抽出）：
 * 结构化 Diff、步骤快照、resync/改基线/丢弃/恢复快照；审计留痕随操作落库。
 */
@Component
public class WorkspaceGitOperations {

    private final JGitWorkspaceManager gitManager;
    private final WorkspaceResolver workspaceResolver;
    private final WorkspaceRepository workspaceRepository;
    private final ArtifactApplication artifactApplication;
    private final com.agenthub.identity.infrastructure.security.AuthAuditService auditService;

    public WorkspaceGitOperations(JGitWorkspaceManager gitManager,
                                  WorkspaceResolver workspaceResolver,
                                  @Autowired(required = false) WorkspaceRepository workspaceRepository,
                                  @Autowired(required = false) ArtifactApplication artifactApplication,
                                  @Autowired(required = false) com.agenthub.identity.infrastructure.security.AuthAuditService auditService) {
        this.gitManager = gitManager;
        this.workspaceResolver = workspaceResolver;
        this.workspaceRepository = workspaceRepository;
        this.artifactApplication = artifactApplication;
        this.auditService = auditService;
    }

    public StructuredDiffView computeStructuredDiff(String workspaceIdOrPath) throws Exception {
        Path safeDir = workspaceResolver.getWorkspaceRoot(workspaceIdOrPath);
        return new StructuredDiffView(gitManager.computeStructuredDiff(safeDir.toFile()));
    }

    public record StructuredDiffView(com.agenthub.domain.workspace.model.StructuredDiff diff) {
    }

    public Map<String, Object> createGitSnapshot(String workspaceId, String runId, String stepRunId,
                                                 String message, String author) throws Exception {
        if (gitManager == null) {
            throw new IllegalStateException("JGitWorkspaceManager is not configured");
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

        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("commitHash", snap.getCommitHash());
        result.put("tagName", snap.getTagName());
        result.put("changedFiles", snap.getChangedFiles());
        result.put("fileChecksums", snap.getFileChecksums());
        if (recordedArtifact != null) {
            result.put("artifactId", recordedArtifact.getId());
        }
        return result;
    }

    public WorkspaceView resyncWorkspace(String workspaceId) throws Exception {
        WorkspaceEntity entity = requireWorkspace(workspaceId);
        Path wsRoot = workspaceResolver.getWorkspaceRoot(workspaceId);
        gitManager.resyncWorkspace(wsRoot.toFile(), entity.getGitBaselineCommit());
        audit("WORKSPACE_RESYNC", "Resynced workspace [" + workspaceId + "] to baseline commit [" + entity.getGitBaselineCommit() + "]");
        return toWorkspaceView(entity);
    }

    public WorkspaceView changeBaseline(String workspaceId, String targetCommitHash, boolean confirmed) throws Exception {
        if (!confirmed) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Baseline change requires explicit user confirmation (confirmed=true)");
        }
        if (targetCommitHash == null || targetCommitHash.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Target commit hash must not be blank");
        }
        WorkspaceEntity entity = requireWorkspace(workspaceId);
        Path wsRoot = workspaceResolver.getWorkspaceRoot(workspaceId);
        String committedHash = gitManager.changeBaseline(wsRoot.toFile(), targetCommitHash.trim());
        entity.setGitBaselineCommit(committedHash);
        entity.setUpdatedAt(java.time.LocalDateTime.now());
        workspaceRepository.save(entity);
        audit("WORKSPACE_CHANGE_BASELINE", "Changed baseline for workspace [" + workspaceId + "] to commit [" + committedHash + "]");
        return toWorkspaceView(entity);
    }

    public WorkspaceView discardWorkspace(String workspaceId, boolean confirmed, String reason) throws Exception {
        if (!confirmed) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Discard workspace changes requires explicit user confirmation (confirmed=true)");
        }
        WorkspaceEntity entity = requireWorkspace(workspaceId);
        Path wsRoot = workspaceResolver.getWorkspaceRoot(workspaceId);
        gitManager.discardWorkspaceChanges(wsRoot.toFile(), entity.getGitBaselineCommit());
        audit("WORKSPACE_DISCARD", "Discarded changes in workspace [" + workspaceId + "], reason: " + (reason != null ? reason : "user initiated"));
        return toWorkspaceView(entity);
    }

    public WorkspaceView restoreSnapshot(String workspaceId, String snapshotCommitOrTag, boolean confirmed) throws Exception {
        if (!confirmed) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Restoring snapshot requires explicit user confirmation (confirmed=true)");
        }
        if (snapshotCommitOrTag == null || snapshotCommitOrTag.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Snapshot commit hash or tag must not be blank");
        }
        WorkspaceEntity entity = requireWorkspace(workspaceId);
        Path wsRoot = workspaceResolver.getWorkspaceRoot(workspaceId);
        String restoredCommit = gitManager.restoreSnapshot(wsRoot.toFile(), snapshotCommitOrTag.trim());
        audit("WORKSPACE_RESTORE_SNAPSHOT", "Restored snapshot in workspace [" + workspaceId + "] to [" + restoredCommit + "]");
        return toWorkspaceView(entity);
    }

    private WorkspaceEntity requireWorkspace(String workspaceId) {
        if (workspaceRepository == null) {
            throw new BusinessException(ErrorCode.WORKSPACE_NOT_FOUND, "Workspace repository not configured");
        }
        return workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WORKSPACE_NOT_FOUND, "Workspace not found: " + workspaceId));
    }

    private void audit(String eventType, String details) {
        if (auditService != null) {
            auditService.record(eventType, RequestContext.get().getUserId(), null, null, details);
        }
    }

    private WorkspaceView toWorkspaceView(WorkspaceEntity entity) {
        return new WorkspaceView(
                entity.getId(),
                entity.getProjectId(),
                entity.getRelativeRoot(),
                entity.getGitBaselineCommit(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
