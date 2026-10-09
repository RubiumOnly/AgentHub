package com.agenthub.sandbox.api.dto;

import java.io.Serializable;
import java.util.Map;

/**
 * Request payload for creating and launching a deployment.
 */
public class CreateDeploymentRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private String projectId;
    private String target; // STATIC_PREVIEW, LOCAL_PROCESS, DOCKER_CONTAINER, DOCKER_NGINX
    private String sandboxType; // LOCAL_PROCESS, DOCKER
    private String buildCommand;
    private String startCommand;
    private Integer port;
    private String healthCheckPath;
    private Map<String, String> envVars;
    private Long timeoutMs;

    public CreateDeploymentRequest() {}

    public CreateDeploymentRequest(String projectId, String target, String sandboxType) {
        this.projectId = projectId;
        this.target = target;
        this.sandboxType = sandboxType;
    }

    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }

    public String getTarget() { return target; }
    public void setTarget(String target) { this.target = target; }

    public String getSandboxType() { return sandboxType; }
    public void setSandboxType(String sandboxType) { this.sandboxType = sandboxType; }

    public String getBuildCommand() { return buildCommand; }
    public void setBuildCommand(String buildCommand) { this.buildCommand = buildCommand; }

    public String getStartCommand() { return startCommand; }
    public void setStartCommand(String startCommand) { this.startCommand = startCommand; }

    public Integer getPort() { return port; }
    public void setPort(Integer port) { this.port = port; }

    public String getHealthCheckPath() { return healthCheckPath; }
    public void setHealthCheckPath(String healthCheckPath) { this.healthCheckPath = healthCheckPath; }

    public Map<String, String> getEnvVars() { return envVars; }
    public void setEnvVars(Map<String, String> envVars) { this.envVars = envVars; }

    public Long getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(Long timeoutMs) { this.timeoutMs = timeoutMs; }
}
