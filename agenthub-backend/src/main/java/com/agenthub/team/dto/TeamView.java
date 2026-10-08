package com.agenthub.team.dto;

import com.agenthub.team.domain.model.TeamTopology;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

public class TeamView {
    private String id;
    private String projectId;
    private String name;
    private String description;
    private TeamTopology topology;
    private String leaderAgentId;
    private Integer maxTurns;
    private String configJson;
    private String status;
    private List<TeamMemberView> members;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public TeamView() {
        this.members = Collections.emptyList();
    }

    public TeamView(String id, String projectId, String name, String description,
                    TeamTopology topology, String leaderAgentId, Integer maxTurns,
                    String configJson, String status, List<TeamMemberView> members,
                    LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.projectId = projectId;
        this.name = name;
        this.description = description;
        this.topology = topology;
        this.leaderAgentId = leaderAgentId;
        this.maxTurns = maxTurns;
        this.configJson = configJson;
        this.status = status;
        this.members = members != null ? members : Collections.emptyList();
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
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
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public List<TeamMemberView> getMembers() { return members; }
    public void setMembers(List<TeamMemberView> members) { this.members = members; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
