package com.agenthub.project.dto.onboarding;

import com.agenthub.project.domain.onboarding.OnboardingSessionStatus;
import com.agenthub.project.domain.onboarding.OnboardingStep;
import com.agenthub.project.domain.source.GitSourceType;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Full View DTO of an Onboarding Session.
 */
public class OnboardingSessionView {

    private String sessionId;
    private String ownerId;
    private String projectName;
    private String description;
    private GitSourceType sourceType;
    private String repoLocationOrUrlMasked;
    private OnboardingStep currentStep;
    private OnboardingSessionStatus status;
    private String projectId;
    private String workspaceId;
    private String isolatedBranch;
    private String baselineCommit;
    private EnvironmentScanResultView scanResult;
    private String generatedRules;
    private List<OnboardingStepRecordView> steps;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public OnboardingSessionView() {}

    public OnboardingSessionView(String sessionId, String ownerId, String projectName, String description,
                                 GitSourceType sourceType, String repoLocationOrUrlMasked,
                                 OnboardingStep currentStep, OnboardingSessionStatus status,
                                 String projectId, String workspaceId, String isolatedBranch,
                                 String baselineCommit, EnvironmentScanResultView scanResult,
                                 String generatedRules, List<OnboardingStepRecordView> steps,
                                 LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.sessionId = sessionId;
        this.ownerId = ownerId;
        this.projectName = projectName;
        this.description = description;
        this.sourceType = sourceType;
        this.repoLocationOrUrlMasked = repoLocationOrUrlMasked;
        this.currentStep = currentStep;
        this.status = status;
        this.projectId = projectId;
        this.workspaceId = workspaceId;
        this.isolatedBranch = isolatedBranch;
        this.baselineCommit = baselineCommit;
        this.scanResult = scanResult;
        this.generatedRules = generatedRules;
        this.steps = steps != null ? steps : List.of();
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
        this.updatedAt = updatedAt != null ? updatedAt : LocalDateTime.now();
    }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }

    public String getProjectName() { return projectName; }
    public void setProjectName(String projectName) { this.projectName = projectName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public GitSourceType getSourceType() { return sourceType; }
    public void setSourceType(GitSourceType sourceType) { this.sourceType = sourceType; }

    public String getRepoLocationOrUrlMasked() { return repoLocationOrUrlMasked; }
    public void setRepoLocationOrUrlMasked(String repoLocationOrUrlMasked) { this.repoLocationOrUrlMasked = repoLocationOrUrlMasked; }

    public OnboardingStep getCurrentStep() { return currentStep; }
    public void setCurrentStep(OnboardingStep currentStep) { this.currentStep = currentStep; }

    public OnboardingSessionStatus getStatus() { return status; }
    public void setStatus(OnboardingSessionStatus status) { this.status = status; }

    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }

    public String getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(String workspaceId) { this.workspaceId = workspaceId; }

    public String getIsolatedBranch() { return isolatedBranch; }
    public void setIsolatedBranch(String isolatedBranch) { this.isolatedBranch = isolatedBranch; }

    public String getBaselineCommit() { return baselineCommit; }
    public void setBaselineCommit(String baselineCommit) { this.baselineCommit = baselineCommit; }

    public EnvironmentScanResultView getScanResult() { return scanResult; }
    public void setScanResult(EnvironmentScanResultView scanResult) { this.scanResult = scanResult; }

    public String getGeneratedRules() { return generatedRules; }
    public void setGeneratedRules(String generatedRules) { this.generatedRules = generatedRules; }

    public List<OnboardingStepRecordView> getSteps() { return steps; }
    public void setSteps(List<OnboardingStepRecordView> steps) { this.steps = steps; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
