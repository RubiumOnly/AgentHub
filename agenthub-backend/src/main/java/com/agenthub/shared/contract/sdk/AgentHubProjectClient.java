package com.agenthub.shared.contract.sdk;

import com.agenthub.project.dto.CreateProjectCommand;
import com.agenthub.project.dto.ProjectView;
import com.agenthub.shared.contract.PageRequestQuery;
import com.agenthub.shared.contract.PageResultView;

/**
 * Standard public SDK contract for managing Projects and Workspace baselines.
 * Exposes only versioned DTOs, strictly avoiding internal entities.
 */
public interface AgentHubProjectClient {

    ProjectView createProject(CreateProjectCommand command);

    ProjectView getProjectById(String projectId);

    PageResultView<ProjectView> listProjects(PageRequestQuery query);
}
