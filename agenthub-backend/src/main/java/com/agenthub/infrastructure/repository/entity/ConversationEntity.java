package com.agenthub.infrastructure.repository.entity;

import com.agenthub.domain.conversation.model.ConversationType;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "agenthub_conversations")
public class ConversationEntity {

    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false, length = 128)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ConversationType type;

    @Column(length = 512)
    private String participantAgentIds;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public ConversationEntity() {}

    public ConversationEntity(String id, String title, ConversationType type, String participantAgentIds) {
        this.id = id;
        this.title = title;
        this.type = type;
        this.participantAgentIds = participantAgentIds;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
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
