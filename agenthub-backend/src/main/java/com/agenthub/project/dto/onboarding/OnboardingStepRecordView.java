package com.agenthub.project.dto.onboarding;

import com.agenthub.project.domain.onboarding.OnboardingStep;
import com.agenthub.project.domain.onboarding.OnboardingStepStatus;

import java.time.LocalDateTime;

/**
 * Step Record View within an Onboarding Session.
 */
public class OnboardingStepRecordView {

    private OnboardingStep step;
    private OnboardingStepStatus status;
    private String summary;
    private String evidence;
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;

    public OnboardingStepRecordView() {}

    public OnboardingStepRecordView(OnboardingStep step, OnboardingStepStatus status, String summary,
                                    String evidence, String errorMessage, LocalDateTime startedAt,
                                    LocalDateTime finishedAt) {
        this.step = step;
        this.status = status;
        this.summary = summary;
        this.evidence = evidence;
        this.errorMessage = errorMessage;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
    }

    public OnboardingStep getStep() { return step; }
    public void setStep(OnboardingStep step) { this.step = step; }

    public OnboardingStepStatus getStatus() { return status; }
    public void setStatus(OnboardingStepStatus status) { this.status = status; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public String getEvidence() { return evidence; }
    public void setEvidence(String evidence) { this.evidence = evidence; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }

    public LocalDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(LocalDateTime finishedAt) { this.finishedAt = finishedAt; }
}
