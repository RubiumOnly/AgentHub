package com.agenthub.conversation.infrastructure.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "conversation_participants")
public class ConversationParticipantEntity {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "conversation_id", nullable = false, length = 64)
    private String conversationId;

    @Column(name = "agent_id", nullable = false, length = 64)
    private String agentId;

    @Column(name = "joined_at", nullable = false)
    private LocalDateTime joinedAt;

    public ConversationParticipantEntity() {}

    public ConversationParticipantEntity(String id, String conversationId, String agentId) {
        this.id = id;
        this.conversationId = conversationId;
        this.agentId = agentId;
        this.joinedAt = LocalDateTime.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }
    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }
    public LocalDateTime getJoinedAt() { return joinedAt; }
    public void setJoinedAt(LocalDateTime joinedAt) { this.joinedAt = joinedAt; }
}
