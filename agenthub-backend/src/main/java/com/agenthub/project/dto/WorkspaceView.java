package com.agenthub.project.dto;

import java.time.LocalDateTime;

public class WorkspaceView {
    private String id;
    private String projectId;
    private String relativeRoot;
    private String gitBaselineCommit;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public WorkspaceView() {}

    public WorkspaceView(String id, String projectId, String relativeRoot, String gitBaselineCommit, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.projectId = projectId;
        this.relativeRoot = relativeRoot;
        this.gitBaselineCommit = gitBaselineCommit;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }
    public String getRelativeRoot() { return relativeRoot; }
    public void setRelativeRoot(String relativeRoot) { this.relativeRoot = relativeRoot; }
    public String getGitBaselineCommit() { return gitBaselineCommit; }
    public void setGitBaselineCommit(String gitBaselineCommit) { this.gitBaselineCommit = gitBaselineCommit; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
