package com.agenthub.conversation.dto;

import com.agenthub.domain.conversation.model.ConversationType;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

public class ConversationView {
    private String id;
    private String ownerId;
    private String projectId;
    private String teamId;
    private String title;
    private ConversationType type;
    private List<String> participantAgentIds;
    private Long lastSequenceNum;
    private String summary;
    private Integer tokenCount;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public ConversationView() {
        this.participantAgentIds = Collections.emptyList();
    }

    public ConversationView(String id, String ownerId, String projectId, String title,
                            ConversationType type, List<String> participantAgentIds,
                            LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.ownerId = ownerId;
        this.projectId = projectId;
        this.title = title;
        this.type = type;
        this.participantAgentIds = participantAgentIds != null ? participantAgentIds : Collections.emptyList();
        this.lastSequenceNum = 0L;
        this.tokenCount = 0;
        this.status = "ACTIVE";
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public ConversationView(String id, String ownerId, String projectId, String teamId, String title,
                            ConversationType type, List<String> participantAgentIds, Long lastSequenceNum,
                            String summary, Integer tokenCount, String status,
                            LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.ownerId = ownerId;
        this.projectId = projectId;
        this.teamId = teamId;
        this.title = title;
        this.type = type;
        this.participantAgentIds = participantAgentIds != null ? participantAgentIds : Collections.emptyList();
        this.lastSequenceNum = lastSequenceNum != null ? lastSequenceNum : 0L;
        this.summary = summary;
        this.tokenCount = tokenCount != null ? tokenCount : 0;
        this.status = status != null ? status : "ACTIVE";
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
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
    public List<String> getParticipantAgentIds() { return participantAgentIds; }
    public void setParticipantAgentIds(List<String> participantAgentIds) { this.participantAgentIds = participantAgentIds; }
    public Long getLastSequenceNum() { return lastSequenceNum != null ? lastSequenceNum : 0L; }
    public void setLastSequenceNum(Long lastSequenceNum) { this.lastSequenceNum = lastSequenceNum; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public Integer getTokenCount() { return tokenCount != null ? tokenCount : 0; }
    public void setTokenCount(Integer tokenCount) { this.tokenCount = tokenCount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
