package com.agenthub.conversation.application;

import com.agenthub.conversation.domain.model.ConversationEventType;
import com.agenthub.conversation.domain.model.LoopDetectionResult;
import com.agenthub.conversation.domain.model.MessageProtocolType;
import com.agenthub.conversation.domain.model.MessageType;
import com.agenthub.conversation.domain.service.*;
import com.agenthub.conversation.domain.sse.ConversationEventBroadcaster;
import com.agenthub.conversation.application.pipeline.ContextWindowManager;
import org.springframework.beans.factory.annotation.Autowired;
import com.agenthub.conversation.application.pipeline.LegacyMentionPipeline;
import com.agenthub.conversation.application.pipeline.TeamTurnCoordinator;
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

    @Autowired
    private com.agenthub.conversation.application.pipeline.TeamTurnCoordinator teamTurnCoordinator;

    @Autowired
    private com.agenthub.conversation.application.pipeline.ContextWindowManager contextWindowManager;

    @Autowired
    private com.agenthub.conversation.application.pipeline.LegacyMentionPipeline legacyMentionPipeline;


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
            if (accessGuard != null && accessGuard.isAllowSuperuserBypass()
                    && accessGuard.getDemoDefaultUserId() != null && !accessGuard.getDemoDefaultUserId().isBlank()) {
                currentUserId = accessGuard.getDemoDefaultUserId();
            } else {
                throw new BusinessException(ErrorCode.UNAUTHORIZED, "Authentication required to create conversation");
            }
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
        String resolvedProjectId = (cmd.getProjectId() != null && !cmd.getProjectId().isBlank())
                ? cmd.getProjectId()
                : (accessGuard != null ? accessGuard.getDemoDefaultProjectId() : null);

        ConversationEntity entity = new ConversationEntity(
                id,
                currentUserId,
                resolvedProjectId,
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
        if (accessGuard != null && accessGuard.isConfiguredSuperuser(currentUserId)) {
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

        String effectiveViewerId = (viewerId != null && !viewerId.isBlank()) ? viewerId : currentUserId;
        boolean isPrivileged = "admin".equalsIgnoreCase(effectiveViewerId)
                || "system".equalsIgnoreCase(effectiveViewerId)
                || ((viewerId == null || viewerId.isBlank())
                    && accessGuard != null && accessGuard.isConfiguredSuperuser(currentUserId));

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

        String defaultSender = (accessGuard != null && accessGuard.getDemoDefaultUserId() != null && !accessGuard.getDemoDefaultUserId().isBlank())
                ? accessGuard.getDemoDefaultUserId()
                : conversation.getOwnerId();
        String senderId = cmd.getSenderId() != null && !cmd.getSenderId().isBlank()
                ? cmd.getSenderId()
                : (currentUserId != null && !currentUserId.isBlank() ? currentUserId : defaultSender);

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
                    teamTurnCoordinator.triggerTeamTurn(conversationId, this::toMessageView, this::generateRollingSummary);
                } else {
                    legacyMentionPipeline.handleLegacyMentions(conversation, cmd.getContent(), mentions, senderType, messageType, this::toMessageView);
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

        return contextWindowManager.getContextWindow(conversationId, windowSize, maxTokens, this::toMessageView);
    }

    @Override
    public ConversationSummaryView generateRollingSummary(String conversationId) {
        ConversationEntity conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND, "Conversation not found: " + conversationId));
        accessGuard.checkOwnership(conv.getOwnerId(), RequestContext.get().getUserId());

        return contextWindowManager.generateRollingSummary(conversationId, this::toMessageView);
    }

    @Override
    public void triggerTeamTurn(String conversationId) {
        teamTurnCoordinator.triggerTeamTurn(conversationId, this::toMessageView, this::generateRollingSummary);
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
