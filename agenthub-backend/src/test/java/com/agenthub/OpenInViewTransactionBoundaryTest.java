package com.agenthub;

import com.agenthub.conversation.application.ConversationApplication;
import com.agenthub.conversation.dto.ConversationView;
import com.agenthub.conversation.dto.CreateConversationCommand;
import com.agenthub.domain.conversation.model.ConversationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class OpenInViewTransactionBoundaryTest {

    @Autowired
    private Environment environment;

    @Autowired
    private ConversationApplication conversationApplication;

    @Test
    @DisplayName("验证 open-in-view 严格关闭，杜绝长事务与连接池泄漏隐患")
    void shouldVerifyOpenInViewIsExplicitlyDisabled() {
        Boolean openInView = environment.getProperty("spring.jpa.open-in-view", Boolean.class);
        assertThat(openInView).isFalse();
    }

    @Test
    @DisplayName("验证事务边界外 DTO 完全独立，无需活跃持久化上下文亦可安全读取所有字段")
    void shouldSafelyAccessAllDtoFieldsOutsideTransaction() {
        ConversationView conv = conversationApplication.createConversation(new CreateConversationCommand(
                "事务边界验证群",
                ConversationType.GROUP_COLLABORATION,
                List.of("BackendArchitect", "FrontendEngineer"),
                "proj-default"
        ));

        // Reading out of transaction boundary
        assertThat(conv.getId()).isNotEmpty();
        assertThat(conv.getTitle()).isEqualTo("事务边界验证群");
        assertThat(conv.getParticipantAgentIds()).containsExactlyInAnyOrder("BackendArchitect", "FrontendEngineer");
        assertThat(conv.getCreatedAt()).isNotNull();
    }
}
