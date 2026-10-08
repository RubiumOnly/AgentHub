package com.agenthub;

import com.agenthub.agent.application.TokenUsageApplication;
import com.agenthub.agent.domain.provider.model.ModelPricing;
import com.agenthub.agent.dto.TokenSummaryView;
import com.agenthub.agent.dto.TokenUsageAuditView;
import com.agenthub.agent.infrastructure.entity.TokenUsageAuditEntity;
import com.agenthub.agent.infrastructure.repository.TokenUsageAuditRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class TokenCostAccountingAndAuditingTest {

    @Autowired
    private TokenUsageApplication tokenUsageApplication;

    @Autowired
    private TokenUsageAuditRepository repository;

    @Test
    @DisplayName("测试 ModelPricing 定价核算：按主流大模型精准计费（GPT-4o, DeepSeek, Claude, Gemini, Ollama）")
    void shouldAccuratelyCalculateCostsForLeadingModels() {
        // 1. GPT-4o: $5.00/1M in, $15.00/1M out
        // 100,000 prompt ($0.50) + 20,000 completion ($0.30) = $0.80
        double gpt4oCost = ModelPricing.calculateCost("gpt-4o", 100_000, 20_000, null, null);
        assertThat(gpt4oCost).isEqualTo(0.800000);

        // 2. DeepSeek Chat: $0.14/1M in, $0.28/1M out
        // 1,000,000 prompt ($0.14) + 1,000,000 completion ($0.28) = $0.42
        double deepseekCost = ModelPricing.calculateCost("deepseek-chat", 1_000_000, 1_000_000, null, null);
        assertThat(deepseekCost).isEqualTo(0.420000);

        // 3. Claude 3.5 Sonnet: $3.00/1M in, $15.00/1M out
        // 10,000 prompt ($0.03) + 5,000 completion ($0.075) = $0.105
        double claudeCost = ModelPricing.calculateCost("claude-3-5-sonnet", 10_000, 5_000, null, null);
        assertThat(claudeCost).isEqualTo(0.105000);

        // 4. Gemini 1.5 Flash: $0.075/1M in, $0.30/1M out
        // 200,000 prompt ($0.015) + 100,000 completion ($0.03) = $0.045
        double geminiCost = ModelPricing.calculateCost("gemini-1.5-flash", 200_000, 100_000, null, null);
        assertThat(geminiCost).isEqualTo(0.045000);

        // 5. Local Ollama: $0.00
        double localCost = ModelPricing.calculateCost("llama3.2:3b", 500_000, 500_000, null, null);
        assertThat(localCost).isEqualTo(0.0);
    }

    @Test
    @DisplayName("测试 自定义 Provider 费率覆盖：企业内部专属定价优先于默认模型牌价")
    void shouldPrioritizeCustomProviderPricingOverrides() {
        // Custom rate: $2.00/1M in, $4.00/1M out for gpt-4o
        double customCost = ModelPricing.calculateCost("gpt-4o", 100_000, 100_000, 2.0, 4.0);
        assertThat(customCost).isEqualTo(0.600000);
    }

    @Test
    @Transactional
    @DisplayName("测试 Token 与成本审计落库：持久化单次 LLM 调用的 Token 消耗、真实延迟与审计状态")
    void shouldPersistTokenUsageAuditRecordInDatabase() {
        String testRunId = "run-audit-" + UUID.randomUUID().toString().substring(0, 8);
        String testStepId = "step-audit-01";

        TokenUsageAuditView recorded = tokenUsageApplication.recordUsage(
                testRunId,
                testStepId,
                "prov-deepseek",
                "DEEPSEEK",
                "deepseek-chat",
                1500,
                800,
                340L,
                0.000434,
                "SUCCESS",
                null
        );

        assertThat(recorded).isNotNull();
        assertThat(recorded.getRunId()).isEqualTo(testRunId);
        assertThat(recorded.getTotalTokens()).isEqualTo(2300);
        assertThat(recorded.getLatencyMs()).isEqualTo(340L);

        // Verify retrieval by runId
        List<TokenUsageAuditView> runUsages = tokenUsageApplication.getRunTokenUsages(testRunId);
        assertThat(runUsages).hasSize(1);
        assertThat(runUsages.get(0).getModel()).isEqualTo("deepseek-chat");

        // Verify summary computation
        TokenSummaryView summary = tokenUsageApplication.getSummary();
        assertThat(summary.getTotalPromptTokens()).isGreaterThanOrEqualTo(1500);
        assertThat(summary.getTotalCompletionTokens()).isGreaterThanOrEqualTo(800);
        assertThat(summary.getTotalEstimatedCost()).isGreaterThan(0.0);
    }
}
