package com.agenthub.sandbox.application.deployment;

import com.agenthub.sandbox.api.dto.DeploymentResponse;
import com.agenthub.sandbox.domain.model.DeploymentStatus;
import com.agenthub.sandbox.domain.service.DeploymentLifecycleWorker;
import com.agenthub.sandbox.infrastructure.entity.DeploymentEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 部署生命周期支撑缝（Phase 10 R6 自 DeploymentApplicationService 抽出）：
 * 状态迁移轨迹、预览 URL 构造、命令分词、日志合并与响应映射的唯一实现。
 */
@Component
public class DeploymentLifecycleSupport {

    public static final List<String> ACTIVE_LIFECYCLE_STATUSES = List.of(
            DeploymentStatus.CREATED.name(),
            DeploymentStatus.BUILD.name(),
            DeploymentStatus.BUILDING.name(),
            DeploymentStatus.START.name(),
            DeploymentStatus.HEALTH_CHECK.name(),
            DeploymentStatus.RUNNING.name(),
            DeploymentStatus.STOP.name(),
            DeploymentStatus.CLEANUP.name()
    );

    private final DeploymentLifecycleWorker lifecycleWorker;

    @Value("${agenthub.sandbox.preview-host:localhost}")
    private String previewHost = "localhost";

    @Value("${agenthub.sandbox.external-base-url:}")
    private String externalBaseUrl = "";

    public DeploymentLifecycleSupport(@Autowired(required = false) DeploymentLifecycleWorker lifecycleWorker) {
        this.lifecycleWorker = lifecycleWorker;
    }

    public void transitionState(DeploymentEntity entity, DeploymentStatus nextStatus) {
        entity.setStatus(nextStatus.name());
        String currentTrace = entity.getLifecycleTrace();
        if (currentTrace == null || currentTrace.isBlank()) {
            entity.setLifecycleTrace(nextStatus.name());
        } else if (!currentTrace.endsWith("->" + nextStatus.name()) && !currentTrace.equals(nextStatus.name())) {
            entity.setLifecycleTrace(currentTrace + "->" + nextStatus.name());
        }
        entity.setUpdatedAt(LocalDateTime.now());
    }

    public String buildPreviewUrl(String target, int port, String projectId, String healthPath) {
        if (externalBaseUrl != null && !externalBaseUrl.isBlank()) {
            String base = externalBaseUrl.trim().replaceAll("/+$", "");
            return base + "/api/sandbox/preview/" + projectId;
        }
        String host = (previewHost != null && !previewHost.isBlank()) ? previewHost.trim() : "localhost";
        if ("STATIC_PREVIEW".equalsIgnoreCase(target) || "DOCKER_NGINX".equalsIgnoreCase(target)) {
            return "http://" + host + ":" + port + "/api/sandbox/preview/" + projectId;
        }
        String normalizedPath = (healthPath != null && !healthPath.isBlank())
                ? (healthPath.startsWith("/") ? healthPath : "/" + healthPath)
                : "/";
        return "http://" + host + ":" + port + normalizedPath;
    }

    public List<String> parseCommandTokens(String commandLine) {
        List<String> tokens = new ArrayList<>();
        if (commandLine == null || commandLine.isBlank()) {
            return tokens;
        }
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        char quoteChar = 0;

        for (int i = 0; i < commandLine.length(); i++) {
            char c = commandLine.charAt(i);
            if ((c == '"' || c == '\'') && (!inQuotes || c == quoteChar)) {
                inQuotes = !inQuotes;
                quoteChar = inQuotes ? c : 0;
            } else if (Character.isWhitespace(c) && !inQuotes) {
                if (current.length() > 0) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) {
            tokens.add(current.toString());
        }
        return tokens;
    }

    public String buildCombinedLogs(DeploymentEntity entity) {
        String baseLogs = entity.getLogOutput() != null ? entity.getLogOutput() : "";
        if (lifecycleWorker != null) {
            String liveLogs = lifecycleWorker.getLiveProcessLogs(entity.getId());
            if (liveLogs != null && !liveLogs.isBlank() && !baseLogs.contains(liveLogs)) {
                return baseLogs + "\n" + liveLogs;
            }
        }
        return baseLogs;
    }

    public DeploymentResponse toResponse(DeploymentEntity entity) {
        String combinedLogs = buildCombinedLogs(entity);
        long effectiveCursor = Math.max(
                entity.getLogCursor() != null ? entity.getLogCursor() : 0L,
                combinedLogs.length()
        );

        DeploymentResponse resp = new DeploymentResponse();
        resp.setId(entity.getId());
        resp.setProjectId(entity.getProjectId());
        resp.setArtifactId(entity.getArtifactId());
        resp.setStatus(entity.getStatus());
        resp.setTarget(entity.getTarget());
        resp.setSandboxType(entity.getSandboxType());
        resp.setPort(entity.getPort());
        resp.setUrl(entity.getUrl());
        resp.setBuildCommand(entity.getBuildCommand());
        resp.setStartCommand(entity.getStartCommand());
        resp.setStartedAt(entity.getStartedAt());
        resp.setFinishedAt(entity.getFinishedAt());
        resp.setCreatedAt(entity.getCreatedAt());
        resp.setErrorMessage(entity.getErrorMessage());
        resp.setHasLogs(!combinedLogs.isBlank());
        resp.setProcessId(entity.getProcessId());
        resp.setContainerId(entity.getContainerId());
        resp.setWorkingDirectory(entity.getWorkingDirectory());
        resp.setLogCursor(effectiveCursor);
        resp.setTerminationReason(entity.getTerminationReason());
        resp.setTemplateType(entity.getTemplateType());
        resp.setLifecycleTrace(entity.getLifecycleTrace());
        return resp;
    }
}
