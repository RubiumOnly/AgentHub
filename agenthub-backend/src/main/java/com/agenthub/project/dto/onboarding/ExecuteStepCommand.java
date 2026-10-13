package com.agenthub.project.dto.onboarding;

import com.agenthub.project.domain.onboarding.OnboardingStep;

/**
 * Command to execute or proceed with a specific Onboarding step.
 */
public class ExecuteStepCommand {

    private OnboardingStep step;
    private String selectedBranch;
    private String customRules;

    public ExecuteStepCommand() {}

    public ExecuteStepCommand(OnboardingStep step, String selectedBranch, String customRules) {
        this.step = step;
        this.selectedBranch = selectedBranch;
        this.customRules = customRules;
    }

    public OnboardingStep getStep() { return step; }
    public void setStep(OnboardingStep step) { this.step = step; }

    public String getSelectedBranch() { return selectedBranch; }
    public void setSelectedBranch(String selectedBranch) { this.selectedBranch = selectedBranch; }

    public String getCustomRules() { return customRules; }
    public void setCustomRules(String customRules) { this.customRules = customRules; }
}
