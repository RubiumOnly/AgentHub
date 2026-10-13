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

import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectApplication projectApplication;
    private final com.agenthub.project.application.ProjectOnboardingApplication onboardingApplication;
    private final com.agenthub.project.application.ContextSourceApplication contextSourceApplication;

    public ProjectController(ProjectApplication projectApplication) {
        this(projectApplication, null, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public ProjectController(ProjectApplication projectApplication,
                             @org.springframework.beans.factory.annotation.Autowired(required = false) com.agenthub.project.application.ProjectOnboardingApplication onboardingApplication,
                             @org.springframework.beans.factory.annotation.Autowired(required = false) com.agenthub.project.application.ContextSourceApplication contextSourceApplication) {
        this.projectApplication = projectApplication;
        this.onboardingApplication = onboardingApplication;
        this.contextSourceApplication = contextSourceApplication;
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
        Comparator<ProjectView> cmp;
        if ("name".equalsIgnoreCase(query.getSortBy())) {
            cmp = Comparator.comparing(p -> p.getName() != null ? p.getName().toLowerCase() : "");
        } else if ("updatedAt".equalsIgnoreCase(query.getSortBy())) {
            cmp = Comparator.comparing(p -> p.getUpdatedAt() != null ? p.getUpdatedAt().toString() : "");
        } else {
            cmp = Comparator.comparing(p -> p.getCreatedAt() != null ? p.getCreatedAt().toString() : "");
        }
        if ("DESC".equalsIgnoreCase(query.getSortDir())) {
            cmp = cmp.reversed();
        }
        list = list.stream().sorted(cmp).toList();
        return Result.ok(PageResultView.slice(list, query));
    }

    @GetMapping("/{projectId}")
    public ResponseEntity<Result<ProjectView>> getProject(
            @PathVariable("projectId") String projectId,
            @RequestHeader(value = ApiContractHeaders.HEADER_IF_NONE_MATCH, required = false) String ifNoneMatch) {
        ProjectView view = projectApplication.getProjectById(projectId);
        String etag = computeProjectEtag(view);
        if (ApiContractHeaders.matchesEtag(ifNoneMatch, etag)) {
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

    // Onboarding Wizard Endpoints
    @PostMapping("/onboarding/start")
    public Result<com.agenthub.project.dto.onboarding.OnboardingSessionView> startOnboarding(
            @RequestBody com.agenthub.project.dto.onboarding.StartOnboardingCommand cmd) {
        if (onboardingApplication == null) {
            throw new com.agenthub.shared.exception.BusinessException(com.agenthub.shared.exception.ErrorCode.INTERNAL_SERVER_ERROR, "Onboarding service unavailable");
        }
        return Result.ok(onboardingApplication.startOnboarding(cmd));
    }

    @PostMapping("/onboarding/{sessionId}/step")
    public Result<com.agenthub.project.dto.onboarding.OnboardingSessionView> executeOnboardingStep(
            @PathVariable("sessionId") String sessionId,
            @RequestBody(required = false) com.agenthub.project.dto.onboarding.ExecuteStepCommand cmd) {
        if (onboardingApplication == null) {
            throw new com.agenthub.shared.exception.BusinessException(com.agenthub.shared.exception.ErrorCode.INTERNAL_SERVER_ERROR, "Onboarding service unavailable");
        }
        return Result.ok(onboardingApplication.executeStep(sessionId, cmd));
    }

    @PostMapping("/onboarding/{sessionId}/retry")
    public Result<com.agenthub.project.dto.onboarding.OnboardingSessionView> retryOnboardingStep(
            @PathVariable("sessionId") String sessionId,
            @RequestParam(value = "step", required = false) com.agenthub.project.domain.onboarding.OnboardingStep step) {
        if (onboardingApplication == null) {
            throw new com.agenthub.shared.exception.BusinessException(com.agenthub.shared.exception.ErrorCode.INTERNAL_SERVER_ERROR, "Onboarding service unavailable");
        }
        return Result.ok(onboardingApplication.retryStep(sessionId, step));
    }

    @GetMapping("/onboarding/{sessionId}")
    public Result<com.agenthub.project.dto.onboarding.OnboardingSessionView> getOnboardingSession(
            @PathVariable("sessionId") String sessionId) {
        if (onboardingApplication == null) {
            throw new com.agenthub.shared.exception.BusinessException(com.agenthub.shared.exception.ErrorCode.INTERNAL_SERVER_ERROR, "Onboarding service unavailable");
        }
        return Result.ok(onboardingApplication.getSession(sessionId));
    }

    @PostMapping("/onboarding/{sessionId}/abort")
    public Result<com.agenthub.project.dto.onboarding.OnboardingSessionView> abortOnboarding(
            @PathVariable("sessionId") String sessionId) {
        if (onboardingApplication == null) {
            throw new com.agenthub.shared.exception.BusinessException(com.agenthub.shared.exception.ErrorCode.INTERNAL_SERVER_ERROR, "Onboarding service unavailable");
        }
        return Result.ok(onboardingApplication.abortOnboarding(sessionId));
    }

    @PostMapping("/onboarding/{sessionId}/complete")
    public Result<ProjectView> completeOnboarding(
            @PathVariable("sessionId") String sessionId) {
        if (onboardingApplication == null) {
            throw new com.agenthub.shared.exception.BusinessException(com.agenthub.shared.exception.ErrorCode.INTERNAL_SERVER_ERROR, "Onboarding service unavailable");
        }
        return Result.ok(onboardingApplication.completeOnboarding(sessionId));
    }

    // Versioned Context Source Endpoints
    @GetMapping("/{projectId}/context-sources")
    public Result<List<com.agenthub.project.dto.context.ContextSourceView>> listContextSources(
            @PathVariable("projectId") String projectId) {
        if (contextSourceApplication == null) {
            return Result.ok(List.of());
        }
        return Result.ok(contextSourceApplication.listContextSources(projectId));
    }

    @PostMapping("/{projectId}/context-sources")
    public Result<com.agenthub.project.dto.context.ContextSourceView> registerContextSource(
            @PathVariable("projectId") String projectId,
            @RequestBody com.agenthub.project.dto.context.RegisterContextSourceCommand cmd) {
        if (contextSourceApplication == null) {
            throw new com.agenthub.shared.exception.BusinessException(com.agenthub.shared.exception.ErrorCode.INTERNAL_SERVER_ERROR, "Context source service unavailable");
        }
        return Result.ok(contextSourceApplication.registerContextSource(projectId, cmd));
    }

    @GetMapping("/{projectId}/context-sources/{sourceId}")
    public Result<com.agenthub.project.dto.context.ContextSourceView> getContextSource(
            @PathVariable("projectId") String projectId,
            @PathVariable("sourceId") String sourceId) {
        if (contextSourceApplication == null) {
            throw new com.agenthub.shared.exception.BusinessException(com.agenthub.shared.exception.ErrorCode.INTERNAL_SERVER_ERROR, "Context source service unavailable");
        }
        return Result.ok(contextSourceApplication.getContextSource(projectId, sourceId));
    }

    private String computeProjectEtag(ProjectView view) {
        if (view == null) {
            return "W/\"proj-empty\"";
        }
        String ts = view.getUpdatedAt() != null ? view.getUpdatedAt().truncatedTo(ChronoUnit.SECONDS).toString()
                : (view.getCreatedAt() != null ? view.getCreatedAt().truncatedTo(ChronoUnit.SECONDS).toString() : "");
        int fingerprint = java.util.Objects.hash(ts, view.getName(), view.getDescription(), view.getStatus(), view.getWorkspaceId());
        return "W/\"proj-" + view.getId() + "-" + view.getStatus() + "-" + Integer.toHexString(fingerprint) + "\"";
    }
}
