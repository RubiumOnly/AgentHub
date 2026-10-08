package com.agenthub.infrastructure.repository.entity;

import com.agenthub.domain.conversation.model.ConversationType;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "conversations")
public class ConversationEntity {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "owner_id", nullable = false, length = 64)
    private String ownerId;

    @Column(name = "project_id", length = 64)
    private String projectId;

    @Column(name = "team_id", length = 64)
    private String teamId;

    @Column(nullable = false, length = 128)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ConversationType type;

    @Column(name = "last_sequence_num", nullable = false)
    private Long lastSequenceNum = 0L;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(name = "token_count", nullable = false)
    private Integer tokenCount = 0;

    @Column(nullable = false, length = 32)
    private String status = "ACTIVE";

    @Transient
    private String participantAgentIds;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public ConversationEntity() {}

    public ConversationEntity(String id, String title, ConversationType type, String participantAgentIds) {
        this.id = id;
        this.ownerId = "user-1";
        this.projectId = "proj-default";
        this.title = title;
        this.type = type;
        this.participantAgentIds = participantAgentIds;
        this.lastSequenceNum = 0L;
        this.tokenCount = 0;
        this.status = "ACTIVE";
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public ConversationEntity(String id, String ownerId, String projectId, String title, ConversationType type, String participantAgentIds) {
        this.id = id;
        this.ownerId = ownerId != null ? ownerId : "user-1";
        this.projectId = projectId != null ? projectId : "proj-default";
        this.title = title;
        this.type = type;
        this.participantAgentIds = participantAgentIds;
        this.lastSequenceNum = 0L;
        this.tokenCount = 0;
        this.status = "ACTIVE";
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public ConversationEntity(String id, String ownerId, String projectId, String teamId, String title, ConversationType type, String participantAgentIds) {
        this.id = id;
        this.ownerId = ownerId != null ? ownerId : "user-1";
        this.projectId = projectId != null ? projectId : "proj-default";
        this.teamId = teamId;
        this.title = title;
        this.type = type;
        this.participantAgentIds = participantAgentIds;
        this.lastSequenceNum = 0L;
        this.tokenCount = 0;
        this.status = "ACTIVE";
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }
    public String getTeamId() { return teamId; }
    public void setTeamId(String teamId) { this.teamId = teamId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public ConversationType getType() { return type; }
    public void setType(ConversationType type) { this.type = type; }
    public Long getLastSequenceNum() { return lastSequenceNum != null ? lastSequenceNum : 0L; }
    public void setLastSequenceNum(Long lastSequenceNum) { this.lastSequenceNum = lastSequenceNum; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public Integer getTokenCount() { return tokenCount != null ? tokenCount : 0; }
    public void setTokenCount(Integer tokenCount) { this.tokenCount = tokenCount; }
    public String getStatus() { return status != null ? status : "ACTIVE"; }
    public void setStatus(String status) { this.status = status; }
    public String getParticipantAgentIds() { return participantAgentIds; }
    public void setParticipantAgentIds(String participantAgentIds) { this.participantAgentIds = participantAgentIds; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
