package com.agenthub.execution.application.evidence;

import com.agenthub.agent.dto.TokenUsageAuditView;
import com.agenthub.execution.dto.RunEventView;
import com.agenthub.execution.dto.RunEvidenceBundleView;
import com.agenthub.execution.infrastructure.entity.StepRunEntity;
import com.agenthub.agent.infrastructure.repository.TokenUsageAuditRepository;
import com.agenthub.agent.infrastructure.entity.TokenUsageAuditEntity;
import com.agenthub.agent.infrastructure.security.SecretMasker;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 证据包 Provider/Token 用量聚合器（Phase 10 R4 自 RunEvidenceModule 抽出的深模块缝）。
 * 汇总 token_usages 审计与 USAGE 事件两条来源，判定 simulated 标识；
 * LIVE 与 DEMO 统计的严格隔离语义集中在此，禁止在别处重复判定。
 */
@Component
public class EvidenceUsageAggregator {

    private final TokenUsageAuditRepository tokenUsageAuditRepository;

    public EvidenceUsageAggregator(@Autowired(required = false) TokenUsageAuditRepository tokenUsageAuditRepository) {
        this.tokenUsageAuditRepository = tokenUsageAuditRepository;
    }

    /**
     * 聚合结果：providerRuntime 证据块、simulated 判定与验证日志。
     */
    public record EvidenceUsageAggregate(
            RunEvidenceBundleView.ProviderRuntimeEvidence providerEvidence,
            boolean simulated,
            List<String> verificationLogs) {
    }

    public EvidenceUsageAggregate aggregate(String runId,
                                            List<StepRunEntity> stepEntities,
                                            List<RunEventView> sanitizedEvents,
                                            Set<String> runtimeTypes,
                                            ObjectMapper mapper) {
        Boolean anyEventSimulated = null;
        long eventPromptTokens = 0L;
        long eventCompletionTokens = 0L;
        double eventCost = 0.0;
        List<String> verificationLogs = new ArrayList<>();

        for (RunEventView ev : sanitizedEvents) {
            String payload = ev.getPayload();
            if (payload == null || payload.isBlank()) continue;
            if ("STEP_STARTED".equalsIgnoreCase(ev.getEventType()) || "STEP_COMPLETED".equalsIgnoreCase(ev.getEventType())) {
                try {
                    JsonNode node = mapper.readTree(payload);
                    if (node.hasNonNull("runtimeType")) {
                        runtimeTypes.add(node.get("runtimeType").asText().trim().toUpperCase());
                    }
                    if (node.hasNonNull("simulated")) {
                        boolean sim = node.get("simulated").asBoolean();
                        anyEventSimulated = (anyEventSimulated == null) ? sim : (anyEventSimulated || sim);
                    }
                } catch (Exception ignored) {}
            } else if ("USAGE".equalsIgnoreCase(ev.getEventType())) {
                try {
                    JsonNode node = mapper.readTree(payload);
                    eventPromptTokens += node.path("promptTokens").asLong(0L);
                    eventCompletionTokens += node.path("completionTokens").asLong(0L);
                    eventCost += node.path("cost").asDouble(0.0);
                } catch (Exception ignored) {}
            } else if ("LOG".equalsIgnoreCase(ev.getEventType()) || "TOOL_CALL".equalsIgnoreCase(ev.getEventType())) {
                verificationLogs.add("[" + ev.getEventType() + "#" + ev.getSequenceNum() + "] " + payload);
            }
        }

        List<TokenUsageAuditView> usageViews = new ArrayList<>();
        Set<String> providerTypes = new LinkedHashSet<>();
        Set<String> models = new LinkedHashSet<>();
        long totalPrompt = 0L;
        long totalCompletion = 0L;
        long totalTokens = 0L;
        double totalCost = 0.0;

        if (tokenUsageAuditRepository != null) {
            Map<String, TokenUsageAuditEntity> usageById = new LinkedHashMap<>();
            for (TokenUsageAuditEntity u : tokenUsageAuditRepository.findByRunIdOrderByCreatedAtAsc(runId)) {
                usageById.put(u.getId(), u);
            }
            for (StepRunEntity se : stepEntities) {
                for (TokenUsageAuditEntity u : tokenUsageAuditRepository.findByStepRunIdOrderByCreatedAtAsc(se.getId())) {
                    usageById.putIfAbsent(u.getId(), u);
                }
            }
            for (TokenUsageAuditEntity u : usageById.values()) {
                if (u.getProviderType() != null && !u.getProviderType().isBlank()) {
                    providerTypes.add(u.getProviderType());
                }
                if (u.getModel() != null && !u.getModel().isBlank()) {
                    models.add(u.getModel());
                }
                totalPrompt += u.getPromptTokens();
                totalCompletion += u.getCompletionTokens();
                totalTokens += u.getTotalTokens();
                totalCost += u.getEstimatedCost();
                usageViews.add(new TokenUsageAuditView(
                        u.getId(),
                        u.getRunId(),
                        u.getStepRunId(),
                        u.getProviderId(),
                        u.getProviderType(),
                        u.getModel(),
                        u.getPromptTokens(),
                        u.getCompletionTokens(),
                        u.getTotalTokens(),
                        u.getLatencyMs(),
                        u.getEstimatedCost(),
                        u.getStatus(),
                        u.getErrorMessage() != null ? SecretMasker.maskInText(u.getErrorMessage()) : null,
                        u.getCreatedAt()
                ));
            }
        }

        if (usageViews.isEmpty() && (eventPromptTokens > 0 || eventCompletionTokens > 0)) {
            totalPrompt = eventPromptTokens;
            totalCompletion = eventCompletionTokens;
            totalTokens = eventPromptTokens + eventCompletionTokens;
            totalCost = Math.round(eventCost * 1_000_000.0) / 1_000_000.0;
        }

        boolean hasLiveTokenUsage = usageViews.stream()
                .anyMatch(u -> "SUCCESS".equalsIgnoreCase(u.getStatus())
                        && u.getTotalTokens() > 0
                        && u.getProviderType() != null
                        && !"MOCK".equalsIgnoreCase(u.getProviderType())
                        && !"SIMULATED".equalsIgnoreCase(u.getProviderType())
                        && !"ROUTER".equalsIgnoreCase(u.getProviderType())
                        && (u.getProviderId() == null || !"prov-mock".equalsIgnoreCase(u.getProviderId())));
        boolean hasSimulatedTokenUsage = usageViews.stream()
                .anyMatch(u -> (u.getProviderType() != null
                        && ("MOCK".equalsIgnoreCase(u.getProviderType())
                            || "SIMULATED".equalsIgnoreCase(u.getProviderType())))
                        || "prov-mock".equalsIgnoreCase(u.getProviderId()));
        boolean hasMockRuntime = runtimeTypes.stream()
                .anyMatch(rt -> "MOCK".equalsIgnoreCase(rt));

        boolean isSimulated = !hasLiveTokenUsage
                || hasSimulatedTokenUsage
                || hasMockRuntime
                || Boolean.TRUE.equals(anyEventSimulated);

        if (runtimeTypes.isEmpty()) {
            runtimeTypes.add(isSimulated ? "MOCK" : "PROVIDER_DRIVEN");
        }
        if (providerTypes.isEmpty()) {
            providerTypes.add(isSimulated ? "MOCK_SIMULATOR" : "DYNAMIC_ROUTER");
        }
        if (models.isEmpty()) {
            models.add(isSimulated ? "mock-offline-v1" : "router-default");
        }

        RunEvidenceBundleView.ProviderRuntimeEvidence providerEvidence = new RunEvidenceBundleView.ProviderRuntimeEvidence();
        providerEvidence.setRuntimeTypes(new ArrayList<>(runtimeTypes));
        providerEvidence.setProviderTypes(new ArrayList<>(providerTypes));
        providerEvidence.setModels(new ArrayList<>(models));
        providerEvidence.setSimulated(isSimulated);
        providerEvidence.setTotalPromptTokens(totalPrompt);
        providerEvidence.setTotalCompletionTokens(totalCompletion);
        providerEvidence.setTotalTokens(totalTokens);
        providerEvidence.setTotalEstimatedCost(Math.round(totalCost * 1_000_000.0) / 1_000_000.0);
        providerEvidence.setTokenUsageRecords(usageViews);

        return new EvidenceUsageAggregate(providerEvidence, isSimulated, verificationLogs);
    }
}
