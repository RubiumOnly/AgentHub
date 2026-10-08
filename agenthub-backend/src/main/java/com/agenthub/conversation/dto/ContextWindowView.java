package com.agenthub.conversation.dto;

import java.util.Collections;
import java.util.List;

public class ContextWindowView {
    private String conversationId;
    private List<MessageView> activeMessages;
    private String rollingSummary;
    private int totalEstimatedTokens;
    private int trimmedCount;

    public ContextWindowView() {
        this.activeMessages = Collections.emptyList();
    }

    public ContextWindowView(String conversationId, List<MessageView> activeMessages,
                             String rollingSummary, int totalEstimatedTokens, int trimmedCount) {
        this.conversationId = conversationId;
        this.activeMessages = activeMessages != null ? activeMessages : Collections.emptyList();
        this.rollingSummary = rollingSummary;
        this.totalEstimatedTokens = totalEstimatedTokens;
        this.trimmedCount = trimmedCount;
    }

    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }
    public List<MessageView> getActiveMessages() { return activeMessages; }
    public void setActiveMessages(List<MessageView> activeMessages) { this.activeMessages = activeMessages; }
    public String getRollingSummary() { return rollingSummary; }
    public void setRollingSummary(String rollingSummary) { this.rollingSummary = rollingSummary; }
    public int getTotalEstimatedTokens() { return totalEstimatedTokens; }
    public void setTotalEstimatedTokens(int totalEstimatedTokens) { this.totalEstimatedTokens = totalEstimatedTokens; }
    public int getTrimmedCount() { return trimmedCount; }
    public void setTrimmedCount(int trimmedCount) { this.trimmedCount = trimmedCount; }
}
