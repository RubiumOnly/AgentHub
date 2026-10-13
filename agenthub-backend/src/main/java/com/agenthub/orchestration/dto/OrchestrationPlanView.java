package com.agenthub.orchestration.dto;

import com.agenthub.orchestration.domain.dsl.WorkflowDsl;
import com.agenthub.orchestration.domain.plan.EstimatedBudget;
import com.agenthub.orchestration.domain.plan.OrchestrationPlanStatus;
import com.agenthub.orchestration.domain.plan.ToolPermissionRequest;

import java.time.LocalDateTime;
import java.util.List;

public class OrchestrationPlanView {

    private String planId;
    private String projectId;
    private String workspaceId;
    private String templateType;
    private String requirement;
    private String scope;
    private OrchestrationPlanStatus status;
    private List<String> affectedFiles;
    private List<ToolPermissionRequest> toolPermissions;
    private EstimatedBudget estimatedBudget;
    private List<String> qualityGates;
    private String rollbackPlan;
    private boolean userConfirmed;
    private String confirmedBy;
    private LocalDateTime confirmedAt;
    private String rejectionReason;
    private WorkflowDsl workflowDsl;
    private LocalDateTime createdAt;

    public OrchestrationPlanView() {}

    public OrchestrationPlanView(String planId,
                                 String projectId,
                                 String workspaceId,
                                 String templateType,
                                 String requirement,
                                 String scope,
                                 OrchestrationPlanStatus status,
                                 List<String> affectedFiles,
                                 List<ToolPermissionRequest> toolPermissions,
                                 EstimatedBudget estimatedBudget,
                                 List<String> qualityGates,
                                 String rollbackPlan,
                                 boolean userConfirmed,
                                 String confirmedBy,
                                 LocalDateTime confirmedAt,
                                 String rejectionReason,
                                 WorkflowDsl workflowDsl,
                                 LocalDateTime createdAt) {
        this.planId = planId;
        this.projectId = projectId;
        this.workspaceId = workspaceId;
        this.templateType = templateType;
        this.requirement = requirement;
        this.scope = scope;
        this.status = status;
        this.affectedFiles = affectedFiles;
        this.toolPermissions = toolPermissions;
        this.estimatedBudget = estimatedBudget;
        this.qualityGates = qualityGates;
        this.rollbackPlan = rollbackPlan;
        this.userConfirmed = userConfirmed;
        this.confirmedBy = confirmedBy;
        this.confirmedAt = confirmedAt;
        this.rejectionReason = rejectionReason;
        this.workflowDsl = workflowDsl;
        this.createdAt = createdAt;
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
    public void setAffectedFiles(List<String> affectedFiles) { this.affectedFiles = affectedFiles; }
    public List<ToolPermissionRequest> getToolPermissions() { return toolPermissions; }
    public void setToolPermissions(List<ToolPermissionRequest> toolPermissions) { this.toolPermissions = toolPermissions; }
    public EstimatedBudget getEstimatedBudget() { return estimatedBudget; }
    public void setEstimatedBudget(EstimatedBudget estimatedBudget) { this.estimatedBudget = estimatedBudget; }
    public List<String> getQualityGates() { return qualityGates; }
    public void setQualityGates(List<String> qualityGates) { this.qualityGates = qualityGates; }
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
}
