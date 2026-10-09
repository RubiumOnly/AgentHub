package com.agenthub.sandbox.domain.model;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Specification for deploying an application or static preview from a workspace.
 */
public class DeploymentSpec implements Serializable {
    private static final long serialVersionUID = 1L;

    private String projectId;
    private DeploymentTarget target;
    private SandboxType sandboxType;
    private String buildCommand;
    private String startCommand;
    private Integer port;
    private String healthCheckPath;
    private Map<String, String> envVars;
    private Long timeoutMs;

    public DeploymentSpec() {
        this.target = DeploymentTarget.STATIC_PREVIEW;
        this.sandboxType = SandboxType.LOCAL_PROCESS;
        this.healthCheckPath = "/";
        this.envVars = new HashMap<>();
        this.timeoutMs = 60_000L;
    }

    public DeploymentSpec(String projectId, DeploymentTarget target, SandboxType sandboxType) {
        this();
        this.projectId = projectId;
        this.target = target != null ? target : DeploymentTarget.STATIC_PREVIEW;
        this.sandboxType = sandboxType != null ? sandboxType : SandboxType.LOCAL_PROCESS;
    }

    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }

    public DeploymentTarget getTarget() { return target; }
    public void setTarget(DeploymentTarget target) { this.target = target; }

    public SandboxType getSandboxType() { return sandboxType; }
    public void setSandboxType(SandboxType sandboxType) { this.sandboxType = sandboxType; }

    public String getBuildCommand() { return buildCommand; }
    public void setBuildCommand(String buildCommand) { this.buildCommand = buildCommand; }

    public String getStartCommand() { return startCommand; }
    public void setStartCommand(String startCommand) { this.startCommand = startCommand; }

    public Integer getPort() { return port; }
    public void setPort(Integer port) { this.port = port; }

    public String getHealthCheckPath() { return healthCheckPath; }
    public void setHealthCheckPath(String healthCheckPath) { this.healthCheckPath = healthCheckPath; }

    public Map<String, String> getEnvVars() { return envVars; }
    public void setEnvVars(Map<String, String> envVars) { this.envVars = envVars != null ? envVars : new HashMap<>(); }

    public Long getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(Long timeoutMs) { this.timeoutMs = timeoutMs; }
}
