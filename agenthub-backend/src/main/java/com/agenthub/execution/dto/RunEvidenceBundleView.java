package com.agenthub.execution.dto;

import com.agenthub.agent.dto.TokenUsageAuditView;
import com.agenthub.audit.dto.ArtifactView;
import com.agenthub.domain.workspace.model.StructuredDiff;
import com.agenthub.orchestration.dto.ApprovalView;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Sanitized Run Evidence Bundle DTO (Phase F).
 * Encapsulates a complete, secret-redacted, tamper-evident audit and portfolio evidence snapshot
 * for a workflow execution run, explicitly distinguishing Demo Mode (simulated=true) from
 * Live Provider Mode (simulated=false).
 */
public class RunEvidenceBundleView {

    private String schemaVersion = "agenthub.evidence.v1";
    private String bundleId;
    private LocalDateTime exportedAt;
    private boolean simulated;
    private String executionMode;
    private ProjectEvidenceSummary project;
    private String workflowDslSnapshot;
    private WorkflowRunView run;
    private List<StepRunView> steps = new ArrayList<>();
    private List<RunEventView> events = new ArrayList<>();
    private ProviderRuntimeEvidence providerRuntime;
    private StructuredDiff gitDiff;
    private List<ArtifactView> artifacts = new ArrayList<>();
    private TestReportEvidence testReportSummary;
    private List<ApprovalView> approvals = new ArrayList<>();
    private List<DeploymentEvidence> deployments = new ArrayList<>();
    private String checksum;

    public RunEvidenceBundleView() {}

    public String getSchemaVersion() { return schemaVersion; }
    public void setSchemaVersion(String schemaVersion) { this.schemaVersion = schemaVersion; }

    public String getBundleId() { return bundleId; }
    public void setBundleId(String bundleId) { this.bundleId = bundleId; }

    public LocalDateTime getExportedAt() { return exportedAt; }
    public void setExportedAt(LocalDateTime exportedAt) { this.exportedAt = exportedAt; }

    public boolean isSimulated() { return simulated; }
    public void setSimulated(boolean simulated) { this.simulated = simulated; }

    public String getExecutionMode() { return executionMode; }
    public void setExecutionMode(String executionMode) { this.executionMode = executionMode; }

    public ProjectEvidenceSummary getProject() { return project; }
    public void setProject(ProjectEvidenceSummary project) { this.project = project; }

    public String getWorkflowDslSnapshot() { return workflowDslSnapshot; }
    public void setWorkflowDslSnapshot(String workflowDslSnapshot) { this.workflowDslSnapshot = workflowDslSnapshot; }

    public WorkflowRunView getRun() { return run; }
    public void setRun(WorkflowRunView run) { this.run = run; }

    public List<StepRunView> getSteps() { return steps; }
    public void setSteps(List<StepRunView> steps) { this.steps = steps != null ? steps : new ArrayList<>(); }

    public List<RunEventView> getEvents() { return events; }
    public void setEvents(List<RunEventView> events) { this.events = events != null ? events : new ArrayList<>(); }

    public ProviderRuntimeEvidence getProviderRuntime() { return providerRuntime; }
    public void setProviderRuntime(ProviderRuntimeEvidence providerRuntime) { this.providerRuntime = providerRuntime; }

    public StructuredDiff getGitDiff() { return gitDiff; }
    public void setGitDiff(StructuredDiff gitDiff) { this.gitDiff = gitDiff; }

    public List<ArtifactView> getArtifacts() { return artifacts; }
    public void setArtifacts(List<ArtifactView> artifacts) { this.artifacts = artifacts != null ? artifacts : new ArrayList<>(); }

    public TestReportEvidence getTestReportSummary() { return testReportSummary; }
    public void setTestReportSummary(TestReportEvidence testReportSummary) { this.testReportSummary = testReportSummary; }

    public List<ApprovalView> getApprovals() { return approvals; }
    public void setApprovals(List<ApprovalView> approvals) { this.approvals = approvals != null ? approvals : new ArrayList<>(); }

    public List<DeploymentEvidence> getDeployments() { return deployments; }
    public void setDeployments(List<DeploymentEvidence> deployments) { this.deployments = deployments != null ? deployments : new ArrayList<>(); }

    public String getChecksum() { return checksum; }
    public void setChecksum(String checksum) { this.checksum = checksum; }

    public static class ProjectEvidenceSummary {
        private String id;
        private String name;
        private String ownerId;
        private String workspaceId;
        private String gitBaselineCommit;

        public ProjectEvidenceSummary() {}

        public ProjectEvidenceSummary(String id, String name, String ownerId, String workspaceId, String gitBaselineCommit) {
            this.id = id;
            this.name = name;
            this.ownerId = ownerId;
            this.workspaceId = workspaceId;
            this.gitBaselineCommit = gitBaselineCommit;
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getOwnerId() { return ownerId; }
        public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
        public String getWorkspaceId() { return workspaceId; }
        public void setWorkspaceId(String workspaceId) { this.workspaceId = workspaceId; }
        public String getGitBaselineCommit() { return gitBaselineCommit; }
        public void setGitBaselineCommit(String gitBaselineCommit) { this.gitBaselineCommit = gitBaselineCommit; }
    }

    public static class ProviderRuntimeEvidence {
        private List<String> runtimeTypes = new ArrayList<>();
        private List<String> providerTypes = new ArrayList<>();
        private List<String> models = new ArrayList<>();
        private boolean simulated;
        private long totalPromptTokens;
        private long totalCompletionTokens;
        private long totalTokens;
        private double totalEstimatedCost;
        private List<TokenUsageAuditView> tokenUsageRecords = new ArrayList<>();

        public ProviderRuntimeEvidence() {}

        public List<String> getRuntimeTypes() { return runtimeTypes; }
        public void setRuntimeTypes(List<String> runtimeTypes) { this.runtimeTypes = runtimeTypes != null ? runtimeTypes : new ArrayList<>(); }
        public List<String> getProviderTypes() { return providerTypes; }
        public void setProviderTypes(List<String> providerTypes) { this.providerTypes = providerTypes != null ? providerTypes : new ArrayList<>(); }
        public List<String> getModels() { return models; }
        public void setModels(List<String> models) { this.models = models != null ? models : new ArrayList<>(); }
        public boolean isSimulated() { return simulated; }
        public void setSimulated(boolean simulated) { this.simulated = simulated; }
        public long getTotalPromptTokens() { return totalPromptTokens; }
        public void setTotalPromptTokens(long totalPromptTokens) { this.totalPromptTokens = totalPromptTokens; }
        public long getTotalCompletionTokens() { return totalCompletionTokens; }
        public void setTotalCompletionTokens(long totalCompletionTokens) { this.totalCompletionTokens = totalCompletionTokens; }
        public long getTotalTokens() { return totalTokens; }
        public void setTotalTokens(long totalTokens) { this.totalTokens = totalTokens; }
        public double getTotalEstimatedCost() { return totalEstimatedCost; }
        public void setTotalEstimatedCost(double totalEstimatedCost) { this.totalEstimatedCost = totalEstimatedCost; }
        public List<TokenUsageAuditView> getTokenUsageRecords() { return tokenUsageRecords; }
        public void setTokenUsageRecords(List<TokenUsageAuditView> tokenUsageRecords) {
            this.tokenUsageRecords = tokenUsageRecords != null ? tokenUsageRecords : new ArrayList<>();
        }
    }

    public static class TestReportEvidence {
        private String status;
        private int totalSteps;
        private int succeededSteps;
        private int failedSteps;
        private int skippedSteps;
        private int cancelledSteps;
        private String verificationSummary;
        private List<String> logs = new ArrayList<>();

        public TestReportEvidence() {}

        public TestReportEvidence(String status, int totalSteps, int succeededSteps, int failedSteps,
                                  int skippedSteps, int cancelledSteps, String verificationSummary, List<String> logs) {
            this.status = status;
            this.totalSteps = totalSteps;
            this.succeededSteps = succeededSteps;
            this.failedSteps = failedSteps;
            this.skippedSteps = skippedSteps;
            this.cancelledSteps = cancelledSteps;
            this.verificationSummary = verificationSummary;
            this.logs = logs != null ? logs : new ArrayList<>();
        }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public int getTotalSteps() { return totalSteps; }
        public void setTotalSteps(int totalSteps) { this.totalSteps = totalSteps; }
        public int getSucceededSteps() { return succeededSteps; }
        public void setSucceededSteps(int succeededSteps) { this.succeededSteps = succeededSteps; }
        public int getFailedSteps() { return failedSteps; }
        public void setFailedSteps(int failedSteps) { this.failedSteps = failedSteps; }
        public int getSkippedSteps() { return skippedSteps; }
        public void setSkippedSteps(int skippedSteps) { this.skippedSteps = skippedSteps; }
        public int getCancelledSteps() { return cancelledSteps; }
        public void setCancelledSteps(int cancelledSteps) { this.cancelledSteps = cancelledSteps; }
        public String getVerificationSummary() { return verificationSummary; }
        public void setVerificationSummary(String verificationSummary) { this.verificationSummary = verificationSummary; }
        public List<String> getLogs() { return logs; }
        public void setLogs(List<String> logs) { this.logs = logs != null ? logs : new ArrayList<>(); }
    }

    public static class DeploymentEvidence {
        private String id;
        private String projectId;
        private String artifactId;
        private String status;
        private String target;
        private String sandboxType;
        private String templateType;
        private String url;
        private Integer port;
        private String healthCheckPath;
        private String lifecycleTrace;
        private String terminationReason;
        private String errorMessage;
        private LocalDateTime startedAt;
        private LocalDateTime finishedAt;

        public DeploymentEvidence() {}

        public DeploymentEvidence(String id, String projectId, String artifactId, String status, String target,
                                  String sandboxType, String templateType, String url, Integer port,
                                  String healthCheckPath, String lifecycleTrace, String terminationReason,
                                  String errorMessage, LocalDateTime startedAt, LocalDateTime finishedAt) {
            this.id = id;
            this.projectId = projectId;
            this.artifactId = artifactId;
            this.status = status;
            this.target = target;
            this.sandboxType = sandboxType;
            this.templateType = templateType;
            this.url = url;
            this.port = port;
            this.healthCheckPath = healthCheckPath;
            this.lifecycleTrace = lifecycleTrace;
            this.terminationReason = terminationReason;
            this.errorMessage = errorMessage;
            this.startedAt = startedAt;
            this.finishedAt = finishedAt;
        }

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
        public String getTemplateType() { return templateType; }
        public void setTemplateType(String templateType) { this.templateType = templateType; }
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public Integer getPort() { return port; }
        public void setPort(Integer port) { this.port = port; }
        public String getHealthCheckPath() { return healthCheckPath; }
        public void setHealthCheckPath(String healthCheckPath) { this.healthCheckPath = healthCheckPath; }
        public String getLifecycleTrace() { return lifecycleTrace; }
        public void setLifecycleTrace(String lifecycleTrace) { this.lifecycleTrace = lifecycleTrace; }
        public String getTerminationReason() { return terminationReason; }
        public void setTerminationReason(String terminationReason) { this.terminationReason = terminationReason; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
        public LocalDateTime getStartedAt() { return startedAt; }
        public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
        public LocalDateTime getFinishedAt() { return finishedAt; }
        public void setFinishedAt(LocalDateTime finishedAt) { this.finishedAt = finishedAt; }
    }
}
