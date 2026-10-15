package com.agenthub.team.dto;

import com.agenthub.team.domain.rbac.ProjectRbacRole;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * View model representing a member within a Project's team collaboration structure.
 */
public class ProjectMemberView {

    private String memberId;
    private String teamId;
    private String projectId;
    private String userId;
    private String displayName;
    private String email;
    private String role;
    private ProjectRbacRole rbacRole;
    private Set<String> permissions;
    private Boolean canDelegate;
    private LocalDateTime createdAt;

    public ProjectMemberView() {}

    public ProjectMemberView(String memberId, String teamId, String projectId, String userId,
                             String displayName, String email, String role, ProjectRbacRole rbacRole,
                             Set<String> permissions, Boolean canDelegate, LocalDateTime createdAt) {
        this.memberId = memberId;
        this.teamId = teamId;
        this.projectId = projectId;
        this.userId = userId;
        this.displayName = displayName;
        this.email = email;
        this.role = role;
        this.rbacRole = rbacRole;
        this.permissions = permissions;
        this.canDelegate = canDelegate;
        this.createdAt = createdAt;
    }

    public String getMemberId() { return memberId; }
    public String getId() { return memberId; }
    public void setMemberId(String memberId) { this.memberId = memberId; }

    public String getTeamId() { return teamId; }
    public void setTeamId(String teamId) { this.teamId = teamId; }

    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public ProjectRbacRole getRbacRole() { return rbacRole; }
    public void setRbacRole(ProjectRbacRole rbacRole) { this.rbacRole = rbacRole; }

    public Set<String> getPermissions() { return permissions; }
    public void setPermissions(Set<String> permissions) { this.permissions = permissions; }

    public Boolean getCanDelegate() { return canDelegate; }
    public void setCanDelegate(Boolean canDelegate) { this.canDelegate = canDelegate; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
