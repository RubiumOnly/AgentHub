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

    @Column(nullable = false, length = 128)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ConversationType type;

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
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public ConversationType getType() { return type; }
    public void setType(ConversationType type) { this.type = type; }
    public String getParticipantAgentIds() { return participantAgentIds; }
    public void setParticipantAgentIds(String participantAgentIds) { this.participantAgentIds = participantAgentIds; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
