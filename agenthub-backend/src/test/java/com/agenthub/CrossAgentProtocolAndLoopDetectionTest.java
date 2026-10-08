package com.agenthub;

import com.agenthub.conversation.application.ConversationApplication;
import com.agenthub.conversation.domain.model.LoopDetectionResult;
import com.agenthub.conversation.domain.model.MessageProtocolType;
import com.agenthub.conversation.domain.model.MessageType;
import com.agenthub.conversation.domain.service.LoopDetector;
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

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class CrossAgentProtocolAndLoopDetectionTest {

    @Autowired
    private ConversationApplication conversationApplication;

    @Autowired
    private LoopDetector loopDetector;

    @BeforeEach
    void setUp() {
        RequestContext.get().setUserId("user-1");
    }

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    @Test
    @DisplayName("测试跨 Agent 协作通讯协议：REQUEST_REPLY 与关联回复 inReplyToId 语义闭环")
    void shouldHandleRequestReplyProtocolWithCorrelation() {
        ConversationView conv = conversationApplication.createConversation(new CreateConversationCommand(
                "协议校验群",
                ConversationType.GROUP_COLLABORATION,
                List.of("BackendArchitect", "QAAuditor"),
                "proj-default"
        ));

        MessageView req = conversationApplication.sendMessage(conv.getId(), new SendMessageCommand(
                "QAAuditor", SenderType.AGENT, "BackendArchitect", MessageType.DIRECT,
                MessageProtocolType.REQUEST_REPLY, "请提供用户注册接口的入参校验规范", null
        ));
        assertThat(req.getProtocolType()).isEqualTo(MessageProtocolType.REQUEST_REPLY);

        MessageView reply = conversationApplication.sendMessage(conv.getId(), new SendMessageCommand(
                "BackendArchitect", SenderType.AGENT, "QAAuditor", MessageType.DIRECT,
                MessageProtocolType.NORMAL, "入参已使用 @Valid 且邮箱已加唯一校验", req.getId()
        ));
        assertThat(reply.getInReplyToId()).isEqualTo(req.getId());
    }

    @Test
    @DisplayName("测试消息防死循环拦截：达到最大对话轮次 (maxTurns) 时自动触发熔断")
    void shouldDetectMaxTurnsExceeded() {
        List<MessageView> history = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            history.add(new MessageView("m" + i, "c1", "agent-" + (i % 2), SenderType.AGENT,
                    null, MessageType.BROADCAST, MessageProtocolType.NORMAL,
                    "Turn message content " + i, null, null, "v1", (long) (i + 1), 10, null, null));
        }

        LoopDetectionResult res = loopDetector.detectLoop(history, 10);
        assertThat(res.isLoopDetected()).isTrue();
        assertThat(res.getLoopType()).isEqualTo(LoopDetectionResult.LoopType.MAX_TURNS_EXCEEDED);
        assertThat(res.getReason()).contains("Exceeded maximum allowed coordination turns: 10");
    }

    @Test
    @DisplayName("测试重复消息碰撞检测：同一 Agent 产生完全重复的内容时快速识别死循环")
    void shouldDetectDuplicateContentCollision() {
        List<MessageView> history = List.of(
                new MessageView("m1", "c1", "BackendArchitect", SenderType.AGENT, null, MessageType.BROADCAST,
                        MessageProtocolType.NORMAL, "正在尝试连接数据库服务...", null, null, "v1", 1L, 10, null, null),
                new MessageView("m2", "c1", "QAAuditor", SenderType.AGENT, null, MessageType.BROADCAST,
                        MessageProtocolType.NORMAL, "等待后端准备就绪", null, null, "v1", 2L, 10, null, null),
                new MessageView("m3", "c1", "BackendArchitect", SenderType.AGENT, null, MessageType.BROADCAST,
                        MessageProtocolType.NORMAL, "正在尝试连接数据库服务...", null, null, "v1", 3L, 10, null, null)
        );

        LoopDetectionResult res = loopDetector.detectLoop(history, 10);
        assertThat(res.isLoopDetected()).isTrue();
        assertThat(res.getLoopType()).isEqualTo(LoopDetectionResult.LoopType.DUPLICATE_CONTENT_COLLISION);
        assertThat(res.getReason()).contains("BackendArchitect");
    }

    @Test
    @DisplayName("测试短周期振荡 (A-B-A-B) 检测：两个 Agent 往复互相 Ping-Pong 且无任务推进时触发熔断")
    void shouldDetectOscillatingPingPongLoop() {
        List<MessageView> history = List.of(
                new MessageView("m1", "c1", "AgentA", SenderType.AGENT, null, MessageType.BROADCAST,
                        MessageProtocolType.NORMAL, "你有新的指示吗？", null, null, "v1", 1L, 10, null, null),
                new MessageView("m2", "c1", "AgentB", SenderType.AGENT, null, MessageType.BROADCAST,
                        MessageProtocolType.NORMAL, "我没有，你呢？", null, null, "v1", 2L, 10, null, null),
                new MessageView("m3", "c1", "AgentA", SenderType.AGENT, null, MessageType.BROADCAST,
                        MessageProtocolType.NORMAL, "我也没有，请问你进展如何？", null, null, "v1", 3L, 10, null, null),
                new MessageView("m4", "c1", "AgentB", SenderType.AGENT, null, MessageType.BROADCAST,
                        MessageProtocolType.NORMAL, "我还在等待，你呢？", null, null, "v1", 4L, 10, null, null)
        );

        LoopDetectionResult res = loopDetector.detectLoop(history, 10);
        assertThat(res.isLoopDetected()).isTrue();
        assertThat(res.getLoopType()).isEqualTo(LoopDetectionResult.LoopType.OSCILLATION_DETECTED);
        assertThat(res.getReason()).contains("AgentA").contains("AgentB");
    }
}
