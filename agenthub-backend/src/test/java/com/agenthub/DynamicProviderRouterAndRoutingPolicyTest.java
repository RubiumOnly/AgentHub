package com.agenthub;

import com.agenthub.agent.domain.provider.model.ChatRequest;
import com.agenthub.agent.domain.provider.router.DynamicProviderRouter;
import com.agenthub.agent.domain.provider.spi.LlmProvider;
import com.agenthub.agent.infrastructure.provider.MockLlmProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DynamicProviderRouterAndRoutingPolicyTest {

    private DynamicProviderRouter router;

    @BeforeEach
    void setUp() {
        router = new DynamicProviderRouter(new MockEnvironment(), null);
        // Clear default providers and register custom matrix for predictable testing
        for (LlmProvider p : router.listProviders()) {
            router.unregisterProvider(p.getId());
        }

        router.registerProvider(new MockLlmProvider("p-high-code", "High Priority Code", "code-fast", 100, 1, Set.of("code", "fast")));
        router.registerProvider(new MockLlmProvider("p-med-general", "Medium Priority General", "general-v1", 70, 1, Set.of("general")));
        router.registerProvider(new MockLlmProvider("p-low-all", "Low Priority All", "omni-v1", 30, 1, Set.of("code", "general", "fast")));
    }

    @Test
    @DisplayName("测试 动态路由优先级策略：在无特殊能力约束时，按 Priority 降序优先选择最高优先级 Provider")
    void shouldSelectHighestPriorityProviderByDefault() {
        ChatRequest req = ChatRequest.of("auto", "Write a summary");
        List<LlmProvider> candidates = router.selectCandidates(req);

        assertThat(candidates).isNotEmpty();
        assertThat(candidates.get(0).getId()).isEqualTo("p-high-code");
        assertThat(candidates.get(1).getId()).isEqualTo("p-med-general");
        assertThat(candidates.get(2).getId()).isEqualTo("p-low-all");
    }

    @Test
    @DisplayName("测试 动态路由能力匹配策略：要求特定 capabilities (如 general) 时精准过滤不满足条件的 Provider")
    void shouldFilterCandidatesByRequiredCapabilities() {
        ChatRequest req = new ChatRequest();
        req.setRequiredCapabilities(Set.of("general"));

        List<LlmProvider> candidates = router.selectCandidates(req);
        assertThat(candidates).hasSize(2);
        assertThat(candidates.get(0).getId()).isEqualTo("p-med-general"); // priority 70
        assertThat(candidates.get(1).getId()).isEqualTo("p-low-all");     // priority 30
        assertThat(candidates).noneMatch(p -> p.getId().equals("p-high-code")); // lacking "general"
    }

    @Test
    @DisplayName("测试 多重能力交集约束：同时要求 code 与 fast 时仅保留两者均满足的 Provider")
    void shouldFilterCandidatesByMultipleCapabilities() {
        ChatRequest req = new ChatRequest();
        req.setRequiredCapabilities(Set.of("code", "fast"));

        List<LlmProvider> candidates = router.selectCandidates(req);
        assertThat(candidates).hasSize(2);
        assertThat(candidates.get(0).getId()).isEqualTo("p-high-code");
        assertThat(candidates.get(1).getId()).isEqualTo("p-low-all");
    }

    @Test
    @DisplayName("测试 显式 Preferred Provider 覆盖：指定目标 Provider 时优先排在首位")
    void shouldPrioritizeExplicitlyPreferredProvider() {
        ChatRequest req = ChatRequest.of("auto", "Do something");
        req.setPreferredProvider("p-low-all");

        List<LlmProvider> candidates = router.selectCandidates(req);
        assertThat(candidates.get(0).getId()).isEqualTo("p-low-all");
    }

    @Test
    @DisplayName("测试 熔断感知路由：当最高优先级 Provider 处于 OPEN 熔断状态时自动将其剔除候选集")
    void shouldExcludeCircuitBrokenProviderFromCandidates() {
        LlmProvider high = router.getProviderById("p-high-code");
        high.getCircuitBreaker().trip(); // Force trip circuit

        ChatRequest req = ChatRequest.of("auto", "Do something");
        List<LlmProvider> candidates = router.selectCandidates(req);

        assertThat(candidates).noneMatch(p -> p.getId().equals("p-high-code"));
        assertThat(candidates.get(0).getId()).isEqualTo("p-med-general");
    }
}
