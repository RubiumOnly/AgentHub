package com.agenthub.conversation.application;

import com.agenthub.conversation.dto.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

public interface ConversationApplication {
    ConversationView createConversation(CreateConversationCommand cmd);
    List<ConversationView> listConversations();
    ConversationView getConversationById(String id);
    List<MessageView> listMessages(String conversationId);
    List<MessageView> listMessages(String conversationId, Long sinceSeq, String viewerId);
    MessageView sendMessage(String conversationId, SendMessageCommand cmd);
    SseEmitter registerStream(String conversationId);
    SseEmitter registerStream(String conversationId, String lastEventId, String viewerId);
    void broadcastEvent(String conversationId, String eventName, Object data);

    // Phase 6 Context Window, Summary & Multi-Agent loop coordination
    ContextWindowView getContextWindow(String conversationId, int windowSize, int maxTokens);
    ConversationSummaryView generateRollingSummary(String conversationId);
    void triggerTeamTurn(String conversationId);
}
