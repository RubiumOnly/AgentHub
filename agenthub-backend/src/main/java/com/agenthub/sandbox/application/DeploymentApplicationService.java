package com.agenthub.sandbox.application;

import com.agenthub.domain.workspace.service.WorkspaceResolver;
import com.agenthub.sandbox.api.dto.CreateDeploymentRequest;
import com.agenthub.sandbox.api.dto.DeploymentResponse;
import com.agenthub.sandbox.domain.model.*;
import com.agenthub.sandbox.domain.provider.SandboxProvider;
import com.agenthub.sandbox.domain.provider.SandboxProviderFactory;
import com.agenthub.sandbox.domain.service.HealthCheckProbeService;
import com.agenthub.sandbox.domain.service.PortAllocationService;
import com.agenthub.sandbox.infrastructure.entity.DeploymentEntity;
import com.agenthub.sandbox.infrastructure.repository.DeploymentRepository;
import com.agenthub.shared.exception.BusinessException;
import com.agenthub.shared.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Orchestrator application service for sandbox deployments, port allocations,
 * and health check verification.
 */
@Service
public class DeploymentApplicationService implements DeploymentApplication {

    private static final Logger log = LoggerFactory.getLogger(DeploymentApplicationService.class);

    private final DeploymentRepository deploymentRepository;
    private final WorkspaceResolver workspaceResolver;
    private final PortAllocationService portAllocationService;
    private final HealthCheckProbeService healthCheckProbeService;
    private final SandboxProviderFactory sandboxProviderFactory;

    public DeploymentApplicationService(DeploymentRepository deploymentRepository,
                                      WorkspaceResolver workspaceResolver,
                                      PortAllocationService portAllocationService,
                                      HealthCheckProbeService healthCheckProbeService,
                                      SandboxProviderFactory sandboxProviderFactory) {
        this.deploymentRepository = deploymentRepository;
        this.workspaceResolver = workspaceResolver;
        this.portAllocationService = portAllocationService;
        this.healthCheckProbeService = healthCheckProbeService;
        this.sandboxProviderFactory = sandboxProviderFactory;
    }

    @Override
    public DeploymentResponse createAndDeploy(CreateDeploymentRequest request) {
        if (request == null || request.getProjectId() == null || request.getProjectId().isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Project ID cannot be empty for deployment");
        }

        String projectId = request.getProjectId();
        Path workspaceRoot = workspaceResolver.getWorkspaceRoot(projectId);

        String deployId = "dep-" + UUID.randomUUID().toString().substring(0, 8);
        String target = request.getTarget() != null ? request.getTarget() : "STATIC_PREVIEW";
        String sandboxType = request.getSandboxType() != null ? request.getSandboxType() : "LOCAL_PROCESS";
        String healthPath = request.getHealthCheckPath() != null ? request.getHealthCheckPath() : "/";

        // 1. Allocate port
        int port;
        if (request.getPort() != null && request.getPort() > 0) {
            port = request.getPort();
        } else {
            port = portAllocationService.allocatePort(deployId);
        }

        // 2. Initial deployment entity in BUILDING status
        DeploymentEntity entity = new DeploymentEntity();
        entity.setId(deployId);
        entity.setProjectId(projectId);
        entity.setArtifactId("art-" + deployId);
        entity.setStatus(DeploymentStatus.BUILDING.name());
        entity.setTarget(target);
        entity.setSandboxType(sandboxType);
        entity.setPort(port);
        entity.setBuildCommand(request.getBuildCommand());
        entity.setStartCommand(request.getStartCommand());
        entity.setHealthCheckPath(healthPath);
        entity.setStartedAt(LocalDateTime.now());
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());

        String previewUrl;
        if ("STATIC_PREVIEW".equalsIgnoreCase(target) || "DOCKER_NGINX".equalsIgnoreCase(target)) {
            previewUrl = "http://localhost:8080/api/sandbox/preview/" + projectId;
        } else {
            previewUrl = "http://localhost:" + port + (healthPath.startsWith("/") ? healthPath : "/" + healthPath);
        }
        entity.setUrl(previewUrl);

        StringBuilder logAccumulator = new StringBuilder();

        // 3. Optional build phase execution in sandbox
        if (request.getBuildCommand() != null && !request.getBuildCommand().isBlank()) {
            log.info("Executing deployment build command [{}] for project [{}]", request.getBuildCommand(), projectId);
            List<String> tokens = parseCommandTokens(request.getBuildCommand());
            String baseCmd = tokens.get(0);
            List<String> args = tokens.size() > 1 ? tokens.subList(1, tokens.size()) : Collections.emptyList();

            SandboxExecutionRequest execReq = SandboxExecutionRequest.builder()
                    .executionId(deployId + "-build")
                    .command(baseCmd)
                    .args(args)
                    .workingDirectory(workspaceRoot)
                    .environment(request.getEnvVars() != null ? request.getEnvVars() : Collections.emptyMap())
                    .resourceQuota(SandboxResourceQuota.of(
                            request.getTimeoutMs() != null ? request.getTimeoutMs() : 60_000L,
                            SandboxResourceQuota.DEFAULT_MAX_OUTPUT_BYTES))
                    .build();

            SandboxProvider provider = sandboxProviderFactory.getProvider(sandboxType);
            SandboxExecutionResult buildResult = provider.execute(execReq);
            logAccumulator.append("=== BUILD OUTPUT ===\n").append(buildResult.getCombinedOutput()).append("\n");

            if (!buildResult.isSuccess()) {
                log.warn("Deployment build failed for [{}]: exitCode={}, error={}", deployId,
                        buildResult.getExitCode(), buildResult.getErrorMessage());
                entity.setStatus(DeploymentStatus.FAILED.name());
                entity.setErrorMessage("Build failed: " + (buildResult.getErrorMessage() != null ? buildResult.getErrorMessage() : "exit code " + buildResult.getExitCode()));
                entity.setFinishedAt(LocalDateTime.now());
                entity.setLogOutput(logAccumulator.toString());
                portAllocationService.releasePort(port);
                deploymentRepository.save(entity);
                return toResponse(entity);
            }
        } else {
            logAccumulator.append("=== BUILD PHASE SKIPPED (Direct static preview) ===\n");
        }

        // 4. Verification and Health Check probing
        boolean healthy = true;
        if (!"STATIC_PREVIEW".equalsIgnoreCase(target) && !"DOCKER_NGINX".equalsIgnoreCase(target)) {
            // For active service deployments, probe endpoint
            healthy = healthCheckProbeService.probe(previewUrl, 2, 300);
        }

        if (healthy) {
            entity.setStatus(DeploymentStatus.RUNNING.name());
            entity.setFinishedAt(LocalDateTime.now());
            logAccumulator.append("=== DEPLOYMENT READY ===\nPreview active at: ").append(previewUrl).append("\n");
        } else {
            entity.setStatus(DeploymentStatus.FAILED.name());
            entity.setErrorMessage("Deployment health check probe failed for " + previewUrl);
            entity.setFinishedAt(LocalDateTime.now());
            logAccumulator.append("=== HEALTH CHECK FAILED ===\nEndpoint probe failed on: ").append(previewUrl).append("\n");
            portAllocationService.releasePort(port);
        }

        entity.setLogOutput(logAccumulator.toString());
        entity.setUpdatedAt(LocalDateTime.now());
        deploymentRepository.save(entity);
        return toResponse(entity);
    }

    @Override
    @Transactional
    public DeploymentResponse stopDeployment(String deploymentId) {
        DeploymentEntity entity = deploymentRepository.findById(deploymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DEPLOYMENT_NOT_FOUND, "Deployment [" + deploymentId + "] not found"));

        if (DeploymentStatus.RUNNING.name().equals(entity.getStatus()) ||
                DeploymentStatus.BUILDING.name().equals(entity.getStatus())) {
            // Forcibly destroy sandbox process or container
            try {
                sandboxProviderFactory.getProvider(entity.getSandboxType()).destroy(deploymentId);
                sandboxProviderFactory.getProvider(entity.getSandboxType()).destroy(deploymentId + "-build");
            } catch (Exception e) {
                log.warn("Error stopping sandbox container/process for deployment [{}]: {}", deploymentId, e.getMessage());
            }

            if (entity.getPort() != null) {
                portAllocationService.releasePort(entity.getPort());
            }
            entity.setStatus(DeploymentStatus.STOPPED.name());
            entity.setFinishedAt(LocalDateTime.now());
            entity.setUpdatedAt(LocalDateTime.now());
            deploymentRepository.save(entity);
            log.info("Stopped deployment [{}]", deploymentId);
        }
        return toResponse(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public DeploymentResponse getDeployment(String deploymentId) {
        DeploymentEntity entity = deploymentRepository.findById(deploymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DEPLOYMENT_NOT_FOUND, "Deployment [" + deploymentId + "] not found"));
        return toResponse(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DeploymentResponse> listDeployments(String projectId) {
        return deploymentRepository.findByProjectIdOrderByStartedAtDesc(projectId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public String getDeploymentLogs(String deploymentId) {
        DeploymentEntity entity = deploymentRepository.findById(deploymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DEPLOYMENT_NOT_FOUND, "Deployment [" + deploymentId + "] not found"));
        return entity.getLogOutput() != null ? entity.getLogOutput() : "";
    }

    private List<String> parseCommandTokens(String commandLine) {
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

    private DeploymentResponse toResponse(DeploymentEntity entity) {
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
        resp.setHasLogs(entity.getLogOutput() != null && !entity.getLogOutput().isBlank());
        return resp;
    }
}
