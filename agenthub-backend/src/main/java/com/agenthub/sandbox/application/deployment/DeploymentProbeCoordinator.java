package com.agenthub.sandbox.application.deployment;

import com.agenthub.sandbox.domain.model.DeploymentStatus;
import com.agenthub.sandbox.domain.service.HealthCheckProbeService;
import com.agenthub.sandbox.infrastructure.entity.DeploymentEntity;
import com.agenthub.sandbox.infrastructure.repository.DeploymentRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 部署探针协同缝（Phase 10 R6 自 DeploymentApplicationService 抽出）：
 * 真实 HTTP 探针、日志留痕与 RUNNING 状态回迁的唯一实现。
 */
@Component
public class DeploymentProbeCoordinator {

    private final DeploymentRepository deploymentRepository;
    private final HealthCheckProbeService healthCheckProbeService;
    private final DeploymentLifecycleSupport lifecycleSupport;

    public DeploymentProbeCoordinator(DeploymentRepository deploymentRepository,
                                      HealthCheckProbeService healthCheckProbeService,
                                      DeploymentLifecycleSupport lifecycleSupport) {
        this.deploymentRepository = deploymentRepository;
        this.healthCheckProbeService = healthCheckProbeService;
        this.lifecycleSupport = lifecycleSupport;
    }

    public record ProbeOutcome(boolean healthy, long elapsedMs) {
    }

    /**
     * 对已加载实体执行真实探针：追加探针日志，健康则回迁 RUNNING，不健康则记录错误。
     * 调用方负责访问校验与响应映射。
     */
    public ProbeOutcome probe(DeploymentEntity entity) {
        if (entity.getPort() == null) {
            throw new com.agenthub.shared.exception.BusinessException(
                    com.agenthub.shared.exception.ErrorCode.PARAM_ERROR,
                    "Deployment [" + entity.getId() + "] does not have an allocated port");
        }

        String localProbeUrl;
        if ("STATIC_PREVIEW".equalsIgnoreCase(entity.getTarget()) || "DOCKER_NGINX".equalsIgnoreCase(entity.getTarget())) {
            localProbeUrl = "http://127.0.0.1:" + entity.getPort() + "/api/sandbox/preview/" + entity.getProjectId();
        } else {
            String path = (entity.getHealthCheckPath() != null && !entity.getHealthCheckPath().isBlank())
                    ? entity.getHealthCheckPath() : "/";
            String normalizedPath = path.startsWith("/") ? path : "/" + path;
            localProbeUrl = "http://127.0.0.1:" + entity.getPort() + normalizedPath;
        }

        long start = System.currentTimeMillis();
        boolean healthy = healthCheckProbeService.probe(localProbeUrl, 2, 200);
        long elapsed = System.currentTimeMillis() - start;

        String logAppend = String.format("\n[PROBE AT %s] URL: %s, Result: %s (latency: %dms)\n",
                LocalDateTime.now(), localProbeUrl, (healthy ? "HEALTHY" : "UNHEALTHY"), elapsed);
        String currentLogs = entity.getLogOutput() != null ? entity.getLogOutput() : "";
        entity.setLogOutput(currentLogs + logAppend);
        if (healthy && !DeploymentStatus.RUNNING.name().equals(entity.getStatus())) {
            lifecycleSupport.transitionState(entity, DeploymentStatus.RUNNING);
        } else if (!healthy && DeploymentStatus.RUNNING.name().equals(entity.getStatus())) {
            entity.setErrorMessage("Health check probe failed: " + localProbeUrl + " is unresponsive (latency: " + elapsed + "ms)");
        }
        entity.setUpdatedAt(LocalDateTime.now());
        deploymentRepository.save(entity);
        return new ProbeOutcome(healthy, elapsed);
    }
}
