package com.agenthub.conversation.application;

import com.agenthub.conversation.dto.CreateConversationCommand;
import com.agenthub.conversation.dto.ConversationView;
import com.agenthub.conversation.dto.MessageView;
import com.agenthub.conversation.dto.SendMessageCommand;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

public interface ConversationApplication {
    ConversationView createConversation(CreateConversationCommand cmd);
    List<ConversationView> listConversations();
    ConversationView getConversationById(String id);
    List<MessageView> listMessages(String conversationId);
    MessageView sendMessage(String conversationId, SendMessageCommand cmd);
    SseEmitter registerStream(String conversationId);
    void broadcastEvent(String conversationId, String eventName, Object data);
}
