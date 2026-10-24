package com.agenthub.sandbox.application.deployment;

import com.agenthub.identity.infrastructure.security.ResourceAccessGuard;
import com.agenthub.project.infrastructure.entity.ProjectEntity;
import com.agenthub.project.infrastructure.repository.ProjectRepository;
import com.agenthub.shared.context.RequestContext;
import com.agenthub.shared.exception.BusinessException;
import com.agenthub.shared.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 部署访问控制缝（Phase 10 R6 自 DeploymentApplicationService 抽出）：
 * 项目只读访问与 DEPLOYMENT_MANAGE 管理访问的统一判定。
 */
@Component
public class DeploymentAccessGuard {

    private final ProjectRepository projectRepository;
    private final ResourceAccessGuard accessGuard;
    private final com.agenthub.identity.infrastructure.security.ResourceOwnershipVerifier ownershipVerifier;

    public DeploymentAccessGuard(@Autowired(required = false) ProjectRepository projectRepository,
                                 @Autowired(required = false) ResourceAccessGuard accessGuard,
                                 @Autowired(required = false) com.agenthub.identity.infrastructure.security.ResourceOwnershipVerifier ownershipVerifier) {
        this.projectRepository = projectRepository;
        this.accessGuard = accessGuard;
        this.ownershipVerifier = ownershipVerifier;
    }

    public void checkProjectAccess(String projectId) {
        if (RequestContext.get().isSystem()) {
            return;
        }
        if (projectRepository != null && accessGuard != null && projectId != null) {
            ProjectEntity project = projectRepository.findById(projectId).orElse(null);
            if (project != null) {
                if (ownershipVerifier != null) {
                    ownershipVerifier.verifyProjectAccess(projectId);
                } else {
                    accessGuard.checkOwnership(project.getOwnerId(), RequestContext.get().getUserId());
                }
            } else if (accessGuard.isAllowSuperuserBypass()) {
                return;
            } else if (ownershipVerifier != null) {
                ownershipVerifier.verifyProjectAccess(projectId);
            } else {
                throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND, "Project not found: " + projectId);
            }
        } else if (ownershipVerifier != null && projectId != null) {
            ownershipVerifier.verifyProjectAccess(projectId);
        }
    }

    public void checkDeploymentManageAccess(String projectId) {
        if (RequestContext.get().isSystem()) {
            return;
        }
        if (projectRepository != null && accessGuard != null && projectId != null) {
            ProjectEntity project = projectRepository.findById(projectId).orElse(null);
            if (project != null) {
                if (ownershipVerifier != null) {
                    ownershipVerifier.verifyProjectAccess(projectId, RequestContext.get().getUserId(), com.agenthub.team.domain.rbac.ProjectPermission.DEPLOYMENT_MANAGE);
                } else {
                    accessGuard.checkOwnership(project.getOwnerId(), RequestContext.get().getUserId());
                }
            } else if (accessGuard.isAllowSuperuserBypass()) {
                return;
            } else if (ownershipVerifier != null) {
                ownershipVerifier.verifyProjectAccess(projectId, RequestContext.get().getUserId(), com.agenthub.team.domain.rbac.ProjectPermission.DEPLOYMENT_MANAGE);
            } else {
                throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND, "Project not found: " + projectId);
            }
        } else if (ownershipVerifier != null && projectId != null) {
            ownershipVerifier.verifyProjectAccess(projectId, RequestContext.get().getUserId(), com.agenthub.team.domain.rbac.ProjectPermission.DEPLOYMENT_MANAGE);
        }
    }
}
