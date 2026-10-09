package com.agenthub.project.api;

import com.agenthub.adapter.common.Result;
import com.agenthub.domain.workspace.model.StructuredDiff;
import com.agenthub.infrastructure.concurrency.WorkspaceLockManager;
import com.agenthub.project.application.WorkspaceApplication;
import com.agenthub.project.dto.WorkspaceFileDetailView;
import com.agenthub.project.dto.WorkspaceFileNode;
import com.agenthub.shared.context.RequestContext;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/workspaces")
public class WorkspaceController {

    private final WorkspaceApplication workspaceApplication;
    private final WorkspaceLockManager lockManager;

    public WorkspaceController(WorkspaceApplication workspaceApplication,
                               WorkspaceLockManager lockManager) {
        this.workspaceApplication = workspaceApplication;
        this.lockManager = lockManager;
    }

    public static class SaveFileRequest {
        public String path;
        public String content;
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

    @PostMapping("/{workspaceId}/file")
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

    @GetMapping("/{workspaceId}/diff")
    public Result<StructuredDiff> getStructuredDiff(
            @PathVariable("workspaceId") String workspaceId) throws Exception {
        StructuredDiff diff = workspaceApplication.computeStructuredDiff(workspaceId);
        return Result.ok(diff);
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
