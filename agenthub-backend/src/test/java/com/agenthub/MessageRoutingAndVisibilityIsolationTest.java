package com.agenthub;

import com.agenthub.conversation.application.ConversationApplication;
import com.agenthub.conversation.domain.model.MessageProtocolType;
import com.agenthub.conversation.domain.model.MessageType;
import com.agenthub.conversation.domain.service.MessageVisibilityFilter;
import com.agenthub.conversation.dto.ConversationView;
import com.agenthub.conversation.dto.CreateConversationCommand;
import com.agenthub.conversation.dto.MessageView;
import com.agenthub.conversation.dto.SendMessageCommand;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class MessageRoutingAndVisibilityIsolationTest {

    @Autowired
    private ConversationApplication conversationApplication;

    @Autowired
    private MessageVisibilityFilter visibilityFilter;

    @BeforeEach
    void setUp() {
        RequestContext.get().setUserId("user-1");
    }

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    @Test
    @DisplayName("测试点对点私聊 (DIRECT P2P) 隔离性：仅双方及管理员可见，第三方 Agent 严格不可见")
    void shouldIsolateDirectP2PMessagesFromUnrelatedPeers() {
        ConversationView conv = conversationApplication.createConversation(new CreateConversationCommand(
                "协作隔离群",
                ConversationType.GROUP_COLLABORATION,
                List.of("BackendArchitect", "FrontendEngineer", "QAAuditor"),
                "proj-default"
        ));

        // 1. BackendArchitect sends broadcast message
        conversationApplication.sendMessage(conv.getId(), new SendMessageCommand(
                "BackendArchitect", SenderType.AGENT, null, MessageType.BROADCAST,
                MessageProtocolType.NORMAL, "全员广播：API 已发布至测试环境", null
        ));

        // 2. BackendArchitect sends direct private message to QAAuditor
        conversationApplication.sendMessage(conv.getId(), new SendMessageCommand(
                "BackendArchitect", SenderType.AGENT, "QAAuditor", MessageType.DIRECT,
                MessageProtocolType.REQUEST_REPLY, "私信：请优先审查第 3 模块的 SQL 注入防御", null
        ));

        // 3. FrontendEngineer queries message list
        List<MessageView> frontendView = conversationApplication.listMessages(conv.getId(), null, "FrontendEngineer");
        assertThat(frontendView).hasSize(1);
        assertThat(frontendView.get(0).getContent()).contains("全员广播");

        // 4. QAAuditor (recipient) queries message list
        List<MessageView> qaView = conversationApplication.listMessages(conv.getId(), null, "QAAuditor");
        assertThat(qaView).hasSize(2);
        assertThat(qaView.stream().anyMatch(m -> m.getContent().contains("私信"))).isTrue();

        // 5. BackendArchitect (sender) queries message list
        List<MessageView> backendView = conversationApplication.listMessages(conv.getId(), null, "BackendArchitect");
        assertThat(backendView).hasSize(2);

        // 6. Admin queries message list -> sees all
        List<MessageView> adminView = conversationApplication.listMessages(conv.getId(), null, "admin");
        assertThat(adminView).hasSize(2);
    }

    @Test
    @DisplayName("测试消息可见性过滤器单元判定规则")
    void shouldFilterVisibilityAccordingToRules() {
        MessageView broadcastMsg = new MessageView("m1", "c1", "agent-a", SenderType.AGENT,
                null, MessageType.BROADCAST, MessageProtocolType.NORMAL, "Public", null, null, "v1", 1L, 10, null, null);

        MessageView directMsg = new MessageView("m2", "c1", "agent-a", SenderType.AGENT,
                "agent-b", MessageType.DIRECT, MessageProtocolType.NORMAL, "Secret", null, null, "v1", 2L, 10, null, null);

        MessageView systemMsg = new MessageView("m3", "c1", "SYSTEM", SenderType.SYSTEM,
                null, MessageType.SYSTEM, MessageProtocolType.NORMAL, "System Alert", null, null, "v1", 3L, 10, null, null);

        // Broadcast: visible to anyone
        assertThat(visibilityFilter.isVisible(broadcastMsg, "agent-c", false)).isTrue();

        // System: visible to anyone
        assertThat(visibilityFilter.isVisible(systemMsg, "agent-c", false)).isTrue();

        // Direct: visible to sender and recipient
        assertThat(visibilityFilter.isVisible(directMsg, "agent-a", false)).isTrue();
        assertThat(visibilityFilter.isVisible(directMsg, "agent-b", false)).isTrue();

        // Direct: invisible to third party
        assertThat(visibilityFilter.isVisible(directMsg, "agent-c", false)).isFalse();

        // Direct: strictly invisible to unauthenticated/null/blank viewer
        assertThat(visibilityFilter.isVisible(directMsg, null, false)).isFalse();
        assertThat(visibilityFilter.isVisible(directMsg, "", false)).isFalse();

        // Direct: visible to admin
        assertThat(visibilityFilter.isVisible(directMsg, "admin", false)).isTrue();

        // Filter list with null/blank viewerId: direct messages MUST be excluded!
        List<MessageView> anonList = visibilityFilter.filterVisible(List.of(broadcastMsg, directMsg, systemMsg), null, false);
        assertThat(anonList).hasSize(2);
        assertThat(anonList).containsExactly(broadcastMsg, systemMsg);

        List<MessageView> blankList = visibilityFilter.filterVisible(List.of(broadcastMsg, directMsg, systemMsg), "   ", false);
        assertThat(blankList).hasSize(2);
        assertThat(blankList).containsExactly(broadcastMsg, systemMsg);
    }
}
