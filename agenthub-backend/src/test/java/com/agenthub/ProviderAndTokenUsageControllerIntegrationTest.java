package com.agenthub;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProviderAndTokenUsageControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("集成测试 GET /api/providers：成功列出所有已配置 Provider 并确认 API Key 已严格脱敏")
    void shouldListProvidersWithRedactedSecrets() throws Exception {
        mockMvc.perform(get("/api/providers")
                        .header("X-User-Id", "user-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(5))))
                .andExpect(jsonPath("$.data[0].id").isNotEmpty())
                .andExpect(jsonPath("$.data[0].circuitStatus").isNotEmpty())
                .andExpect(jsonPath("$.data[0].secretRef", not(containsString("raw-secret-value"))));
    }

    @Test
    @DisplayName("集成测试 POST /api/providers：成功注册新的模型 Provider 并生效到路由矩阵")
    void shouldRegisterNewProviderSuccessfully() throws Exception {
        Map<String, Object> payload = Map.of(
                "providerType", "OPENAI",
                "baseUrl", "https://api.openai.com/v1",
                "secretRef", "env:CUSTOM_KEY",
                "model", "gpt-4o-mini",
                "priority", 88,
                "weight", 2,
                "capabilities", "code,fast",
                "costPerMillionInput", 0.15,
                "costPerMillionOutput", 0.60
        );

        mockMvc.perform(post("/api/providers")
                        .header("X-User-Id", "user-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andExpect(jsonPath("$.data.model").value("gpt-4o-mini"))
                .andExpect(jsonPath("$.data.priority").value(88));
    }

    @Test
    @DisplayName("集成测试 POST /api/providers/route：根据请求要求智能推演并返回最佳 Provider 路由决策")
    void shouldEvaluateRouteDecisionAccurately() throws Exception {
        Map<String, Object> payload = Map.of(
                "model", "gpt-4o",
                "messages", new Object[]{
                        Map.of("role", "user", "content", "Test routing evaluation")
                },
                "requiredCapabilities", new String[]{"code"}
        );

        mockMvc.perform(post("/api/providers/route")
                        .header("X-User-Id", "user-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.primaryProviderId").isNotEmpty())
                .andExpect(jsonPath("$.data.candidateProviderIds", hasSize(greaterThan(0))));
    }

    @Test
    @DisplayName("集成测试 POST /api/providers/{id}/test：对指定 Provider 发起轻量 Ping 连通性测试并返回延迟与耗时")
    void shouldTestProviderConnectivitySuccessfully() throws Exception {
        mockMvc.perform(post("/api/providers/prov-ollama/test")
                        .header("X-User-Id", "user-1")
                        .param("prompt", "Ping test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.providerId").value("prov-ollama"))
                .andExpect(jsonPath("$.data.status").value("OK"))
                .andExpect(jsonPath("$.data.latencyMs").isNumber());
    }

    @Test
    @DisplayName("集成测试 GET /api/token-usages/summary：查询平台全量 Token 消耗与成本计量汇总")
    void shouldQueryTokenUsagesSummarySuccessfully() throws Exception {
        mockMvc.perform(get("/api/token-usages/summary")
                        .header("X-User-Id", "user-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.totalPromptTokens").isNumber())
                .andExpect(jsonPath("$.data.totalCompletionTokens").isNumber())
                .andExpect(jsonPath("$.data.totalEstimatedCost").isNumber());
    }
}
