package com.agenthub.project.api;

import com.agenthub.adapter.common.Result;
import com.agenthub.project.application.GitConnectionApplication;
import com.agenthub.project.dto.connection.AuthorizeGitConnectionCommand;
import com.agenthub.project.dto.connection.GitConnectionView;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Git provider connection lifecycle endpoints (Phase 11).
 * Authorization, connectivity test and idempotent revocation; all permission
 * checks run through the project RBAC matrix inside the application layer.
 */
@RestController
@RequestMapping("/api/projects/{projectId}/git-connections")
public class GitConnectionController {

    private final GitConnectionApplication gitConnectionApplication;

    public GitConnectionController(GitConnectionApplication gitConnectionApplication) {
        this.gitConnectionApplication = gitConnectionApplication;
    }

    @PostMapping
    public ResponseEntity<Result<GitConnectionView>> authorize(
            @PathVariable("projectId") String projectId,
            @RequestBody AuthorizeGitConnectionCommand cmd) {
        GitConnectionView view = gitConnectionApplication.authorize(projectId, cmd);
        return ResponseEntity.ok()
                .header(HttpHeaders.ETAG, "\"" + view.getId() + "-" + view.getUpdatedAt() + "\"")
                .body(Result.ok(view));
    }

    @GetMapping
    public Result<List<GitConnectionView>> list(@PathVariable("projectId") String projectId) {
        return Result.ok(gitConnectionApplication.list(projectId));
    }

    @PostMapping("/{connectionId}/test")
    public Result<GitConnectionView> test(
            @PathVariable("projectId") String projectId,
            @PathVariable("connectionId") String connectionId) {
        return Result.ok(gitConnectionApplication.testConnection(projectId, connectionId));
    }

    @PostMapping("/{connectionId}/revoke")
    public Result<GitConnectionView> revoke(
            @PathVariable("projectId") String projectId,
            @PathVariable("connectionId") String connectionId) {
        return Result.ok(gitConnectionApplication.revoke(projectId, connectionId));
    }
}
