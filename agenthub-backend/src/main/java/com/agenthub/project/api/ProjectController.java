package com.agenthub.project.api;

import com.agenthub.adapter.common.Result;
import com.agenthub.project.application.ProjectApplication;
import com.agenthub.project.dto.CreateProjectCommand;
import com.agenthub.project.dto.ProjectView;
import com.agenthub.project.dto.WorkspaceView;
import com.agenthub.shared.contract.ApiContractHeaders;
import com.agenthub.shared.contract.PageRequestQuery;
import com.agenthub.shared.contract.PageResultView;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectApplication projectApplication;

    public ProjectController(ProjectApplication projectApplication) {
        this.projectApplication = projectApplication;
    }

    @PostMapping
    public ResponseEntity<Result<ProjectView>> createProject(@RequestBody CreateProjectCommand cmd) {
        ProjectView view = projectApplication.createProject(cmd);
        String etag = computeProjectEtag(view);
        return ResponseEntity.ok()
                .header(HttpHeaders.ETAG, etag)
                .body(Result.ok(view));
    }

    @GetMapping
    public Result<List<ProjectView>> listProjects() {
        List<ProjectView> list = projectApplication.listCurrentUserProjects();
        return Result.ok(list);
    }

    @GetMapping("/page")
    public Result<PageResultView<ProjectView>> listProjectsPaged(
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(value = "sortBy", required = false) String sortBy,
            @RequestParam(value = "sortDir", required = false) String sortDir) {
        PageRequestQuery query = PageRequestQuery.of(page, size, sortBy, sortDir);
        List<ProjectView> list = projectApplication.listCurrentUserProjects();
        if ("name".equalsIgnoreCase(query.getSortBy())) {
            Comparator<ProjectView> cmp = Comparator.comparing(
                    p -> p.getName() != null ? p.getName().toLowerCase() : "");
            if ("DESC".equalsIgnoreCase(query.getSortDir())) {
                cmp = cmp.reversed();
            }
            list = list.stream().sorted(cmp).toList();
        }
        return Result.ok(PageResultView.slice(list, query));
    }

    @GetMapping("/{projectId}")
    public ResponseEntity<Result<ProjectView>> getProject(
            @PathVariable("projectId") String projectId,
            @RequestHeader(value = ApiContractHeaders.HEADER_IF_NONE_MATCH, required = false) String ifNoneMatch) {
        ProjectView view = projectApplication.getProjectById(projectId);
        String etag = computeProjectEtag(view);
        if (ifNoneMatch != null && ifNoneMatch.trim().equals(etag)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .header(HttpHeaders.ETAG, etag)
                    .build();
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.ETAG, etag)
                .body(Result.ok(view));
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

    private String computeProjectEtag(ProjectView view) {
        if (view == null) {
            return "W/\"proj-empty\"";
        }
        String ts = view.getUpdatedAt() != null
                ? view.getUpdatedAt().truncatedTo(java.time.temporal.ChronoUnit.SECONDS).toString()
                : "";
        int fingerprint = java.util.Objects.hash(ts, view.getName(), view.getDescription(), view.getStatus(), view.getWorkspaceId());
        return "W/\"proj-" + view.getId() + "-" + view.getStatus() + "-" + Integer.toHexString(fingerprint) + "\"";
    }
}
