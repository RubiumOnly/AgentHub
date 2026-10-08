package com.agenthub.project.application;

import com.agenthub.project.dto.CreateProjectCommand;
import com.agenthub.project.dto.ProjectView;
import com.agenthub.project.dto.WorkspaceView;

import java.util.List;

public interface ProjectApplication {
    ProjectView createProject(CreateProjectCommand cmd);
    List<ProjectView> listCurrentUserProjects();
    List<ProjectView> listUserProjects(String userId);
    ProjectView getProjectById(String projectId);
    WorkspaceView getWorkspaceByProjectId(String projectId);
}
