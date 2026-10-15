package com.agenthub.execution.domain.memory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 3-tier memory model:
 * - ProjectMemory: Project rules, tech stack conventions, baseline git commit, AOCI cognition status
 * - RunMemory: Run goal, workflow DSL snapshot, inputs, confirmed plan, token budget limit
 * - StepMemoryItem: Step prompt, tool executions, outputs, created artifacts, event sequence range
 */
public class HierarchicalContextMemory {

    public static class ProjectMemory {
        private String projectId;
        private String projectRules;
        private String gitBaselineCommit;
        private String aociFingerprint;
        private String aociCognitionStatus; // ALIGNED, REPAIR_REQUIRED

        public ProjectMemory() {}
        public ProjectMemory(String projectId, String projectRules, String gitBaselineCommit,
                             String aociFingerprint, String aociCognitionStatus) {
            this.projectId = projectId;
            this.projectRules = projectRules;
            this.gitBaselineCommit = gitBaselineCommit;
            this.aociFingerprint = aociFingerprint;
            this.aociCognitionStatus = aociCognitionStatus;
        }

        public String getProjectId() { return projectId; }
        public void setProjectId(String projectId) { this.projectId = projectId; }
        public String getProjectRules() { return projectRules; }
        public void setProjectRules(String projectRules) { this.projectRules = projectRules; }
        public String getGitBaselineCommit() { return gitBaselineCommit; }
        public void setGitBaselineCommit(String gitBaselineCommit) { this.gitBaselineCommit = gitBaselineCommit; }
        public String getAociFingerprint() { return aociFingerprint; }
        public void setAociFingerprint(String aociFingerprint) { this.aociFingerprint = aociFingerprint; }
        public String getAociCognitionStatus() { return aociCognitionStatus; }
        public void setAociCognitionStatus(String aociCognitionStatus) { this.aociCognitionStatus = aociCognitionStatus; }
    }

    public static class RunMemory {
        private String runId;
        private String goal;
        private String definitionId;
        private String planId;
        private int tokenBudget;
        private long tokensUsed;
        private List<String> authorizedTools = new ArrayList<>();

        public RunMemory() {}
        public RunMemory(String runId, String goal, String definitionId, String planId, int tokenBudget, long tokensUsed) {
            this.runId = runId;
            this.goal = goal;
            this.definitionId = definitionId;
            this.planId = planId;
            this.tokenBudget = tokenBudget;
            this.tokensUsed = tokensUsed;
        }

        public String getRunId() { return runId; }
        public void setRunId(String runId) { this.runId = runId; }
        public String getGoal() { return goal; }
        public void setGoal(String goal) { this.goal = goal; }
        public String getDefinitionId() { return definitionId; }
        public void setDefinitionId(String definitionId) { this.definitionId = definitionId; }
        public String getPlanId() { return planId; }
        public void setPlanId(String planId) { this.planId = planId; }
        public int getTokenBudget() { return tokenBudget; }
        public void setTokenBudget(int tokenBudget) { this.tokenBudget = tokenBudget; }
        public long getTokensUsed() { return tokensUsed; }
        public void setTokensUsed(long tokensUsed) { this.tokensUsed = tokensUsed; }
        public List<String> getAuthorizedTools() { return authorizedTools; }
        public void setAuthorizedTools(List<String> authorizedTools) { this.authorizedTools = authorizedTools; }
    }

    public static class StepMemoryItem {
        private String stepRunId;
        private String stepId;
        private String status;
        private long eventSeqStart;
        private long eventSeqEnd;
        private List<String> toolsInvoked = new ArrayList<>();
        private List<String> modifiedFiles = new ArrayList<>();
        private String rawOutput;
        private String summary;
        private String errorDetails;
        private String approvalDecision;
        private boolean compressed;
        private LocalDateTime updatedAt;

        public StepMemoryItem() {}
        public StepMemoryItem(String stepRunId, String stepId, String status, long eventSeqStart, long eventSeqEnd) {
            this.stepRunId = stepRunId;
            this.stepId = stepId;
            this.status = status;
            this.eventSeqStart = eventSeqStart;
            this.eventSeqEnd = eventSeqEnd;
            this.updatedAt = LocalDateTime.now();
        }

        public String getStepRunId() { return stepRunId; }
        public void setStepRunId(String stepRunId) { this.stepRunId = stepRunId; }
        public String getStepId() { return stepId; }
        public void setStepId(String stepId) { this.stepId = stepId; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public long getEventSeqStart() { return eventSeqStart; }
        public void setEventSeqStart(long eventSeqStart) { this.eventSeqStart = eventSeqStart; }
        public long getEventSeqEnd() { return eventSeqEnd; }
        public void setEventSeqEnd(long eventSeqEnd) { this.eventSeqEnd = eventSeqEnd; }
        public List<String> getToolsInvoked() { return toolsInvoked; }
        public void setToolsInvoked(List<String> toolsInvoked) { this.toolsInvoked = toolsInvoked; }
        public List<String> getModifiedFiles() { return modifiedFiles; }
        public void setModifiedFiles(List<String> modifiedFiles) { this.modifiedFiles = modifiedFiles; }
        public String getRawOutput() { return rawOutput; }
        public void setRawOutput(String rawOutput) { this.rawOutput = rawOutput; }
        public String getSummary() { return summary; }
        public void setSummary(String summary) { this.summary = summary; }
        public String getErrorDetails() { return errorDetails; }
        public void setErrorDetails(String errorDetails) { this.errorDetails = errorDetails; }
        public String getApprovalDecision() { return approvalDecision; }
        public void setApprovalDecision(String approvalDecision) { this.approvalDecision = approvalDecision; }
        public boolean isCompressed() { return compressed; }
        public void setCompressed(boolean compressed) { this.compressed = compressed; }
        public LocalDateTime getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    }

    private ProjectMemory projectMemory;
    private RunMemory runMemory;
    private List<StepMemoryItem> stepMemories = new ArrayList<>();

    public HierarchicalContextMemory() {}
    public HierarchicalContextMemory(ProjectMemory projectMemory, RunMemory runMemory) {
        this.projectMemory = projectMemory;
        this.runMemory = runMemory;
    }

    public ProjectMemory getProjectMemory() { return projectMemory; }
    public void setProjectMemory(ProjectMemory projectMemory) { this.projectMemory = projectMemory; }
    public RunMemory getRunMemory() { return runMemory; }
    public void setRunMemory(RunMemory runMemory) { this.runMemory = runMemory; }
    public List<StepMemoryItem> getStepMemories() { return stepMemories; }
    public void setStepMemories(List<StepMemoryItem> stepMemories) { this.stepMemories = stepMemories; }

    public void addStepMemory(StepMemoryItem item) {
        if (item != null) {
            this.stepMemories.add(item);
        }
    }
}
