package com.agenthub.conversation.domain.model;

import java.time.LocalDateTime;

public class ConversationEvent {

    private String eventId;
    private ConversationEventType eventType;
    private String conversationId;
    private Long sequenceNum;
    private Object payload;
    private LocalDateTime timestamp;

    public ConversationEvent() {
        this.timestamp = LocalDateTime.now();
    }

    public ConversationEvent(String eventId, ConversationEventType eventType, String conversationId, Long sequenceNum, Object payload) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.conversationId = conversationId;
        this.sequenceNum = sequenceNum;
        this.payload = payload;
        this.timestamp = LocalDateTime.now();
    }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public ConversationEventType getEventType() { return eventType; }
    public void setEventType(ConversationEventType eventType) { this.eventType = eventType; }
    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }
    public Long getSequenceNum() { return sequenceNum; }
    public void setSequenceNum(Long sequenceNum) { this.sequenceNum = sequenceNum; }
    public Object getPayload() { return payload; }
    public void setPayload(Object payload) { this.payload = payload; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}
