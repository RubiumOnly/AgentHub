package com.agenthub.agent.application;

import com.agenthub.agent.domain.provider.model.ProviderExecutionMode;
import com.agenthub.agent.dto.*;
import com.agenthub.agent.infrastructure.repository.TokenUsageAuditRepository;
import com.agenthub.shared.exception.BusinessException;
import com.agenthub.shared.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Enterprise Token & Cost Budget Governance Guard.
 * Enforces hard cost ceilings, warning thresholds, and multi-dimensional cost summaries.
 */
@Component
public class BudgetGovernanceGuard {

    private static final Logger log = LoggerFactory.getLogger(BudgetGovernanceGuard.class);

    private final TokenUsageAuditRepository tokenUsageRepository;
    private final Environment environment;

    @Value("${agenthub.budget.hard-limit-usd:50.00}")
    private double hardBudgetLimitUsd;

    @Value("${agenthub.budget.warning-ratio:0.80}")
    private double warningRatio;

    @Value("${agenthub.provider.mode:LIVE_SELF_HOSTED}")
    private String configuredProviderMode;

    public BudgetGovernanceGuard(TokenUsageAuditRepository tokenUsageRepository,
                                 Environment environment) {
        this.tokenUsageRepository = tokenUsageRepository;
        this.environment = environment;
    }

    /**
     * Pre-invocation hard budget guard. Intercepts if current cost plus estimated cost exceeds hard limit.
     */
    @Transactional(readOnly = true)
    public BudgetCheckResultView enforceBudget(String runId, String projectId, double estimatedCost, int requiredTokens) {
        double currentCost = tokenUsageRepository.sumTotalEstimatedCost();
        double limit = getEffectiveHardLimit();
        double remaining = Math.max(0.0, limit - currentCost);
        double utilizationPercent = limit > 0 ? round((currentCost / limit) * 100.0, 2) : 100.0;
        boolean warning = utilizationPercent >= (warningRatio * 100.0);

        if (currentCost + estimatedCost > limit) {
            log.warn("Budget limit exceeded! Limit: ${}, Consumed: ${}, Requested: ${}, Run: {}, Project: {}",
                    limit, currentCost, estimatedCost, runId, projectId);
            throw new BusinessException(ErrorCode.BUDGET_EXCEEDED,
                    String.format("Budget hard limit exceeded: limit=$%.4f, consumed=$%.4f, requested=$%.4f. Execution stopped to prevent cost overrun.",
                            limit, currentCost, estimatedCost));
        }

        if (warning) {
            log.warn("Budget governance warning: utilization at {}% (${}/${})", utilizationPercent, currentCost, limit);
        }

        return BudgetCheckResultView.allowed(currentCost, limit, remaining, utilizationPercent, warning);
    }

    /**
     * Non-throwing budget check query.
     */
    @Transactional(readOnly = true)
    public BudgetCheckResultView evaluateBudget(BudgetCheckCommand cmd) {
        double currentCost = tokenUsageRepository.sumTotalEstimatedCost();
        double limit = getEffectiveHardLimit();
        double estimated = cmd != null && cmd.getEstimatedCost() != null ? cmd.getEstimatedCost() : 0.05;
        double remaining = Math.max(0.0, limit - currentCost);
        double utilizationPercent = limit > 0 ? round((currentCost / limit) * 100.0, 2) : 100.0;
        boolean warning = utilizationPercent >= (warningRatio * 100.0);

        if (currentCost + estimated > limit) {
            return BudgetCheckResultView.rejected(currentCost, limit, remaining, utilizationPercent,
                    String.format("Requested invocation would exceed hard budget limit ($%.4f > $%.4f)",
                            currentCost + estimated, limit));
        }

        return BudgetCheckResultView.allowed(currentCost, limit, remaining, utilizationPercent, warning);
    }

    /**
     * Generates a comprehensive token governance report.
     */
    @Transactional(readOnly = true)
    public TokenGovernanceView getGovernanceView() {
        long promptTokens = tokenUsageRepository.sumTotalPromptTokens();
        long completionTokens = tokenUsageRepository.sumTotalCompletionTokens();
        long totalTokens = promptTokens + completionTokens;
        double totalCost = round(tokenUsageRepository.sumTotalEstimatedCost(), 4);
        long invocations = tokenUsageRepository.count();

        double limit = getEffectiveHardLimit();
        double remaining = Math.max(0.0, round(limit - totalCost, 4));
        double utilizationPercent = limit > 0 ? round((totalCost / limit) * 100.0, 2) : 100.0;
        boolean exhausted = totalCost >= limit;
        boolean warning = utilizationPercent >= (warningRatio * 100.0);

        ProviderExecutionMode mode = getEffectiveMode();

        List<ModelCostSummaryView> modelBreakdown = new ArrayList<>();
        List<Object[]> rawModel = tokenUsageRepository.summarizeByModel();
        if (rawModel != null) {
            for (Object[] row : rawModel) {
                if (row == null) continue;
                String model = row[0] != null ? String.valueOf(row[0]) : "unknown";
                long pTokens = row[1] != null ? ((Number) row[1]).longValue() : 0L;
                long cTokens = row[2] != null ? ((Number) row[2]).longValue() : 0L;
                long tTokens = row[3] != null ? ((Number) row[3]).longValue() : 0L;
                double cost = row[4] != null ? round(((Number) row[4]).doubleValue(), 4) : 0.0;
                long cnt = row[5] != null ? ((Number) row[5]).longValue() : 0L;
                modelBreakdown.add(new ModelCostSummaryView(model, pTokens, cTokens, tTokens, cost, cnt));
            }
        }

        List<ProviderCostSummaryView> providerBreakdown = new ArrayList<>();
        List<Object[]> rawProvider = tokenUsageRepository.summarizeByProvider();
        if (rawProvider != null) {
            for (Object[] row : rawProvider) {
                if (row == null) continue;
                String pId = row[0] != null ? String.valueOf(row[0]) : "unassigned";
                String pType = row[1] != null ? String.valueOf(row[1]) : "UNKNOWN";
                double cost = row[2] != null ? round(((Number) row[2]).doubleValue(), 4) : 0.0;
                long tTokens = row[3] != null ? ((Number) row[3]).longValue() : 0L;
                long latency = row[4] != null ? ((Number) row[4]).longValue() : 0L;
                long cnt = row[5] != null ? ((Number) row[5]).longValue() : 0L;
                providerBreakdown.add(new ProviderCostSummaryView(pId, pType, cost, tTokens, latency, cnt));
            }
        }

        return new TokenGovernanceView(
                promptTokens,
                completionTokens,
                totalTokens,
                totalCost,
                invocations,
                limit,
                remaining,
                utilizationPercent,
                exhausted,
                warning,
                mode.name(),
                modelBreakdown,
                providerBreakdown
        );
    }

    public double getEffectiveHardLimit() {
        return hardBudgetLimitUsd > 0 ? hardBudgetLimitUsd : 50.00;
    }

    public void setHardBudgetLimitUsd(double limit) {
        this.hardBudgetLimitUsd = limit;
    }

    public ProviderExecutionMode getEffectiveMode() {
        String envMode = environment != null ? environment.getProperty("agenthub.provider.mode", configuredProviderMode) : configuredProviderMode;
        return ProviderExecutionMode.fromString(envMode);
    }

    private double round(double val, int scale) {
        return BigDecimal.valueOf(val).setScale(scale, RoundingMode.HALF_UP).doubleValue();
    }
}
