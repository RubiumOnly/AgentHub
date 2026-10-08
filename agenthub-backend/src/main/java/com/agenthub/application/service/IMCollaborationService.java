package com.agenthub.application.service;

import com.agenthub.conversation.application.ConversationApplication;
import com.agenthub.conversation.dto.CreateConversationCommand;
import com.agenthub.conversation.dto.ConversationView;
import com.agenthub.conversation.dto.MessageView;
import com.agenthub.conversation.dto.SendMessageCommand;
import com.agenthub.conversation.infrastructure.entity.ConversationParticipantEntity;
import com.agenthub.conversation.infrastructure.repository.ConversationParticipantRepository;
import com.agenthub.domain.agent.model.AgentExecutionRequest;
import com.agenthub.domain.agent.model.AgentExecutionResult;
import com.agenthub.domain.agent.model.AgentPlatformType;
import com.agenthub.domain.agent.service.AgentAdapterFactory;
import com.agenthub.domain.agent.spi.UnifiedAgentAdapter;
import com.agenthub.domain.conversation.model.ConversationType;
import com.agenthub.domain.conversation.model.InteractiveCard;
import com.agenthub.domain.conversation.model.SenderType;
import com.agenthub.domain.conversation.service.MentionParser;
import com.agenthub.domain.conversation.service.OrchestratorTaskDecomposer;
import com.agenthub.identity.infrastructure.security.ResourceAccessGuard;
import com.agenthub.infrastructure.repository.ConversationRepository;
import com.agenthub.infrastructure.repository.MessageRepository;
import com.agenthub.infrastructure.repository.entity.ConversationEntity;
import com.agenthub.infrastructure.repository.entity.MessageEntity;
import com.agenthub.shared.context.RequestContext;
import com.agenthub.shared.exception.BusinessException;
import com.agenthub.shared.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

@Service
public class IMCollaborationService implements ConversationApplication {

    private static final Logger log = LoggerFactory.getLogger(IMCollaborationService.class);

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final ConversationParticipantRepository participantRepository;
    private final MentionParser mentionParser;
    private final OrchestratorTaskDecomposer orchestratorTaskDecomposer;
    private final AgentAdapterFactory agentAdapterFactory;
    private final ObjectMapper objectMapper;
    private final ResourceAccessGuard accessGuard;

    @Value("${agenthub.workspace.base-dir:./data/workspaces}")
    private String workspaceBaseDir;

    // Concurrent map of conversation ID -> list of active SseEmitters
    private final Map<String, List<SseEmitter>> sseEmitterMap = new ConcurrentHashMap<>();

    public IMCollaborationService(ConversationRepository conversationRepository,
                                  MessageRepository messageRepository,
                                  ConversationParticipantRepository participantRepository,
                                  MentionParser mentionParser,
                                  OrchestratorTaskDecomposer orchestratorTaskDecomposer,
                                  AgentAdapterFactory agentAdapterFactory,
                                  ObjectMapper objectMapper,
                                  ResourceAccessGuard accessGuard) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.participantRepository = participantRepository;
        this.mentionParser = mentionParser;
        this.orchestratorTaskDecomposer = orchestratorTaskDecomposer;
        this.agentAdapterFactory = agentAdapterFactory;
        this.objectMapper = objectMapper;
        this.accessGuard = accessGuard;
    }

    @Override
    @Transactional
    public ConversationView createConversation(CreateConversationCommand cmd) {
        String currentUserId = RequestContext.get().getUserId();
        if (currentUserId == null || currentUserId.isBlank()) {
            currentUserId = "user-1";
        }
        String id = "conv-" + UUID.randomUUID().toString().substring(0, 8);
        String agentIdsStr = cmd.getAgentIds() != null ? String.join(",", cmd.getAgentIds()) : "";
        ConversationType type = cmd.getType() != null ? cmd.getType() : ConversationType.DIRECT_CHAT;

        ConversationEntity entity = new ConversationEntity(
                id,
                currentUserId,
                cmd.getProjectId() != null ? cmd.getProjectId() : "proj-default",
                cmd.getTitle(),
                type,
                agentIdsStr
        );
        conversationRepository.save(entity);

        // Persist relations into conversation_participants table
        if (cmd.getAgentIds() != null) {
            for (String agentId : cmd.getAgentIds()) {
                String participantId = "part-" + UUID.randomUUID().toString().substring(0, 8);
                participantRepository.save(new ConversationParticipantEntity(participantId, id, agentId));
            }
        }

        return toConversationView(entity, cmd.getAgentIds());
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
        String currentUserId = RequestContext.get().getUserId();
        if (currentUserId == null || currentUserId.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Authentication required to list conversations");
        }
        if ("user-1".equals(currentUserId) || "system".equals(currentUserId)) {
            return conversationRepository.findAllByOrderByUpdatedAtDesc().stream()
                    .map(this::populateAndConvertToView)
                    .collect(Collectors.toList());
        }
        return conversationRepository.findByOwnerIdOrderByUpdatedAtDesc(currentUserId).stream()
                .map(this::populateAndConvertToView)
                .collect(Collectors.toList());
    }

    // Overload for legacy list return
    @Transactional(readOnly = true)
    public List<ConversationEntity> listConversationEntities() {
        return conversationRepository.findAllByOrderByUpdatedAtDesc();
    }

    @Override
    @Transactional(readOnly = true)
    public ConversationView getConversationById(String id) {
        ConversationEntity conv = conversationRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND, "Conversation not found: " + id));
        accessGuard.checkOwnership(conv.getOwnerId(), RequestContext.get().getUserId());
        return populateAndConvertToView(conv);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MessageView> listMessages(String conversationId) {
        ConversationEntity conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND, "Conversation not found: " + conversationId));
        accessGuard.checkOwnership(conv.getOwnerId(), RequestContext.get().getUserId());
        return messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId).stream()
                .map(this::toMessageView)
                .collect(Collectors.toList());
    }

    // Overload for legacy entity return & test compatibility
    @Transactional(readOnly = true)
    public List<MessageEntity> getMessages(String conversationId) {
        return messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
    }

    @Override
    public SseEmitter registerStream(String conversationId) {
        ConversationEntity conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND, "Conversation not found: " + conversationId));
        accessGuard.checkOwnership(conv.getOwnerId(), RequestContext.get().getUserId());

        SseEmitter emitter = new SseEmitter(180_000L); // 3 minutes timeout
        sseEmitterMap.computeIfAbsent(conversationId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> removeEmitter(conversationId, emitter));
        emitter.onTimeout(() -> removeEmitter(conversationId, emitter));
        emitter.onError((e) -> removeEmitter(conversationId, emitter));

        try {
            emitter.send(SseEmitter.event().name("connected").data(Map.of("conversationId", conversationId, "status", "ready")));
        } catch (IOException ignored) {}

        return emitter;
    }

    private void removeEmitter(String conversationId, SseEmitter emitter) {
        List<SseEmitter> list = sseEmitterMap.get(conversationId);
        if (list != null) {
            list.remove(emitter);
        }
    }

    @Override
    public void broadcastEvent(String conversationId, String eventName, Object data) {
        List<SseEmitter> emitters = sseEmitterMap.get(conversationId);
        if (emitters == null || emitters.isEmpty()) return;

        List<SseEmitter> deadEmitters = new ArrayList<>();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(data));
            } catch (Exception e) {
                deadEmitters.add(emitter);
            }
        }
        emitters.removeAll(deadEmitters);
    }

    @Override
    @Transactional
    public MessageView sendMessage(String conversationId, SendMessageCommand cmd) {
        ConversationEntity conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND, "Conversation not found: " + conversationId));
        accessGuard.checkOwnership(conv.getOwnerId(), RequestContext.get().getUserId());

        String senderId = cmd.getSenderId() != null && !cmd.getSenderId().isBlank()
                ? cmd.getSenderId()
                : (RequestContext.get().getUserId() != null ? RequestContext.get().getUserId() : "user-1");

        MessageEntity saved = internalSendMessage(
                conversationId,
                senderId,
                cmd.getSenderType() != null ? cmd.getSenderType() : SenderType.USER,
                cmd.getContent()
        );
        return toMessageView(saved);
    }

    // Overload for legacy signature & test compatibility
    @Transactional
    public MessageEntity sendMessage(String conversationId, String senderId, SenderType senderType, String content) {
        return internalSendMessage(conversationId, senderId, senderType, content);
    }

    private MessageEntity internalSendMessage(String conversationId, String senderId, SenderType senderType, String content) {
        ConversationEntity conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND, "Conversation not found: " + conversationId));

        long currentSeq = messageRepository.countByConversationId(conversationId) + 1;
        String msgId = "msg-" + UUID.randomUUID().toString().substring(0, 8);
        MessageEntity messageEntity = new MessageEntity(msgId, conversationId, senderId, senderType, content, "v1", currentSeq);

        List<String> mentions = mentionParser.extractMentions(content);
        if (!mentions.isEmpty()) {
            messageEntity.setMentions(String.join(",", mentions));
        }

        conversation.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conversation);
        MessageEntity saved = messageRepository.save(messageEntity);

        broadcastEvent(conversationId, "message", toMessageView(saved));

        // Process Agent Actions asynchronously
        CompletableFuture.runAsync(() -> handleAgentMentions(conversation, content, mentions));

        return saved;
    }

    private void handleAgentMentions(ConversationEntity conversation, String content, List<String> mentions) {
        String convId = conversation.getId();
        String workspaceDir = workspaceBaseDir + "/default";

        // 1. If Orchestrator mentioned or general prompt in group collaboration, decompose task and trigger real multi-agent generation
        boolean isOrchestratorTargeted = mentionParser.hasOrchestratorMention(content) ||
                (conversation.getType() == ConversationType.GROUP_COLLABORATION && mentions.isEmpty());
        if (isOrchestratorTargeted) {
            InteractiveCard card = orchestratorTaskDecomposer.decomposeTask(content);
            try {
                String cardJson = objectMapper.writeValueAsString(card);
                long cardSeq = messageRepository.countByConversationId(convId) + 1;
                MessageEntity cardMsg = new MessageEntity(
                        "msg-" + UUID.randomUUID().toString().substring(0, 8),
                        convId,
                        "Orchestrator",
                        SenderType.ORCHESTRATOR,
                        "已完成多 Agent 协作任务拆解与编排派发。",
                        "v1",
                        cardSeq
                );
                cardMsg.setCardPayloadJson(cardJson);
                messageRepository.save(cardMsg);
                broadcastEvent(convId, "message", toMessageView(cardMsg));

                // Invoke BackendArchitect with DeepSeek
                UnifiedAgentAdapter apiAdapter = agentAdapterFactory.getAdapter(AgentPlatformType.SPRING_AI_API);
                AgentExecutionRequest backendReq = new AgentExecutionRequest(
                        "BackendArchitect",
                        AgentPlatformType.SPRING_AI_API,
                        workspaceDir,
                        "你作为资深后端架构师，请针对业务需求「" + content + "」编写完整的 Spring Boot 3 控制器或服务层核心代码。"
                );
                AgentExecutionResult backendRes = apiAdapter.execute(backendReq);
                long backendSeq = messageRepository.countByConversationId(convId) + 1;
                MessageEntity backendMsg = new MessageEntity(
                        "msg-" + UUID.randomUUID().toString().substring(0, 8),
                        convId,
                        "BackendArchitect",
                        SenderType.AGENT,
                        "【阶段一完成】后端领域服务代码已编写并写入工作区：\n" + backendRes.getOutput(),
                        "v1",
                        backendSeq
                );
                messageRepository.save(backendMsg);
                broadcastEvent(convId, "message", toMessageView(backendMsg));

                // Invoke FrontendEngineer with DeepSeek
                AgentExecutionRequest frontendReq = new AgentExecutionRequest(
                        "FrontendEngineer",
                        AgentPlatformType.SPRING_AI_API,
                        workspaceDir,
                        "你作为资深前端工程师，请针对业务需求「" + content + "」编写完整的 Next.js 14 / React 响应式界面组件代码。"
                );
                AgentExecutionResult frontendRes = apiAdapter.execute(frontendReq);
                long frontendSeq = messageRepository.countByConversationId(convId) + 1;
                MessageEntity frontendMsg = new MessageEntity(
                        "msg-" + UUID.randomUUID().toString().substring(0, 8),
                        convId,
                        "FrontendEngineer",
                        SenderType.AGENT,
                        "【阶段二完成】前端交互界面代码已编写并写入工作区：\n" + frontendRes.getOutput(),
                        "v1",
                        frontendSeq
                );
                messageRepository.save(frontendMsg);
                broadcastEvent(convId, "message", toMessageView(frontendMsg));

            } catch (Exception e) {
                log.error("Failed to execute Orchestrator workflow: ", e);
            }
        }

        // 2. If specific other agent mentioned, invoke execution
        for (String mention : mentions) {
            if ("orchestrator".equalsIgnoreCase(mention)) continue;

            AgentPlatformType platformType = AgentPlatformType.fromCode(mention);
            UnifiedAgentAdapter adapter = agentAdapterFactory.getAdapter(platformType);

            AgentExecutionRequest request = new AgentExecutionRequest(
                    mention,
                    platformType,
                    workspaceDir,
                    content
            );

            adapter.executeStream(
                    request,
                    chunk -> broadcastEvent(convId, "message_delta", Map.of("sender", mention, "delta", chunk)),
                    result -> {
                        long seq = messageRepository.countByConversationId(convId) + 1;
                        MessageEntity agentMsg = new MessageEntity(
                                "msg-" + UUID.randomUUID().toString().substring(0, 8),
                                convId,
                                mention,
                                SenderType.AGENT,
                                result.getOutput(),
                                "v1",
                                seq
                        );
                        messageRepository.save(agentMsg);
                        broadcastEvent(convId, "message", toMessageView(agentMsg));
                    }
            );
        }
    }

    private ConversationView populateAndConvertToView(ConversationEntity entity) {
        List<String> agentIds = participantRepository.findByConversationId(entity.getId()).stream()
                .map(ConversationParticipantEntity::getAgentId)
                .collect(Collectors.toList());
        return toConversationView(entity, agentIds);
    }

    private ConversationView toConversationView(ConversationEntity entity, List<String> agentIds) {
        return new ConversationView(
                entity.getId(),
                entity.getOwnerId(),
                entity.getProjectId(),
                entity.getTitle(),
                entity.getType(),
                agentIds != null ? agentIds : Collections.emptyList(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private MessageView toMessageView(MessageEntity entity) {
        return new MessageView(
                entity.getId(),
                entity.getConversationId(),
                entity.getSenderId(),
                entity.getSenderType(),
                entity.getContent(),
                entity.getCardPayloadJson(),
                entity.getMentions(),
                entity.getSchemaVersion(),
                entity.getSequenceNum(),
                entity.getCreatedAt()
        );
    }
}
