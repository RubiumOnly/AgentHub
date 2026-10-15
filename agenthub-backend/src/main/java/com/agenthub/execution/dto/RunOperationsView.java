package com.agenthub.execution.dto;

import com.agenthub.audit.dto.ArtifactLineageView;

import java.util.List;

public class RunOperationsView {
    private WorkflowRunView workflowRun;
    private List<StepRunView> stepRuns;
    private String unifiedStatus;
    private String nextAction;
    private boolean requiresHumanIntervention;
    private List<PendingDecisionItem> pendingDecisions;
    private List<ToolSideEffectView> toolSideEffects;
    private HierarchicalMemorySummary memorySummary;
    private ArtifactLineageView artifactLineage;
    private List<RunEventView> recentEvents;
    private LeaseStatusView leaseStatus;
    private Integer totalRetries;
    private String failureCategory;
    private String failureReason;

    public RunOperationsView() {}

    public RunOperationsView(WorkflowRunView workflowRun, List<StepRunView> stepRuns,
                             String unifiedStatus, String nextAction, boolean requiresHumanIntervention,
                             List<PendingDecisionItem> pendingDecisions, List<ToolSideEffectView> toolSideEffects,
                             HierarchicalMemorySummary memorySummary, ArtifactLineageView artifactLineage,
                             List<RunEventView> recentEvents, LeaseStatusView leaseStatus,
                             Integer totalRetries, String failureCategory, String failureReason) {
        this.workflowRun = workflowRun;
        this.stepRuns = stepRuns;
        this.unifiedStatus = unifiedStatus;
        this.nextAction = nextAction;
        this.requiresHumanIntervention = requiresHumanIntervention;
        this.pendingDecisions = pendingDecisions;
        this.toolSideEffects = toolSideEffects;
        this.memorySummary = memorySummary;
        this.artifactLineage = artifactLineage;
        this.recentEvents = recentEvents;
        this.leaseStatus = leaseStatus;
        this.totalRetries = totalRetries;
        this.failureCategory = failureCategory;
        this.failureReason = failureReason;
    }

    public WorkflowRunView getWorkflowRun() { return workflowRun; }
    public void setWorkflowRun(WorkflowRunView workflowRun) { this.workflowRun = workflowRun; }

    public List<StepRunView> getStepRuns() { return stepRuns; }
    public void setStepRuns(List<StepRunView> stepRuns) { this.stepRuns = stepRuns; }

    public String getUnifiedStatus() { return unifiedStatus; }
    public void setUnifiedStatus(String unifiedStatus) { this.unifiedStatus = unifiedStatus; }

    public String getNextAction() { return nextAction; }
    public void setNextAction(String nextAction) { this.nextAction = nextAction; }

    public boolean isRequiresHumanIntervention() { return requiresHumanIntervention; }
    public void setRequiresHumanIntervention(boolean requiresHumanIntervention) { this.requiresHumanIntervention = requiresHumanIntervention; }

    public List<PendingDecisionItem> getPendingDecisions() { return pendingDecisions; }
    public void setPendingDecisions(List<PendingDecisionItem> pendingDecisions) { this.pendingDecisions = pendingDecisions; }

    public List<ToolSideEffectView> getToolSideEffects() { return toolSideEffects; }
    public void setToolSideEffects(List<ToolSideEffectView> toolSideEffects) { this.toolSideEffects = toolSideEffects; }

    public HierarchicalMemorySummary getMemorySummary() { return memorySummary; }
    public void setMemorySummary(HierarchicalMemorySummary memorySummary) { this.memorySummary = memorySummary; }

    public ArtifactLineageView getArtifactLineage() { return artifactLineage; }
    public void setArtifactLineage(ArtifactLineageView artifactLineage) { this.artifactLineage = artifactLineage; }

    public List<RunEventView> getRecentEvents() { return recentEvents; }
    public void setRecentEvents(List<RunEventView> recentEvents) { this.recentEvents = recentEvents; }

    public LeaseStatusView getLeaseStatus() { return leaseStatus; }
    public void setLeaseStatus(LeaseStatusView leaseStatus) { this.leaseStatus = leaseStatus; }

    public Integer getTotalRetries() { return totalRetries; }
    public void setTotalRetries(Integer totalRetries) { this.totalRetries = totalRetries; }

    public String getFailureCategory() { return failureCategory; }
    public void setFailureCategory(String failureCategory) { this.failureCategory = failureCategory; }

    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
}
