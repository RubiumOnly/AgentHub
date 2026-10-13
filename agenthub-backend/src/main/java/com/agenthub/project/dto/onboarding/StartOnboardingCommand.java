package com.agenthub.project.dto.onboarding;

import com.agenthub.project.domain.source.GitSourceConfig;

/**
 * Command to start a new Project Onboarding Wizard session.
 */
public class StartOnboardingCommand {

    private String projectName;
    private String description;
    private GitSourceConfig sourceConfig;

    public StartOnboardingCommand() {}

    public StartOnboardingCommand(String projectName, String description, GitSourceConfig sourceConfig) {
        this.projectName = projectName;
        this.description = description;
        this.sourceConfig = sourceConfig;
    }

    public String getProjectName() { return projectName; }
    public void setProjectName(String projectName) { this.projectName = projectName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public GitSourceConfig getSourceConfig() { return sourceConfig; }
    public void setSourceConfig(GitSourceConfig sourceConfig) { this.sourceConfig = sourceConfig; }
}
