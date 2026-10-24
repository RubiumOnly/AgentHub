package com.agenthub.conversation.application.pipeline;

import com.agenthub.conversation.domain.service.SlidingWindowContextTrimmer;
import com.agenthub.conversation.domain.service.TokenBudgetContextManager;
import com.agenthub.conversation.dto.ContextWindowView;
import com.agenthub.conversation.dto.ConversationSummaryView;
import com.agenthub.conversation.dto.MessageView;
import com.agenthub.conversation.domain.sse.ConversationEventBroadcaster;
import com.agenthub.identity.infrastructure.security.ResourceAccessGuard;
import com.agenthub.infrastructure.repository.ConversationRepository;
import com.agenthub.infrastructure.repository.MessageRepository;
import com.agenthub.infrastructure.repository.entity.ConversationEntity;
import com.agenthub.shared.context.RequestContext;
import com.agenthub.shared.exception.BusinessException;
import com.agenthub.shared.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 上下文窗口管理缝（Phase 10 R7 自 ConversationApplicationService 抽出）：
 * 滑窗裁剪 + Token 预算适配的上下文投影，以及滚动摘要的刷新与广播。
 */
@Component
public class ContextWindowManager {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final SlidingWindowContextTrimmer contextTrimmer;
    private final TokenBudgetContextManager tokenBudgetManager;
    private final com.agenthub.conversation.domain.service.RollingSummaryService rollingSummaryService;
    private final ResourceAccessGuard accessGuard;
    private final ConversationEventBroadcaster eventBroadcaster;

    public ContextWindowManager(ConversationRepository conversationRepository,
                                MessageRepository messageRepository,
                                SlidingWindowContextTrimmer contextTrimmer,
                                TokenBudgetContextManager tokenBudgetManager,
                                com.agenthub.conversation.domain.service.RollingSummaryService rollingSummaryService,
                                ResourceAccessGuard accessGuard,
                                ConversationEventBroadcaster eventBroadcaster) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.contextTrimmer = contextTrimmer;
        this.tokenBudgetManager = tokenBudgetManager;
        this.rollingSummaryService = rollingSummaryService;
        this.accessGuard = accessGuard;
        this.eventBroadcaster = eventBroadcaster;
    }

    public ContextWindowView getContextWindow(String conversationId, int windowSize, int maxTokens,
                                              Function<com.agenthub.infrastructure.repository.entity.MessageEntity, MessageView> viewMapper) {
        ConversationEntity conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND, "Conversation not found: " + conversationId));
        accessGuard.checkOwnership(conv.getOwnerId(), RequestContext.get().getUserId());

        List<MessageView> allMessages = messageRepository.findByConversationIdOrderBySequenceNumAsc(conversationId).stream()
                .map(viewMapper)
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

    public ConversationSummaryView generateRollingSummary(String conversationId,
                                                          Function<com.agenthub.infrastructure.repository.entity.MessageEntity, MessageView> viewMapper) {
        ConversationEntity conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND, "Conversation not found: " + conversationId));
        accessGuard.checkOwnership(conv.getOwnerId(), RequestContext.get().getUserId());

        List<MessageView> allMessages = messageRepository.findByConversationIdOrderBySequenceNumAsc(conversationId).stream()
                .map(viewMapper)
                .collect(Collectors.toList());

        String effectiveSummary = conv.getSummary();
        if (allMessages.size() > 4) {
            SlidingWindowContextTrimmer.WindowSplit split = contextTrimmer.trimToWindow(allMessages, 4);
            if (!split.getOlderMessages().isEmpty()) {
                String updatedSummary = rollingSummaryService.synthesizeRollingSummary(conv.getSummary(), split.getOlderMessages());
                conversationRepository.updateSummary(conversationId, updatedSummary, LocalDateTime.now());
                effectiveSummary = updatedSummary;
                eventBroadcaster.broadcastEvent(conversationId, "summary_generated", Map.of(
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
}
