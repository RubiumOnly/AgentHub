package com.agenthub.application.service;

import com.agenthub.adapter.common.BusinessException;
import com.agenthub.adapter.common.ErrorCode;
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
import com.agenthub.infrastructure.repository.ConversationRepository;
import com.agenthub.infrastructure.repository.MessageRepository;
import com.agenthub.infrastructure.repository.entity.ConversationEntity;
import com.agenthub.infrastructure.repository.entity.MessageEntity;
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

@Service
public class IMCollaborationService {

    private static final Logger log = LoggerFactory.getLogger(IMCollaborationService.class);

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final MentionParser mentionParser;
    private final OrchestratorTaskDecomposer orchestratorTaskDecomposer;
    private final AgentAdapterFactory agentAdapterFactory;
    private final ObjectMapper objectMapper;

    @Value("${agenthub.workspace.base-dir:./data/workspaces}")
    private String workspaceBaseDir;

    // Concurrent map of conversation ID -> list of active SseEmitters
    private final Map<String, List<SseEmitter>> sseEmitterMap = new ConcurrentHashMap<>();

    public IMCollaborationService(ConversationRepository conversationRepository,
                                  MessageRepository messageRepository,
                                  MentionParser mentionParser,
                                  OrchestratorTaskDecomposer orchestratorTaskDecomposer,
                                  AgentAdapterFactory agentAdapterFactory,
                                  ObjectMapper objectMapper) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.mentionParser = mentionParser;
        this.orchestratorTaskDecomposer = orchestratorTaskDecomposer;
        this.agentAdapterFactory = agentAdapterFactory;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ConversationEntity createConversation(String title, ConversationType type, List<String> agentIds) {
        String id = "conv-" + UUID.randomUUID().toString().substring(0, 8);
        String agentIdsStr = agentIds != null ? String.join(",", agentIds) : "";
        ConversationEntity entity = new ConversationEntity(id, title, type, agentIdsStr);
        return conversationRepository.save(entity);
    }

    public List<ConversationEntity> listConversations() {
        return conversationRepository.findAllByOrderByUpdatedAtDesc();
    }

    public List<MessageEntity> getMessages(String conversationId) {
        return messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
    }

    public SseEmitter registerStream(String conversationId) {
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

    @Transactional
    public MessageEntity sendMessage(String conversationId, String senderId, SenderType senderType, String content) {
        ConversationEntity conversation = conversationRepository.findById(conversationId)
                .orElseGet(() -> {
                    // Self-healing fallback if client sends with default-conv or stale id
                    log.warn("Conversation {} not found, auto-creating default fallback conversation.", conversationId);
                    ConversationEntity fallback = new ConversationEntity(
                            conversationId,
                            "🔥 全栈特性突击小队",
                            ConversationType.GROUP_COLLABORATION,
                            "BackendArchitect,FrontendEngineer,QAAuditor"
                    );
                    return conversationRepository.save(fallback);
                });

        String msgId = "msg-" + UUID.randomUUID().toString().substring(0, 8);
        MessageEntity messageEntity = new MessageEntity(msgId, conversationId, senderId, senderType, content);

        List<String> mentions = mentionParser.extractMentions(content);
        if (!mentions.isEmpty()) {
            messageEntity.setMentions(String.join(",", mentions));
        }

        conversation.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conversation);
        MessageEntity saved = messageRepository.save(messageEntity);

        broadcastEvent(conversationId, "message", saved);

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
                MessageEntity cardMsg = new MessageEntity(
                        "msg-" + UUID.randomUUID().toString().substring(0, 8),
                        convId,
                        "Orchestrator",
                        SenderType.ORCHESTRATOR,
                        "已完成多 Agent 协作任务拆解与编排派发。"
                );
                cardMsg.setCardPayloadJson(cardJson);
                messageRepository.save(cardMsg);
                broadcastEvent(convId, "message", cardMsg);

                // Invoke BackendArchitect with DeepSeek
                UnifiedAgentAdapter apiAdapter = agentAdapterFactory.getAdapter(AgentPlatformType.SPRING_AI_API);
                AgentExecutionRequest backendReq = new AgentExecutionRequest(
                        "BackendArchitect",
                        AgentPlatformType.SPRING_AI_API,
                        workspaceDir,
                        "你作为资深后端架构师，请针对业务需求「" + content + "」编写完整的 Spring Boot 3 控制器或服务层核心代码。"
                );
                AgentExecutionResult backendRes = apiAdapter.execute(backendReq);
                MessageEntity backendMsg = new MessageEntity(
                        "msg-" + UUID.randomUUID().toString().substring(0, 8),
                        convId,
                        "BackendArchitect",
                        SenderType.AGENT,
                        "【阶段一完成】后端领域服务代码已编写并写入工作区：\n" + backendRes.getOutput()
                );
                messageRepository.save(backendMsg);
                broadcastEvent(convId, "message", backendMsg);

                // Invoke FrontendEngineer with DeepSeek
                AgentExecutionRequest frontendReq = new AgentExecutionRequest(
                        "FrontendEngineer",
                        AgentPlatformType.SPRING_AI_API,
                        workspaceDir,
                        "你作为资深前端工程师，请针对业务需求「" + content + "」编写完整的 Next.js 14 / React 响应式界面组件代码。"
                );
                AgentExecutionResult frontendRes = apiAdapter.execute(frontendReq);
                MessageEntity frontendMsg = new MessageEntity(
                        "msg-" + UUID.randomUUID().toString().substring(0, 8),
                        convId,
                        "FrontendEngineer",
                        SenderType.AGENT,
                        "【阶段二完成】前端交互界面代码已编写并写入工作区：\n" + frontendRes.getOutput()
                );
                messageRepository.save(frontendMsg);
                broadcastEvent(convId, "message", frontendMsg);

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
                        MessageEntity agentMsg = new MessageEntity(
                                "msg-" + UUID.randomUUID().toString().substring(0, 8),
                                convId,
                                mention,
                                SenderType.AGENT,
                                result.getOutput()
                        );
                        messageRepository.save(agentMsg);
                        broadcastEvent(convId, "message", agentMsg);
                    }
            );
        }
    }
}
