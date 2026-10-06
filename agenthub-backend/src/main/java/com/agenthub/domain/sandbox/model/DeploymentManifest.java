package com.agenthub.domain.sandbox.model;

import java.io.Serializable;
import java.time.LocalDateTime;

public class DeploymentManifest implements Serializable {
    private static final long serialVersionUID = 1L;

    private String deploymentId;
    private String projectId;
    private String status; // CREATED, RUNNING, DEPLOYED
    private String previewUrl;
    private String dockerfileContent;
    private LocalDateTime deployedAt;

    public DeploymentManifest() {}

    public DeploymentManifest(String deploymentId, String projectId, String previewUrl, String dockerfileContent) {
        this.deploymentId = deploymentId;
        this.projectId = projectId;
        this.status = "DEPLOYED";
        this.previewUrl = previewUrl;
        this.dockerfileContent = dockerfileContent;
        this.deployedAt = LocalDateTime.now();
    }

    public String getDeploymentId() { return deploymentId; }
    public void setDeploymentId(String deploymentId) { this.deploymentId = deploymentId; }
    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getPreviewUrl() { return previewUrl; }
    public void setPreviewUrl(String previewUrl) { this.previewUrl = previewUrl; }
    public String getDockerfileContent() { return dockerfileContent; }
    public void setDockerfileContent(String dockerfileContent) { this.dockerfileContent = dockerfileContent; }
    public LocalDateTime getDeployedAt() { return deployedAt; }
    public void setDeployedAt(LocalDateTime deployedAt) { this.deployedAt = deployedAt; }
}
