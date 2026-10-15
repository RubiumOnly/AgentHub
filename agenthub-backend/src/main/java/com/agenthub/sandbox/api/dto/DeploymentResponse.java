package com.agenthub.sandbox.api.dto;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * View DTO representing deployment record details.
 */
public class DeploymentResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String projectId;
    private String artifactId;
    private String status;
    private String target;
    private String sandboxType;
    private Integer port;
    private String url;
    private String buildCommand;
    private String startCommand;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
    private String errorMessage;
    private boolean hasLogs;
    private Long processId;
    private String containerId;
    private String workingDirectory;
    private Long logCursor;
    private String terminationReason;
    private String templateType;
    private String lifecycleTrace;
    private Long probeLatencyMs;
    private Boolean healthy;

    public DeploymentResponse() {}

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

    public String getSandboxType() { return sandboxType; }
    public void setSandboxType(String sandboxType) { this.sandboxType = sandboxType; }

    public Integer getPort() { return port; }
    public void setPort(Integer port) { this.port = port; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getBuildCommand() { return buildCommand; }
    public void setBuildCommand(String buildCommand) { this.buildCommand = buildCommand; }

    public String getStartCommand() { return startCommand; }
    public void setStartCommand(String startCommand) { this.startCommand = startCommand; }

    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }

    public LocalDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(LocalDateTime finishedAt) { this.finishedAt = finishedAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public boolean isHasLogs() { return hasLogs; }
    public void setHasLogs(boolean hasLogs) { this.hasLogs = hasLogs; }

    public Long getProcessId() { return processId; }
    public void setProcessId(Long processId) { this.processId = processId; }

    public String getContainerId() { return containerId; }
    public void setContainerId(String containerId) { this.containerId = containerId; }

    public String getWorkingDirectory() { return workingDirectory; }
    public void setWorkingDirectory(String workingDirectory) { this.workingDirectory = workingDirectory; }

    public Long getLogCursor() { return logCursor; }
    public void setLogCursor(Long logCursor) { this.logCursor = logCursor; }

    public String getTerminationReason() { return terminationReason; }
    public void setTerminationReason(String terminationReason) { this.terminationReason = terminationReason; }

    public String getTemplateType() { return templateType; }
    public void setTemplateType(String templateType) { this.templateType = templateType; }

    public String getLifecycleTrace() { return lifecycleTrace; }
    public void setLifecycleTrace(String lifecycleTrace) { this.lifecycleTrace = lifecycleTrace; }

    public Long getProbeLatencyMs() { return probeLatencyMs; }
    public void setProbeLatencyMs(Long probeLatencyMs) { this.probeLatencyMs = probeLatencyMs; }

    public Boolean getHealthy() { return healthy; }
    public void setHealthy(Boolean healthy) { this.healthy = healthy; }
}
