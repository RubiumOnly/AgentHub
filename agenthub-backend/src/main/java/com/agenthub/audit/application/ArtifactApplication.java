package com.agenthub.audit.application;

import com.agenthub.audit.dto.ArtifactLineageView;
import com.agenthub.audit.dto.ArtifactView;
import com.agenthub.domain.workspace.model.StructuredDiff;

import java.util.List;
import java.util.Map;

public interface ArtifactApplication {
    List<ArtifactView> listArtifactsByRunId(String runId);
    ArtifactView getArtifactById(String artifactId);
    ArtifactView acceptArtifact(String artifactId, String reviewerId);
    ArtifactView rejectArtifact(String artifactId, String reviewerId, String reason);
    ArtifactView revertArtifact(String artifactId, String actorId);
    ArtifactView recordStepSnapshot(String runId, String stepRunId, String workspaceId,
                                    String commitHash, String tagName, List<String> changedFiles,
                                    Map<String, String> fileChecksums, String author);

    ArtifactLineageView getArtifactLineageByRunId(String runId);
    StructuredDiff getArtifactDiff(String artifactId);

    // Phase 5 Quality Gate & Controlled Review
    com.agenthub.audit.domain.quality.QualityGateReport evaluateQualityGate(String artifactId);
    com.agenthub.audit.domain.quality.QualityGateReport getQualityGateReport(String artifactId);
    com.agenthub.audit.dto.PullRequestDraftView generatePullRequestDraft(String artifactId);
    ArtifactView revertSingleFile(String artifactId, String filePath, String actorId);

    default void validateRunWorkspaceBinding(String runId, String workspaceId) {}
}
