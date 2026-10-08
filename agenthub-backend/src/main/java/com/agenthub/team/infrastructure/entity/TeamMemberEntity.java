package com.agenthub.team.infrastructure.entity;

import com.agenthub.team.domain.model.TeamRole;
import jakarta.persistence.*;

@Entity
@Table(name = "team_members")
public class TeamMemberEntity {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "team_id", nullable = false, length = 64)
    private String teamId;

    @Column(name = "agent_instance_id", nullable = false, length = 64)
    private String agentInstanceId;

    @Column(length = 64)
    private String role;

    @Enumerated(EnumType.STRING)
    @Column(name = "role_type", length = 64)
    private TeamRole roleType = TeamRole.CUSTOM;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(length = 512)
    private String responsibilities;

    @Column(name = "system_prompt_override", columnDefinition = "TEXT")
    private String systemPromptOverride;

    @Column(name = "can_delegate")
    private Boolean canDelegate = true;

    public TeamMemberEntity() {}

    public TeamMemberEntity(String id, String teamId, String agentInstanceId, String role,
                            TeamRole roleType, Integer sortOrder, String responsibilities,
                            String systemPromptOverride, Boolean canDelegate) {
        this.id = id;
        this.teamId = teamId;
        this.agentInstanceId = agentInstanceId;
        this.role = role;
        this.roleType = roleType != null ? roleType : TeamRole.CUSTOM;
        this.sortOrder = sortOrder != null ? sortOrder : 0;
        this.responsibilities = responsibilities;
        this.systemPromptOverride = systemPromptOverride;
        this.canDelegate = canDelegate != null ? canDelegate : true;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTeamId() { return teamId; }
    public void setTeamId(String teamId) { this.teamId = teamId; }
    public String getAgentInstanceId() { return agentInstanceId; }
    public void setAgentInstanceId(String agentInstanceId) { this.agentInstanceId = agentInstanceId; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public TeamRole getRoleType() { return roleType != null ? roleType : TeamRole.CUSTOM; }
    public void setRoleType(TeamRole roleType) { this.roleType = roleType; }
    public Integer getSortOrder() { return sortOrder != null ? sortOrder : 0; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public String getResponsibilities() { return responsibilities; }
    public void setResponsibilities(String responsibilities) { this.responsibilities = responsibilities; }
    public String getSystemPromptOverride() { return systemPromptOverride; }
    public void setSystemPromptOverride(String systemPromptOverride) { this.systemPromptOverride = systemPromptOverride; }
    public Boolean getCanDelegate() { return canDelegate != null ? canDelegate : true; }
    public void setCanDelegate(Boolean canDelegate) { this.canDelegate = canDelegate; }
}
