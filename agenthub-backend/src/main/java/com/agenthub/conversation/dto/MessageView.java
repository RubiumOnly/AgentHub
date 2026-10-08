package com.agenthub.conversation.dto;

import com.agenthub.domain.conversation.model.SenderType;

import java.time.LocalDateTime;

public class MessageView {
    private String id;
    private String conversationId;
    private String senderId;
    private SenderType senderType;
    private String content;
    private String cardPayloadJson;
    private String mentions;
    private String schemaVersion;
    private Long sequenceNum;
    private LocalDateTime createdAt;

    public MessageView() {}

    public MessageView(String id, String conversationId, String senderId, SenderType senderType, String content, String cardPayloadJson, String mentions, String schemaVersion, Long sequenceNum, LocalDateTime createdAt) {
        this.id = id;
        this.conversationId = conversationId;
        this.senderId = senderId;
        this.senderType = senderType;
        this.content = content;
        this.cardPayloadJson = cardPayloadJson;
        this.mentions = mentions;
        this.schemaVersion = schemaVersion;
        this.sequenceNum = sequenceNum;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }
    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }
    public SenderType getSenderType() { return senderType; }
    public void setSenderType(SenderType senderType) { this.senderType = senderType; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getCardPayloadJson() { return cardPayloadJson; }
    public void setCardPayloadJson(String cardPayloadJson) { this.cardPayloadJson = cardPayloadJson; }
    public String getMentions() { return mentions; }
    public void setMentions(String mentions) { this.mentions = mentions; }
    public String getSchemaVersion() { return schemaVersion; }
    public void setSchemaVersion(String schemaVersion) { this.schemaVersion = schemaVersion; }
    public Long getSequenceNum() { return sequenceNum; }
    public void setSequenceNum(Long sequenceNum) { this.sequenceNum = sequenceNum; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
