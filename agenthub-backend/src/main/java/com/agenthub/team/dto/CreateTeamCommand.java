package com.agenthub.team.dto;

import com.agenthub.team.domain.model.TeamTopology;

import java.util.List;

public class CreateTeamCommand {
    private String projectId;
    private String name;
    private String description;
    private TeamTopology topology;
    private String leaderAgentId;
    private Integer maxTurns;
    private String configJson;
    private List<AddTeamMemberCommand> initialMembers;

    public CreateTeamCommand() {}

    public CreateTeamCommand(String projectId, String name, String description,
                             TeamTopology topology, String leaderAgentId, Integer maxTurns,
                             String configJson, List<AddTeamMemberCommand> initialMembers) {
        this.projectId = projectId;
        this.name = name;
        this.description = description;
        this.topology = topology;
        this.leaderAgentId = leaderAgentId;
        this.maxTurns = maxTurns;
        this.configJson = configJson;
        this.initialMembers = initialMembers;
    }

    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public TeamTopology getTopology() { return topology; }
    public void setTopology(TeamTopology topology) { this.topology = topology; }
    public String getLeaderAgentId() { return leaderAgentId; }
    public void setLeaderAgentId(String leaderAgentId) { this.leaderAgentId = leaderAgentId; }
    public Integer getMaxTurns() { return maxTurns; }
    public void setMaxTurns(Integer maxTurns) { this.maxTurns = maxTurns; }
    public String getConfigJson() { return configJson; }
    public void setConfigJson(String configJson) { this.configJson = configJson; }
    public List<AddTeamMemberCommand> getInitialMembers() { return initialMembers; }
    public void setInitialMembers(List<AddTeamMemberCommand> initialMembers) { this.initialMembers = initialMembers; }
}
