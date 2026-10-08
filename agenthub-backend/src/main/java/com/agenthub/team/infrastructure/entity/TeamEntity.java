package com.agenthub.team.infrastructure.entity;

import com.agenthub.team.domain.model.TeamTopology;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "teams")
public class TeamEntity {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "project_id", length = 64)
    private String projectId;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(length = 512)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TeamTopology topology = TeamTopology.HIERARCHICAL;

    @Column(name = "leader_agent_id", length = 64)
    private String leaderAgentId;

    @Column(name = "max_turns", nullable = false)
    private Integer maxTurns = 10;

    @Column(name = "config_json", columnDefinition = "TEXT")
    private String configJson;

    @Column(nullable = false, length = 32)
    private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public TeamEntity() {}

    public TeamEntity(String id, String projectId, String name, String description,
                      TeamTopology topology, String leaderAgentId, Integer maxTurns, String configJson) {
        this.id = id;
        this.projectId = projectId != null ? projectId : "proj-default";
        this.name = name;
        this.description = description;
        this.topology = topology != null ? topology : TeamTopology.HIERARCHICAL;
        this.leaderAgentId = leaderAgentId;
        this.maxTurns = maxTurns != null ? maxTurns : 10;
        this.configJson = configJson;
        this.status = "ACTIVE";
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
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
    public Integer getMaxTurns() { return maxTurns != null ? maxTurns : 10; }
    public void setMaxTurns(Integer maxTurns) { this.maxTurns = maxTurns; }
    public String getConfigJson() { return configJson; }
    public void setConfigJson(String configJson) { this.configJson = configJson; }
    public String getStatus() { return status != null ? status : "ACTIVE"; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
