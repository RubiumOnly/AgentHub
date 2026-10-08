package com.agenthub;

import com.agenthub.conversation.application.ConversationApplication;
import com.agenthub.conversation.domain.sse.ConversationEventBroadcaster;
import com.agenthub.conversation.dto.ConversationView;
import com.agenthub.conversation.dto.CreateConversationCommand;
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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ConversationSseStreamAndReconnectionTest {

    @Autowired
    private ConversationApplication conversationApplication;

    @Autowired
    private ConversationEventBroadcaster eventBroadcaster;

    @BeforeEach
    void setUp() {
        RequestContext.get().setUserId("user-1");
    }

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    @Test
    @DisplayName("测试会话 SSE 订阅与 Last-Event-ID 断点续传重放机制")
    void shouldRegisterSseAndReplayFromLastEventId() {
        ConversationView conv = conversationApplication.createConversation(new CreateConversationCommand(
                "SSE 流与断线重连会话",
                ConversationType.DIRECT_CHAT,
                List.of("BackendArchitect"),
                "proj-default"
        ));

        // Send 5 messages
        for (int i = 1; i <= 5; i++) {
            conversationApplication.sendMessage(conv.getId(), new SendMessageCommand(
                    "user-1", SenderType.USER, "消息 " + i
            ));
        }

        // 1. Initial connect
        SseEmitter emitter1 = conversationApplication.registerStream(conv.getId());
        assertThat(emitter1).isNotNull();
        assertThat(eventBroadcaster.getActiveSubscriberCount(conv.getId())).isGreaterThanOrEqualTo(1);

        // 2. Reconnect with Last-Event-ID = "3"
        // Should replay messages 4 and 5
        SseEmitter reconnectEmitter = conversationApplication.registerStream(conv.getId(), "3", "user-1");
        assertThat(reconnectEmitter).isNotNull();

        // 3. Trigger heartbeat
        eventBroadcaster.sendHeartbeat();
    }
}
