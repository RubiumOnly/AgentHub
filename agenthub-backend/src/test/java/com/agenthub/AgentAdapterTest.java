package com.agenthub;

import com.agenthub.domain.agent.model.AgentExecutionRequest;
import com.agenthub.domain.agent.model.AgentExecutionResult;
import com.agenthub.domain.agent.model.AgentPlatformType;
import com.agenthub.domain.agent.service.AgentAdapterFactory;
import com.agenthub.domain.agent.spi.UnifiedAgentAdapter;
import com.agenthub.infrastructure.adapter.CliProcessAdapter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AgentAdapterTest {

    @Autowired
    private AgentAdapterFactory adapterFactory;

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("工厂能正确解析并返回每种平台类型的适配器")
    void shouldResolveAdaptersForAllPlatforms() {
        for (AgentPlatformType type : AgentPlatformType.values()) {
            UnifiedAgentAdapter adapter = adapterFactory.getAdapter(type);
            assertThat(adapter).isNotNull();
        }
    }

    @Test
    @DisplayName("测试敏感 API Key 脱敏过滤器有效性")
    void shouldSanitizeApiKeysFromOutput() {
        String rawWithKey = "Connecting using key sk-1234567890abcdef123456 to endpoint.";
        String sanitized = CliProcessAdapter.sanitizeOutput(rawWithKey);
        assertThat(sanitized).doesNotContain("sk-1234567890abcdef123456");
        assertThat(sanitized).contains("[REDACTED_SECRET]");
    }

    @Test
    @DisplayName("当宿主机缺少 CLI 时应优雅降级为安全模拟模式而不会崩溃抛错")
    void shouldGracefullyFallbackWhenCliMissing() {
        UnifiedAgentAdapter claudeAdapter = adapterFactory.getAdapter(AgentPlatformType.CLAUDE_CODE);
        AgentExecutionRequest request = new AgentExecutionRequest(
                "agent-test-1",
                AgentPlatformType.CLAUDE_CODE,
                "./target/test-workspaces/agent-test",
                "Generate a quick test spec"
        );
        AgentExecutionResult result = claudeAdapter.execute(request);
        assertThat(result).isNotNull();
        assertThat(result.getOutput()).isNotEmpty();
    }

    @Test
    @DisplayName("测试 SpringAiApiAdapter 在无有效 API Key 时优雅降级为模拟模式并正确标记 degraded")
    void shouldDegradeGracefullyWhenSpringAiKeyNotConfigured() {
        UnifiedAgentAdapter springAiAdapter = adapterFactory.getAdapter(AgentPlatformType.SPRING_AI_API);
        AgentExecutionRequest request = new AgentExecutionRequest(
                "agent-springai-1",
                AgentPlatformType.SPRING_AI_API,
                "./target/test-workspaces/agent-springai",
                "Generate authentication service"
        );
        AgentExecutionResult result = springAiAdapter.execute(request);
        assertThat(result).isNotNull();
        assertThat(result.isDegraded()).isTrue();
        assertThat(result.getStatus()).isEqualTo(AgentExecutionResult.Status.DEGRADED);
        assertThat(result.isSimulated()).isTrue();
        assertThat(result.getDurationMs()).isGreaterThanOrEqualTo(0);
        assertThat(result.getErrorDetails()).contains("fallback");
        assertThat(result.getOutput()).isNotEmpty();
    }

    @Test
    @DisplayName("GET /api/agents/platforms 应返回包含状态的平台列表")
    void shouldReturnPlatformListViaHttp() throws Exception {
        mockMvc.perform(get("/api/agents/platforms").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].code").isNotEmpty());
    }
}
