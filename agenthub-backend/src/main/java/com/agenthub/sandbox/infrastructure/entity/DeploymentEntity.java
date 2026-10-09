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

    @Column
    private Integer port;

    @Column(name = "build_command", length = 256)
    private String buildCommand;

    @Column(name = "start_command", length = 256)
    private String startCommand;

    @Column(name = "health_check_path", length = 128)
    private String healthCheckPath;

    @Column(name = "log_output", columnDefinition = "TEXT")
    private String logOutput;

    @Column(name = "error_message", length = 1024)
    private String errorMessage;

    @Column(name = "sandbox_type", nullable = false, length = 32)
    private String sandboxType;

    @Column(name = "container_id", length = 128)
    private String containerId;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public DeploymentEntity() {}

    public DeploymentEntity(String id, String projectId, String artifactId, String status, String target, String url) {
        this.id = id;
        this.projectId = projectId;
        this.artifactId = artifactId;
        this.status = status != null ? status : "DEPLOYED";
        this.target = target != null ? target : "DOCKER_NGINX";
        this.url = url;
        this.sandboxType = "LOCAL_PROCESS";
        this.startedAt = LocalDateTime.now();
        this.finishedAt = LocalDateTime.now();
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
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
    public Integer getPort() { return port; }
    public void setPort(Integer port) { this.port = port; }
    public String getBuildCommand() { return buildCommand; }
    public void setBuildCommand(String buildCommand) { this.buildCommand = buildCommand; }
    public String getStartCommand() { return startCommand; }
    public void setStartCommand(String startCommand) { this.startCommand = startCommand; }
    public String getHealthCheckPath() { return healthCheckPath; }
    public void setHealthCheckPath(String healthCheckPath) { this.healthCheckPath = healthCheckPath; }
    public String getLogOutput() { return logOutput; }
    public void setLogOutput(String logOutput) { this.logOutput = logOutput; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public String getSandboxType() { return sandboxType; }
    public void setSandboxType(String sandboxType) { this.sandboxType = sandboxType; }
    public String getContainerId() { return containerId; }
    public void setContainerId(String containerId) { this.containerId = containerId; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(LocalDateTime finishedAt) { this.finishedAt = finishedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
