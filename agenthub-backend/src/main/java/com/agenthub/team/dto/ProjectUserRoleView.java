package com.agenthub.team.dto;

import com.agenthub.team.domain.rbac.ProjectRbacRole;

import java.util.Set;

/**
 * View model detailing the current authenticated user's role and granted permissions for a project.
 */
public class ProjectUserRoleView {

    private String userId;
    private String projectId;
    private ProjectRbacRole rbacRole;
    private Set<String> permissions;
    private boolean isOwner;

    public ProjectUserRoleView() {}

    public ProjectUserRoleView(String userId, String projectId, ProjectRbacRole rbacRole, Set<String> permissions, boolean isOwner) {
        this.userId = userId;
        this.projectId = projectId;
        this.rbacRole = rbacRole;
        this.permissions = permissions;
        this.isOwner = isOwner;
    }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }

    public ProjectRbacRole getRbacRole() { return rbacRole; }
    public ProjectRbacRole getRole() { return rbacRole; }
    public void setRbacRole(ProjectRbacRole rbacRole) { this.rbacRole = rbacRole; }

    public Set<String> getPermissions() { return permissions; }
    public void setPermissions(Set<String> permissions) { this.permissions = permissions; }

    public boolean isOwner() { return isOwner; }
    public void setOwner(boolean owner) { isOwner = owner; }
}
