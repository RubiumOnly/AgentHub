package com.agenthub.team.domain.rbac;

/**
 * Granular permissions governing Project, Workspace, Workflow, Run, Artifact, Deployment, Secret, and Ops operations.
 */
public enum ProjectPermission {
    /**
     * Read project metadata, members, and workspace trees/files.
     */
    PROJECT_VIEW,

    /**
     * Update project settings, invite/remove members, and modify member roles.
     */
    PROJECT_MANAGE,

    /**
     * Read workspace files, diffs, and snapshots.
     */
    WORKSPACE_READ,

    /**
     * Write, edit, or delete files in the workspace, or revert files.
     */
    WORKSPACE_WRITE,

    /**
     * Start, cancel, rework, or reject workflow runs.
     */
    WORKFLOW_EXECUTE,

    /**
     * Accept or reject human-in-the-loop approvals.
     */
    APPROVAL_DECIDE,

    /**
     * Create, start, stop, or probe preview deployments.
     */
    DEPLOYMENT_MANAGE,

    /**
     * Register, update, rotate, or delete provider credentials and secrets.
     */
    SECRET_MANAGE,

    /**
     * Query immutable audit logs, view artifact lineage, and export evidence packages.
     */
    AUDIT_VIEW,

    /**
     * Trigger backups, verify backup integrity, and perform clean restorations.
     */
    DISASTER_RECOVERY
}
