package com.agenthub.project.infrastructure.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "workspaces")
public class WorkspaceEntity {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "project_id", nullable = false, length = 64)
    private String projectId;

    @Column(name = "relative_root", nullable = false, length = 256)
    private String relativeRoot;

    @Column(name = "git_baseline_commit", length = 64)
    private String gitBaselineCommit;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public WorkspaceEntity() {}

    public WorkspaceEntity(String id, String projectId, String relativeRoot, String gitBaselineCommit) {
        this.id = id;
        this.projectId = projectId;
        this.relativeRoot = relativeRoot;
        this.gitBaselineCommit = gitBaselineCommit;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
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
