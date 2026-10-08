package com.agenthub.infrastructure.repository.entity;

import com.agenthub.conversation.domain.model.MessageProtocolType;
import com.agenthub.conversation.domain.model.MessageType;
import com.agenthub.domain.conversation.model.SenderType;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "messages")
public class MessageEntity {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "conversation_id", nullable = false, length = 64)
    private String conversationId;

    @Column(name = "sender_id", nullable = false, length = 64)
    private String senderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "sender_type", nullable = false, length = 32)
    private SenderType senderType;

    @Column(name = "recipient_id", length = 64)
    private String recipientId;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 32)
    private MessageType messageType = MessageType.BROADCAST;

    @Enumerated(EnumType.STRING)
    @Column(name = "protocol_type", nullable = false, length = 32)
    private MessageProtocolType protocolType = MessageProtocolType.NORMAL;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "card_payload_json", columnDefinition = "TEXT")
    private String cardPayloadJson;

    @Column(length = 256)
    private String mentions;

    @Column(name = "schema_version", nullable = false, length = 16)
    private String schemaVersion;

    @Column(name = "sequence_num", nullable = false)
    private Long sequenceNum;

    @Column(name = "token_count", nullable = false)
    private Integer tokenCount = 0;

    @Column(name = "in_reply_to_id", length = 64)
    private String inReplyToId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public MessageEntity() {}

    public MessageEntity(String id, String conversationId, String senderId, SenderType senderType, String content) {
        this.id = id;
        this.conversationId = conversationId;
        this.senderId = senderId;
        this.senderType = senderType;
        this.content = content;
        this.schemaVersion = "v1";
        this.sequenceNum = 1L;
        this.messageType = MessageType.BROADCAST;
        this.protocolType = MessageProtocolType.NORMAL;
        this.tokenCount = 0;
        this.createdAt = LocalDateTime.now();
    }

    public MessageEntity(String id, String conversationId, String senderId, SenderType senderType, String content, String schemaVersion, Long sequenceNum) {
        this.id = id;
        this.conversationId = conversationId;
        this.senderId = senderId;
        this.senderType = senderType;
        this.content = content;
        this.schemaVersion = schemaVersion != null ? schemaVersion : "v1";
        this.sequenceNum = sequenceNum != null ? sequenceNum : 1L;
        this.messageType = MessageType.BROADCAST;
        this.protocolType = MessageProtocolType.NORMAL;
        this.tokenCount = 0;
        this.createdAt = LocalDateTime.now();
    }

    public MessageEntity(String id, String conversationId, String senderId, SenderType senderType, String recipientId,
                         MessageType messageType, MessageProtocolType protocolType, String content,
                         String schemaVersion, Long sequenceNum, Integer tokenCount, String inReplyToId) {
        this.id = id;
        this.conversationId = conversationId;
        this.senderId = senderId;
        this.senderType = senderType;
        this.recipientId = recipientId;
        this.messageType = messageType != null ? messageType : MessageType.BROADCAST;
        this.protocolType = protocolType != null ? protocolType : MessageProtocolType.NORMAL;
        this.content = content;
        this.schemaVersion = schemaVersion != null ? schemaVersion : "v1";
        this.sequenceNum = sequenceNum != null ? sequenceNum : 1L;
        this.tokenCount = tokenCount != null ? tokenCount : 0;
        this.inReplyToId = inReplyToId;
        this.createdAt = LocalDateTime.now();
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
    public MessageType getMessageType() { return messageType != null ? messageType : MessageType.BROADCAST; }
    public void setMessageType(MessageType messageType) { this.messageType = messageType; }
    public MessageProtocolType getProtocolType() { return protocolType != null ? protocolType : MessageProtocolType.NORMAL; }
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
    public Integer getTokenCount() { return tokenCount != null ? tokenCount : 0; }
    public void setTokenCount(Integer tokenCount) { this.tokenCount = tokenCount; }
    public String getInReplyToId() { return inReplyToId; }
    public void setInReplyToId(String inReplyToId) { this.inReplyToId = inReplyToId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
