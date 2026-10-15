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

    @GetMapping("/lineage/{runId}")
    public Result<com.agenthub.audit.dto.ArtifactLineageView> getArtifactLineage(@PathVariable("runId") String runId) {
        com.agenthub.audit.dto.ArtifactLineageView lineage = artifactApplication.getArtifactLineageByRunId(runId);
        return Result.ok(lineage);
    }

    @GetMapping("/{artifactId}/diff")
    public Result<com.agenthub.domain.workspace.model.StructuredDiff> getArtifactDiff(@PathVariable("artifactId") String artifactId) {
        com.agenthub.domain.workspace.model.StructuredDiff diff = artifactApplication.getArtifactDiff(artifactId);
        return Result.ok(diff);
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

    public static class RevertFileRequest {
        public String filePath;
    }

    @GetMapping("/{artifactId}/quality-gate")
    public Result<com.agenthub.audit.domain.quality.QualityGateReport> getQualityGateReport(@PathVariable("artifactId") String artifactId) {
        com.agenthub.audit.domain.quality.QualityGateReport report = artifactApplication.getQualityGateReport(artifactId);
        return Result.ok(report);
    }

    @PostMapping("/{artifactId}/quality-gate/evaluate")
    public Result<com.agenthub.audit.domain.quality.QualityGateReport> evaluateQualityGate(@PathVariable("artifactId") String artifactId) {
        com.agenthub.audit.domain.quality.QualityGateReport report = artifactApplication.evaluateQualityGate(artifactId);
        return Result.ok(report);
    }

    @GetMapping("/{artifactId}/pr-draft")
    public Result<com.agenthub.audit.dto.PullRequestDraftView> getPullRequestDraft(@PathVariable("artifactId") String artifactId) {
        com.agenthub.audit.dto.PullRequestDraftView draft = artifactApplication.generatePullRequestDraft(artifactId);
        return Result.ok(draft);
    }

    @PostMapping("/{artifactId}/revert-file")
    public Result<ArtifactView> revertSingleFile(
            @PathVariable("artifactId") String artifactId,
            @RequestBody(required = false) RevertFileRequest req,
            @RequestParam(value = "filePath", required = false) String queryFilePath) {
        String currentUserId = RequestContext.get().getUserId();
        String path = (req != null && req.filePath != null && !req.filePath.isBlank()) ? req.filePath : queryFilePath;
        ArtifactView view = artifactApplication.revertSingleFile(artifactId, path, currentUserId);
        return Result.ok(view);
    }
}
