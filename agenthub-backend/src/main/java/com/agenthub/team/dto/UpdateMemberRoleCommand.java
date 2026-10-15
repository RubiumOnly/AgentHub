package com.agenthub.team.dto;

import com.agenthub.shared.contract.BoundedContext;
import com.agenthub.shared.contract.ContextCommand;
import com.agenthub.team.domain.rbac.ProjectRbacRole;

/**
 * Command to update the RBAC role of an existing project team member.
 */
public class UpdateMemberRoleCommand implements ContextCommand {

    private ProjectRbacRole rbacRole;
    private String role;
    private String responsibilities;

    public UpdateMemberRoleCommand() {}

    public UpdateMemberRoleCommand(ProjectRbacRole rbacRole) {
        this(rbacRole, null);
    }

    public UpdateMemberRoleCommand(ProjectRbacRole rbacRole, String role) {
        this.rbacRole = rbacRole;
        this.role = role;
    }

    @Override
    public BoundedContext boundedContext() {
        return BoundedContext.TEAM_EXTENSION;
    }

    public ProjectRbacRole getRbacRole() { return rbacRole; }
    public void setRbacRole(ProjectRbacRole rbacRole) { this.rbacRole = rbacRole; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getResponsibilities() { return responsibilities; }
    public void setResponsibilities(String responsibilities) { this.responsibilities = responsibilities; }
}
