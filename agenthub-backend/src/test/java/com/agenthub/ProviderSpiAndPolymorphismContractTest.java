package com.agenthub;

import com.agenthub.agent.domain.provider.model.*;
import com.agenthub.agent.infrastructure.provider.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ProviderSpiAndPolymorphismContractTest {

    private final MockEnvironment env = new MockEnvironment();

    @Test
    @DisplayName("测试 OpenAI 兼容适配器契约：消息抽象转换、Prompt/Completion Token 统计与响应封装")
    void shouldVerifyOpenAiCompatibleProviderContract() {
        OpenAiCompatibleProvider provider = new OpenAiCompatibleProvider(
                "test-openai",
                "OPENAI",
                "OpenAI Test",
                "https://api.openai.com/v1",
                "none",
                "gpt-4o",
                100,
                1,
                Set.of("code", "general"),
                5.0,
                15.0,
                env
        );

        assertThat(provider.getId()).isEqualTo("test-openai");
        assertThat(provider.getProviderType()).isEqualTo("OPENAI");
        assertThat(provider.getModel()).isEqualTo("gpt-4o");
        assertThat(provider.isSimulated()).isTrue();

        ChatRequest request = new ChatRequest("gpt-4o", List.of(
                ChatMessage.system("You are an expert Java architect."),
                ChatMessage.user("Generate a Spring Boot health indicator.")
        ));

        ChatResponse response = provider.chat(request);
        assertThat(response).isNotNull();
        assertThat(response.getContent()).contains("GeneratedSolution");
        assertThat(response.getUsage().getPromptTokens()).isGreaterThan(0);
        assertThat(response.getUsage().getCompletionTokens()).isGreaterThan(0);
        assertThat(response.getUsage().getTotalTokens()).isEqualTo(
                response.getUsage().getPromptTokens() + response.getUsage().getCompletionTokens()
        );
        assertThat(response.getLatencyMs()).isGreaterThanOrEqualTo(0L);
        assertThat(response.getEstimatedCost()).isGreaterThan(0.0);
    }

    @Test
    @DisplayName("测试 Anthropic Claude 适配器契约：消息分层、流式 Chunk 输出与 Token 消耗")
    void shouldVerifyAnthropicProviderContract() {
        AnthropicProvider provider = new AnthropicProvider(
                "test-anthropic",
                "ANTHROPIC",
                "Claude Test",
                "https://api.anthropic.com/v1",
                "none",
                "claude-3-5-sonnet-20241022",
                90,
                1,
                Set.of("code", "reasoning"),
                3.0,
                15.0,
                env
        );

        assertThat(provider.getCapabilities()).contains("code", "reasoning");

        ChatRequest request = ChatRequest.of("claude-3-5-sonnet-20241022", "Implement a reactive stream pipeline");
        List<ChatChunk> emittedChunks = new ArrayList<>();

        provider.streamChat(request, emittedChunks::add, null);

        assertThat(emittedChunks).isNotEmpty();
        ChatChunk finalChunk = emittedChunks.get(emittedChunks.size() - 1);
        assertThat(finalChunk.getFinishReason()).isEqualTo("stop");
        assertThat(finalChunk.getUsage()).isNotNull();
        assertThat(finalChunk.getUsage().getTotalTokens()).isGreaterThan(0);
    }

    @Test
    @DisplayName("测试 Google Gemini 适配器契约：多模态/推理模型支持与健康检查")
    void shouldVerifyGeminiProviderContract() {
        GeminiProvider provider = new GeminiProvider(
                "test-gemini",
                "GEMINI",
                "Gemini Test",
                "https://generativelanguage.googleapis.com",
                "none",
                "gemini-1.5-flash",
                85,
                1,
                Set.of("general", "fast", "long_context"),
                0.075,
                0.30,
                env
        );

        ProviderHealth health = provider.checkHealth();
        assertThat(health).isNotNull();
        assertThat(health.isAvailable()).isTrue();

        ChatRequest request = ChatRequest.of("gemini-1.5-flash", "Summarize architecture tradeoffs");
        ChatResponse response = provider.chat(request);

        assertThat(response.getContent()).contains("Gemini");
        assertThat(response.getEstimatedCost()).isGreaterThanOrEqualTo(0.0);
    }

    @Test
    @DisplayName("测试 本地 Ollama 适配器契约：零成本模型定价与原生本地执行")
    void shouldVerifyOllamaProviderContract() {
        OllamaProvider provider = new OllamaProvider(
                "test-ollama",
                "OLLAMA",
                "Local Llama",
                "http://localhost:11434",
                "none",
                "llama3.2:3b",
                50,
                1,
                Set.of("code", "local"),
                0.0,
                0.0,
                env
        );

        ChatRequest request = ChatRequest.of("llama3.2:3b", "Generate SQL schema");
        ChatResponse response = provider.chat(request);

        assertThat(response.getContent()).contains("Local Llama");
        assertThat(response.getEstimatedCost()).isEqualTo(0.0); // Zero cost for local models
    }

    @Test
    @DisplayName("测试 Mock 适配器多态契约：完全可控的模拟输出与延迟验证")
    void shouldVerifyMockProviderContract() {
        MockLlmProvider provider = new MockLlmProvider(
                "test-mock",
                "Mock Engine",
                "mock-v1",
                10,
                1,
                Set.of("code", "general")
        );

        provider.setSimulatedTokens(100, 200);
        provider.setSimulatedLatencyMs(25);

        ChatResponse response = provider.chat(ChatRequest.of("mock-v1", "Test task"));
        assertThat(response.getUsage().getPromptTokens()).isEqualTo(100);
        assertThat(response.getUsage().getCompletionTokens()).isEqualTo(200);
        assertThat(response.getUsage().getTotalTokens()).isEqualTo(300);
        assertThat(response.getLatencyMs()).isGreaterThanOrEqualTo(20L);
    }

    @Test
    @DisplayName("测试 极端空值与空消息防御：所有 Provider 均能防御 null 消息、null 角色与 null 内容，杜绝 NPE")
    void shouldHandleNullAndEmptyMessagesWithoutNpeAcrossAllProviders() {
        ChatMessage msgWithNulls = new ChatMessage();
        msgWithNulls.setRole(null);
        msgWithNulls.setContent(null);

        ChatRequest edgeRequest = new ChatRequest();
        edgeRequest.setMessages(List.of(msgWithNulls));

        // 1. OpenAI Compatible
        OpenAiCompatibleProvider openAi = new OpenAiCompatibleProvider("test-oai", "OPENAI", "OAI", "https://api.openai.com", "none", "gpt-4o", 100, 1, Set.of("general"), 5.0, 15.0, env);
        ChatResponse resp1 = openAi.chat(edgeRequest);
        assertThat(resp1).isNotNull();

        // 2. Anthropic
        AnthropicProvider anthropic = new AnthropicProvider("test-anth", "ANTHROPIC", "Anth", "https://api.anthropic.com", "none", "claude-3-5-sonnet", 90, 1, Set.of("general"), 3.0, 15.0, env);
        ChatResponse resp2 = anthropic.chat(edgeRequest);
        assertThat(resp2).isNotNull();

        // 3. Gemini
        GeminiProvider gemini = new GeminiProvider("test-gem", "GEMINI", "Gem", "https://generativelanguage.googleapis.com", "none", "gemini-1.5-flash", 80, 1, Set.of("general"), 0.075, 0.3, env);
        ChatResponse resp3 = gemini.chat(edgeRequest);
        assertThat(resp3).isNotNull();

        // 4. Ollama
        OllamaProvider ollama = new OllamaProvider("test-oll", "OLLAMA", "Oll", "http://localhost:11434", "none", "llama3.2:3b", 50, 1, Set.of("general"), 0.0, 0.0, env);
        ChatResponse resp4 = ollama.chat(edgeRequest);
        assertThat(resp4).isNotNull();
    }
}
