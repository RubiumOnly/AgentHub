package com.agenthub;

import com.agenthub.agent.domain.provider.exception.NoAvailableProviderException;
import com.agenthub.agent.domain.provider.model.*;
import com.agenthub.agent.domain.provider.router.DynamicProviderRouter;
import com.agenthub.agent.domain.provider.spi.LlmProvider;
import com.agenthub.agent.infrastructure.provider.MockLlmProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProviderFaultToleranceAndFallbackTest {

    private DynamicProviderRouter router;
    private MockLlmProvider primary;
    private MockLlmProvider backup;

    @BeforeEach
    void setUp() {
        router = new DynamicProviderRouter(new MockEnvironment(), null);
        for (LlmProvider p : router.listProviders()) {
            router.unregisterProvider(p.getId());
        }

        primary = new MockLlmProvider("prov-primary", "Primary OpenAI", "gpt-4o", 100, 1, Set.of("code", "general"));
        backup = new MockLlmProvider("prov-backup", "Backup Claude", "claude-3-5-sonnet", 80, 1, Set.of("code", "general"));

        router.registerProvider(primary);
        router.registerProvider(backup);
    }

    @Test
    @DisplayName("测试 429 限流高可用自动降级：主 Provider 发生 429 时无缝切换至备用 Provider 并沉淀降级告警")
    void shouldAutomaticallyFallbackWhenPrimaryHits429RateLimit() {
        primary.setSimulatedFailure(429, "Rate limit exceeded (429)");

        List<FallbackEvent> fallbackEvents = new ArrayList<>();
        ChatRequest request = ChatRequest.of("auto", "Perform critical code analysis");

        ChatResponse response = router.routeAndExecute(request, fallbackEvents::add);

        assertThat(response).isNotNull();
        assertThat(response.isFallbackUsed()).isTrue();
        assertThat(response.getOriginalProviderId()).isEqualTo("prov-primary");
        assertThat(response.getProviderId()).isEqualTo("prov-backup");
        assertThat(response.getContent()).contains("Backup Claude");

        assertThat(fallbackEvents).hasSize(1);
        FallbackEvent event = fallbackEvents.get(0);
        assertThat(event.getFailedProviderId()).isEqualTo("prov-primary");
        assertThat(event.getTargetProviderId()).isEqualTo("prov-backup");
        assertThat(event.getStatusCode()).isEqualTo(429);
        assertThat(event.toAlertMessage()).contains("falling back to backup provider");
    }

    @Test
    @DisplayName("测试 5xx 服务端错误自动容灾降级：主 Provider 发生 503 超时崩溃时自动路由至备用 Provider")
    void shouldAutomaticallyFallbackWhenPrimaryHits503ServerError() {
        primary.setSimulatedFailure(503, "Service Unavailable");

        AtomicBoolean fallbackTriggered = new AtomicBoolean(false);
        ChatResponse response = router.routeAndExecute(ChatRequest.of("auto", "Build backend module"), event -> {
            fallbackTriggered.set(true);
        });

        assertThat(response).isNotNull();
        assertThat(response.isFallbackUsed()).isTrue();
        assertThat(response.getProviderId()).isEqualTo("prov-backup");
        assertThat(fallbackTriggered.get()).isTrue();
    }

    @Test
    @DisplayName("测试 熔断器状态机防护：连续失败触发熔断 OPEN，后续请求快速阻断并直接路由至健康节点")
    void shouldTripCircuitBreakerAfterConsecutiveFailures() {
        primary.setSimulatedFailure(429, "Too many requests");

        assertThat(primary.getCircuitBreaker().getStatus()).isEqualTo(CircuitStatus.CLOSED);

        // Fail 3 times to exceed failure threshold
        for (int i = 0; i < 3; i++) {
            try {
                primary.chat(ChatRequest.of("gpt-4o", "Task"));
            } catch (Exception ignored) {}
        }

        assertThat(primary.getCircuitBreaker().getStatus()).isEqualTo(CircuitStatus.OPEN);
        assertThat(primary.getCircuitBreaker().allowRequest()).isFalse();

        // Next router candidate selection should completely bypass primary
        List<LlmProvider> candidates = router.selectCandidates(ChatRequest.of("auto", "New task"));
        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).getId()).isEqualTo("prov-backup");
    }

    @Test
    @DisplayName("测试 流式 SSE 执行容灾降级：主 Provider 首帧前崩溃时备用 Provider 接管流式输出")
    void shouldFallbackStreamWhenPrimaryFailsBeforeFirstChunk() {
        primary.setSimulatedFailure(500, "Internal gateway error");

        List<ChatChunk> chunks = new ArrayList<>();
        List<FallbackEvent> events = new ArrayList<>();

        router.routeAndStream(ChatRequest.of("auto", "Stream task"), chunks::add, events::add, null);

        assertThat(chunks).isNotEmpty();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getFailedProviderId()).isEqualTo("prov-primary");
        assertThat(events.get(0).getTargetProviderId()).isEqualTo("prov-backup");
    }

    @Test
    @DisplayName("测试 全链路崩溃熔断熔毁：当所有备选 Provider 均不可用时抛出 NoAvailableProviderException")
    void shouldThrowNoAvailableProviderWhenAllCandidatesFail() {
        primary.setSimulatedFailure(500, "Primary down");
        backup.setSimulatedFailure(503, "Backup also down");

        assertThatThrownBy(() -> router.routeAndExecute(ChatRequest.of("auto", "Do task"), null))
                .isInstanceOf(NoAvailableProviderException.class)
                .hasMessageContaining("All 2 candidate LLM providers failed");
    }

    @Test
    @DisplayName("测试 熔断半开状态单探针保护与并发压测限流：HALF_OPEN 状态仅放行单次探测，未完成前拦截并发流量")
    void shouldGateProbingRequestsInHalfOpenState() throws Exception {
        CircuitBreaker cb = new CircuitBreaker("test-cb", 2, 50L);
        cb.trip();
        assertThat(cb.getStatus()).isEqualTo(CircuitStatus.OPEN);
        assertThat(cb.allowRequest()).isFalse();

        // Wait for open timeout to expire
        Thread.sleep(60L);

        // First call should transition to HALF_OPEN and be allowed as probe
        boolean firstProbe = cb.allowRequest();
        assertThat(firstProbe).isTrue();
        assertThat(cb.getStatus()).isEqualTo(CircuitStatus.HALF_OPEN);

        // Second concurrent call while probe is in flight MUST be blocked
        boolean secondConcurrent = cb.allowRequest();
        assertThat(secondConcurrent).isFalse();

        // Successful completion closes circuit
        cb.recordSuccess(42L);
        assertThat(cb.getStatus()).isEqualTo(CircuitStatus.CLOSED);
        assertThat(cb.getLastLatencyMs()).isEqualTo(42L);
        assertThat(cb.allowRequest()).isTrue();
    }

    @Test
    @DisplayName("测试 熔断半开探测失败即刻重熔：HALF_OPEN 状态下探测失败无需累积计数直接回退至 OPEN")
    void shouldImmediatelyTripBackToOpenWhenProbeFailsInHalfOpen() throws Exception {
        CircuitBreaker cb = new CircuitBreaker("test-cb-fail", 3, 50L);
        cb.trip();
        Thread.sleep(60L);

        // Transition to HALF_OPEN
        assertThat(cb.allowRequest()).isTrue();
        assertThat(cb.getStatus()).isEqualTo(CircuitStatus.HALF_OPEN);

        // Probe fails
        cb.recordFailure(new RuntimeException("Probe connect error"));
        assertThat(cb.getStatus()).isEqualTo(CircuitStatus.OPEN);
        assertThat(cb.allowRequest()).isFalse();
    }
}
