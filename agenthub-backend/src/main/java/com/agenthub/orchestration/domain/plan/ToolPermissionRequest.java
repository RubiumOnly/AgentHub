package com.agenthub.orchestration.domain.plan;

import com.agenthub.agent.domain.definition.AgentToolCategory;

public class ToolPermissionRequest {

    private String toolName;
    private AgentToolCategory category;
    private boolean highImpact;
    private String reason;
    private boolean authorized;

    public ToolPermissionRequest() {}

    public ToolPermissionRequest(String toolName, AgentToolCategory category, boolean highImpact, String reason, boolean authorized) {
        this.toolName = toolName;
        this.category = category;
        this.highImpact = highImpact;
        this.reason = reason;
        this.authorized = authorized;
    }

    public String getToolName() { return toolName; }
    public void setToolName(String toolName) { this.toolName = toolName; }
    public AgentToolCategory getCategory() { return category; }
    public void setCategory(AgentToolCategory category) { this.category = category; }
    public boolean isHighImpact() { return highImpact; }
    public void setHighImpact(boolean highImpact) { this.highImpact = highImpact; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public boolean isAuthorized() { return authorized; }
    public void setAuthorized(boolean authorized) { this.authorized = authorized; }
}
