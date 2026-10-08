package com.agenthub.agent.application;

import com.agenthub.agent.dto.TokenSummaryView;
import com.agenthub.agent.dto.TokenUsageAuditView;
import com.agenthub.agent.infrastructure.entity.TokenUsageAuditEntity;
import com.agenthub.agent.infrastructure.repository.TokenUsageAuditRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class TokenUsageApplicationService implements TokenUsageApplication {

    private final TokenUsageAuditRepository repository;

    public TokenUsageApplicationService(TokenUsageAuditRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TokenUsageAuditView> getRunTokenUsages(String runId) {
        return repository.findByRunIdOrderByCreatedAtAsc(runId).stream()
                .map(this::toView)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<TokenUsageAuditView> getStepTokenUsages(String stepRunId) {
        return repository.findByStepRunIdOrderByCreatedAtAsc(stepRunId).stream()
                .map(this::toView)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public TokenSummaryView getSummary() {
        long promptTokens = repository.sumTotalPromptTokens();
        long completionTokens = repository.sumTotalCompletionTokens();
        double cost = repository.sumTotalEstimatedCost();
        long invocations = repository.count();

        return new TokenSummaryView(
                promptTokens,
                completionTokens,
                promptTokens + completionTokens,
                cost,
                invocations
        );
    }

    @Override
    @Transactional
    public TokenUsageAuditView recordUsage(String runId,
                                          String stepRunId,
                                          String providerId,
                                          String providerType,
                                          String model,
                                          int promptTokens,
                                          int completionTokens,
                                          long latencyMs,
                                          double estimatedCost,
                                          String status,
                                          String errorMessage) {
        String id = "use-" + UUID.randomUUID().toString().substring(0, 8);
        TokenUsageAuditEntity entity = new TokenUsageAuditEntity(
                id,
                runId,
                stepRunId,
                providerId,
                providerType,
                model,
                promptTokens,
                completionTokens,
                promptTokens + completionTokens,
                latencyMs,
                estimatedCost,
                status != null ? status : "SUCCESS",
                errorMessage
        );
        repository.save(entity);
        return toView(entity);
    }

    private TokenUsageAuditView toView(TokenUsageAuditEntity entity) {
        return new TokenUsageAuditView(
                entity.getId(),
                entity.getRunId(),
                entity.getStepRunId(),
                entity.getProviderId(),
                entity.getProviderType(),
                entity.getModel(),
                entity.getPromptTokens(),
                entity.getCompletionTokens(),
                entity.getTotalTokens(),
                entity.getLatencyMs(),
                entity.getEstimatedCost(),
                entity.getStatus(),
                entity.getErrorMessage(),
                entity.getCreatedAt()
        );
    }
}
