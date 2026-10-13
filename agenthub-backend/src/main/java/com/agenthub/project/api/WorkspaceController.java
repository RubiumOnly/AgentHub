package com.agenthub.project.api;

import com.agenthub.adapter.common.Result;
import com.agenthub.domain.workspace.model.StructuredDiff;
import com.agenthub.project.application.WorkspaceApplication;
import com.agenthub.project.dto.WorkspaceFileDetailView;
import com.agenthub.project.dto.WorkspaceFileNode;
import com.agenthub.project.dto.WorkspaceLockStatusView;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/workspaces")
public class WorkspaceController {

    private final WorkspaceApplication workspaceApplication;

    public WorkspaceController(WorkspaceApplication workspaceApplication) {
        this.workspaceApplication = workspaceApplication;
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
    public ResponseEntity<byte[]> downloadFile(
            @PathVariable("workspaceId") String workspaceId,
            @RequestParam("path") String path) throws Exception {
        byte[] data = workspaceApplication.downloadFile(workspaceId, path);
        String filename = workspaceApplication.extractFilename(path);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(data);
    }

    @GetMapping("/{workspaceId}/archive")
    public ResponseEntity<byte[]> archiveWorkspace(
            @PathVariable("workspaceId") String workspaceId) throws Exception {
        byte[] zipBytes = workspaceApplication.archiveWorkspace(workspaceId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + workspaceId + ".zip\"")
                .contentType(MediaType.parseMediaType("application/zip"))
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
        Map<String, Object> result = workspaceApplication.createGitSnapshot(
                workspaceId,
                req != null ? req.runId : null,
                req != null ? req.stepRunId : null,
                req != null ? req.message : null,
                req != null ? req.author : null
        );
        return Result.ok(result);
    }

    @PostMapping("/{workspaceId}/lock/acquire")
    public Result<Boolean> acquireLock(
            @PathVariable("workspaceId") String workspaceId,
            @RequestBody(required = false) AcquireLockRequest req) {
        boolean acquired = workspaceApplication.acquireLock(
                workspaceId,
                req != null ? req.ownerId : null,
                req != null ? req.waitTimeoutMs : 3000,
                req != null ? req.leaseTtlMs : 60000
        );
        return Result.ok(acquired);
    }

    @PostMapping("/{workspaceId}/lock/renew")
    public Result<Boolean> renewLock(
            @PathVariable("workspaceId") String workspaceId,
            @RequestBody(required = false) RenewLockRequest req) {
        boolean renewed = workspaceApplication.renewLock(
                workspaceId,
                req != null ? req.ownerId : null,
                req != null ? req.additionalTtlMs : 60000
        );
        return Result.ok(renewed);
    }

    @PostMapping("/{workspaceId}/lock/release")
    public Result<Boolean> releaseLock(
            @PathVariable("workspaceId") String workspaceId,
            @RequestBody(required = false) ReleaseLockRequest req) {
        boolean released = workspaceApplication.releaseLock(
                workspaceId,
                req != null ? req.ownerId : null
        );
        return Result.ok(released);
    }

    @GetMapping("/{workspaceId}/lock/status")
    public Result<WorkspaceLockStatusView> getLockStatus(
            @PathVariable("workspaceId") String workspaceId) {
        WorkspaceLockStatusView info = workspaceApplication.getLockStatus(workspaceId);
        return Result.ok(info);
    }
}
