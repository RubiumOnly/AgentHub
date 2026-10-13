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
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Unified resource ownership resolution and verification across Project, Workspace,
 * Workflow, Run, Artifact, and Deployment bounded contexts.
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

    public ResourceOwnershipVerifier(ResourceAccessGuard accessGuard,
                                     ProjectRepository projectRepository,
                                     WorkspaceRepository workspaceRepository,
                                     WorkflowDefinitionRepository workflowDefinitionRepository,
                                     WorkflowRunRepository workflowRunRepository,
                                     ArtifactRepository artifactRepository,
                                     DeploymentRepository deploymentRepository) {
        this.accessGuard = accessGuard;
        this.projectRepository = projectRepository;
        this.workspaceRepository = workspaceRepository;
        this.workflowDefinitionRepository = workflowDefinitionRepository;
        this.workflowRunRepository = workflowRunRepository;
        this.artifactRepository = artifactRepository;
        this.deploymentRepository = deploymentRepository;
    }

    public ProjectEntity verifyProjectAccess(String projectId) {
        return verifyProjectAccess(projectId, RequestContext.get().getUserId());
    }

    public ProjectEntity verifyProjectAccess(String projectId, String userId) {
        ProjectEntity project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND, "Project not found: " + projectId));
        accessGuard.checkOwnership(project.getOwnerId(), userId);
        return project;
    }

    public WorkspaceEntity verifyWorkspaceAccess(String workspaceId) {
        return verifyWorkspaceAccess(workspaceId, RequestContext.get().getUserId());
    }

    public WorkspaceEntity verifyWorkspaceAccess(String workspaceId, String userId) {
        Optional<WorkspaceEntity> wsOpt = workspaceRepository.findById(workspaceId);
        if (wsOpt.isEmpty()) {
            wsOpt = workspaceRepository.findByProjectId(workspaceId);
        }
        WorkspaceEntity ws = wsOpt.orElseThrow(
                () -> new BusinessException(ErrorCode.WORKSPACE_NOT_FOUND, "Workspace not found: " + workspaceId));
        if (ws.getProjectId() != null && !ws.getProjectId().isBlank()) {
            verifyProjectAccess(ws.getProjectId(), userId);
        }
        return ws;
    }

    public WorkflowDefinitionEntity verifyWorkflowAccess(String definitionId, String projectId) {
        return verifyWorkflowAccess(definitionId, projectId, RequestContext.get().getUserId());
    }

    public WorkflowDefinitionEntity verifyWorkflowAccess(String definitionId, String projectId, String userId) {
        WorkflowDefinitionEntity def = workflowDefinitionRepository.findById(definitionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WORKFLOW_DEFINITION_NOT_FOUND, "Workflow definition not found: " + definitionId));
        if (projectId != null && !projectId.isBlank()) {
            verifyProjectAccess(projectId, userId);
        }
        return def;
    }

    public WorkflowRunEntity verifyRunAccess(String runId) {
        return verifyRunAccess(runId, RequestContext.get().getUserId());
    }

    public WorkflowRunEntity verifyRunAccess(String runId, String userId) {
        WorkflowRunEntity run = workflowRunRepository.findById(runId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RUN_NOT_FOUND, "Workflow run not found: " + runId));
        if (run.getProjectId() != null && !run.getProjectId().isBlank()) {
            projectRepository.findById(run.getProjectId())
                    .ifPresent(project -> accessGuard.checkOwnership(project.getOwnerId(), userId));
        }
        return run;
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
        verifyProjectAccess(deployment.getProjectId(), userId);
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
}
