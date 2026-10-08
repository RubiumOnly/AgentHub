package com.agenthub.audit.api;

import com.agenthub.adapter.common.Result;
import com.agenthub.audit.application.ArtifactApplication;
import com.agenthub.audit.dto.ArtifactView;
import com.agenthub.shared.context.RequestContext;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/audit/artifacts")
public class ArtifactController {

    private final ArtifactApplication artifactApplication;

    public ArtifactController(ArtifactApplication artifactApplication) {
        this.artifactApplication = artifactApplication;
    }

    public static class RejectArtifactRequest {
        public String reason;
    }

    @GetMapping("/run/{runId}")
    public Result<List<ArtifactView>> listArtifactsByRunId(@PathVariable("runId") String runId) {
        List<ArtifactView> artifacts = artifactApplication.listArtifactsByRunId(runId);
        return Result.ok(artifacts);
    }

    @GetMapping("/{artifactId}")
    public Result<ArtifactView> getArtifactById(@PathVariable("artifactId") String artifactId) {
        ArtifactView artifact = artifactApplication.getArtifactById(artifactId);
        return Result.ok(artifact);
    }

    @PostMapping("/{artifactId}/accept")
    public Result<ArtifactView> acceptArtifact(@PathVariable("artifactId") String artifactId) {
        String currentUserId = RequestContext.get().getUserId();
        ArtifactView view = artifactApplication.acceptArtifact(artifactId, currentUserId);
        return Result.ok(view);
    }

    @PostMapping("/{artifactId}/reject")
    public Result<ArtifactView> rejectArtifact(
            @PathVariable("artifactId") String artifactId,
            @RequestBody(required = false) RejectArtifactRequest req) {
        String currentUserId = RequestContext.get().getUserId();
        String reason = (req != null) ? req.reason : "Rejected by user";
        ArtifactView view = artifactApplication.rejectArtifact(artifactId, currentUserId, reason);
        return Result.ok(view);
    }

    @PostMapping("/{artifactId}/revert")
    public Result<ArtifactView> revertArtifact(@PathVariable("artifactId") String artifactId) {
        String currentUserId = RequestContext.get().getUserId();
        ArtifactView view = artifactApplication.revertArtifact(artifactId, currentUserId);
        return Result.ok(view);
    }
}
