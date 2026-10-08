package com.agenthub.conversation.dto;

import com.agenthub.domain.conversation.model.ConversationType;

import java.time.LocalDateTime;
import java.util.List;

public class ConversationView {
    private String id;
    private String ownerId;
    private String projectId;
    private String title;
    private ConversationType type;
    private List<String> participantAgentIds;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public ConversationView() {}

    public ConversationView(String id, String ownerId, String projectId, String title, ConversationType type, List<String> participantAgentIds, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.ownerId = ownerId;
        this.projectId = projectId;
        this.title = title;
        this.type = type;
        this.participantAgentIds = participantAgentIds;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
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
    public List<String> getParticipantAgentIds() { return participantAgentIds; }
    public void setParticipantAgentIds(List<String> participantAgentIds) { this.participantAgentIds = participantAgentIds; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
