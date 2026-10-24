package com.agenthub.conversation.application.pipeline;

import com.agenthub.conversation.domain.service.ConversationSequenceManager;
import com.agenthub.conversation.domain.sse.ConversationEventBroadcaster;
import com.agenthub.conversation.dto.MessageView;
import com.agenthub.conversation.domain.model.MessageProtocolType;
import com.agenthub.conversation.domain.model.MessageType;
import com.agenthub.domain.conversation.model.SenderType;
import com.agenthub.infrastructure.repository.ConversationRepository;
import com.agenthub.infrastructure.repository.MessageRepository;
import com.agenthub.infrastructure.repository.entity.ConversationEntity;
import com.agenthub.infrastructure.repository.entity.MessageEntity;
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
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * 团队拓扑轮次协调缝（Phase 10 R7 自 ConversationApplicationService 抽出）：
 * 依据团队拓扑决定下一位发言者并落库系统/代理消息；TERMINATE/SUMMARIZE 语义集中在此。
 */
@Component
public class TeamTurnCoordinator {

    private final ConversationRepository conversationRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final MessageRepository messageRepository;
    private final ConversationSequenceManager sequenceManager;
    private final TeamTopologyStrategyFactory strategyFactory;
    private final com.agenthub.conversation.domain.service.TokenBudgetContextManager tokenBudgetManager;
    private final ConversationEventBroadcaster eventBroadcaster;

    public TeamTurnCoordinator(ConversationRepository conversationRepository,
                               TeamRepository teamRepository,
                               TeamMemberRepository teamMemberRepository,
                               MessageRepository messageRepository,
                               ConversationSequenceManager sequenceManager,
                               TeamTopologyStrategyFactory strategyFactory,
                               com.agenthub.conversation.domain.service.TokenBudgetContextManager tokenBudgetManager,
                               ConversationEventBroadcaster eventBroadcaster) {
        this.conversationRepository = conversationRepository;
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.messageRepository = messageRepository;
        this.sequenceManager = sequenceManager;
        this.strategyFactory = strategyFactory;
        this.tokenBudgetManager = tokenBudgetManager;
        this.eventBroadcaster = eventBroadcaster;
    }

    public void triggerTeamTurn(String conversationId, Function<MessageEntity, MessageView> viewMapper,
                                java.util.function.Consumer<String> rollingSummaryRefresher) {
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
                .map(viewMapper)
                .collect(java.util.stream.Collectors.toList());

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
            eventBroadcaster.broadcastEvent(conversationId, "message", viewMapper.apply(finishMsg));
            return;
        }

        if (decision.getAction() == NextSpeakerDecision.Action.SUMMARIZE) {
            // 原语义：先触发滚动摘要刷新（含广播与持久化），消息文本仍使用方法入口加载的实体摘要快照
            rollingSummaryRefresher.accept(conversationId);
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
            eventBroadcaster.broadcastEvent(conversationId, "message", viewMapper.apply(summaryMsg));
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
        eventBroadcaster.broadcastEvent(conversationId, "message", viewMapper.apply(speakerMsg));
    }
}
