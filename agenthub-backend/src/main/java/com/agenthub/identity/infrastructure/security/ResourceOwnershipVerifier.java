package com.agenthub.identity.infrastructure.security;

import com.agenthub.audit.infrastructure.entity.ArtifactEntity;
import com.agenthub.audit.infrastructure.repository.ArtifactRepository;
import com.agenthub.execution.infrastructure.entity.WorkflowRunEntity;
import com.agenthub.execution.infrastructure.repository.WorkflowRunRepository;
import com.agenthub.orchestration.infrastructure.entity.WorkflowDefinitionEntity;
import com.agenthub.orchestration.infrastructure.repository.WorkflowDefinitionRepository;
import com.agenthub.project.infrastructure.entity.ProjectEntity;
import com.agenthub.project.infrastructure.entity.WorkspaceEntity;
import com.agenthub.project.infrastructure.repository.ProjectRepository;
import com.agenthub.project.infrastructure.repository.WorkspaceRepository;
import com.agenthub.sandbox.infrastructure.entity.DeploymentEntity;
import com.agenthub.sandbox.infrastructure.repository.DeploymentRepository;
import com.agenthub.shared.context.RequestContext;
import com.agenthub.shared.exception.BusinessException;
import com.agenthub.shared.exception.ErrorCode;
import com.agenthub.infrastructure.metrics.AgentHubMetricsCollector;
import com.agenthub.team.domain.rbac.ProjectPermission;
import com.agenthub.team.domain.rbac.ProjectRbacRole;
import com.agenthub.team.infrastructure.entity.TeamEntity;
import com.agenthub.team.infrastructure.entity.TeamMemberEntity;
import com.agenthub.team.infrastructure.repository.TeamMemberRepository;
import com.agenthub.team.infrastructure.repository.TeamRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Unified resource ownership resolution and verification across Project, Workspace,
 * Workflow, Run, Artifact, and Deployment bounded contexts.
 * Supports enterprise multi-role RBAC enforcement (OWNER, ADMIN, MAINTAINER, DEVELOPER, VIEWER, AUDITOR).
 */
@Component
public class ResourceOwnershipVerifier {

    private final ResourceAccessGuard accessGuard;
    private final ProjectRepository projectRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkflowDefinitionRepository workflowDefinitionRepository;
    private final WorkflowRunRepository workflowRunRepository;
    private final ArtifactRepository artifactRepository;
    private final DeploymentRepository deploymentRepository;

    @Autowired(required = false)
    private TeamRepository teamRepository;

    @Autowired(required = false)
    private TeamMemberRepository teamMemberRepository;

    @Autowired(required = false)
    private AuthAuditService authAuditService;

    @Autowired(required = false)
    private AgentHubMetricsCollector metricsCollector;

    public ResourceOwnershipVerifier(ResourceAccessGuard accessGuard,
                                     ProjectRepository projectRepository,
                                     WorkspaceRepository workspaceRepository,
                                     WorkflowDefinitionRepository workflowDefinitionRepository,
                                     WorkflowRunRepository workflowRunRepository,
                                     ArtifactRepository artifactRepository,
                                     DeploymentRepository deploymentRepository) {
        this(accessGuard, projectRepository, workspaceRepository, workflowDefinitionRepository,
                workflowRunRepository, artifactRepository, deploymentRepository, null, null, null, null);
    }

    @Autowired
    public ResourceOwnershipVerifier(ResourceAccessGuard accessGuard,
                                     ProjectRepository projectRepository,
                                     WorkspaceRepository workspaceRepository,
                                     WorkflowDefinitionRepository workflowDefinitionRepository,
                                     WorkflowRunRepository workflowRunRepository,
                                     ArtifactRepository artifactRepository,
                                     DeploymentRepository deploymentRepository,
                                     @Autowired(required = false) TeamRepository teamRepository,
                                     @Autowired(required = false) TeamMemberRepository teamMemberRepository,
                                     @Autowired(required = false) AuthAuditService authAuditService,
                                     @Autowired(required = false) AgentHubMetricsCollector metricsCollector) {
        this.accessGuard = accessGuard;
        this.projectRepository = projectRepository;
        this.workspaceRepository = workspaceRepository;
        this.workflowDefinitionRepository = workflowDefinitionRepository;
        this.workflowRunRepository = workflowRunRepository;
        this.artifactRepository = artifactRepository;
        this.deploymentRepository = deploymentRepository;
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.authAuditService = authAuditService;
        this.metricsCollector = metricsCollector;
    }

    public ProjectEntity verifyProjectAccess(String projectId) {
        return verifyProjectAccess(projectId, RequestContext.get().getUserId(), ProjectPermission.PROJECT_VIEW);
    }

    public ProjectEntity verifyProjectAccess(String projectId, String userId) {
        return verifyProjectAccess(projectId, userId, ProjectPermission.PROJECT_VIEW);
    }

    public ProjectEntity verifyProjectAccess(String projectId, String userId, ProjectPermission requiredPermission) {
        if (userId == null || userId.isBlank()) {
            userId = RequestContext.get().getUserId();
        }
        if (RequestContext.get().isSystem()) {
            return projectRepository.findById(projectId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND, "Project not found: " + projectId));
        }
        if (userId == null || userId.isBlank()) {
            if (accessGuard.isAllowSuperuserBypass()) {
                return projectRepository.findById(projectId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND, "Project not found: " + projectId));
            }
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Authentication required to access resource");
        }
        ProjectEntity project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND, "Project not found: " + projectId));

        if (accessGuard.isConfiguredSuperuser(userId)) {
            if (metricsCollector != null) {
                metricsCollector.recordRbacEvaluation("SUPERUSER", "ALLOW", requiredPermission != null ? requiredPermission.name() : "VIEW");
            }
            return project;
        }

        // Direct Owner has all permissions
        if (project.getOwnerId().equals(userId)) {
            if (metricsCollector != null) {
                metricsCollector.recordRbacEvaluation("OWNER", "ALLOW", requiredPermission != null ? requiredPermission.name() : "VIEW");
            }
            return project;
        }

        // Check Project Team membership & RBAC role
        ProjectRbacRole role = resolveUserRole(projectId, userId);
        if (role != null && (requiredPermission == null || role.hasPermission(requiredPermission))) {
            if (metricsCollector != null) {
                metricsCollector.recordRbacEvaluation(role.name(), "ALLOW", requiredPermission != null ? requiredPermission.name() : "VIEW");
            }
            return project;
        }

        // Denied
        if (metricsCollector != null) {
            metricsCollector.recordRbacEvaluation(role != null ? role.name() : "NONE", "DENY", requiredPermission != null ? requiredPermission.name() : "VIEW");
        }
        if (authAuditService != null) {
            authAuditService.record("RBAC_ACCESS_DENIED", userId, null, null,
                    "Access denied: role [" + (role != null ? role.name() : "NONE")
                            + "] lacks permission [" + requiredPermission + "] on project [" + projectId + "]");
        }
        throw new BusinessException(ErrorCode.FORBIDDEN,
                "Access denied: user [" + userId + "] with role [" + (role != null ? role.name() : "NONE")
                        + "] lacks permission [" + requiredPermission + "] on project " + projectId);
    }

    public ProjectRbacRole resolveUserRole(String projectId, String userId) {
        if (userId == null || userId.isBlank()) {
            return null;
        }
        if (RequestContext.get().isSystem() || accessGuard.isConfiguredSuperuser(userId)) {
            return ProjectRbacRole.OWNER;
        }
        Optional<ProjectEntity> projOpt = projectRepository != null ? projectRepository.findById(projectId) : Optional.empty();
        if (projOpt.isEmpty()) {
            return null;
        }
        if (projOpt.get().getOwnerId().equals(userId)) {
            return ProjectRbacRole.OWNER;
        }
        if (teamRepository != null && teamMemberRepository != null) {
            List<TeamEntity> teams = teamRepository.findByProjectIdOrderByCreatedAtDesc(projectId);
            for (TeamEntity team : teams) {
                List<TeamMemberEntity> members = teamMemberRepository.findByTeamIdOrderBySortOrderAsc(team.getId());
                for (TeamMemberEntity member : members) {
                    if (userId.equals(member.getAgentInstanceId())) {
                        if (member.getRoleType() != null) {
                            return ProjectRbacRole.fromString(member.getRoleType().name());
                        }
                        return ProjectRbacRole.fromString(member.getRole());
                    }
                }
            }
        }
        return null;
    }

    public WorkspaceEntity verifyWorkspaceAccess(String workspaceId) {
        return verifyWorkspaceAccess(workspaceId, RequestContext.get().getUserId(), ProjectPermission.WORKSPACE_READ);
    }

    public WorkspaceEntity verifyWorkspaceAccess(String workspaceId, String userId) {
        return verifyWorkspaceAccess(workspaceId, userId, ProjectPermission.WORKSPACE_READ);
    }

    public WorkspaceEntity verifyWorkspaceAccess(String workspaceId, String userId, ProjectPermission permission) {
        Optional<WorkspaceEntity> wsOpt = workspaceRepository != null ? workspaceRepository.findById(workspaceId) : Optional.empty();
        if (wsOpt.isEmpty() && workspaceRepository != null) {
            wsOpt = workspaceRepository.findByProjectId(workspaceId);
        }
        WorkspaceEntity ws = wsOpt.orElseThrow(
                () -> new BusinessException(ErrorCode.WORKSPACE_NOT_FOUND, "Workspace not found: " + workspaceId));
        if (ws.getProjectId() != null && !ws.getProjectId().isBlank()) {
            verifyProjectAccess(ws.getProjectId(), userId, permission != null ? permission : ProjectPermission.WORKSPACE_READ);
        } else {
            accessGuard.checkOwnership(null, userId);
        }
        return ws;
    }

    public WorkspaceEntity verifyWorkspaceWriteAccess(String workspaceId) {
        return verifyWorkspaceAccess(workspaceId, RequestContext.get().getUserId(), ProjectPermission.WORKSPACE_WRITE);
    }

    public WorkspaceEntity verifyWorkspaceWriteAccess(String workspaceId, String userId) {
        return verifyWorkspaceAccess(workspaceId, userId, ProjectPermission.WORKSPACE_WRITE);
    }

    public WorkflowDefinitionEntity verifyWorkflowAccess(String definitionId, String projectId) {
        return verifyWorkflowAccess(definitionId, projectId, RequestContext.get().getUserId());
    }

    public WorkflowDefinitionEntity verifyWorkflowAccess(String definitionId, String projectId, String userId) {
        WorkflowDefinitionEntity def = workflowDefinitionRepository.findById(definitionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WORKFLOW_DEFINITION_NOT_FOUND, "Workflow definition not found: " + definitionId));
        if (projectId != null && !projectId.isBlank()) {
            verifyProjectAccess(projectId, userId, ProjectPermission.WORKFLOW_EXECUTE);
        }
        return def;
    }

    public WorkflowRunEntity verifyRunAccess(String runId) {
        return verifyRunAccess(runId, RequestContext.get().getUserId(), ProjectPermission.PROJECT_VIEW);
    }

    public WorkflowRunEntity verifyRunAccess(String runId, String userId) {
        return verifyRunAccess(runId, userId, ProjectPermission.PROJECT_VIEW);
    }

    public WorkflowRunEntity verifyRunAccess(String runId, String userId, ProjectPermission permission) {
        WorkflowRunEntity run = workflowRunRepository.findById(runId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RUN_NOT_FOUND, "Workflow run not found: " + runId));
        if (run.getProjectId() != null && !run.getProjectId().isBlank()) {
            verifyProjectAccess(run.getProjectId(), userId, permission != null ? permission : ProjectPermission.PROJECT_VIEW);
        } else if (accessGuard != null) {
            accessGuard.checkOwnership(null, userId);
        }
        return run;
    }

    public WorkflowRunEntity verifyRunExecuteAccess(String runId) {
        return verifyRunAccess(runId, RequestContext.get().getUserId(), ProjectPermission.WORKFLOW_EXECUTE);
    }

    public WorkflowRunEntity verifyRunExecuteAccess(String runId, String userId) {
        return verifyRunAccess(runId, userId, ProjectPermission.WORKFLOW_EXECUTE);
    }

    public void verifyApprovalAccess(String runId) {
        verifyApprovalAccess(runId, RequestContext.get().getUserId());
    }

    public void verifyApprovalAccess(String runId, String userId) {
        WorkflowRunEntity run = workflowRunRepository.findById(runId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RUN_NOT_FOUND, "Workflow run not found: " + runId));
        if (run.getProjectId() != null && !run.getProjectId().isBlank()) {
            verifyProjectAccess(run.getProjectId(), userId, ProjectPermission.APPROVAL_DECIDE);
        }
    }

    public ArtifactEntity verifyArtifactAccess(String artifactId) {
        return verifyArtifactAccess(artifactId, RequestContext.get().getUserId());
    }

    public ArtifactEntity verifyArtifactAccess(String artifactId, String userId) {
        ArtifactEntity artifact = artifactRepository.findById(artifactId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ARTIFACT_NOT_FOUND, "Artifact not found: " + artifactId));
        verifyRunAccess(artifact.getRunId(), userId);
        return artifact;
    }

    public DeploymentEntity verifyDeploymentAccess(String deploymentId) {
        return verifyDeploymentAccess(deploymentId, RequestContext.get().getUserId());
    }

    public DeploymentEntity verifyDeploymentAccess(String deploymentId, String userId) {
        DeploymentEntity deployment = deploymentRepository.findById(deploymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DEPLOYMENT_NOT_FOUND, "Deployment not found: " + deploymentId));
        verifyProjectAccess(deployment.getProjectId(), userId, ProjectPermission.DEPLOYMENT_MANAGE);
        return deployment;
    }

    public String resolveProjectOwnerId(String projectId) {
        return projectRepository.findById(projectId).map(ProjectEntity::getOwnerId).orElse(null);
    }

    public String resolveWorkspaceOwnerId(String workspaceId) {
        return workspaceRepository.findById(workspaceId)
                .or(() -> workspaceRepository.findByProjectId(workspaceId))
                .map(WorkspaceEntity::getProjectId)
                .flatMap(projectRepository::findById)
                .map(ProjectEntity::getOwnerId)
                .orElse(null);
    }

    public String resolveRunOwnerId(String runId) {
        return workflowRunRepository.findById(runId)
                .map(WorkflowRunEntity::getProjectId)
                .flatMap(projectRepository::findById)
                .map(ProjectEntity::getOwnerId)
                .orElse(null);
    }

    public String resolveArtifactOwnerId(String artifactId) {
        return artifactRepository.findById(artifactId)
                .map(ArtifactEntity::getRunId)
                .map(this::resolveRunOwnerId)
                .orElse(null);
    }

    public String resolveDeploymentOwnerId(String deploymentId) {
        return deploymentRepository.findById(deploymentId)
                .map(DeploymentEntity::getProjectId)
                .flatMap(projectRepository::findById)
                .map(ProjectEntity::getOwnerId)
                .orElse(null);
    }

    public void setTeamRepository(TeamRepository teamRepository) {
        this.teamRepository = teamRepository;
    }

    public void setTeamMemberRepository(TeamMemberRepository teamMemberRepository) {
        this.teamMemberRepository = teamMemberRepository;
    }

    public void setAuthAuditService(AuthAuditService authAuditService) {
        this.authAuditService = authAuditService;
    }

    public void setMetricsCollector(AgentHubMetricsCollector metricsCollector) {
        this.metricsCollector = metricsCollector;
    }
}
