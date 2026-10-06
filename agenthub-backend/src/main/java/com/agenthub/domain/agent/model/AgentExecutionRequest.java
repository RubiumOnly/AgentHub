package com.agenthub.domain.agent.model;

import java.util.Collections;
import java.util.Map;

public class AgentExecutionRequest {
    private String agentId;
    private AgentPlatformType platformType;
    private String workspacePath;
    private String prompt;
    private String sessionId;
    private Map<String, String> environmentVariables;
    private int timeoutSeconds;

    public AgentExecutionRequest() {
        this.timeoutSeconds = 300;
        this.environmentVariables = Collections.emptyMap();
    }

    public AgentExecutionRequest(String agentId, AgentPlatformType platformType, String workspacePath, String prompt) {
        this.agentId = agentId;
        this.platformType = platformType;
        this.workspacePath = workspacePath;
        this.prompt = prompt;
        this.timeoutSeconds = 300;
        this.environmentVariables = Collections.emptyMap();
    }

    public String getAgentId() {
        return agentId;
    }

    public String getAgentName() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public AgentPlatformType getPlatformType() {
        return platformType;
    }

    public void setPlatformType(AgentPlatformType platformType) {
        this.platformType = platformType;
    }

    public String getWorkspacePath() {
        return workspacePath;
    }

    public void setWorkspacePath(String workspacePath) {
        this.workspacePath = workspacePath;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public Map<String, String> getEnvironmentVariables() {
        return environmentVariables;
    }

    public void setEnvironmentVariables(Map<String, String> environmentVariables) {
        this.environmentVariables = environmentVariables;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }
}
