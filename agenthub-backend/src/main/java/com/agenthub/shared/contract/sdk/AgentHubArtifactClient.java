package com.agenthub.shared.contract.sdk;

import com.agenthub.audit.domain.quality.QualityGateReport;
import com.agenthub.audit.dto.ArtifactLineageView;
import com.agenthub.audit.dto.ArtifactView;
import com.agenthub.audit.dto.PullRequestDraftView;

import java.util.List;

/**
 * Standard public SDK contract for artifact management, quality gate evaluation,
 * PR draft generation, and single-file/full-artifact reverting.
 */
public interface AgentHubArtifactClient {

    List<ArtifactView> listArtifactsByRunId(String runId);

    ArtifactView getArtifactById(String artifactId);

    ArtifactLineageView getArtifactLineageByRunId(String runId);

    QualityGateReport evaluateQualityGate(String artifactId);

    QualityGateReport getQualityGateReport(String artifactId);

    PullRequestDraftView generatePullRequestDraft(String artifactId);

    ArtifactView revertArtifact(String artifactId);

    ArtifactView revertSingleFile(String artifactId, String relativeFilePath);
}
