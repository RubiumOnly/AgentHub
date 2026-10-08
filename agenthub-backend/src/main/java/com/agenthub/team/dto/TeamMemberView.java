package com.agenthub.team.dto;

import com.agenthub.team.domain.model.TeamRole;

public class TeamMemberView {
    private String id;
    private String teamId;
    private String agentInstanceId;
    private String role;
    private TeamRole roleType;
    private Integer sortOrder;
    private String responsibilities;
    private String systemPromptOverride;
    private Boolean canDelegate;

    public TeamMemberView() {}

    public TeamMemberView(String id, String teamId, String agentInstanceId, String role,
                          TeamRole roleType, Integer sortOrder, String responsibilities,
                          String systemPromptOverride, Boolean canDelegate) {
        this.id = id;
        this.teamId = teamId;
        this.agentInstanceId = agentInstanceId;
        this.role = role;
        this.roleType = roleType;
        this.sortOrder = sortOrder;
        this.responsibilities = responsibilities;
        this.systemPromptOverride = systemPromptOverride;
        this.canDelegate = canDelegate;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTeamId() { return teamId; }
    public void setTeamId(String teamId) { this.teamId = teamId; }
    public String getAgentInstanceId() { return agentInstanceId; }
    public void setAgentInstanceId(String agentInstanceId) { this.agentInstanceId = agentInstanceId; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public TeamRole getRoleType() { return roleType; }
    public void setRoleType(TeamRole roleType) { this.roleType = roleType; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public String getResponsibilities() { return responsibilities; }
    public void setResponsibilities(String responsibilities) { this.responsibilities = responsibilities; }
    public String getSystemPromptOverride() { return systemPromptOverride; }
    public void setSystemPromptOverride(String systemPromptOverride) { this.systemPromptOverride = systemPromptOverride; }
    public Boolean getCanDelegate() { return canDelegate; }
    public void setCanDelegate(Boolean canDelegate) { this.canDelegate = canDelegate; }
}
