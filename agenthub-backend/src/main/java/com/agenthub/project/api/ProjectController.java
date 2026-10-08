package com.agenthub.project.api;

import com.agenthub.adapter.common.Result;
import com.agenthub.project.application.ProjectApplication;
import com.agenthub.project.dto.CreateProjectCommand;
import com.agenthub.project.dto.ProjectView;
import com.agenthub.project.dto.WorkspaceView;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectApplication projectApplication;

    public ProjectController(ProjectApplication projectApplication) {
        this.projectApplication = projectApplication;
    }

    @PostMapping
    public Result<ProjectView> createProject(@RequestBody CreateProjectCommand cmd) {
        ProjectView view = projectApplication.createProject(cmd);
        return Result.ok(view);
    }

    @GetMapping
    public Result<List<ProjectView>> listProjects() {
        List<ProjectView> list = projectApplication.listCurrentUserProjects();
        return Result.ok(list);
    }

    @GetMapping("/{projectId}")
    public Result<ProjectView> getProject(@PathVariable("projectId") String projectId) {
        ProjectView view = projectApplication.getProjectById(projectId);
        return Result.ok(view);
    }

    @GetMapping("/{projectId}/workspace")
    public Result<WorkspaceView> getWorkspace(@PathVariable("projectId") String projectId) {
        WorkspaceView view = projectApplication.getWorkspaceByProjectId(projectId);
        return Result.ok(view);
    }

    @GetMapping("/{projectId}/workspaces")
    public Result<List<WorkspaceView>> getWorkspaces(@PathVariable("projectId") String projectId) {
        WorkspaceView view = projectApplication.getWorkspaceByProjectId(projectId);
        return Result.ok(List.of(view));
    }
}
