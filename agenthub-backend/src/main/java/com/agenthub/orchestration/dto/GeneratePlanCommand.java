package com.agenthub.orchestration.dto;

import java.util.List;

public class GeneratePlanCommand {

    private String projectId;
    private String workspaceId;
    private String templateType; // BUG_FIX_WITH_TESTS or NEW_FEATURE_ENDPOINT
    private String requirement;
    private List<String> targetFiles;

    public GeneratePlanCommand() {}

    public GeneratePlanCommand(String projectId, String workspaceId, String templateType, String requirement) {
        this.projectId = projectId;
        this.workspaceId = workspaceId;
        this.templateType = templateType;
        this.requirement = requirement;
    }

    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }
    public String getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(String workspaceId) { this.workspaceId = workspaceId; }
    public String getTemplateType() { return templateType; }
    public void setTemplateType(String templateType) { this.templateType = templateType; }
    public String getRequirement() { return requirement; }
    public void setRequirement(String requirement) { this.requirement = requirement; }
    public List<String> getTargetFiles() { return targetFiles; }
    public void setTargetFiles(List<String> targetFiles) { this.targetFiles = targetFiles; }
}
