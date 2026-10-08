package com.agenthub.conversation.dto;

import com.agenthub.conversation.domain.model.MessageProtocolType;
import com.agenthub.conversation.domain.model.MessageType;
import com.agenthub.domain.conversation.model.SenderType;

public class SendMessageCommand {
    private String senderId;
    private SenderType senderType;
    private String recipientId;
    private MessageType messageType;
    private MessageProtocolType protocolType;
    private String content;
    private String inReplyToId;

    public SendMessageCommand() {}

    public SendMessageCommand(String senderId, SenderType senderType, String content) {
        this.senderId = senderId;
        this.senderType = senderType;
        this.content = content;
        this.messageType = MessageType.BROADCAST;
        this.protocolType = MessageProtocolType.NORMAL;
    }

    public SendMessageCommand(String senderId, SenderType senderType, String recipientId,
                              MessageType messageType, MessageProtocolType protocolType,
                              String content, String inReplyToId) {
        this.senderId = senderId;
        this.senderType = senderType;
        this.recipientId = recipientId;
        this.messageType = messageType != null ? messageType : MessageType.BROADCAST;
        this.protocolType = protocolType != null ? protocolType : MessageProtocolType.NORMAL;
        this.content = content;
        this.inReplyToId = inReplyToId;
    }

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
    public String getInReplyToId() { return inReplyToId; }
    public void setInReplyToId(String inReplyToId) { this.inReplyToId = inReplyToId; }
}
