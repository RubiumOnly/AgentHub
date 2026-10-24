package com.agenthub.audit.application.artifact;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 产物 metadata JSON 提取器（Phase 10 R5 自 ArtifactApplicationService 抽出的共享缝）。
 * 解析 files/changedFiles 与 fileChecksums/checksums 两种历史键名，宽松降级。
 */
public final class ArtifactMetadataExtractor {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private ArtifactMetadataExtractor() {
    }

    public static List<String> extractFilesFromMetadata(String metadataJson) {
        List<String> files = new ArrayList<>();
        if (metadataJson == null || metadataJson.isBlank()) {
            return files;
        }
        try {
            JsonNode root = MAPPER.readTree(metadataJson);
            JsonNode filesNode = root.get("files");
            if (filesNode == null || !filesNode.isArray()) {
                filesNode = root.get("changedFiles");
            }
            if (filesNode != null && filesNode.isArray()) {
                for (JsonNode f : filesNode) {
                    if (f.isTextual() && !f.asText().isBlank()) {
                        files.add(f.asText().trim());
                    }
                }
            }
        } catch (Exception e) {
            // metadata 缺失或非法时返回空清单，血缘与回滚按零文件处理
        }
        return files;
    }

    public static Map<String, String> extractChecksumsFromMetadata(String metadataJson) {
        Map<String, String> checksums = new HashMap<>();
        if (metadataJson == null || metadataJson.isBlank()) {
            return checksums;
        }
        try {
            JsonNode root = MAPPER.readTree(metadataJson);
            JsonNode sumsNode = root.get("fileChecksums");
            if (sumsNode == null || !sumsNode.isObject()) {
                sumsNode = root.get("checksums");
            }
            if (sumsNode != null && sumsNode.isObject()) {
                sumsNode.fields().forEachRemaining(entry -> {
                    if (entry.getValue().isTextual()) {
                        checksums.put(entry.getKey(), entry.getValue().asText());
                    }
                });
            }
        } catch (Exception e) {
            // 同上：宽松降级
        }
        return checksums;
    }
}
