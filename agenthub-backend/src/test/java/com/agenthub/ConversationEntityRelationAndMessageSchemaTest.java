package com.agenthub;

import com.agenthub.conversation.application.ConversationApplication;
import com.agenthub.conversation.dto.CreateConversationCommand;
import com.agenthub.conversation.dto.ConversationView;
import com.agenthub.conversation.dto.MessageView;
import com.agenthub.conversation.dto.SendMessageCommand;
import com.agenthub.conversation.infrastructure.entity.ConversationParticipantEntity;
import com.agenthub.conversation.infrastructure.repository.ConversationParticipantRepository;
import com.agenthub.domain.conversation.model.ConversationType;
import com.agenthub.domain.conversation.model.SenderType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ConversationEntityRelationAndMessageSchemaTest {

    @Autowired
    private ConversationApplication conversationApplication;

    @Autowired
    private ConversationParticipantRepository participantRepository;

    @Test
    @DisplayName("测试会话参与者关系表持久化：彻底弃用逗号字符串，多Agent参与者入库 conversation_participants")
    void shouldPersistConversationParticipantsInRelationalTable() {
        List<String> agents = List.of("BackendArchitect", "FrontendEngineer", "QAAuditor");
        ConversationView conv = conversationApplication.createConversation(new CreateConversationCommand(
                "架构评审群",
                ConversationType.GROUP_COLLABORATION,
                agents,
                "proj-default"
        ));

        assertThat(conv.getId()).isNotEmpty();
        assertThat(conv.getParticipantAgentIds()).containsExactlyInAnyOrderElementsOf(agents);

        // Verify direct relational table entries
        List<ConversationParticipantEntity> records = participantRepository.findByConversationId(conv.getId());
        assertThat(records).hasSize(3);
        List<String> persistedAgentIds = records.stream().map(ConversationParticipantEntity::getAgentId).toList();
        assertThat(persistedAgentIds).containsExactlyInAnyOrderElementsOf(agents);
    }

    @Test
    @DisplayName("测试消息实体 schema_version 与严格自增 sequence_num 字段")
    void shouldEnforceMessageSchemaVersionAndStrictSequenceNum() {
        ConversationView conv = conversationApplication.createConversation(new CreateConversationCommand(
                "时序消息测试群",
                ConversationType.DIRECT_CHAT,
                List.of("BackendArchitect"),
                "proj-default"
        ));

        MessageView msg1 = conversationApplication.sendMessage(conv.getId(), new SendMessageCommand(
                "user-1",
                SenderType.USER,
                "第 1 条指令"
        ));
        assertThat(msg1.getSchemaVersion()).isEqualTo("v1");
        assertThat(msg1.getSequenceNum()).isEqualTo(1L);

        MessageView msg2 = conversationApplication.sendMessage(conv.getId(), new SendMessageCommand(
                "user-1",
                SenderType.USER,
                "第 2 条指令"
        ));
        assertThat(msg2.getSchemaVersion()).isEqualTo("v1");
        assertThat(msg2.getSequenceNum()).isEqualTo(2L);

        MessageView msg3 = conversationApplication.sendMessage(conv.getId(), new SendMessageCommand(
                "user-1",
                SenderType.USER,
                "第 3 条指令"
        ));
        assertThat(msg3.getSchemaVersion()).isEqualTo("v1");
        assertThat(msg3.getSequenceNum()).isEqualTo(3L);

        List<MessageView> allMessages = conversationApplication.listMessages(conv.getId());
        assertThat(allMessages).hasSize(3);
        assertThat(allMessages.get(0).getSequenceNum()).isEqualTo(1L);
        assertThat(allMessages.get(1).getSequenceNum()).isEqualTo(2L);
        assertThat(allMessages.get(2).getSequenceNum()).isEqualTo(3L);
    }
}
