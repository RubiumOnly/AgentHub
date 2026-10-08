package com.agenthub.conversation.domain.model;

import com.agenthub.conversation.dto.MessageView;

import java.util.Collections;
import java.util.List;

public class ContextWindow {

    private List<MessageView> activeMessages;
    private String rollingSummary;
    private int totalEstimatedTokens;
    private int trimmedCount;

    public ContextWindow() {
        this.activeMessages = Collections.emptyList();
    }

    public ContextWindow(List<MessageView> activeMessages, String rollingSummary, int totalEstimatedTokens, int trimmedCount) {
        this.activeMessages = activeMessages != null ? activeMessages : Collections.emptyList();
        this.rollingSummary = rollingSummary;
        this.totalEstimatedTokens = totalEstimatedTokens;
        this.trimmedCount = trimmedCount;
    }

    public List<MessageView> getActiveMessages() { return activeMessages; }
    public void setActiveMessages(List<MessageView> activeMessages) { this.activeMessages = activeMessages; }
    public String getRollingSummary() { return rollingSummary; }
    public void setRollingSummary(String rollingSummary) { this.rollingSummary = rollingSummary; }
    public int getTotalEstimatedTokens() { return totalEstimatedTokens; }
    public void setTotalEstimatedTokens(int totalEstimatedTokens) { this.totalEstimatedTokens = totalEstimatedTokens; }
    public int getTrimmedCount() { return trimmedCount; }
    public void setTrimmedCount(int trimmedCount) { this.trimmedCount = trimmedCount; }
}
