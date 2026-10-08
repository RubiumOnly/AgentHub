package com.agenthub.agent.application;

import com.agenthub.agent.dto.TokenSummaryView;
import com.agenthub.agent.dto.TokenUsageAuditView;

import java.util.List;

public interface TokenUsageApplication {
    List<TokenUsageAuditView> getRunTokenUsages(String runId);
    List<TokenUsageAuditView> getStepTokenUsages(String stepRunId);
    TokenSummaryView getSummary();
    TokenUsageAuditView recordUsage(String runId,
                                   String stepRunId,
                                   String providerId,
                                   String providerType,
                                   String model,
                                   int promptTokens,
                                   int completionTokens,
                                   long latencyMs,
                                   double estimatedCost,
                                   String status,
                                   String errorMessage);
}
