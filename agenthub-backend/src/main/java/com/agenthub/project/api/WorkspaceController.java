package com.agenthub.project.api;

import com.agenthub.adapter.common.Result;
import com.agenthub.audit.application.ArtifactApplication;
import com.agenthub.audit.dto.ArtifactView;
import com.agenthub.domain.workspace.model.StructuredDiff;
import com.agenthub.domain.workspace.service.JGitWorkspaceManager;
import com.agenthub.domain.workspace.service.WorkspaceResolver;
import com.agenthub.infrastructure.concurrency.WorkspaceLockManager;
import com.agenthub.project.application.WorkspaceApplication;
import com.agenthub.project.dto.WorkspaceFileDetailView;
import com.agenthub.project.dto.WorkspaceFileNode;
import com.agenthub.shared.context.RequestContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/workspaces")
public class WorkspaceController {

    private final WorkspaceApplication workspaceApplication;
    private final WorkspaceLockManager lockManager;
    private final JGitWorkspaceManager gitManager;
    private final WorkspaceResolver workspaceResolver;
    private final ArtifactApplication artifactApplication;

    public WorkspaceController(WorkspaceApplication workspaceApplication,
                               WorkspaceLockManager lockManager) {
        this(workspaceApplication, lockManager, null, null, null);
    }

    @Autowired
    public WorkspaceController(WorkspaceApplication workspaceApplication,
                               WorkspaceLockManager lockManager,
                               @Autowired(required = false) JGitWorkspaceManager gitManager,
                               @Autowired(required = false) WorkspaceResolver workspaceResolver,
                               @Autowired(required = false) ArtifactApplication artifactApplication) {
        this.workspaceApplication = workspaceApplication;
        this.lockManager = lockManager;
        this.gitManager = gitManager;
        this.workspaceResolver = workspaceResolver;
        this.artifactApplication = artifactApplication;
    }

    public static class SaveFileRequest {
        public String path;
        public String content;
    }

    public static class GitSnapshotRequest {
        public String runId;
        public String stepRunId;
        public String author;
        public String message;
    }

    public static class RenameFileRequest {
        public String oldPath;
        public String newPath;
    }

    public static class AcquireLockRequest {
        public String ownerId;
        public long waitTimeoutMs = 3000;
        public long leaseTtlMs = 60000;
    }

    public static class RenewLockRequest {
        public String ownerId;
        public long additionalTtlMs = 60000;
    }

    public static class ReleaseLockRequest {
        public String ownerId;
    }

    @GetMapping("/{workspaceId}/tree")
    public Result<List<WorkspaceFileNode>> listFiles(
            @PathVariable("workspaceId") String workspaceId,
            @RequestParam(value = "path", required = false, defaultValue = "") String path) {
        List<WorkspaceFileNode> files = workspaceApplication.listFiles(workspaceId, path);
        return Result.ok(files);
    }

    @GetMapping("/{workspaceId}/file")
    public Result<WorkspaceFileDetailView> getFile(
            @PathVariable("workspaceId") String workspaceId,
            @RequestParam("path") String path) throws Exception {
        WorkspaceFileDetailView view = workspaceApplication.getFileDetail(workspaceId, path);
        return Result.ok(view);
    }

    @RequestMapping(value = {"/{workspaceId}/file", "/{workspaceId}/files"}, method = {RequestMethod.POST, RequestMethod.PUT})
    public Result<Void> saveFile(
            @PathVariable("workspaceId") String workspaceId,
            @RequestBody SaveFileRequest req) throws Exception {
        workspaceApplication.saveFile(workspaceId, req.path, req.content);
        return Result.ok();
    }

    @PostMapping("/{workspaceId}/file/rename")
    public Result<Void> renameFile(
            @PathVariable("workspaceId") String workspaceId,
            @RequestBody RenameFileRequest req) throws Exception {
        workspaceApplication.renameFile(workspaceId, req.oldPath, req.newPath);
        return Result.ok();
    }

    @DeleteMapping("/{workspaceId}/file")
    public Result<Void> deleteFile(
            @PathVariable("workspaceId") String workspaceId,
            @RequestParam("path") String path) throws Exception {
        workspaceApplication.deleteFile(workspaceId, path);
        return Result.ok();
    }

    @GetMapping("/{workspaceId}/download")
    public org.springframework.http.ResponseEntity<byte[]> downloadFile(
            @PathVariable("workspaceId") String workspaceId,
            @RequestParam("path") String path) throws Exception {
        byte[] data = workspaceApplication.downloadFile(workspaceId, path);
        String filename = java.nio.file.Path.of(path).getFileName().toString();
        return org.springframework.http.ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM)
                .body(data);
    }

    @GetMapping("/{workspaceId}/archive")
    public org.springframework.http.ResponseEntity<byte[]> archiveWorkspace(
            @PathVariable("workspaceId") String workspaceId) throws Exception {
        byte[] zipBytes = workspaceApplication.archiveWorkspace(workspaceId);
        return org.springframework.http.ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + workspaceId + ".zip\"")
                .contentType(org.springframework.http.MediaType.parseMediaType("application/zip"))
                .body(zipBytes);
    }

    @GetMapping({"/{workspaceId}/diff", "/{workspaceId}/git/diff-structured"})
    public Result<StructuredDiff> getStructuredDiff(
            @PathVariable("workspaceId") String workspaceId) throws Exception {
        StructuredDiff diff = workspaceApplication.computeStructuredDiff(workspaceId);
        return Result.ok(diff);
    }

    @PostMapping({"/{workspaceId}/git/snapshot", "/{workspaceId}/git/commit"})
    public Result<Map<String, Object>> createGitSnapshot(
            @PathVariable("workspaceId") String workspaceId,
            @RequestBody(required = false) GitSnapshotRequest req) throws Exception {
        workspaceApplication.checkWorkspaceAccess(workspaceId);
        if (gitManager == null || workspaceResolver == null) {
            throw new IllegalStateException("JGitWorkspaceManager or WorkspaceResolver is not configured");
        }
        if (req != null && req.runId != null && !req.runId.isBlank() && artifactApplication != null) {
            artifactApplication.validateRunWorkspaceBinding(req.runId, workspaceId);
        }
        Path wsRoot = workspaceResolver.getWorkspaceRoot(workspaceId);
        String effectiveRunId = (req != null && req.runId != null && !req.runId.isBlank()) ? req.runId : "manual-" + workspaceId;
        String effectiveStepId = (req != null && req.stepRunId != null && !req.stepRunId.isBlank()) ? req.stepRunId : "step-" + System.currentTimeMillis();
        String effectiveMessage = (req != null && req.message != null && !req.message.isBlank()) ? req.message : "Workspace snapshot commit";
        String effectiveAuthor = (req != null && req.author != null && !req.author.isBlank()) ? req.author : "operator@agenthub.local";

        JGitWorkspaceManager.StepSnapshotResult snap = gitManager.createStepSnapshot(
                wsRoot.toFile(), effectiveRunId, effectiveStepId, effectiveMessage, effectiveAuthor);

        ArtifactView recordedArtifact = null;
        if (req != null && req.runId != null && !req.runId.isBlank() && artifactApplication != null) {
            recordedArtifact = artifactApplication.recordStepSnapshot(
                    req.runId, effectiveStepId, workspaceId,
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
        return Result.ok(result);
    }

    @PostMapping("/{workspaceId}/lock/acquire")
    public Result<Boolean> acquireLock(
            @PathVariable("workspaceId") String workspaceId,
            @RequestBody(required = false) AcquireLockRequest req) {
        workspaceApplication.checkWorkspaceAccess(workspaceId);
        String currentUserId = RequestContext.get().getUserId();
        String lockOwner = (currentUserId != null && !currentUserId.isBlank())
                ? currentUserId
                : (req != null && req.ownerId != null && !req.ownerId.isBlank() ? req.ownerId : "anonymous");
        long waitTimeout = (req != null && req.waitTimeoutMs > 0) ? req.waitTimeoutMs : 3000;
        long leaseTtl = (req != null && req.leaseTtlMs > 0) ? req.leaseTtlMs : 60000;
        boolean acquired = lockManager.tryAcquireLock(workspaceId, lockOwner, waitTimeout, leaseTtl);
        return Result.ok(acquired);
    }

    @PostMapping("/{workspaceId}/lock/renew")
    public Result<Boolean> renewLock(
            @PathVariable("workspaceId") String workspaceId,
            @RequestBody(required = false) RenewLockRequest req) {
        workspaceApplication.checkWorkspaceAccess(workspaceId);
        String currentUserId = RequestContext.get().getUserId();
        String lockOwner = (currentUserId != null && !currentUserId.isBlank())
                ? currentUserId
                : (req != null && req.ownerId != null && !req.ownerId.isBlank() ? req.ownerId : "anonymous");
        long additionalTtl = (req != null && req.additionalTtlMs > 0) ? req.additionalTtlMs : 60000;
        boolean renewed = lockManager.renewLease(workspaceId, lockOwner, additionalTtl);
        return Result.ok(renewed);
    }

    @PostMapping("/{workspaceId}/lock/release")
    public Result<Boolean> releaseLock(
            @PathVariable("workspaceId") String workspaceId,
            @RequestBody(required = false) ReleaseLockRequest req) {
        workspaceApplication.checkWorkspaceAccess(workspaceId);
        String currentUserId = RequestContext.get().getUserId();
        String lockOwner = (currentUserId != null && !currentUserId.isBlank())
                ? currentUserId
                : (req != null && req.ownerId != null && !req.ownerId.isBlank() ? req.ownerId : "anonymous");
        boolean released = lockManager.releaseLock(workspaceId, lockOwner);
        return Result.ok(released);
    }

    @GetMapping("/{workspaceId}/lock/status")
    public Result<WorkspaceLockManager.LockInfo> getLockStatus(
            @PathVariable("workspaceId") String workspaceId) {
        workspaceApplication.checkWorkspaceAccess(workspaceId);
        WorkspaceLockManager.LockInfo info = lockManager.getLockInfo(workspaceId)
                .orElse(new WorkspaceLockManager.LockInfo(workspaceId, null, false, 0, 0));
        return Result.ok(info);
    }
}
