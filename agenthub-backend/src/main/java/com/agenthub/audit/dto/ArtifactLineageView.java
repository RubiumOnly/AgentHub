package com.agenthub.audit.dto;

import java.util.List;

public class ArtifactLineageView {
    private String runId;
    private String projectId;
    private String workspaceId;
    private String baselineCommitHash;
    private String currentCommitHash;
    private List<StepLineageNode> stepNodes;
    private List<ArtifactView> artifacts;
    private int totalArtifacts;
    private int totalFilesChanged;
    private boolean isReverted;

    public ArtifactLineageView() {}

    public ArtifactLineageView(String runId, String projectId, String workspaceId,
                               String baselineCommitHash, String currentCommitHash,
                               List<StepLineageNode> stepNodes, List<ArtifactView> artifacts,
                               int totalArtifacts, int totalFilesChanged, boolean isReverted) {
        this.runId = runId;
        this.projectId = projectId;
        this.workspaceId = workspaceId;
        this.baselineCommitHash = baselineCommitHash;
        this.currentCommitHash = currentCommitHash;
        this.stepNodes = stepNodes;
        this.artifacts = artifacts;
        this.totalArtifacts = totalArtifacts;
        this.totalFilesChanged = totalFilesChanged;
        this.isReverted = isReverted;
    }

    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }

    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }

    public String getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(String workspaceId) { this.workspaceId = workspaceId; }

    public String getBaselineCommitHash() { return baselineCommitHash; }
    public void setBaselineCommitHash(String baselineCommitHash) { this.baselineCommitHash = baselineCommitHash; }

    public String getCurrentCommitHash() { return currentCommitHash; }
    public void setCurrentCommitHash(String currentCommitHash) { this.currentCommitHash = currentCommitHash; }

    public List<StepLineageNode> getStepNodes() { return stepNodes; }
    public void setStepNodes(List<StepLineageNode> stepNodes) { this.stepNodes = stepNodes; }

    public List<ArtifactView> getArtifacts() { return artifacts; }
    public void setArtifacts(List<ArtifactView> artifacts) { this.artifacts = artifacts; }

    public int getTotalArtifacts() { return totalArtifacts; }
    public void setTotalArtifacts(int totalArtifacts) { this.totalArtifacts = totalArtifacts; }

    public int getTotalFilesChanged() { return totalFilesChanged; }
    public void setTotalFilesChanged(int totalFilesChanged) { this.totalFilesChanged = totalFilesChanged; }

    public boolean isReverted() { return isReverted; }
    public void setReverted(boolean reverted) { isReverted = reverted; }
}
