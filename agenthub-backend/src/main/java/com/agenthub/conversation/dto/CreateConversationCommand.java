package com.agenthub.conversation.dto;

import com.agenthub.domain.conversation.model.ConversationType;

import java.util.List;

public class CreateConversationCommand {
    private String title;
    private ConversationType type;
    private List<String> agentIds;
    private String projectId;
    private String teamId;

    public CreateConversationCommand() {}

    public CreateConversationCommand(String title, ConversationType type, List<String> agentIds, String projectId) {
        this.title = title;
        this.type = type;
        this.agentIds = agentIds;
        this.projectId = projectId;
    }

    public CreateConversationCommand(String title, ConversationType type, List<String> agentIds, String projectId, String teamId) {
        this.title = title;
        this.type = type;
        this.agentIds = agentIds;
        this.projectId = projectId;
        this.teamId = teamId;
    }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public ConversationType getType() { return type; }
    public void setType(ConversationType type) { this.type = type; }
    public List<String> getAgentIds() { return agentIds; }
    public void setAgentIds(List<String> agentIds) { this.agentIds = agentIds; }
    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }
    public String getTeamId() { return teamId; }
    public void setTeamId(String teamId) { this.teamId = teamId; }
}
