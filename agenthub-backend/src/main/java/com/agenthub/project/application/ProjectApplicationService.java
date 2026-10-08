package com.agenthub.project.application;

import com.agenthub.identity.infrastructure.security.ResourceAccessGuard;
import com.agenthub.project.dto.CreateProjectCommand;
import com.agenthub.project.dto.ProjectView;
import com.agenthub.project.dto.WorkspaceView;
import com.agenthub.project.infrastructure.entity.ProjectEntity;
import com.agenthub.project.infrastructure.entity.WorkspaceEntity;
import com.agenthub.project.infrastructure.repository.ProjectRepository;
import com.agenthub.project.infrastructure.repository.WorkspaceRepository;
import com.agenthub.shared.context.RequestContext;
import com.agenthub.shared.exception.BusinessException;
import com.agenthub.shared.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ProjectApplicationService implements ProjectApplication {

    private final ProjectRepository projectRepository;
    private final WorkspaceRepository workspaceRepository;
    private final ResourceAccessGuard accessGuard;

    public ProjectApplicationService(ProjectRepository projectRepository,
                                     WorkspaceRepository workspaceRepository,
                                     ResourceAccessGuard accessGuard) {
        this.projectRepository = projectRepository;
        this.workspaceRepository = workspaceRepository;
        this.accessGuard = accessGuard;
    }

    @Override
    @Transactional
    public ProjectView createProject(CreateProjectCommand cmd) {
        if (cmd == null || cmd.getName() == null || cmd.getName().isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Project name must not be blank");
        }

        String currentUserId = RequestContext.get().getUserId();
        if (currentUserId == null || currentUserId.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Authentication required to create project");
        }

        String projectId = "proj-" + UUID.randomUUID().toString().substring(0, 8);
        String workspaceId = "ws-" + UUID.randomUUID().toString().substring(0, 8);
        String relativeRoot = (cmd.getRelativeRoot() != null && !cmd.getRelativeRoot().isBlank())
                ? cmd.getRelativeRoot().trim() : projectId;

        ProjectEntity project = new ProjectEntity(
                projectId,
                currentUserId,
                cmd.getName(),
                cmd.getDescription(),
                "ACTIVE",
                workspaceId
        );
        projectRepository.save(project);

        WorkspaceEntity workspace = new WorkspaceEntity(
                workspaceId,
                projectId,
                relativeRoot,
                null
        );
        workspaceRepository.save(workspace);

        return toView(project);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProjectView> listCurrentUserProjects() {
        String currentUserId = RequestContext.get().getUserId();
        if (currentUserId == null || currentUserId.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Authentication required to list projects");
        }
        return listUserProjects(currentUserId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProjectView> listUserProjects(String userId) {
        return projectRepository.findByOwnerIdOrderByUpdatedAtDesc(userId).stream()
                .map(this::toView)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectView getProjectById(String projectId) {
        ProjectEntity project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND, "Project not found: " + projectId));
        accessGuard.checkOwnership(project.getOwnerId(), RequestContext.get().getUserId());
        return toView(project);
    }

    @Override
    @Transactional(readOnly = true)
    public WorkspaceView getWorkspaceByProjectId(String projectId) {
        ProjectEntity project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND, "Project not found: " + projectId));
        accessGuard.checkOwnership(project.getOwnerId(), RequestContext.get().getUserId());

        WorkspaceEntity workspace = workspaceRepository.findByProjectId(projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WORKSPACE_NOT_FOUND, "Workspace not found for project: " + projectId));
        return toView(workspace);
    }

    private ProjectView toView(ProjectEntity entity) {
        return new ProjectView(
                entity.getId(),
                entity.getOwnerId(),
                entity.getName(),
                entity.getDescription(),
                entity.getStatus(),
                entity.getWorkspaceId(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private WorkspaceView toView(WorkspaceEntity entity) {
        return new WorkspaceView(
                entity.getId(),
                entity.getProjectId(),
                entity.getRelativeRoot(),
                entity.getGitBaselineCommit(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
