package com.agenthub.conversation.dto;

public class ConversationSummaryView {
    private String conversationId;
    private String summary;
    private int messageCount;
    private int tokenCount;

    public ConversationSummaryView() {}

    public ConversationSummaryView(String conversationId, String summary, int messageCount, int tokenCount) {
        this.conversationId = conversationId;
        this.summary = summary;
        this.messageCount = messageCount;
        this.tokenCount = tokenCount;
    }

    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public int getMessageCount() { return messageCount; }
    public void setMessageCount(int messageCount) { this.messageCount = messageCount; }
    public int getTokenCount() { return tokenCount; }
    public void setTokenCount(int tokenCount) { this.tokenCount = tokenCount; }
}
