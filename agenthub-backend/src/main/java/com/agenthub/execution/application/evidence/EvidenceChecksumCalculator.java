package com.agenthub.execution.application.evidence;

import com.agenthub.execution.dto.RunEvidenceBundleView;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 证据包确定性校验和计算器（Phase 10 R4 自 RunEvidenceModule 抽出的深模块缝）。
 * 规范化 JSON 序列化 + SHA-256；任何字段顺序或脱敏输出变化都会改变校验和。
 */
public final class EvidenceChecksumCalculator {

    private static final ObjectMapper EVIDENCE_MAPPER = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .build();

    private EvidenceChecksumCalculator() {
    }

    public static ObjectMapper evidenceMapper() {
        return EVIDENCE_MAPPER;
    }

    public static String computeDeterministicChecksum(RunEvidenceBundleView bundle) {
        try {
            Map<String, Object> canonical = new LinkedHashMap<>();
            canonical.put("schemaVersion", bundle.getSchemaVersion());
            canonical.put("bundleId", bundle.getBundleId());
            canonical.put("simulated", bundle.isSimulated());
            canonical.put("executionMode", bundle.getExecutionMode());
            canonical.put("project", bundle.getProject());
            canonical.put("workflowDslSnapshot", bundle.getWorkflowDslSnapshot());
            canonical.put("run", bundle.getRun());
            canonical.put("steps", bundle.getSteps());
            canonical.put("events", bundle.getEvents());
            canonical.put("providerRuntime", bundle.getProviderRuntime());
            canonical.put("gitDiff", bundle.getGitDiff());
            canonical.put("artifacts", bundle.getArtifacts());
            canonical.put("testReportSummary", bundle.getTestReportSummary());
            canonical.put("approvals", bundle.getApprovals());
            canonical.put("deployments", bundle.getDeployments());

            String json = EVIDENCE_MAPPER.writeValueAsString(canonical);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(json.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute deterministic SHA-256 checksum for RunEvidenceBundleView", e);
        }
    }

    public static boolean verifyEvidenceBundleChecksum(RunEvidenceBundleView bundle) {
        if (bundle == null || bundle.getChecksum() == null || bundle.getChecksum().isBlank()) {
            return false;
        }
        String expected = computeDeterministicChecksum(bundle);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                bundle.getChecksum().trim().toLowerCase().getBytes(StandardCharsets.UTF_8)
        );
    }
}
