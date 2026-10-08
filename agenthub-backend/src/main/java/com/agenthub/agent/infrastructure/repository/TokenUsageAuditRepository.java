package com.agenthub.agent.infrastructure.repository;

import com.agenthub.agent.infrastructure.entity.TokenUsageAuditEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TokenUsageAuditRepository extends JpaRepository<TokenUsageAuditEntity, String> {

    List<TokenUsageAuditEntity> findByRunIdOrderByCreatedAtAsc(String runId);

    List<TokenUsageAuditEntity> findByStepRunIdOrderByCreatedAtAsc(String stepRunId);

    List<TokenUsageAuditEntity> findByProviderIdOrderByCreatedAtDesc(String providerId);

    List<TokenUsageAuditEntity> findTop50ByOrderByCreatedAtDesc();

    @Query("SELECT COALESCE(SUM(t.promptTokens), 0) FROM TokenUsageAuditEntity t")
    long sumTotalPromptTokens();

    @Query("SELECT COALESCE(SUM(t.completionTokens), 0) FROM TokenUsageAuditEntity t")
    long sumTotalCompletionTokens();

    @Query("SELECT COALESCE(SUM(t.estimatedCost), 0.0) FROM TokenUsageAuditEntity t")
    double sumTotalEstimatedCost();
}
