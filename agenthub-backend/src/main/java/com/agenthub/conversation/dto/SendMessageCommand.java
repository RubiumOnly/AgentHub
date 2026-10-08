package com.agenthub.conversation.dto;

import com.agenthub.domain.conversation.model.SenderType;

public class SendMessageCommand {
    private String senderId;
    private SenderType senderType;
    private String content;

    public SendMessageCommand() {}

    public SendMessageCommand(String senderId, SenderType senderType, String content) {
        this.senderId = senderId;
        this.senderType = senderType;
        this.content = content;
    }

    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }
    public SenderType getSenderType() { return senderType; }
    public void setSenderType(SenderType senderType) { this.senderType = senderType; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}
