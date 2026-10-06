package com.agenthub;

import com.agenthub.application.service.IMCollaborationService;
import com.agenthub.domain.conversation.model.ConversationType;
import com.agenthub.domain.conversation.model.InteractiveCard;
import com.agenthub.domain.conversation.model.SenderType;
import com.agenthub.domain.conversation.service.MentionParser;
import com.agenthub.domain.conversation.service.OrchestratorTaskDecomposer;
import com.agenthub.infrastructure.repository.entity.ConversationEntity;
import com.agenthub.infrastructure.repository.entity.MessageEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class IMCollaborationTest {

    @Autowired
    private MentionParser mentionParser;

    @Autowired
    private OrchestratorTaskDecomposer orchestratorTaskDecomposer;

    @Autowired
    private IMCollaborationService imService;

    @Test
    @DisplayName("测试 @Mention 语法正确解析多个目标 Agent")
    void shouldExtractMentionsCorrectly() {
        String msg = "请 @Orchestrator 协调，@claude-code 编写接口，并让 @前端工程师 审核。";
        List<String> mentions = mentionParser.extractMentions(msg);
        assertThat(mentions).contains("Orchestrator", "claude-code", "前端工程师");
        assertThat(mentionParser.hasOrchestratorMention(msg)).isTrue();
    }

    @Test
    @DisplayName("测试 Orchestrator 对复杂需求自动完成多阶段子任务拆解与卡片生成")
    void shouldDecomposeTaskIntoInteractiveCard() {
        InteractiveCard card = orchestratorTaskDecomposer.decomposeTask("开发多租户权限系统与用户注册界面");
        assertThat(card).isNotNull();
        assertThat(card.getHeaderTitle()).contains("Orchestrator");
        assertThat(card.getSubtasks()).hasSize(3);
        assertThat(card.getSubtasks().get(0).getTargetAgent()).isEqualTo("BackendArchitect");
        assertThat(card.getSubtasks().get(1).getTargetAgent()).isEqualTo("FrontendEngineer");
        assertThat(card.getSubtasks().get(2).getTargetAgent()).isEqualTo("QAAuditor");
    }

    @Test
    @DisplayName("测试群聊会话创建、消息发送及历史获取")
    void shouldCreateGroupConversationAndSendMessage() throws InterruptedException {
        ConversationEntity conv = imService.createConversation(
                "全栈特性突击小队",
                ConversationType.GROUP_COLLABORATION,
                List.of("BackendArchitect", "FrontendEngineer", "QAAuditor")
        );
        assertThat(conv.getId()).isNotEmpty();

        MessageEntity userMsg = imService.sendMessage(
                conv.getId(),
                "user-dev",
                SenderType.USER,
                "@Orchestrator 请拆解并实现用户个人资料与头像上传功能"
        );
        assertThat(userMsg.getId()).isNotEmpty();
        assertThat(userMsg.getMentions()).contains("Orchestrator");

        // Wait a brief moment for asynchronous agent mention handling
        Thread.sleep(500);

        List<MessageEntity> messages = imService.getMessages(conv.getId());
        assertThat(messages.size()).isGreaterThanOrEqualTo(2);
        // Verify that Orchestrator card message was persisted
        boolean hasCardMsg = messages.stream()
                .anyMatch(m -> m.getSenderType() == SenderType.ORCHESTRATOR && m.getCardPayloadJson() != null);
        assertThat(hasCardMsg).isTrue();
    }
}
