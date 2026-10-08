package com.agenthub.conversation.dto;

import com.agenthub.conversation.domain.model.MessageProtocolType;
import com.agenthub.conversation.domain.model.MessageType;
import com.agenthub.domain.conversation.model.SenderType;

import java.time.LocalDateTime;

public class MessageView {
    private String id;
    private String conversationId;
    private String senderId;
    private SenderType senderType;
    private String recipientId;
    private MessageType messageType;
    private MessageProtocolType protocolType;
    private String content;
    private String cardPayloadJson;
    private String mentions;
    private String schemaVersion;
    private Long sequenceNum;
    private Integer tokenCount;
    private String inReplyToId;
    private LocalDateTime createdAt;

    public MessageView() {}

    public MessageView(String id, String conversationId, String senderId, SenderType senderType,
                       String content, String cardPayloadJson, String mentions,
                       String schemaVersion, Long sequenceNum, LocalDateTime createdAt) {
        this.id = id;
        this.conversationId = conversationId;
        this.senderId = senderId;
        this.senderType = senderType;
        this.content = content;
        this.cardPayloadJson = cardPayloadJson;
        this.mentions = mentions;
        this.schemaVersion = schemaVersion;
        this.sequenceNum = sequenceNum;
        this.messageType = MessageType.BROADCAST;
        this.protocolType = MessageProtocolType.NORMAL;
        this.tokenCount = 0;
        this.createdAt = createdAt;
    }

    public MessageView(String id, String conversationId, String senderId, SenderType senderType,
                       String recipientId, MessageType messageType, MessageProtocolType protocolType,
                       String content, String cardPayloadJson, String mentions,
                       String schemaVersion, Long sequenceNum, Integer tokenCount,
                       String inReplyToId, LocalDateTime createdAt) {
        this.id = id;
        this.conversationId = conversationId;
        this.senderId = senderId;
        this.senderType = senderType;
        this.recipientId = recipientId;
        this.messageType = messageType != null ? messageType : MessageType.BROADCAST;
        this.protocolType = protocolType != null ? protocolType : MessageProtocolType.NORMAL;
        this.content = content;
        this.cardPayloadJson = cardPayloadJson;
        this.mentions = mentions;
        this.schemaVersion = schemaVersion;
        this.sequenceNum = sequenceNum;
        this.tokenCount = tokenCount != null ? tokenCount : 0;
        this.inReplyToId = inReplyToId;
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
    public String getRecipientId() { return recipientId; }
    public void setRecipientId(String recipientId) { this.recipientId = recipientId; }
    public MessageType getMessageType() { return messageType; }
    public void setMessageType(MessageType messageType) { this.messageType = messageType; }
    public MessageProtocolType getProtocolType() { return protocolType; }
    public void setProtocolType(MessageProtocolType protocolType) { this.protocolType = protocolType; }
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
    public Integer getTokenCount() { return tokenCount; }
    public void setTokenCount(Integer tokenCount) { this.tokenCount = tokenCount; }
    public String getInReplyToId() { return inReplyToId; }
    public void setInReplyToId(String inReplyToId) { this.inReplyToId = inReplyToId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
