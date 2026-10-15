package com.agenthub.team.dto;

import com.agenthub.shared.contract.BoundedContext;
import com.agenthub.shared.contract.ContextCommand;
import com.agenthub.team.domain.rbac.ProjectRbacRole;

/**
 * Command to add or invite a team member to a project with an assigned RBAC role.
 */
public class AddProjectMemberCommand implements ContextCommand {

    private String userId;
    private ProjectRbacRole rbacRole = ProjectRbacRole.DEVELOPER;
    private String role;
    private String responsibilities;
    private Boolean canDelegate = true;

    public AddProjectMemberCommand() {}

    public AddProjectMemberCommand(String userId, ProjectRbacRole rbacRole, String role) {
        this(userId, rbacRole, role, null);
    }

    public AddProjectMemberCommand(String userId, ProjectRbacRole rbacRole, String role, String responsibilities) {
        this.userId = userId;
        this.rbacRole = rbacRole != null ? rbacRole : ProjectRbacRole.DEVELOPER;
        this.role = role;
        this.responsibilities = responsibilities;
        this.canDelegate = true;
    }

    @Override
    public BoundedContext boundedContext() {
        return BoundedContext.TEAM_EXTENSION;
    }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public ProjectRbacRole getRbacRole() { return rbacRole; }
    public void setRbacRole(ProjectRbacRole rbacRole) { this.rbacRole = rbacRole; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getResponsibilities() { return responsibilities; }
    public void setResponsibilities(String responsibilities) { this.responsibilities = responsibilities; }

    public Boolean getCanDelegate() { return canDelegate; }
    public void setCanDelegate(Boolean canDelegate) { this.canDelegate = canDelegate; }
}
