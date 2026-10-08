package com.agenthub.application.service;

import com.agenthub.conversation.application.ConversationApplication;
import com.agenthub.conversation.application.ConversationApplicationService;
import com.agenthub.conversation.dto.*;
import com.agenthub.domain.conversation.model.ConversationType;
import com.agenthub.domain.conversation.model.SenderType;
import com.agenthub.infrastructure.repository.ConversationRepository;
import com.agenthub.infrastructure.repository.MessageRepository;
import com.agenthub.infrastructure.repository.entity.ConversationEntity;
import com.agenthub.infrastructure.repository.entity.MessageEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * Legacy IM Collaboration Service maintained for backward compatibility.
 * Delegates core operations to ConversationApplicationService.
 */
@Service
public class IMCollaborationService implements ConversationApplication {

    private final ConversationApplicationService conversationApplicationService;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;

    public IMCollaborationService(ConversationApplicationService conversationApplicationService,
                                  ConversationRepository conversationRepository,
                                  MessageRepository messageRepository) {
        this.conversationApplicationService = conversationApplicationService;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
    }

    @Override
    @Transactional
    public ConversationView createConversation(CreateConversationCommand cmd) {
        return conversationApplicationService.createConversation(cmd);
    }

    // Overload for legacy signature & test compatibility
    @Transactional
    public ConversationEntity createConversation(String title, ConversationType type, List<String> agentIds) {
        ConversationView view = createConversation(new CreateConversationCommand(title, type, agentIds, "proj-default"));
        ConversationEntity entity = conversationRepository.findById(view.getId()).orElseThrow();
        entity.setParticipantAgentIds(agentIds != null ? String.join(",", agentIds) : "");
        return entity;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConversationView> listConversations() {
        return conversationApplicationService.listConversations();
    }

    // Overload for legacy list return
    @Transactional(readOnly = true)
    public List<ConversationEntity> listConversationEntities() {
        return conversationRepository.findAllByOrderByUpdatedAtDesc();
    }

    @Override
    @Transactional(readOnly = true)
    public ConversationView getConversationById(String id) {
        return conversationApplicationService.getConversationById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MessageView> listMessages(String conversationId) {
        return conversationApplicationService.listMessages(conversationId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MessageView> listMessages(String conversationId, Long sinceSeq, String viewerId) {
        return conversationApplicationService.listMessages(conversationId, sinceSeq, viewerId);
    }

    // Overload for legacy entity return & test compatibility
    @Transactional(readOnly = true)
    public List<MessageEntity> getMessages(String conversationId) {
        return messageRepository.findByConversationIdOrderBySequenceNumAsc(conversationId);
    }

    @Override
    public SseEmitter registerStream(String conversationId) {
        return conversationApplicationService.registerStream(conversationId);
    }

    @Override
    public SseEmitter registerStream(String conversationId, String lastEventId, String viewerId) {
        return conversationApplicationService.registerStream(conversationId, lastEventId, viewerId);
    }

    @Override
    public void broadcastEvent(String conversationId, String eventName, Object data) {
        conversationApplicationService.broadcastEvent(conversationId, eventName, data);
    }

    @Override
    @Transactional
    public MessageView sendMessage(String conversationId, SendMessageCommand cmd) {
        return conversationApplicationService.sendMessage(conversationId, cmd);
    }

    // Overload for legacy signature & test compatibility
    @Transactional
    public MessageEntity sendMessage(String conversationId, String senderId, SenderType senderType, String content) {
        MessageView view = sendMessage(conversationId, new SendMessageCommand(senderId, senderType, content));
        return messageRepository.findById(view.getId()).orElseThrow();
    }

    @Override
    @Transactional(readOnly = true)
    public ContextWindowView getContextWindow(String conversationId, int windowSize, int maxTokens) {
        return conversationApplicationService.getContextWindow(conversationId, windowSize, maxTokens);
    }

    @Override
    @Transactional
    public ConversationSummaryView generateRollingSummary(String conversationId) {
        return conversationApplicationService.generateRollingSummary(conversationId);
    }

    @Override
    @Transactional
    public void triggerTeamTurn(String conversationId) {
        conversationApplicationService.triggerTeamTurn(conversationId);
    }
}
