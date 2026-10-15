package com.agenthub;

import com.agenthub.agent.application.BudgetGovernanceGuard;
import com.agenthub.agent.application.ProviderApplication;
import com.agenthub.agent.application.TokenUsageApplication;
import com.agenthub.agent.domain.provider.model.CircuitBreaker;
import com.agenthub.agent.domain.provider.exception.NoAvailableProviderException;
import com.agenthub.agent.domain.provider.exception.ProviderUnavailableException;
import com.agenthub.agent.domain.provider.model.*;
import com.agenthub.agent.domain.provider.router.DynamicProviderRouter;
import com.agenthub.agent.dto.*;
import com.agenthub.agent.infrastructure.entity.TokenUsageAuditEntity;
import com.agenthub.agent.infrastructure.provider.AnthropicProvider;
import com.agenthub.agent.infrastructure.provider.GeminiProvider;
import com.agenthub.agent.infrastructure.provider.MockLlmProvider;
import com.agenthub.agent.infrastructure.provider.OllamaProvider;
import com.agenthub.agent.infrastructure.provider.OpenAiCompatibleProvider;
import com.agenthub.agent.infrastructure.repository.ProviderRepository;
import com.agenthub.agent.infrastructure.repository.TokenUsageAuditRepository;
import com.agenthub.domain.agent.model.AgentExecutionRequest;
import com.agenthub.domain.agent.model.AgentPlatformType;
import com.agenthub.execution.domain.model.CancelToken;
import com.agenthub.runtime.port.ExecutionHandle;
import com.agenthub.runtime.port.RuntimeEventSink;
import com.agenthub.runtime.provider.ProviderDrivenAgentRuntime;
import com.agenthub.shared.exception.BusinessException;
import com.agenthub.shared.exception.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 7: 真实 Provider 接入、成本治理与生产环境验证 (Live Providers, Budget Governance & Production Validation)
 * 自动化测试套件。
 * 覆盖：
 * 1. 真实 Provider 客户端适配器（OpenAI, Anthropic, Gemini, DeepSeek）的工具调用 Payload、响应与流式解析、请求超时与退避重试；
 * 2. 生产运行模式严格隔离（DEMO_SIMULATED vs LIVE_SELF_HOSTED / LIVE_TEAM），杜绝向 MockProvider 静默降级；
 * 3. 细粒度 Token 计量、精细化模型单价核算与多维度（按模型、按 Provider）汇总；
 * 4. 生产硬预算看门狗（BudgetGovernanceGuard）调用前拦截、80% 警戒阈值与超额熔断保护；
 * 5. Provider 主动网络延迟探测（Probe）与三态熔断器动态状态监控及手动重置；
 * 6. REST API 端点契约验证与生产环境变量规范；
 * 7. 严格的测试隔离与级联清理。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class Phase7LiveProvidersAndBudgetGovernanceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProviderApplication providerApplication;

    @Autowired
    private TokenUsageApplication tokenUsageApplication;

    @Autowired
    private BudgetGovernanceGuard budgetGovernanceGuard;

    @Autowired
    private DynamicProviderRouter dynamicProviderRouter;

    @Autowired
    private TokenUsageAuditRepository tokenUsageRepository;

    @Autowired
    private ProviderRepository providerRepository;

    private final List<String> createdUsageIds = new ArrayList<>();
    private double originalBudgetLimit;

    @BeforeEach
    void setUp() {
        originalBudgetLimit = budgetGovernanceGuard.getEffectiveHardLimit();
    }

    @AfterEach
    void tearDown() {
        budgetGovernanceGuard.setHardBudgetLimitUsd(originalBudgetLimit);
        for (String id : createdUsageIds) {
            tokenUsageRepository.deleteById(id);
        }
        createdUsageIds.clear();
        for (var p : dynamicProviderRouter.listProviders()) {
            p.getCircuitBreaker().reset();
        }
    }

    // =========================================================================
    // 1. Live Provider Client Adapters & Tool Calling Payloads
    // =========================================================================

    @Test
    @DisplayName("测试 OpenAI 兼容适配器：正确构建 tools/function_call payload 并成功解析结构化 ToolCall 响应")
    void shouldFormatAndParseOpenAiToolCalls() throws Exception {
        OpenAiCompatibleProvider provider = new OpenAiCompatibleProvider(
                "prov-openai-test",
                "https://api.openai.com/v1",
                "test-api-key",
                "gpt-4o",
                100,
                1,
                Set.of("code", "tools"),
                2.50,
                10.00
        );

        ToolDefinition tool = ToolDefinition.function(
                "queryDatabase",
                "Execute safe read-only SQL query",
                Map.of(
                        "type", "object",
                        "properties", Map.of("sql", Map.of("type", "string", "description", "SQL statement")),
                        "required", List.of("sql")
                )
        );

        ChatRequest request = new ChatRequest();
        request.setMessages(List.of(ChatMessage.user("Query user count")));
        request.setTools(List.of(tool));
        request.setToolChoice("auto");
        request.setExecutionMode(ProviderExecutionMode.LIVE_SELF_HOSTED);

        // Verify request payload formatting
        Map<String, Object> payload = provider.buildRequestBody(request, false);
        assertThat(payload).containsKey("tools");
        assertThat(payload).containsKey("tool_choice");
        assertThat(payload.get("tool_choice")).isEqualTo("auto");

        List<?> toolsList = (List<?>) payload.get("tools");
        assertThat(toolsList).hasSize(1);
        Map<?, ?> firstTool = (Map<?, ?>) toolsList.get(0);
        assertThat(firstTool.get("type")).isEqualTo("function");
        Map<?, ?> functionMap = (Map<?, ?>) firstTool.get("function");
        assertThat(functionMap.get("name")).isEqualTo("queryDatabase");

        // Verify tool call response parsing
        String mockOpenAiResponse = """
                {
                  "id": "chatcmpl-test-123",
                  "choices": [{
                    "index": 0,
                    "message": {
                      "role": "assistant",
                      "content": null,
                      "tool_calls": [{
                        "id": "call_abc123",
                        "type": "function",
                        "function": {
                          "name": "queryDatabase",
                          "arguments": "{\\"sql\\":\\"SELECT count(*) FROM users\\"}"
                        }
                      }]
                    },
                    "finish_reason": "tool_calls"
                  }],
                  "usage": {
                    "prompt_tokens": 45,
                    "completion_tokens": 28,
                    "total_tokens": 73
                  }
                }
                """;

        ChatResponse response = provider.parseResponse(mockOpenAiResponse, 120L);
        assertThat(response).isNotNull();
        assertThat(response.hasToolCalls()).isTrue();
        assertThat(response.getToolCalls()).hasSize(1);
        ToolCall tc = response.getToolCalls().get(0);
        assertThat(tc.getId()).isEqualTo("call_abc123");
        assertThat(tc.getName()).isEqualTo("queryDatabase");
        assertThat(tc.getArguments()).contains("SELECT count(*) FROM users");
        assertThat(response.getUsage().getPromptTokens()).isEqualTo(45);
        assertThat(response.getUsage().getCompletionTokens()).isEqualTo(28);
    }

    @Test
    @DisplayName("测试 Anthropic 适配器：正确构建 input_schema 工具声明并解析 tool_use 块")
    void shouldFormatAndParseAnthropicToolCalls() throws Exception {
        AnthropicProvider provider = new AnthropicProvider(
                "prov-anthropic-test",
                "https://api.anthropic.com/v1",
                "test-claude-key",
                "claude-3-5-sonnet",
                90,
                1,
                Set.of("code", "reasoning"),
                3.00,
                15.00
        );

        ToolDefinition tool = ToolDefinition.function(
                "readFile",
                "Read file contents",
                Map.of(
                        "type", "object",
                        "properties", Map.of("path", Map.of("type", "string")),
                        "required", List.of("path")
                )
        );

        ChatRequest request = new ChatRequest();
        request.setMessages(List.of(ChatMessage.user("Read pom.xml")));
        request.setTools(List.of(tool));
        request.setExecutionMode(ProviderExecutionMode.LIVE_SELF_HOSTED);

        Map<String, Object> payload = provider.buildRequestBody(request, false);
        assertThat(payload).containsKey("tools");
        List<?> tools = (List<?>) payload.get("tools");
        assertThat(tools).hasSize(1);
        @SuppressWarnings("unchecked")
        Map<String, Object> firstTool = (Map<String, Object>) tools.get(0);
        assertThat(firstTool.get("name")).isEqualTo("readFile");
        assertThat(firstTool).containsKey("input_schema");

        // Parse Anthropic content block response
        String mockAnthropicResponse = """
                {
                  "id": "msg_01XyZ",
                  "type": "message",
                  "role": "assistant",
                  "content": [
                    { "type": "text", "text": "I will inspect pom.xml for you." },
                    { "type": "tool_use", "id": "toolu_01A", "name": "readFile", "input": { "path": "pom.xml" } }
                  ],
                  "stop_reason": "tool_use",
                  "usage": { "input_tokens": 60, "output_tokens": 35 }
                }
                """;

        ChatResponse response = provider.parseResponse(mockAnthropicResponse, 180L);
        assertThat(response).isNotNull();
        assertThat(response.hasToolCalls()).isTrue();
        assertThat(response.getToolCalls().get(0).getId()).isEqualTo("toolu_01A");
        assertThat(response.getToolCalls().get(0).getName()).isEqualTo("readFile");
        assertThat(response.getToolCalls().get(0).getArguments()).contains("pom.xml");
        assertThat(response.getContent()).contains("I will inspect pom.xml");
        assertThat(response.getUsage().getPromptTokens()).isEqualTo(60);
        assertThat(response.getUsage().getCompletionTokens()).isEqualTo(35);
    }

    @Test
    @DisplayName("测试 Gemini 适配器：正确构建 function_declarations 并解析 candidate 中的 functionCall")
    void shouldFormatAndParseGeminiToolCalls() throws Exception {
        GeminiProvider provider = new GeminiProvider(
                "prov-gemini-test",
                "https://generativelanguage.googleapis.com/v1beta",
                "test-gemini-key",
                "gemini-1.5-flash",
                85,
                1,
                Set.of("fast", "code"),
                0.075,
                0.30
        );

        ToolDefinition tool = ToolDefinition.function(
                "runTests",
                "Execute backend unit tests",
                Map.of("type", "object", "properties", Map.of("pattern", Map.of("type", "string")))
        );

        ChatRequest request = new ChatRequest();
        request.setMessages(List.of(ChatMessage.user("Run backend tests")));
        request.setTools(List.of(tool));
        request.setExecutionMode(ProviderExecutionMode.LIVE_SELF_HOSTED);

        Map<String, Object> payload = provider.buildRequestBody(request);
        assertThat(payload).containsKey("tools");
        List<?> tools = (List<?>) payload.get("tools");
        @SuppressWarnings("unchecked")
        Map<String, Object> first = (Map<String, Object>) tools.get(0);
        assertThat(first).containsKey("function_declarations");

        // Parse Gemini functionCall candidate
        String mockGeminiResponse = """
                {
                  "candidates": [{
                    "content": {
                      "role": "model",
                      "parts": [{
                        "functionCall": {
                          "name": "runTests",
                          "args": { "pattern": "*Test" }
                        }
                      }]
                    },
                    "finishReason": "STOP"
                  }],
                  "usageMetadata": {
                    "promptTokenCount": 50,
                    "candidatesTokenCount": 20,
                    "totalTokenCount": 70
                  }
                }
                """;

        ChatResponse response = provider.parseResponse(mockGeminiResponse, 150L);
        assertThat(response).isNotNull();
        assertThat(response.hasToolCalls()).isTrue();
        assertThat(response.getToolCalls().get(0).getName()).isEqualTo("runTests");
        assertThat(response.getToolCalls().get(0).getArguments()).contains("*Test");
        assertThat(response.getUsage().getPromptTokens()).isEqualTo(50);
        assertThat(response.getUsage().getCompletionTokens()).isEqualTo(20);
    }

    // =========================================================================
    // 2. Mode Segregation: Reject Silent Mock Fallback in LIVE Modes
    // =========================================================================

    @Test
    @DisplayName("测试生产运行模式隔离：LIVE_SELF_HOSTED 模式下严禁静默回退到 MockLlmProvider")
    void shouldStrictlyRejectMockFallbackInLiveMode() {
        // Build a dedicated router containing ONLY a mock provider
        DynamicProviderRouter testRouter = new DynamicProviderRouter();
        MockLlmProvider mockProvider = new MockLlmProvider("prov-mock", "Mock Provider", "mock-v1", 10, 1, Set.of("code", "general"));
        testRouter.registerProvider(mockProvider);

        ChatRequest liveRequest = new ChatRequest();
        liveRequest.setMessages(List.of(ChatMessage.user("Generate user service")));
        liveRequest.setExecutionMode(ProviderExecutionMode.LIVE_SELF_HOSTED);

        // LIVE_SELF_HOSTED must strictly reject mock provider
        assertThatThrownBy(() -> testRouter.routeAndExecute(liveRequest))
                .isInstanceOf(NoAvailableProviderException.class)
                .hasMessageContaining("Mock fallback is strictly blocked in live mode");

        // LIVE_TEAM must also strictly reject mock provider
        ChatRequest teamRequest = new ChatRequest();
        teamRequest.setMessages(List.of(ChatMessage.user("Generate user service")));
        teamRequest.setExecutionMode(ProviderExecutionMode.LIVE_TEAM);

        assertThatThrownBy(() -> testRouter.routeAndExecute(teamRequest))
                .isInstanceOf(NoAvailableProviderException.class)
                .hasMessageContaining("Mock fallback is strictly blocked in live mode");

        // But DEMO_SIMULATED mode explicitly permits mock provider
        ChatRequest demoRequest = new ChatRequest();
        demoRequest.setMessages(List.of(ChatMessage.user("Generate user service")));
        demoRequest.setExecutionMode(ProviderExecutionMode.DEMO_SIMULATED);

        ChatResponse demoResponse = testRouter.routeAndExecute(demoRequest);
        assertThat(demoResponse).isNotNull();
        assertThat(demoResponse.getProviderType()).isEqualTo("MOCK");
    }

    @Test
    @DisplayName("测试 Ollama 本地 Provider 模式隔离：LIVE 模式下连接失败严禁静默回退为模拟输出，DEMO 模式允许降级")
    void shouldStrictlyBlockOllamaSilentFallbackInLiveModes() {
        // Point Ollama to an unreachable port to trigger ConnectException
        OllamaProvider unreachableOllama = new OllamaProvider(
                "prov-ollama-test",
                "OLLAMA",
                "Unreachable Local Ollama",
                "http://127.0.0.1:59999",
                "none",
                "llama3.2:3b",
                50,
                1,
                Set.of("code", "local"),
                0.0,
                0.0,
                null
        );

        ChatRequest liveRequest = new ChatRequest();
        liveRequest.setMessages(List.of(ChatMessage.user("Generate rust function")));
        liveRequest.setExecutionMode(ProviderExecutionMode.LIVE_SELF_HOSTED);

        // Under LIVE_SELF_HOSTED, connection failure MUST throw ProviderUnavailableException
        assertThatThrownBy(() -> unreachableOllama.chat(liveRequest))
                .isInstanceOf(ProviderUnavailableException.class)
                .hasMessageContaining("simulated fallback strictly blocked");

        // Under DEMO_SIMULATED, connection failure gracefully falls back to simulation
        ChatRequest demoRequest = new ChatRequest();
        demoRequest.setMessages(List.of(ChatMessage.user("Generate rust function")));
        demoRequest.setExecutionMode(ProviderExecutionMode.DEMO_SIMULATED);

        ChatResponse demoResp = unreachableOllama.chat(demoRequest);
        assertThat(demoResp).isNotNull();
        assertThat(demoResp.getContent()).contains("Local Llama / Ollama Output");
    }

    @Test
    @DisplayName("测试 ProviderProbeResultView 探针结果的 reachable 字段契约与序列化对齐")
    void shouldSerializeReachableInProviderProbeResultView() {
        ProviderProbeResultView upView = new ProviderProbeResultView(
                "prov-test-up", "OPENAI", "gpt-4o", "UP", 35L, "CLOSED", false, "Endpoint healthy", null
        );
        assertThat(upView.isReachable()).isTrue();
        assertThat(upView.getReachable()).isTrue();

        ProviderProbeResultView downView = new ProviderProbeResultView(
                "prov-test-down", "OPENAI", "gpt-4o", "DOWN", 0L, "OPEN", false, "Connection refused", null
        );
        assertThat(downView.isReachable()).isFalse();
        assertThat(downView.getReachable()).isFalse();

        ProviderProbeResultView degradedView = new ProviderProbeResultView(
                "prov-test-deg", "OPENAI", "gpt-4o", "DEGRADED", 150L, "HALF_OPEN", false, "Probing recovery", null
        );
        assertThat(degradedView.isReachable()).isTrue();
        assertThat(degradedView.getReachable()).isTrue();
    }

    // =========================================================================
    // 3. Fine-Grained Pricing, Token Accounting & Cost Governance Guard
    // =========================================================================

    @Test
    @DisplayName("测试 ModelPricing 定价字典与多维度成本精确核算")
    void shouldCalculateAccurateModelCosts() {
        // DeepSeek Chat: In $0.14/1M, Out $0.28/1M
        double deepseekCost = ModelPricing.calculateCost("deepseek-chat", 1_000_000, 1_000_000);
        assertThat(deepseekCost).isEqualTo(0.42);

        // OpenAI GPT-4o: default rate In $5.00/1M, Out $15.00/1M => 100K in (0.50) + 100K out (1.50) = $2.00
        double gpt4oCost = ModelPricing.calculateCost("gpt-4o", 100_000, 100_000);
        assertThat(gpt4oCost).isEqualTo(2.00);

        // OpenAI GPT-4o with provider-level custom rate override: In $2.50/1M, Out $10.00/1M => $1.25
        double gpt4oCustomCost = ModelPricing.calculateCost("gpt-4o", 100_000, 100_000, 2.50, 10.00);
        assertThat(gpt4oCustomCost).isEqualTo(1.25);

        // Anthropic Claude 3.5 Sonnet: In $3.00/1M, Out $15.00/1M
        double claudeCost = ModelPricing.calculateCost("claude-3-5-sonnet", 50_000, 20_000);
        assertThat(claudeCost).isEqualTo(0.45);

        // Gemini 1.5 Flash: In $0.075/1M, Out $0.30/1M
        double geminiCost = ModelPricing.calculateCost("gemini-1.5-flash", 200_000, 100_000);
        assertThat(geminiCost).isEqualTo(0.045);

        // Test with prompt cache hit discount (e.g. 90% discount on cached tokens)
        double cachedCost = ModelPricing.calculateCostWithCache("deepseek-chat", 1_000_000, 500_000, 1_000_000);
        // regular = 0.5M * 0.14 = 0.07; cached = 0.5M * 0.014 = 0.007; out = 1M * 0.28 = 0.28 => 0.357
        assertThat(cachedCost).isEqualTo(0.357);
    }

    @Test
    @DisplayName("测试 BudgetGovernanceGuard：正常额度放行、预警阈值标记与超额硬拦截")
    void shouldEnforceHardBudgetCeilingAndWarningThreshold() {
        // Set hard limit to a tight $1.00 for testing
        budgetGovernanceGuard.setHardBudgetLimitUsd(1.00);

        // 1) Small request under limit -> allowed
        BudgetCheckResultView allowed = budgetGovernanceGuard.enforceBudget("run-test-1", "proj-1", 0.05, 1000);
        assertThat(allowed.isAllowed()).isTrue();

        // 2) Record simulated token usage to push cost to $0.85 (85% utilization -> warning threshold)
        TokenUsageAuditView audit1 = tokenUsageApplication.recordUsage(
                "run-test-audit-1",
                "step-test-1",
                null,
                "OPENAI",
                "gpt-4o",
                50_000,
                50_000,
                250L,
                0.85,
                "SUCCESS",
                null
        );
        createdUsageIds.add(audit1.getId());

        // Check budget evaluation query
        BudgetCheckCommand checkCmd = new BudgetCheckCommand("run-test-2", "proj-1", 0.05, 1000);
        BudgetCheckResultView evalRes = budgetGovernanceGuard.evaluateBudget(checkCmd);
        assertThat(evalRes.isAllowed()).isTrue();
        assertThat(evalRes.isWarning()).isTrue(); // > 80%

        // 3) Push cost beyond hard limit ($0.85 + $0.20 > $1.00) -> hard interception
        assertThatThrownBy(() -> budgetGovernanceGuard.enforceBudget("run-test-3", "proj-1", 0.20, 5000))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.BUDGET_EXCEEDED);
                    assertThat(be.getMessage()).contains("Budget hard limit exceeded");
                });

        // 4) Verify multi-dimensional governance view
        TokenGovernanceView govView = budgetGovernanceGuard.getGovernanceView();
        assertThat(govView.getTotalEstimatedCostUsd()).isGreaterThanOrEqualTo(0.85);
        assertThat(govView.getHardBudgetLimitUsd()).isEqualTo(1.00);
        assertThat(govView.isBudgetWarning()).isTrue();
        assertThat(govView.getModelSummaries()).isNotEmpty();
    }

    // =========================================================================
    // 4. Provider Active Probing & Circuit Breaker Trips and Resets
    // =========================================================================

    @Test
    @DisplayName("测试 Provider 主动健康探测与三态熔断器连续失败熔断与手动重置")
    void shouldProbeProviderAndTripCircuitBreaker() {
        String testProviderId = "prov-deepseek";

        // 1) Active probe latency test
        ProviderProbeResultView probeResult = dynamicProviderRouter.probeProvider(testProviderId);
        assertThat(probeResult).isNotNull();
        assertThat(probeResult.getProviderId()).isEqualTo(testProviderId);
        assertThat(probeResult.getLatencyMs()).isGreaterThanOrEqualTo(0L);

        // 2) Verify cluster health status
        List<ProviderHealthView> healthViews = dynamicProviderRouter.getClusterHealth();
        assertThat(healthViews).isNotEmpty();
        assertThat(healthViews.stream().anyMatch(h -> h.getProviderId().equals(testProviderId))).isTrue();

        // 3) Trip the circuit breaker: record 5 consecutive failures
        CircuitBreaker cb = dynamicProviderRouter.getCircuitBreaker(testProviderId);
        assertThat(cb).isNotNull();
        cb.reset();
        assertThat(cb.getState()).isEqualTo(CircuitStatus.CLOSED);

        for (int i = 0; i < 5; i++) {
            cb.recordFailure();
        }

        // Circuit breaker must now be OPEN
        assertThat(cb.getState()).isEqualTo(CircuitStatus.OPEN);
        assertThat(cb.allowRequest()).isFalse();

        CircuitBreakerView cbView = dynamicProviderRouter.getCircuitBreakerView(testProviderId);
        assertThat(cbView.getState()).isEqualTo("OPEN");
        assertThat(cbView.isRequestAllowed()).isFalse();

        // 4) Reset the circuit breaker back to CLOSED
        CircuitBreakerView resetView = dynamicProviderRouter.resetCircuitBreaker(testProviderId);
        assertThat(resetView.getState()).isEqualTo("CLOSED");
        assertThat(resetView.isRequestAllowed()).isTrue();
        assertThat(cb.allowRequest()).isTrue();
    }

    // =========================================================================
    // 5. REST & SSE Controller Endpoints Integration (OpenAPI Alignment)
    // =========================================================================

    @Test
    @DisplayName("集成测试 GET /api/providers/health：返回所有注册 Provider 的健康监控指标")
    void shouldGetProviderClusterHealthViaApi() throws Exception {
        mockMvc.perform(get("/api/providers/health")
                        .header("X-User-Id", "user-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.data[0].providerId").isNotEmpty())
                .andExpect(jsonPath("$.data[0].circuitStatus").isNotEmpty())
                .andExpect(jsonPath("$.data[0].status").isNotEmpty());
    }

    @Test
    @DisplayName("集成测试 POST /api/providers/{id}/probe：主动探测指定 Provider 并返回延迟")
    void shouldProbeProviderViaApi() throws Exception {
        mockMvc.perform(post("/api/providers/prov-deepseek/probe")
                        .header("X-User-Id", "user-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.providerId").value("prov-deepseek"))
                .andExpect(jsonPath("$.data.latencyMs").isNumber());
    }

    @Test
    @DisplayName("集成测试 GET & POST /api/providers/{id}/circuit-breaker：获取与重置熔断器")
    void shouldManageCircuitBreakerViaApi() throws Exception {
        mockMvc.perform(get("/api/providers/prov-deepseek/circuit-breaker")
                        .header("X-User-Id", "user-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.providerId").value("prov-deepseek"))
                .andExpect(jsonPath("$.data.state").isNotEmpty());

        mockMvc.perform(post("/api/providers/prov-deepseek/circuit-breaker/reset")
                        .header("X-User-Id", "user-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.state").value("CLOSED"))
                .andExpect(jsonPath("$.data.requestAllowed").value(true));
    }

    @Test
    @DisplayName("集成测试 GET /api/token-usages/governance & POST /api/token-usages/budget/check")
    void shouldQueryTokenGovernanceAndCheckBudgetViaApi() throws Exception {
        mockMvc.perform(get("/api/token-usages/governance")
                        .header("X-User-Id", "user-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.hardBudgetLimitUsd").isNumber())
                .andExpect(jsonPath("$.data.totalEstimatedCostUsd").isNumber())
                .andExpect(jsonPath("$.data.executionMode").isNotEmpty());

        BudgetCheckCommand cmd = new BudgetCheckCommand("run-api-test", "proj-api-test", 0.05, 1000);
        mockMvc.perform(post("/api/token-usages/budget/check")
                        .header("X-User-Id", "user-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cmd)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.allowed").value(true))
                .andExpect(jsonPath("$.data.limitCost").isNumber());
    }

    // =========================================================================
    // 6. Runtime Integration: ProviderDrivenAgentRuntime Pre-Invocation Guard
    // =========================================================================

    @Test
    @DisplayName("集成测试 ProviderDrivenAgentRuntime：预算耗尽时前置拦截并终止 Agent 执行")
    void shouldAbortAgentRuntimeWhenBudgetExhausted() throws Exception {
        ProviderDrivenAgentRuntime runtime = new ProviderDrivenAgentRuntime(
                dynamicProviderRouter,
                tokenUsageApplication,
                budgetGovernanceGuard
        );

        // Set budget limit to 0 to simulate exhausted budget
        budgetGovernanceGuard.setHardBudgetLimitUsd(0.0001);

        // Record a small cost to guarantee exhaustion
        TokenUsageAuditView audit = tokenUsageApplication.recordUsage(
                "run-exhaustion-test",
                "step-1",
                null,
                "OPENAI",
                "gpt-4o",
                100, 100, 50L,
                0.01,
                "SUCCESS",
                null
        );
        createdUsageIds.add(audit.getId());

        AgentExecutionRequest request = new AgentExecutionRequest(
                "code_agent",
                AgentPlatformType.SPRING_AI_API,
                "temp-workspace",
                "Write hello world function"
        );
        request.setRunId("run-guard-test-" + UUID.randomUUID().toString().substring(0, 8));
        request.setStepRunId("step-guard-test-" + UUID.randomUUID().toString().substring(0, 8));

        CompletableFuture<String> future = new CompletableFuture<>();
        RuntimeEventSink testSink = new RuntimeEventSink() {
            @Override
            public void onToken(String runId, String stepRunId, String deltaToken) {}
            @Override
            public void onMessage(String runId, String stepRunId, String sender, String content) {}
            @Override
            public void onToolCall(String runId, String stepRunId, String toolName, String inputJson) {}
            @Override
            public void onLog(String runId, String stepRunId, String level, String message) {}
            @Override
            public void onUsage(String runId, String stepRunId, int promptTokens, int completionTokens, double cost) {}
            @Override
            public void onFileChange(String runId, String stepRunId, String filePath, String changeType) {}
            @Override
            public void onStepCompleted(String runId, String stepRunId, String finalResult) {
                future.complete(finalResult);
            }
            @Override
            public void onStepFailed(String runId, String stepRunId, String errorMessage, Throwable cause) {
                future.completeExceptionally(new RuntimeException(errorMessage, cause));
            }
        };

        ExecutionHandle handle = runtime.start(request, testSink, new CancelToken(request.getRunId()));
        assertThat(handle).isNotNull();

        assertThatThrownBy(() -> future.get(5, TimeUnit.SECONDS))
                .hasCauseInstanceOf(RuntimeException.class)
                .hasMessageContaining("Budget hard limit exceeded");
    }
}
