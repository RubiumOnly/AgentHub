package com.agenthub.project.application;

import com.agenthub.project.domain.onboarding.OnboardingStep;
import com.agenthub.project.dto.ProjectView;
import com.agenthub.project.dto.onboarding.ExecuteStepCommand;
import com.agenthub.project.dto.onboarding.OnboardingSessionView;
import com.agenthub.project.dto.onboarding.StartOnboardingCommand;

/**
 * Application service interface for Project Onboarding Wizard.
 */
public interface ProjectOnboardingApplication {

    OnboardingSessionView startOnboarding(StartOnboardingCommand cmd);

    OnboardingSessionView executeStep(String sessionId, ExecuteStepCommand cmd);

    OnboardingSessionView retryStep(String sessionId, OnboardingStep step);

    OnboardingSessionView abortOnboarding(String sessionId);

    OnboardingSessionView getSession(String sessionId);

    ProjectView completeOnboarding(String sessionId);
}
