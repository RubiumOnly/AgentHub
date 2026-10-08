package com.agenthub.team.domain.model;

import com.agenthub.conversation.dto.MessageView;

import java.util.Collections;
import java.util.List;

/**
 * Coordination context provided to topology decision strategies.
 */
public class TeamCoordinationContext {

    private String conversationId;
    private String currentSpeakerId;
    private int currentTurn;
    private int maxTurns;
    private String lastMention;
    private String handoffTarget;
    private String taskGoal;
    private List<MessageView> recentMessages;

    public TeamCoordinationContext() {
        this.recentMessages = Collections.emptyList();
    }

    public TeamCoordinationContext(String conversationId, String currentSpeakerId, int currentTurn,
                                   int maxTurns, String lastMention, String handoffTarget,
                                   String taskGoal, List<MessageView> recentMessages) {
        this.conversationId = conversationId;
        this.currentSpeakerId = currentSpeakerId;
        this.currentTurn = currentTurn;
        this.maxTurns = maxTurns;
        this.lastMention = lastMention;
        this.handoffTarget = handoffTarget;
        this.taskGoal = taskGoal;
        this.recentMessages = recentMessages != null ? recentMessages : Collections.emptyList();
    }

    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }
    public String getCurrentSpeakerId() { return currentSpeakerId; }
    public void setCurrentSpeakerId(String currentSpeakerId) { this.currentSpeakerId = currentSpeakerId; }
    public int getCurrentTurn() { return currentTurn; }
    public void setCurrentTurn(int currentTurn) { this.currentTurn = currentTurn; }
    public int getMaxTurns() { return maxTurns; }
    public void setMaxTurns(int maxTurns) { this.maxTurns = maxTurns; }
    public String getLastMention() { return lastMention; }
    public void setLastMention(String lastMention) { this.lastMention = lastMention; }
    public String getHandoffTarget() { return handoffTarget; }
    public void setHandoffTarget(String handoffTarget) { this.handoffTarget = handoffTarget; }
    public String getTaskGoal() { return taskGoal; }
    public void setTaskGoal(String taskGoal) { this.taskGoal = taskGoal; }
    public List<MessageView> getRecentMessages() { return recentMessages; }
    public void setRecentMessages(List<MessageView> recentMessages) { this.recentMessages = recentMessages; }
}
