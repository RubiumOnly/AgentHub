package com.agenthub.team.domain.rbac;

import java.util.Collections;
import java.util.Set;

/**
 * Enterprise RBAC roles assigned to project members.
 * Supports OWNER, ADMIN, MAINTAINER, DEVELOPER, VIEWER, and AUDITOR.
 */
public enum ProjectRbacRole {

    /**
     * Project Owner: unconstrained full access to project, workspace, runs, approvals, deployments, and ops.
     */
    OWNER(Set.of(ProjectPermission.values())),

    /**
     * Team Administrator: full administrative control except deleting the project or transferring ownership.
     */
    ADMIN(Set.of(
            ProjectPermission.PROJECT_VIEW,
            ProjectPermission.PROJECT_MANAGE,
            ProjectPermission.WORKSPACE_READ,
            ProjectPermission.WORKSPACE_WRITE,
            ProjectPermission.WORKFLOW_EXECUTE,
            ProjectPermission.APPROVAL_DECIDE,
            ProjectPermission.DEPLOYMENT_MANAGE,
            ProjectPermission.SECRET_MANAGE,
            ProjectPermission.AUDIT_VIEW,
            ProjectPermission.DISASTER_RECOVERY
    )),

    /**
     * Technical Maintainer: can write code, run workflows, approve changes, and trigger deployments.
     */
    MAINTAINER(Set.of(
            ProjectPermission.PROJECT_VIEW,
            ProjectPermission.WORKSPACE_READ,
            ProjectPermission.WORKSPACE_WRITE,
            ProjectPermission.WORKFLOW_EXECUTE,
            ProjectPermission.APPROVAL_DECIDE,
            ProjectPermission.DEPLOYMENT_MANAGE,
            ProjectPermission.AUDIT_VIEW
    )),

    /**
     * Developer: can edit files, run workflows, rework steps, and inspect diffs.
     * Cannot approve high-impact changes, manage preview deployments, or modify team members.
     */
    DEVELOPER(Set.of(
            ProjectPermission.PROJECT_VIEW,
            ProjectPermission.WORKSPACE_READ,
            ProjectPermission.WORKSPACE_WRITE,
            ProjectPermission.WORKFLOW_EXECUTE,
            ProjectPermission.AUDIT_VIEW
    )),

    /**
     * Viewer: read-only access to project, workspace files, run execution progress, and diffs.
     */
    VIEWER(Set.of(
            ProjectPermission.PROJECT_VIEW,
            ProjectPermission.WORKSPACE_READ,
            ProjectPermission.AUDIT_VIEW
    )),

    /**
     * Auditor: read-only compliance inspection, immutable audit logs querying, and evidence verification.
     */
    AUDITOR(Set.of(
            ProjectPermission.PROJECT_VIEW,
            ProjectPermission.WORKSPACE_READ,
            ProjectPermission.AUDIT_VIEW
    ));

    private final Set<ProjectPermission> permissions;

    ProjectRbacRole(Set<ProjectPermission> permissions) {
        this.permissions = Collections.unmodifiableSet(permissions);
    }

    public boolean hasPermission(ProjectPermission permission) {
        return permissions.contains(permission);
    }

    public Set<ProjectPermission> getPermissions() {
        return permissions;
    }

    public static ProjectRbacRole fromString(String roleStr) {
        if (roleStr == null || roleStr.isBlank()) {
            return VIEWER;
        }
        String normalized = roleStr.trim().toUpperCase();
        for (ProjectRbacRole r : values()) {
            if (r.name().equals(normalized)) {
                return r;
            }
        }
        if (normalized.contains("OWNER")) return OWNER;
        if (normalized.contains("ADMIN") || normalized.contains("LEAD")) return ADMIN;
        if (normalized.contains("MAINTAIN")) return MAINTAINER;
        if (normalized.contains("DEV") || normalized.contains("CODE") || normalized.contains("ARCHITECT")) return DEVELOPER;
        if (normalized.contains("AUDIT") || normalized.contains("COMPLIANCE")) return AUDITOR;
        return VIEWER;
    }
}
