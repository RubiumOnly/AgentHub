package com.agenthub.conversation.application;

import com.agenthub.conversation.domain.model.ConversationEventType;
import com.agenthub.conversation.domain.model.LoopDetectionResult;
import com.agenthub.conversation.domain.model.MessageProtocolType;
import com.agenthub.conversation.domain.model.MessageType;
import com.agenthub.conversation.domain.service.*;
import com.agenthub.conversation.domain.sse.ConversationEventBroadcaster;
import com.agenthub.conversation.dto.*;
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
import com.agenthub.team.domain.model.NextSpeakerDecision;
import com.agenthub.team.domain.model.TeamCoordinationContext;
import com.agenthub.team.domain.strategy.TeamTopologyStrategy;
import com.agenthub.team.domain.strategy.TeamTopologyStrategyFactory;
import com.agenthub.team.infrastructure.entity.TeamEntity;
import com.agenthub.team.infrastructure.entity.TeamMemberEntity;
import com.agenthub.team.infrastructure.repository.TeamMemberRepository;
import com.agenthub.team.infrastructure.repository.TeamRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Primary
@Service
public class ConversationApplicationService implements ConversationApplication {

    private static final Logger log = LoggerFactory.getLogger(ConversationApplicationService.class);

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final ConversationParticipantRepository participantRepository;
    private final ConversationSequenceManager sequenceManager;
    private final MessageVisibilityFilter visibilityFilter;
    private final LoopDetector loopDetector;
    private final SlidingWindowContextTrimmer contextTrimmer;
    private final TokenBudgetContextManager tokenBudgetManager;
    private final RollingSummaryService rollingSummaryService;
    private final ConversationEventBroadcaster eventBroadcaster;
    private final MentionParser mentionParser;
    private final OrchestratorTaskDecomposer orchestratorTaskDecomposer;
    private final AgentAdapterFactory agentAdapterFactory;
    private final ObjectMapper objectMapper;
    private final ResourceAccessGuard accessGuard;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TeamTopologyStrategyFactory strategyFactory;

    @Value("${agenthub.workspace.base-dir:./data/workspaces}")
    private String workspaceBaseDir;

    public ConversationApplicationService(ConversationRepository conversationRepository,
                                          MessageRepository messageRepository,
                                          ConversationParticipantRepository participantRepository,
                                          ConversationSequenceManager sequenceManager,
                                          MessageVisibilityFilter visibilityFilter,
                                          LoopDetector loopDetector,
                                          SlidingWindowContextTrimmer contextTrimmer,
                                          TokenBudgetContextManager tokenBudgetManager,
                                          RollingSummaryService rollingSummaryService,
                                          ConversationEventBroadcaster eventBroadcaster,
                                          MentionParser mentionParser,
                                          OrchestratorTaskDecomposer orchestratorTaskDecomposer,
                                          AgentAdapterFactory agentAdapterFactory,
                                          ObjectMapper objectMapper,
                                          ResourceAccessGuard accessGuard,
                                          TeamRepository teamRepository,
                                          TeamMemberRepository teamMemberRepository,
                                          TeamTopologyStrategyFactory strategyFactory) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.participantRepository = participantRepository;
        this.sequenceManager = sequenceManager;
        this.visibilityFilter = visibilityFilter;
        this.loopDetector = loopDetector;
        this.contextTrimmer = contextTrimmer;
        this.tokenBudgetManager = tokenBudgetManager;
        this.rollingSummaryService = rollingSummaryService;
        this.eventBroadcaster = eventBroadcaster;
        this.mentionParser = mentionParser;
        this.orchestratorTaskDecomposer = orchestratorTaskDecomposer;
        this.agentAdapterFactory = agentAdapterFactory;
        this.objectMapper = objectMapper;
        this.accessGuard = accessGuard;
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.strategyFactory = strategyFactory;
    }

    @Override
    @Transactional
    public ConversationView createConversation(CreateConversationCommand cmd) {
        String currentUserId = RequestContext.get().getUserId();
        if (currentUserId == null || currentUserId.isBlank()) {
            currentUserId = "user-1";
        }
        String id = "conv-" + UUID.randomUUID().toString().substring(0, 8);
        ConversationType type = cmd.getType() != null ? cmd.getType() : ConversationType.DIRECT_CHAT;

        List<String> agentIds = cmd.getAgentIds() != null ? new ArrayList<>(cmd.getAgentIds()) : new ArrayList<>();
        if (cmd.getTeamId() != null && !cmd.getTeamId().isBlank()) {
            TeamEntity team = teamRepository.findById(cmd.getTeamId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.TEAM_NOT_FOUND, "Team not found: " + cmd.getTeamId()));
            if (agentIds.isEmpty()) {
                List<TeamMemberEntity> members = teamMemberRepository.findByTeamIdOrderBySortOrderAsc(team.getId());
                agentIds = members.stream().map(TeamMemberEntity::getAgentInstanceId).collect(Collectors.toList());
            }
        }

        String agentIdsStr = String.join(",", agentIds);

        ConversationEntity entity = new ConversationEntity(
                id,
                currentUserId,
                cmd.getProjectId() != null ? cmd.getProjectId() : "proj-default",
                cmd.getTeamId(),
                cmd.getTitle(),
                type,
                agentIdsStr
        );
        conversationRepository.save(entity);

        for (String agentId : agentIds) {
            String participantId = "part-" + UUID.randomUUID().toString().substring(0, 8);
            participantRepository.save(new ConversationParticipantEntity(participantId, id, agentId));
        }

        return toConversationView(entity, agentIds);
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
        return listMessages(conversationId, null, null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MessageView> listMessages(String conversationId, Long sinceSeq, String viewerId) {
        ConversationEntity conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND, "Conversation not found: " + conversationId));
        String currentUserId = RequestContext.get().getUserId();
        accessGuard.checkOwnership(conv.getOwnerId(), currentUserId);

        List<MessageEntity> entities;
        if (sinceSeq != null && sinceSeq > 0) {
            entities = messageRepository.findByConversationIdAndSequenceNumGreaterThanOrderBySequenceNumAsc(conversationId, sinceSeq);
        } else {
            entities = messageRepository.findByConversationIdOrderBySequenceNumAsc(conversationId);
        }

        boolean isPrivileged = "admin".equalsIgnoreCase(currentUserId) || "system".equalsIgnoreCase(currentUserId);
        String effectiveViewerId = (viewerId != null && !viewerId.isBlank()) ? viewerId : currentUserId;

        List<MessageView> views = entities.stream().map(this::toMessageView).collect(Collectors.toList());
        return visibilityFilter.filterVisible(views, effectiveViewerId, isPrivileged);
    }

    @Override
    public MessageView sendMessage(String conversationId, SendMessageCommand cmd) {
        ConversationEntity conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND, "Conversation not found: " + conversationId));
        String currentUserId = RequestContext.get().getUserId();
        if (currentUserId != null && !currentUserId.isBlank()) {
            accessGuard.checkOwnership(conversation.getOwnerId(), currentUserId);
        }

        if ("TERMINATED".equalsIgnoreCase(conversation.getStatus())) {
            throw new BusinessException(ErrorCode.LOOP_DETECTED, "Conversation is terminated due to loop detection or max turns");
        }

        String senderId = cmd.getSenderId() != null && !cmd.getSenderId().isBlank()
                ? cmd.getSenderId()
                : (currentUserId != null ? currentUserId : "user-1");

        SenderType senderType = cmd.getSenderType() != null ? cmd.getSenderType() : SenderType.USER;
        MessageType messageType = cmd.getMessageType() != null
                ? cmd.getMessageType()
                : (cmd.getRecipientId() != null && !cmd.getRecipientId().isBlank() ? MessageType.DIRECT : MessageType.BROADCAST);
        MessageProtocolType protocolType = cmd.getProtocolType() != null ? cmd.getProtocolType() : MessageProtocolType.NORMAL;

        long nextSeq = sequenceManager.nextSequenceNum(conversationId);
        int tokenCount = tokenBudgetManager.estimateTokens(cmd.getContent());

        String msgId = "msg-" + UUID.randomUUID().toString().substring(0, 8);
        MessageEntity messageEntity = new MessageEntity(
                msgId,
                conversationId,
                senderId,
                senderType,
                cmd.getRecipientId(),
                messageType,
                protocolType,
                cmd.getContent(),
                "v1",
                nextSeq,
                tokenCount,
                cmd.getInReplyToId()
        );

        List<String> mentions = mentionParser.extractMentions(cmd.getContent());
        if (!mentions.isEmpty()) {
            messageEntity.setMentions(String.join(",", mentions));
        }

        conversationRepository.recordMessageActivity(conversationId, tokenCount, LocalDateTime.now());
        MessageEntity saved = messageRepository.save(messageEntity);
        MessageView savedView = toMessageView(saved);

        broadcastEvent(conversationId, "message", savedView);

        // Loop detection check
        List<MessageView> recentMessages = messageRepository.findByConversationIdOrderBySequenceNumAsc(conversationId).stream()
                .map(this::toMessageView)
                .collect(Collectors.toList());

        int maxTurns = 10;
        if (conversation.getTeamId() != null && !conversation.getTeamId().isBlank()) {
            maxTurns = teamRepository.findById(conversation.getTeamId())
                    .map(t -> t.getMaxTurns() != null && t.getMaxTurns() > 0 ? t.getMaxTurns() : 10)
                    .orElse(10);
        }

        LoopDetectionResult loopResult = loopDetector.detectLoop(recentMessages, maxTurns);
        if (loopResult.isLoopDetected()) {
            log.warn("Loop detected in conversation [{}]: {} ({})", conversationId, loopResult.getReason(), loopResult.getLoopType());
            conversationRepository.updateStatus(conversationId, "TERMINATED", LocalDateTime.now());

            long sysSeq = sequenceManager.nextSequenceNum(conversationId);
            MessageEntity sysMsg = new MessageEntity(
                    "msg-" + UUID.randomUUID().toString().substring(0, 8),
                    conversationId,
                    "SYSTEM",
                    SenderType.SYSTEM,
                    null,
                    MessageType.SYSTEM,
                    MessageProtocolType.NORMAL,
                    "【消息总线死循环拦截告警】" + loopResult.getReason() + "。协作已被系统安全终止以防 Token 耗尽。",
                    "v1",
                    sysSeq,
                    0,
                    null
            );
            messageRepository.save(sysMsg);
            broadcastEvent(conversationId, "message", toMessageView(sysMsg));
            broadcastEvent(conversationId, "loop_detected", Map.of(
                    "conversationId", conversationId,
                    "loopType", loopResult.getLoopType().name(),
                    "reason", loopResult.getReason()
            ));

            return savedView;
        }

        // Asynchronous agent invocation
        CompletableFuture.runAsync(() -> {
            try {
                if (conversation.getTeamId() != null && !conversation.getTeamId().isBlank()) {
                    triggerTeamTurn(conversationId);
                } else {
                    handleLegacyMentions(conversation, cmd.getContent(), mentions, senderType, messageType);
                }
            } catch (Exception e) {
                log.error("Failed to execute asynchronous turn for conversation {}: ", conversationId, e);
            }
        });

        return savedView;
    }

    @Override
    public SseEmitter registerStream(String conversationId) {
        return registerStream(conversationId, null, null);
    }

    @Override
    public SseEmitter registerStream(String conversationId, String lastEventId, String viewerId) {
        ConversationEntity conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND, "Conversation not found: " + conversationId));
        accessGuard.checkOwnership(conv.getOwnerId(), RequestContext.get().getUserId());

        return eventBroadcaster.registerStream(conversationId, lastEventId, viewerId);
    }

    @Override
    public void broadcastEvent(String conversationId, String eventName, Object data) {
        eventBroadcaster.broadcastEvent(conversationId, eventName, data);
    }

    @Override
    @Transactional(readOnly = true)
    public ContextWindowView getContextWindow(String conversationId, int windowSize, int maxTokens) {
        ConversationEntity conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND, "Conversation not found: " + conversationId));
        accessGuard.checkOwnership(conv.getOwnerId(), RequestContext.get().getUserId());

        List<MessageView> allMessages = messageRepository.findByConversationIdOrderBySequenceNumAsc(conversationId).stream()
                .map(this::toMessageView)
                .collect(Collectors.toList());

        int effectiveWindowSize = windowSize > 0 ? windowSize : 8;
        int effectiveMaxTokens = maxTokens > 0 ? maxTokens : 4000;

        SlidingWindowContextTrimmer.WindowSplit split = contextTrimmer.trimToWindow(allMessages, effectiveWindowSize);
        List<MessageView> fitted = tokenBudgetManager.fitToBudget(split.getActiveMessages(), conv.getSummary(), effectiveMaxTokens);

        int totalTokens = tokenBudgetManager.estimateTokens(conv.getSummary());
        for (MessageView mv : fitted) {
            totalTokens += tokenBudgetManager.estimateMessageTokens(mv);
        }

        int trimmedCount = allMessages.size() - fitted.size();

        return new ContextWindowView(
                conversationId,
                fitted,
                conv.getSummary(),
                totalTokens,
                trimmedCount
        );
    }

    @Override
    public ConversationSummaryView generateRollingSummary(String conversationId) {
        ConversationEntity conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND, "Conversation not found: " + conversationId));
        accessGuard.checkOwnership(conv.getOwnerId(), RequestContext.get().getUserId());

        List<MessageView> allMessages = messageRepository.findByConversationIdOrderBySequenceNumAsc(conversationId).stream()
                .map(this::toMessageView)
                .collect(Collectors.toList());

        String effectiveSummary = conv.getSummary();
        if (allMessages.size() > 4) {
            SlidingWindowContextTrimmer.WindowSplit split = contextTrimmer.trimToWindow(allMessages, 4);
            if (!split.getOlderMessages().isEmpty()) {
                String updatedSummary = rollingSummaryService.synthesizeRollingSummary(conv.getSummary(), split.getOlderMessages());
                conversationRepository.updateSummary(conversationId, updatedSummary, LocalDateTime.now());
                effectiveSummary = updatedSummary;
                broadcastEvent(conversationId, "summary_generated", Map.of(
                        "conversationId", conversationId,
                        "summary", updatedSummary
                ));
            }
        }

        return new ConversationSummaryView(
                conversationId,
                effectiveSummary,
                allMessages.size(),
                tokenBudgetManager.estimateTokens(effectiveSummary)
        );
    }

    @Override
    public void triggerTeamTurn(String conversationId) {
        ConversationEntity conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND, "Conversation not found: " + conversationId));

        if (conv.getTeamId() == null || conv.getTeamId().isBlank() || "TERMINATED".equalsIgnoreCase(conv.getStatus())) {
            return;
        }

        TeamEntity team = teamRepository.findById(conv.getTeamId()).orElse(null);
        if (team == null) return;

        List<TeamMemberEntity> members = teamMemberRepository.findByTeamIdOrderBySortOrderAsc(team.getId());
        if (members.isEmpty()) return;

        List<MessageView> recent = messageRepository.findByConversationIdOrderBySequenceNumAsc(conversationId).stream()
                .map(this::toMessageView)
                .collect(Collectors.toList());

        String currentSpeaker = recent.isEmpty() ? null : recent.get(recent.size() - 1).getSenderId();

        TeamCoordinationContext context = new TeamCoordinationContext(
                conversationId,
                currentSpeaker,
                recent.size(),
                team.getMaxTurns(),
                null,
                null,
                conv.getTitle(),
                recent
        );

        TeamTopologyStrategy strategy = strategyFactory.getStrategy(team.getTopology());
        NextSpeakerDecision decision = strategy.decideNextSpeaker(team, members, context);

        if (decision.getAction() == NextSpeakerDecision.Action.TERMINATE) {
            conversationRepository.updateStatus(conversationId, "TERMINATED", LocalDateTime.now());
            long seq = sequenceManager.nextSequenceNum(conversationId);
            MessageEntity finishMsg = new MessageEntity(
                    "msg-" + UUID.randomUUID().toString().substring(0, 8),
                    conversationId,
                    "SYSTEM",
                    SenderType.SYSTEM,
                    null,
                    MessageType.SYSTEM,
                    MessageProtocolType.NORMAL,
                    "【团队协作完成】" + decision.getReason(),
                    "v1",
                    seq,
                    0,
                    null
            );
            messageRepository.save(finishMsg);
            broadcastEvent(conversationId, "message", toMessageView(finishMsg));
            return;
        }

        if (decision.getAction() == NextSpeakerDecision.Action.SUMMARIZE) {
            generateRollingSummary(conversationId);
            long seq = sequenceManager.nextSequenceNum(conversationId);
            MessageEntity summaryMsg = new MessageEntity(
                    "msg-" + UUID.randomUUID().toString().substring(0, 8),
                    conversationId,
                    decision.getNextSpeakerId(),
                    SenderType.AGENT,
                    null,
                    MessageType.BROADCAST,
                    MessageProtocolType.SUMMARIZE,
                    "【团队交付汇总结论】已完成协同编排并收敛产物：\n" + (conv.getSummary() != null ? conv.getSummary() : "多智能体任务已顺利达成共识并执行完毕。"),
                    "v1",
                    seq,
                    tokenBudgetManager.estimateTokens(conv.getSummary()),
                    null
            );
            messageRepository.save(summaryMsg);
            conversationRepository.updateStatus(conversationId, "COMPLETED", LocalDateTime.now());
            broadcastEvent(conversationId, "message", toMessageView(summaryMsg));
            return;
        }

        // Active speaker action (CONTINUE or HANDOFF)
        String speaker = decision.getNextSpeakerId();
        long seq = sequenceManager.nextSequenceNum(conversationId);
        MessageProtocolType proto = decision.getAction() == NextSpeakerDecision.Action.HANDOFF
                ? MessageProtocolType.HANDOFF
                : MessageProtocolType.NORMAL;

        String content = "【" + decision.getNextSpeakerRole() + "】响应执行协同动作 (" + decision.getReason() + ")";
        int tokens = tokenBudgetManager.estimateTokens(content);

        MessageEntity speakerMsg = new MessageEntity(
                "msg-" + UUID.randomUUID().toString().substring(0, 8),
                conversationId,
                speaker,
                SenderType.AGENT,
                null,
                MessageType.BROADCAST,
                proto,
                content,
                "v1",
                seq,
                tokens,
                null
        );
        messageRepository.save(speakerMsg);
        broadcastEvent(conversationId, "message", toMessageView(speakerMsg));
    }

    private void handleLegacyMentions(ConversationEntity conversation, String content, List<String> mentions,
                                      SenderType senderType, MessageType messageType) {
        String convId = conversation.getId();
        String workspaceDir = workspaceBaseDir + "/default";

        boolean isOrchestratorTargeted = mentionParser.hasOrchestratorMention(content) ||
                (senderType == SenderType.USER && messageType == MessageType.BROADCAST &&
                 conversation.getType() == ConversationType.GROUP_COLLABORATION && mentions.isEmpty());

        if (isOrchestratorTargeted) {
            InteractiveCard card = orchestratorTaskDecomposer.decomposeTask(content);
            try {
                String cardJson = objectMapper.writeValueAsString(card);
                long cardSeq = sequenceManager.nextSequenceNum(convId);
                MessageEntity cardMsg = new MessageEntity(
                        "msg-" + UUID.randomUUID().toString().substring(0, 8),
                        convId,
                        "Orchestrator",
                        SenderType.ORCHESTRATOR,
                        null,
                        MessageType.BROADCAST,
                        MessageProtocolType.NORMAL,
                        "已完成多 Agent 协作任务拆解与编排派发。",
                        "v1",
                        cardSeq,
                        tokenBudgetManager.estimateTokens(cardJson),
                        null
                );
                cardMsg.setCardPayloadJson(cardJson);
                messageRepository.save(cardMsg);
                broadcastEvent(convId, "message", toMessageView(cardMsg));

                UnifiedAgentAdapter apiAdapter = agentAdapterFactory.getAdapter(AgentPlatformType.SPRING_AI_API);
                AgentExecutionRequest backendReq = new AgentExecutionRequest(
                        "BackendArchitect",
                        AgentPlatformType.SPRING_AI_API,
                        workspaceDir,
                        "你作为资深后端架构师，请针对业务需求「" + content + "」编写完整的 Spring Boot 3 控制器或服务层核心代码。"
                );
                AgentExecutionResult backendRes = apiAdapter.execute(backendReq);
                long backendSeq = sequenceManager.nextSequenceNum(convId);
                MessageEntity backendMsg = new MessageEntity(
                        "msg-" + UUID.randomUUID().toString().substring(0, 8),
                        convId,
                        "BackendArchitect",
                        SenderType.AGENT,
                        null,
                        MessageType.BROADCAST,
                        MessageProtocolType.NORMAL,
                        "【阶段一完成】后端领域服务代码已编写并写入工作区：\n" + backendRes.getOutput(),
                        "v1",
                        backendSeq,
                        tokenBudgetManager.estimateTokens(backendRes.getOutput()),
                        null
                );
                messageRepository.save(backendMsg);
                broadcastEvent(convId, "message", toMessageView(backendMsg));

                AgentExecutionRequest frontendReq = new AgentExecutionRequest(
                        "FrontendEngineer",
                        AgentPlatformType.SPRING_AI_API,
                        workspaceDir,
                        "你作为资深前端工程师，请针对业务需求「" + content + "」编写完整的 Next.js 14 / React 响应式界面组件代码。"
                );
                AgentExecutionResult frontendRes = apiAdapter.execute(frontendReq);
                long frontendSeq = sequenceManager.nextSequenceNum(convId);
                MessageEntity frontendMsg = new MessageEntity(
                        "msg-" + UUID.randomUUID().toString().substring(0, 8),
                        convId,
                        "FrontendEngineer",
                        SenderType.AGENT,
                        null,
                        MessageType.BROADCAST,
                        MessageProtocolType.NORMAL,
                        "【阶段二完成】前端交互界面代码已编写并写入工作区：\n" + frontendRes.getOutput(),
                        "v1",
                        frontendSeq,
                        tokenBudgetManager.estimateTokens(frontendRes.getOutput()),
                        null
                );
                messageRepository.save(frontendMsg);
                broadcastEvent(convId, "message", toMessageView(frontendMsg));

            } catch (Exception e) {
                log.error("Failed to execute Orchestrator workflow: ", e);
            }
        }

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
                        long seq = sequenceManager.nextSequenceNum(convId);
                        MessageEntity agentMsg = new MessageEntity(
                                "msg-" + UUID.randomUUID().toString().substring(0, 8),
                                convId,
                                mention,
                                SenderType.AGENT,
                                null,
                                MessageType.BROADCAST,
                                MessageProtocolType.NORMAL,
                                result.getOutput(),
                                "v1",
                                seq,
                                tokenBudgetManager.estimateTokens(result.getOutput()),
                                null
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
                entity.getTeamId(),
                entity.getTitle(),
                entity.getType(),
                agentIds != null ? agentIds : Collections.emptyList(),
                entity.getLastSequenceNum(),
                entity.getSummary(),
                entity.getTokenCount(),
                entity.getStatus(),
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
                entity.getRecipientId(),
                entity.getMessageType(),
                entity.getProtocolType(),
                entity.getContent(),
                entity.getCardPayloadJson(),
                entity.getMentions(),
                entity.getSchemaVersion(),
                entity.getSequenceNum(),
                entity.getTokenCount(),
                entity.getInReplyToId(),
                entity.getCreatedAt()
        );
    }
}
