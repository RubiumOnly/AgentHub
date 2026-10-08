package com.agenthub;

import com.agenthub.conversation.application.ConversationApplication;
import com.agenthub.conversation.domain.service.RollingSummaryService;
import com.agenthub.conversation.domain.service.SlidingWindowContextTrimmer;
import com.agenthub.conversation.domain.service.TokenBudgetContextManager;
import com.agenthub.conversation.dto.*;
import com.agenthub.domain.conversation.model.ConversationType;
import com.agenthub.domain.conversation.model.SenderType;
import com.agenthub.shared.context.RequestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ContextWindowAndRollingSummaryTest {

    @Autowired
    private ConversationApplication conversationApplication;

    @Autowired
    private SlidingWindowContextTrimmer contextTrimmer;

    @Autowired
    private TokenBudgetContextManager tokenBudgetManager;

    @Autowired
    private RollingSummaryService rollingSummaryService;

    @BeforeEach
    void setUp() {
        RequestContext.get().setUserId("user-1");
    }

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    @Test
    @DisplayName("测试滑动窗口裁剪器 (SlidingWindowTrimmer)：将超长消息序列精确划分为活跃窗口与归档历史")
    void shouldTrimMessagesIntoActiveWindowAndArchivedHistory() {
        List<MessageView> messages = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            messages.add(new MessageView("m" + i, "conv-1", "user-1", SenderType.USER,
                    "Message " + i, null, null, "v1", (long) i, null));
        }

        SlidingWindowContextTrimmer.WindowSplit split = contextTrimmer.trimToWindow(messages, 4);
        assertThat(split.getActiveMessages()).hasSize(4);
        assertThat(split.getOlderMessages()).hasSize(6);
        assertThat(split.getTrimmedCount()).isEqualTo(6);

        // Verify that active messages are the newest (7, 8, 9, 10)
        assertThat(split.getActiveMessages().get(0).getContent()).isEqualTo("Message 7");
        assertThat(split.getActiveMessages().get(3).getContent()).isEqualTo("Message 10");
    }

    @Test
    @DisplayName("测试 Token 预算裁剪管理器 (TokenBudgetContextManager)：超限时从旧至新裁剪活跃消息")
    void shouldFitMessagesToTokenBudget() {
        List<MessageView> activeMessages = List.of(
                new MessageView("m1", "c1", "u1", SenderType.USER, "Old message with substantial token footprint A", null, null, "v1", 1L, null),
                new MessageView("m2", "c1", "u1", SenderType.USER, "Old message with substantial token footprint B", null, null, "v1", 2L, null),
                new MessageView("m3", "c1", "u1", SenderType.USER, "Recent crucial prompt", null, null, "v1", 3L, null)
        );

        // Limit budget strictly so only newest message fits
        List<MessageView> fitted = tokenBudgetManager.fitToBudget(activeMessages, "Summary note", 120);
        assertThat(fitted).isNotEmpty();
        assertThat(fitted.get(fitted.size() - 1).getContent()).isEqualTo("Recent crucial prompt");
    }

    @Test
    @DisplayName("测试滚动历史摘要生成 (RollingSummaryService)：将老消息压缩为紧凑前情要点")
    void shouldSynthesizeRollingSummaryFromAgedMessages() {
        List<MessageView> aged = List.of(
                new MessageView("m1", "c1", "user-1", SenderType.USER, "我们需要做一个用户密码找回功能", null, null, "v1", 1L, null),
                new MessageView("m2", "c1", "BackendArchitect", SenderType.AGENT, "建议使用邮件验证码与 Redis 5分钟过期", null, null, "v1", 2L, null)
        );

        String summary = rollingSummaryService.synthesizeRollingSummary(null, aged);
        assertThat(summary).contains("【前序会话滚动历史摘要】");
        assertThat(summary).contains("用户密码找回");
        assertThat(summary).contains("Redis 5分钟过期");
    }

    @Test
    @DisplayName("测试应用服务层 ContextWindow 与 RollingSummary API 端到端闭环")
    void shouldProduceContextWindowAndRollingSummaryViaApplication() {
        ConversationView conv = conversationApplication.createConversation(new CreateConversationCommand(
                "长对话上下文治理会话",
                ConversationType.DIRECT_CHAT,
                List.of("BackendArchitect"),
                "proj-default"
        ));

        for (int i = 1; i <= 8; i++) {
            conversationApplication.sendMessage(conv.getId(), new SendMessageCommand(
                    "user-1", SenderType.USER, "第 " + i + " 轮需求探讨与代码审查"
            ));
        }

        // Generate rolling summary
        ConversationSummaryView summaryView = conversationApplication.generateRollingSummary(conv.getId());
        assertThat(summaryView.getConversationId()).isEqualTo(conv.getId());
        assertThat(summaryView.getSummary()).isNotNull();

        // Get context window with windowSize=3
        ContextWindowView contextView = conversationApplication.getContextWindow(conv.getId(), 3, 2000);
        assertThat(contextView.getActiveMessages()).hasSize(3);
        assertThat(contextView.getRollingSummary()).isEqualTo(summaryView.getSummary());
        assertThat(contextView.getTrimmedCount()).isEqualTo(5);
    }
}
