package com.agenthub.sandbox.infrastructure.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "deployments")
public class DeploymentEntity {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "project_id", nullable = false, length = 64)
    private String projectId;

    @Column(name = "artifact_id", length = 64)
    private String artifactId;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(nullable = false, length = 64)
    private String target;

    @Column(length = 256)
    private String url;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public DeploymentEntity() {}

    public DeploymentEntity(String id, String projectId, String artifactId, String status, String target, String url) {
        this.id = id;
        this.projectId = projectId;
        this.artifactId = artifactId;
        this.status = status != null ? status : "DEPLOYED";
        this.target = target != null ? target : "DOCKER_NGINX";
        this.url = url;
        this.startedAt = LocalDateTime.now();
        this.finishedAt = LocalDateTime.now();
        this.createdAt = LocalDateTime.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }
    public String getArtifactId() { return artifactId; }
    public void setArtifactId(String artifactId) { this.artifactId = artifactId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getTarget() { return target; }
    public void setTarget(String target) { this.target = target; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(LocalDateTime finishedAt) { this.finishedAt = finishedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
