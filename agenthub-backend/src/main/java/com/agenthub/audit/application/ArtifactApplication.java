package com.agenthub.audit.application;

import com.agenthub.audit.dto.ArtifactView;

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
}
