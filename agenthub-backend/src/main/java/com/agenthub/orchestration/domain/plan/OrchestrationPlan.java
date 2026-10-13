package com.agenthub.orchestration.domain.plan;

import com.agenthub.orchestration.domain.dsl.WorkflowDsl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class OrchestrationPlan {

    private String planId;
    private String projectId;
    private String workspaceId;
    private String templateType;
    private String requirement;
    private String scope;
    private OrchestrationPlanStatus status;
    private List<String> affectedFiles = new ArrayList<>();
    private List<ToolPermissionRequest> toolPermissions = new ArrayList<>();
    private EstimatedBudget estimatedBudget;
    private List<String> qualityGates = new ArrayList<>();
    private String rollbackPlan;
    private boolean userConfirmed;
    private String confirmedBy;
    private LocalDateTime confirmedAt;
    private String rejectionReason;
    private WorkflowDsl workflowDsl;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public OrchestrationPlan() {
        this.status = OrchestrationPlanStatus.GENERATED;
        this.userConfirmed = false;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public String getPlanId() { return planId; }
    public void setPlanId(String planId) { this.planId = planId; }
    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }
    public String getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(String workspaceId) { this.workspaceId = workspaceId; }
    public String getTemplateType() { return templateType; }
    public void setTemplateType(String templateType) { this.templateType = templateType; }
    public String getRequirement() { return requirement; }
    public void setRequirement(String requirement) { this.requirement = requirement; }
    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }
    public OrchestrationPlanStatus getStatus() { return status; }
    public void setStatus(OrchestrationPlanStatus status) { this.status = status; }
    public List<String> getAffectedFiles() { return affectedFiles; }
    public void setAffectedFiles(List<String> affectedFiles) { this.affectedFiles = affectedFiles != null ? affectedFiles : new ArrayList<>(); }
    public List<ToolPermissionRequest> getToolPermissions() { return toolPermissions; }
    public void setToolPermissions(List<ToolPermissionRequest> toolPermissions) { this.toolPermissions = toolPermissions != null ? toolPermissions : new ArrayList<>(); }
    public EstimatedBudget getEstimatedBudget() { return estimatedBudget; }
    public void setEstimatedBudget(EstimatedBudget estimatedBudget) { this.estimatedBudget = estimatedBudget; }
    public List<String> getQualityGates() { return qualityGates; }
    public void setQualityGates(List<String> qualityGates) { this.qualityGates = qualityGates != null ? qualityGates : new ArrayList<>(); }
    public String getRollbackPlan() { return rollbackPlan; }
    public void setRollbackPlan(String rollbackPlan) { this.rollbackPlan = rollbackPlan; }
    public boolean isUserConfirmed() { return userConfirmed; }
    public void setUserConfirmed(boolean userConfirmed) { this.userConfirmed = userConfirmed; }
    public String getConfirmedBy() { return confirmedBy; }
    public void setConfirmedBy(String confirmedBy) { this.confirmedBy = confirmedBy; }
    public LocalDateTime getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(LocalDateTime confirmedAt) { this.confirmedAt = confirmedAt; }
    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
    public WorkflowDsl getWorkflowDsl() { return workflowDsl; }
    public void setWorkflowDsl(WorkflowDsl workflowDsl) { this.workflowDsl = workflowDsl; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
