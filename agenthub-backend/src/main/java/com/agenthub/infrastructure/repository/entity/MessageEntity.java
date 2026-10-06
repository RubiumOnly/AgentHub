package com.agenthub.infrastructure.repository.entity;

import com.agenthub.domain.conversation.model.SenderType;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "agenthub_messages")
public class MessageEntity {

    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false, length = 64)
    private String conversationId;

    @Column(nullable = false, length = 64)
    private String senderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SenderType senderType;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String content;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String cardPayloadJson;

    @Column(length = 256)
    private String mentions;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public MessageEntity() {}

    public MessageEntity(String id, String conversationId, String senderId, SenderType senderType, String content) {
        this.id = id;
        this.conversationId = conversationId;
        this.senderId = senderId;
        this.senderType = senderType;
        this.content = content;
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
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getCardPayloadJson() { return cardPayloadJson; }
    public void setCardPayloadJson(String cardPayloadJson) { this.cardPayloadJson = cardPayloadJson; }
    public String getMentions() { return mentions; }
    public void setMentions(String mentions) { this.mentions = mentions; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
