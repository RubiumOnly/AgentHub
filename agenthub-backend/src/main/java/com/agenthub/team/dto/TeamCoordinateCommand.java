package com.agenthub.team.dto;

import com.agenthub.conversation.dto.MessageView;

import java.util.List;

public class TeamCoordinateCommand {
    private String conversationId;
    private String currentSpeakerId;
    private Integer currentTurn;
    private String lastMention;
    private String handoffTarget;
    private String taskGoal;
    private List<MessageView> recentMessages;

    public TeamCoordinateCommand() {}

    public TeamCoordinateCommand(String conversationId, String currentSpeakerId, Integer currentTurn,
                                 String lastMention, String handoffTarget, String taskGoal,
                                 List<MessageView> recentMessages) {
        this.conversationId = conversationId;
        this.currentSpeakerId = currentSpeakerId;
        this.currentTurn = currentTurn;
        this.lastMention = lastMention;
        this.handoffTarget = handoffTarget;
        this.taskGoal = taskGoal;
        this.recentMessages = recentMessages;
    }

    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }
    public String getCurrentSpeakerId() { return currentSpeakerId; }
    public void setCurrentSpeakerId(String currentSpeakerId) { this.currentSpeakerId = currentSpeakerId; }
    public Integer getCurrentTurn() { return currentTurn; }
    public void setCurrentTurn(Integer currentTurn) { this.currentTurn = currentTurn; }
    public String getLastMention() { return lastMention; }
    public void setLastMention(String lastMention) { this.lastMention = lastMention; }
    public String getHandoffTarget() { return handoffTarget; }
    public void setHandoffTarget(String handoffTarget) { this.handoffTarget = handoffTarget; }
    public String getTaskGoal() { return taskGoal; }
    public void setTaskGoal(String taskGoal) { this.taskGoal = taskGoal; }
    public List<MessageView> getRecentMessages() { return recentMessages; }
    public void setRecentMessages(List<MessageView> recentMessages) { this.recentMessages = recentMessages; }
}
