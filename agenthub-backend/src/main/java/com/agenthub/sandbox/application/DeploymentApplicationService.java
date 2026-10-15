package com.agenthub.sandbox.application;

import com.agenthub.audit.infrastructure.entity.ArtifactEntity;
import com.agenthub.audit.infrastructure.repository.ArtifactRepository;
import com.agenthub.domain.workspace.service.WorkspaceResolver;
import com.agenthub.execution.infrastructure.entity.WorkflowRunEntity;
import com.agenthub.execution.infrastructure.repository.WorkflowRunRepository;
import com.agenthub.identity.infrastructure.security.ResourceAccessGuard;
import com.agenthub.project.infrastructure.entity.ProjectEntity;
import com.agenthub.project.infrastructure.repository.ProjectRepository;
import com.agenthub.sandbox.api.dto.CreateDeploymentRequest;
import com.agenthub.sandbox.api.dto.DeploymentResponse;
import com.agenthub.sandbox.domain.model.*;
import com.agenthub.sandbox.domain.provider.SandboxProvider;
import com.agenthub.sandbox.domain.provider.SandboxProviderFactory;
import com.agenthub.sandbox.domain.security.CommandSecurityGuard;
import com.agenthub.sandbox.domain.service.DeploymentLifecycleWorker;
import com.agenthub.sandbox.domain.service.HealthCheckProbeService;
import com.agenthub.sandbox.domain.service.PortAllocationService;
import com.agenthub.sandbox.infrastructure.entity.DeploymentEntity;
import com.agenthub.sandbox.infrastructure.repository.DeploymentRepository;
import com.agenthub.shared.context.RequestContext;
import com.agenthub.shared.exception.BusinessException;
import com.agenthub.shared.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Orchestrator application service for sandbox deployments, port allocations,
 * lifecycle Worker execution, artifact binding, and health check verification.
 */
@Service
public class DeploymentApplicationService implements DeploymentApplication {

    private static final Logger log = LoggerFactory.getLogger(DeploymentApplicationService.class);

    private static final List<String> ACTIVE_LIFECYCLE_STATUSES = List.of(
            DeploymentStatus.CREATED.name(),
            DeploymentStatus.BUILD.name(),
            DeploymentStatus.BUILDING.name(),
            DeploymentStatus.START.name(),
            DeploymentStatus.HEALTH_CHECK.name(),
            DeploymentStatus.RUNNING.name(),
            DeploymentStatus.STOP.name(),
            DeploymentStatus.CLEANUP.name()
    );

    private final DeploymentRepository deploymentRepository;
    private final WorkspaceResolver workspaceResolver;
    private final PortAllocationService portAllocationService;
    private final HealthCheckProbeService healthCheckProbeService;
    private final SandboxProviderFactory sandboxProviderFactory;
    private final ProjectRepository projectRepository;
    private final ResourceAccessGuard accessGuard;
    private final DeploymentLifecycleWorker lifecycleWorker;
    private final ArtifactRepository artifactRepository;
    private final WorkflowRunRepository workflowRunRepository;
    private final CommandSecurityGuard commandSecurityGuard = new CommandSecurityGuard();

    @Value("${agenthub.sandbox.preview-host:localhost}")
    private String previewHost = "localhost";

    @Value("${agenthub.sandbox.external-base-url:}")
    private String externalBaseUrl = "";

    @Autowired(required = false)
    private com.agenthub.identity.infrastructure.security.ResourceOwnershipVerifier ownershipVerifier;

    public DeploymentApplicationService(DeploymentRepository deploymentRepository,
                                        WorkspaceResolver workspaceResolver,
                                        PortAllocationService portAllocationService,
                                        HealthCheckProbeService healthCheckProbeService,
                                        SandboxProviderFactory sandboxProviderFactory) {
        this(deploymentRepository, workspaceResolver, portAllocationService, healthCheckProbeService,
                sandboxProviderFactory, null, null, null, null, null, null);
    }

    public DeploymentApplicationService(DeploymentRepository deploymentRepository,
                                        WorkspaceResolver workspaceResolver,
                                        PortAllocationService portAllocationService,
                                        HealthCheckProbeService healthCheckProbeService,
                                        SandboxProviderFactory sandboxProviderFactory,
                                        ProjectRepository projectRepository,
                                        ResourceAccessGuard accessGuard) {
        this(deploymentRepository, workspaceResolver, portAllocationService, healthCheckProbeService,
                sandboxProviderFactory, projectRepository, accessGuard, null, null, null, null);
    }

    public DeploymentApplicationService(DeploymentRepository deploymentRepository,
                                        WorkspaceResolver workspaceResolver,
                                        PortAllocationService portAllocationService,
                                        HealthCheckProbeService healthCheckProbeService,
                                        SandboxProviderFactory sandboxProviderFactory,
                                        ProjectRepository projectRepository,
                                        ResourceAccessGuard accessGuard,
                                        DeploymentLifecycleWorker lifecycleWorker,
                                        ArtifactRepository artifactRepository,
                                        WorkflowRunRepository workflowRunRepository) {
        this(deploymentRepository, workspaceResolver, portAllocationService, healthCheckProbeService,
                sandboxProviderFactory, projectRepository, accessGuard, lifecycleWorker, artifactRepository, workflowRunRepository, null);
    }

    @Autowired
    public DeploymentApplicationService(DeploymentRepository deploymentRepository,
                                        WorkspaceResolver workspaceResolver,
                                        PortAllocationService portAllocationService,
                                        HealthCheckProbeService healthCheckProbeService,
                                        SandboxProviderFactory sandboxProviderFactory,
                                        @Autowired(required = false) ProjectRepository projectRepository,
                                        @Autowired(required = false) ResourceAccessGuard accessGuard,
                                        @Autowired(required = false) DeploymentLifecycleWorker lifecycleWorker,
                                        @Autowired(required = false) ArtifactRepository artifactRepository,
                                        @Autowired(required = false) WorkflowRunRepository workflowRunRepository,
                                        @Autowired(required = false) com.agenthub.identity.infrastructure.security.ResourceOwnershipVerifier ownershipVerifier) {
        this.deploymentRepository = deploymentRepository;
        this.workspaceResolver = workspaceResolver;
        this.portAllocationService = portAllocationService;
        this.healthCheckProbeService = healthCheckProbeService;
        this.sandboxProviderFactory = sandboxProviderFactory;
        this.projectRepository = projectRepository;
        this.accessGuard = accessGuard;
        this.lifecycleWorker = lifecycleWorker != null ? lifecycleWorker : new DeploymentLifecycleWorker(sandboxProviderFactory);
        this.artifactRepository = artifactRepository;
        this.workflowRunRepository = workflowRunRepository;
        this.ownershipVerifier = ownershipVerifier != null ? ownershipVerifier
                : (accessGuard != null ? new com.agenthub.identity.infrastructure.security.ResourceOwnershipVerifier(accessGuard, projectRepository, null, null, workflowRunRepository, artifactRepository, deploymentRepository) : null);
    }

    private void checkProjectAccess(String projectId) {
        if (RequestContext.get().isSystem()) {
            return;
        }
        if (projectRepository != null && accessGuard != null && projectId != null) {
            ProjectEntity project = projectRepository.findById(projectId).orElse(null);
            if (project != null) {
                if (ownershipVerifier != null) {
                    ownershipVerifier.verifyProjectAccess(projectId);
                } else {
                    accessGuard.checkOwnership(project.getOwnerId(), RequestContext.get().getUserId());
                }
            } else if (accessGuard.isAllowSuperuserBypass()) {
                return;
            } else if (ownershipVerifier != null) {
                ownershipVerifier.verifyProjectAccess(projectId);
            } else {
                throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND, "Project not found: " + projectId);
            }
        } else if (ownershipVerifier != null && projectId != null) {
            ownershipVerifier.verifyProjectAccess(projectId);
        }
    }

    private void checkDeploymentManageAccess(String projectId) {
        if (RequestContext.get().isSystem()) {
            return;
        }
        if (projectRepository != null && accessGuard != null && projectId != null) {
            ProjectEntity project = projectRepository.findById(projectId).orElse(null);
            if (project != null) {
                if (ownershipVerifier != null) {
                    ownershipVerifier.verifyProjectAccess(projectId, RequestContext.get().getUserId(), com.agenthub.team.domain.rbac.ProjectPermission.DEPLOYMENT_MANAGE);
                } else {
                    accessGuard.checkOwnership(project.getOwnerId(), RequestContext.get().getUserId());
                }
            } else if (accessGuard.isAllowSuperuserBypass()) {
                return;
            } else if (ownershipVerifier != null) {
                ownershipVerifier.verifyProjectAccess(projectId, RequestContext.get().getUserId(), com.agenthub.team.domain.rbac.ProjectPermission.DEPLOYMENT_MANAGE);
            } else {
                throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND, "Project not found: " + projectId);
            }
        } else if (ownershipVerifier != null && projectId != null) {
            ownershipVerifier.verifyProjectAccess(projectId, RequestContext.get().getUserId(), com.agenthub.team.domain.rbac.ProjectPermission.DEPLOYMENT_MANAGE);
        }
    }

    private String resolveAndValidateArtifact(String projectId, String requestedArtifactId, String deployId) {
        if (requestedArtifactId != null && !requestedArtifactId.isBlank()) {
            if (artifactRepository != null) {
                ArtifactEntity artifact = artifactRepository.findById(requestedArtifactId.trim())
                        .orElseThrow(() -> new BusinessException(ErrorCode.ARTIFACT_NOT_FOUND,
                                "Artifact [" + requestedArtifactId + "] not found"));
                String reviewStatus = artifact.getReviewStatus();
                if ("REJECTED".equalsIgnoreCase(reviewStatus) || "REVERTED".equalsIgnoreCase(reviewStatus) || "GATE_FAILED".equalsIgnoreCase(reviewStatus)) {
                    throw new BusinessException(ErrorCode.PARAM_ERROR,
                            "Artifact [" + requestedArtifactId + "] cannot be deployed because its quality gate status is " + reviewStatus);
                }
                if (workflowRunRepository != null && artifact.getRunId() != null) {
                    WorkflowRunEntity run = workflowRunRepository.findById(artifact.getRunId()).orElse(null);
                    if (run != null && !projectId.equals(run.getProjectId())) {
                        throw new BusinessException(ErrorCode.FORBIDDEN,
                                "Artifact [" + requestedArtifactId + "] does not belong to project [" + projectId + "]");
                    }
                }
                return artifact.getId();
            }
            return requestedArtifactId.trim();
        }
        return "art-" + deployId;
    }

    private DeploymentTemplateType resolveAndValidateTemplate(String requestedTemplate, String targetStr) {
        DeploymentTarget target = DeploymentTarget.STATIC_PREVIEW;
        if (targetStr != null) {
            for (DeploymentTarget t : DeploymentTarget.values()) {
                if (t.name().equalsIgnoreCase(targetStr.trim())) {
                    target = t;
                    break;
                }
            }
        }
        if (requestedTemplate != null && !requestedTemplate.isBlank()) {
            for (DeploymentTemplateType type : DeploymentTemplateType.values()) {
                if (type.name().equalsIgnoreCase(requestedTemplate.trim())) {
                    return type;
                }
            }
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "Unsupported deployment template [" + requestedTemplate + "]. Supported templates: "
                            + Arrays.toString(DeploymentTemplateType.values()));
        }
        return DeploymentTemplateType.fromStringOrDefault(requestedTemplate, target);
    }

    private void transitionState(DeploymentEntity entity, DeploymentStatus nextStatus) {
        entity.setStatus(nextStatus.name());
        String currentTrace = entity.getLifecycleTrace();
        if (currentTrace == null || currentTrace.isBlank()) {
            entity.setLifecycleTrace(nextStatus.name());
        } else if (!currentTrace.endsWith("->" + nextStatus.name()) && !currentTrace.equals(nextStatus.name())) {
            entity.setLifecycleTrace(currentTrace + "->" + nextStatus.name());
        }
        entity.setUpdatedAt(LocalDateTime.now());
    }

    private String buildPreviewUrl(String target, int port, String projectId, String healthPath) {
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

    @Override
    public DeploymentResponse createAndDeploy(CreateDeploymentRequest request) {
        if (request == null || request.getProjectId() == null || request.getProjectId().isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Project ID cannot be empty for deployment");
        }

        String projectId = request.getProjectId().trim();
        checkDeploymentManageAccess(projectId);
        Path workspaceRoot = workspaceResolver.getWorkspaceRoot(projectId);

        String deployId = "dep-" + UUID.randomUUID().toString().substring(0, 8);
        String target = (request.getTarget() != null && !request.getTarget().isBlank())
                ? request.getTarget().trim().toUpperCase(Locale.ROOT)
                : "STATIC_PREVIEW";
        String sandboxType = (request.getSandboxType() != null && !request.getSandboxType().isBlank())
                ? request.getSandboxType().trim().toUpperCase(Locale.ROOT)
                : "LOCAL_PROCESS";
        String healthPath = (request.getHealthCheckPath() != null && !request.getHealthCheckPath().isBlank())
                ? request.getHealthCheckPath().trim()
                : "/";

        DeploymentTemplateType templateType = resolveAndValidateTemplate(request.getTemplateType(), target);
        String artifactId = resolveAndValidateArtifact(projectId, request.getArtifactId(), deployId);

        // Defensively validate commands against security firewall before allocating resources
        if (request.getBuildCommand() != null && !request.getBuildCommand().isBlank()) {
            List<String> buildTokens = parseCommandTokens(request.getBuildCommand());
            commandSecurityGuard.validateCommand(
                    buildTokens.get(0),
                    buildTokens.size() > 1 ? buildTokens.subList(1, buildTokens.size()) : Collections.emptyList()
            );
        }
        if (request.getStartCommand() != null && !request.getStartCommand().isBlank()) {
            List<String> startTokens = parseCommandTokens(request.getStartCommand());
            commandSecurityGuard.validateCommand(
                    startTokens.get(0),
                    startTokens.size() > 1 ? startTokens.subList(1, startTokens.size()) : Collections.emptyList()
            );
        }

        // 1. Atomically allocate or reserve specific port with real TCP socket verification
        int port = portAllocationService.allocateOrReserveSpecificPort(deployId, request.getPort());

        // 2. Initialize deployment entity in CREATED state
        DeploymentEntity entity = new DeploymentEntity();
        entity.setId(deployId);
        entity.setProjectId(projectId);
        entity.setArtifactId(artifactId);
        entity.setTemplateType(templateType.name());
        entity.setTarget(target);
        entity.setSandboxType(sandboxType);
        entity.setPort(port);
        entity.setWorkingDirectory(workspaceRoot.toAbsolutePath().normalize().toString());
        entity.setBuildCommand(request.getBuildCommand());
        entity.setStartCommand(request.getStartCommand());
        entity.setHealthCheckPath(healthPath);
        entity.setStartedAt(LocalDateTime.now());
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        transitionState(entity, DeploymentStatus.CREATED);

        String previewUrl = buildPreviewUrl(target, port, projectId, healthPath);
        entity.setUrl(previewUrl);

        StringBuilder logAccumulator = new StringBuilder();
        logAccumulator.append("[PHASE: CREATED] Deployment ").append(deployId)
                .append(" bound to project=").append(projectId)
                .append(", artifact=").append(artifactId)
                .append(", template=").append(templateType.name())
                .append(", port=").append(port).append("\n");

        // 3. BUILD phase execution in sandbox
        transitionState(entity, DeploymentStatus.BUILD);
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
            SandboxExecutionResult buildResult;
            try {
                buildResult = provider.execute(execReq);
            } catch (BusinessException be) {
                portAllocationService.releasePort(port);
                throw be;
            } catch (Exception ex) {
                transitionState(entity, DeploymentStatus.CLEANUP);
                portAllocationService.releasePort(port);
                transitionState(entity, DeploymentStatus.FAILED);
                entity.setTerminationReason("BUILD_FAILED");
                entity.setErrorMessage("Build execution error: " + ex.getMessage());
                entity.setFinishedAt(LocalDateTime.now());
                logAccumulator.append("=== BUILD EXCEPTION ===\n").append(ex.getMessage()).append("\n");
                entity.setLogOutput(logAccumulator.toString());
                deploymentRepository.save(entity);
                return toResponse(entity);
            }
            logAccumulator.append("=== BUILD OUTPUT ===\n").append(buildResult.getCombinedOutput()).append("\n");

            if (!buildResult.isSuccess()) {
                log.warn("Deployment build failed for [{}]: exitCode={}, error={}", deployId,
                        buildResult.getExitCode(), buildResult.getErrorMessage());
                transitionState(entity, DeploymentStatus.CLEANUP);
                portAllocationService.releasePort(port);
                transitionState(entity, DeploymentStatus.FAILED);
                entity.setTerminationReason("BUILD_FAILED");
                entity.setErrorMessage("Build failed: " + (buildResult.getErrorMessage() != null ? buildResult.getErrorMessage() : "exit code " + buildResult.getExitCode()));
                entity.setFinishedAt(LocalDateTime.now());
                entity.setLogOutput(logAccumulator.toString());
                deploymentRepository.save(entity);
                return toResponse(entity);
            }
        } else {
            logAccumulator.append("=== BUILD PHASE SKIPPED (Direct static preview) ===\n");
        }

        // 4. START phase: launch dedicated runtime worker instance
        transitionState(entity, DeploymentStatus.START);
        DeploymentLifecycleWorker.LiveRuntimeInstance runtimeInstance = null;
        try {
            if ("DOCKER_CONTAINER".equalsIgnoreCase(target) || "DOCKER".equalsIgnoreCase(sandboxType)) {
                List<String> startTokens = parseCommandTokens(request.getStartCommand());
                String cmd = startTokens.isEmpty() ? null : startTokens.get(0);
                List<String> args = startTokens.size() > 1 ? startTokens.subList(1, startTokens.size()) : Collections.emptyList();
                runtimeInstance = lifecycleWorker.startDockerContainerService(
                        deployId, projectId, port, workspaceRoot, null, cmd, args,
                        request.getEnvVars(), request.getTimeoutMs() != null ? request.getTimeoutMs() : 60_000L
                );
            } else if ("STATIC_PREVIEW".equalsIgnoreCase(target) || "DOCKER_NGINX".equalsIgnoreCase(target)) {
                runtimeInstance = lifecycleWorker.startStaticPreviewServer(
                        deployId, projectId, artifactId, port, workspaceRoot
                );
            } else if (request.getStartCommand() != null && !request.getStartCommand().isBlank()) {
                List<String> startTokens = parseCommandTokens(request.getStartCommand());
                String baseCmd = startTokens.get(0);
                List<String> args = startTokens.size() > 1 ? startTokens.subList(1, startTokens.size()) : Collections.emptyList();
                runtimeInstance = lifecycleWorker.startLocalProcessService(
                        deployId, projectId, port, workspaceRoot, baseCmd, args,
                        request.getEnvVars(), request.getTimeoutMs() != null ? request.getTimeoutMs() : 60_000L
                );
                if (runtimeInstance.getProcessHandle() != null && !runtimeInstance.getProcessHandle().isAlive()) {
                    Integer exitCode = runtimeInstance.getProcessHandle().getExitCodeIfTerminated();
                    String procLogs = runtimeInstance.getProcessHandle().getCombinedLogs();
                    logAccumulator.append("=== START OUTPUT ===\n").append(procLogs).append("\n");
                    throw new IllegalStateException("Start process exited prematurely with code " + exitCode);
                }
            } else {
                throw new IllegalStateException("Start command is required for non-static process deployment target [" + target + "]");
            }

            if (runtimeInstance != null) {
                entity.setProcessId(runtimeInstance.getProcessId());
                entity.setContainerId(runtimeInstance.getContainerId());
                logAccumulator.append("[PHASE: START] Runtime active (containerId=")
                        .append(runtimeInstance.getContainerId())
                        .append(", pid=").append(runtimeInstance.getProcessId())
                        .append(")\n");
            }
        } catch (BusinessException be) {
            transitionState(entity, DeploymentStatus.CLEANUP);
            lifecycleWorker.stopRuntime(deployId, entity.getProcessId(), entity.getSandboxType());
            portAllocationService.releasePort(port);
            transitionState(entity, DeploymentStatus.FAILED);
            entity.setTerminationReason("START_SECURITY_OR_ISOLATION_REJECTED");
            entity.setErrorMessage(be.getMessage());
            entity.setFinishedAt(LocalDateTime.now());
            entity.setLogOutput(logAccumulator.toString());
            deploymentRepository.save(entity);
            throw be;
        } catch (Exception e) {
            log.warn("Deployment start failed for [{}]: {}", deployId, e.getMessage());
            transitionState(entity, DeploymentStatus.CLEANUP);
            lifecycleWorker.stopRuntime(deployId, entity.getProcessId(), entity.getSandboxType());
            portAllocationService.releasePort(port);
            transitionState(entity, DeploymentStatus.FAILED);
            entity.setTerminationReason("START_FAILED");
            entity.setErrorMessage("Start phase failed: " + e.getMessage());
            entity.setFinishedAt(LocalDateTime.now());
            logAccumulator.append("=== START FAILED ===\n").append(e.getMessage()).append("\n");
            entity.setLogOutput(logAccumulator.toString());
            deploymentRepository.save(entity);
            return toResponse(entity);
        }

        // 5. HEALTH_CHECK phase: verify HTTP endpoint reachability and runtime liveness
        transitionState(entity, DeploymentStatus.HEALTH_CHECK);
        String localProbeUrl;
        if ("STATIC_PREVIEW".equalsIgnoreCase(target) || "DOCKER_NGINX".equalsIgnoreCase(target)) {
            localProbeUrl = "http://127.0.0.1:" + port + "/api/sandbox/preview/" + projectId;
        } else {
            String normalizedPath = healthPath.startsWith("/") ? healthPath : "/" + healthPath;
            localProbeUrl = "http://127.0.0.1:" + port + normalizedPath;
        }

        boolean healthy = runtimeInstance != null
                && lifecycleWorker.isRuntimeAlive(deployId)
                && healthCheckProbeService.probe(localProbeUrl, 4, 200);
        if (healthy && runtimeInstance.getProcessHandle() != null) {
            healthy = runtimeInstance.getProcessHandle().isAlive();
        }

        if (healthy) {
            transitionState(entity, DeploymentStatus.RUNNING);
            entity.setFinishedAt(LocalDateTime.now());
            logAccumulator.append("=== DEPLOYMENT READY ===\nPreview active at: ").append(previewUrl).append("\n");
        } else {
            transitionState(entity, DeploymentStatus.STOP);
            String liveLogs = lifecycleWorker.getLiveProcessLogs(deployId);
            if (liveLogs != null && !liveLogs.isBlank()) {
                logAccumulator.append("=== RUNTIME LOGS BEFORE FAILURE ===\n").append(liveLogs).append("\n");
            }
            lifecycleWorker.stopRuntime(deployId, entity.getProcessId(), entity.getSandboxType());
            transitionState(entity, DeploymentStatus.CLEANUP);
            portAllocationService.releasePort(port);
            transitionState(entity, DeploymentStatus.FAILED);
            entity.setTerminationReason("HEALTH_CHECK_FAILED");
            entity.setErrorMessage("Deployment health check probe failed for " + previewUrl);
            entity.setFinishedAt(LocalDateTime.now());
            logAccumulator.append("=== HEALTH CHECK FAILED ===\nEndpoint probe failed on: ").append(previewUrl).append("\n");
        }

        entity.setLogOutput(logAccumulator.toString());
        entity.setUpdatedAt(LocalDateTime.now());
        deploymentRepository.save(entity);
        return toResponse(entity);
    }

    @Override
    public DeploymentResponse stopDeployment(String deploymentId) {
        DeploymentEntity entity = deploymentRepository.findById(deploymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DEPLOYMENT_NOT_FOUND, "Deployment [" + deploymentId + "] not found"));
        checkDeploymentManageAccess(entity.getProjectId());

        if (ACTIVE_LIFECYCLE_STATUSES.contains(entity.getStatus())) {
            transitionState(entity, DeploymentStatus.STOP);

            String liveLogs = lifecycleWorker.getLiveProcessLogs(deploymentId);
            lifecycleWorker.stopRuntime(deploymentId, entity.getProcessId(), entity.getSandboxType());

            transitionState(entity, DeploymentStatus.CLEANUP);
            if (entity.getPort() != null) {
                portAllocationService.releasePort(entity.getPort());
            }

            transitionState(entity, DeploymentStatus.STOPPED);
            entity.setTerminationReason("USER_STOPPED");
            entity.setFinishedAt(LocalDateTime.now());

            String existingLogs = entity.getLogOutput() != null ? entity.getLogOutput() : "";
            StringBuilder updatedLogs = new StringBuilder(existingLogs);
            if (liveLogs != null && !liveLogs.isBlank() && !existingLogs.contains(liveLogs)) {
                updatedLogs.append("\n=== PROCESS RUNTIME OUTPUT ===\n").append(liveLogs).append("\n");
            }
            updatedLogs.append("=== DEPLOYMENT STOPPED BY USER (CLEANUP COMPLETED) ===\n");
            entity.setLogOutput(updatedLogs.toString());

            entity.setUpdatedAt(LocalDateTime.now());
            deploymentRepository.save(entity);
            log.info("Stopped and cleaned up deployment [{}]", deploymentId);
        }
        return toResponse(entity);
    }

    /**
     * Reconciles orphaned deployments left in active states whose runtime process/server/container is no longer alive.
     * Automatically invoked on application startup, periodically via @Scheduled watchdog, and on-demand.
     */
    @Override
    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(
            initialDelayString = "${agenthub.sandbox.orphan-reconcile-initial-delay-ms:30000}",
            fixedDelayString = "${agenthub.sandbox.orphan-reconcile-interval-ms:30000}"
    )
    public int reconcileOrphanDeployments() {
        List<DeploymentEntity> candidates = deploymentRepository.findByStatusIn(ACTIVE_LIFECYCLE_STATUSES);
        int reconciled = 0;
        for (DeploymentEntity entity : candidates) {
            if (!lifecycleWorker.isRuntimeAlive(entity.getId())) {
                reconcileSingleOrphan(entity);
                reconciled++;
            }
        }
        if (reconciled > 0) {
            log.info("Reconciled {} orphaned deployment(s)", reconciled);
        }
        return reconciled;
    }

    private void reconcileSingleOrphan(DeploymentEntity entity) {
        log.warn("Reconciling orphaned deployment [{}] (previous status={})", entity.getId(), entity.getStatus());
        String liveLogs = lifecycleWorker.getLiveProcessLogs(entity.getId());
        transitionState(entity, DeploymentStatus.STOP);
        lifecycleWorker.stopRuntime(entity.getId(), entity.getProcessId(), entity.getSandboxType());
        transitionState(entity, DeploymentStatus.CLEANUP);
        if (entity.getPort() != null) {
            portAllocationService.releasePort(entity.getPort());
        }
        transitionState(entity, DeploymentStatus.FAILED);
        entity.setTerminationReason("ORPHAN_RECONCILED");
        entity.setErrorMessage("Orphaned deployment reconciled: runtime worker or child process is no longer alive");
        entity.setFinishedAt(LocalDateTime.now());
        String prevLogs = entity.getLogOutput() != null ? entity.getLogOutput() : "";
        StringBuilder sb = new StringBuilder(prevLogs);
        if (liveLogs != null && !liveLogs.isBlank() && !prevLogs.contains(liveLogs)) {
            sb.append("\n=== PROCESS OUTPUT BEFORE EXIT ===\n").append(liveLogs);
        }
        sb.append("\n=== ORPHAN RECONCILIATION: Runtime not alive, port and process tree cleaned up ===\n");
        entity.setLogOutput(sb.toString());
        entity.setUpdatedAt(LocalDateTime.now());
        deploymentRepository.save(entity);
    }

    @Override
    public DeploymentResponse getDeployment(String deploymentId) {
        DeploymentEntity entity = deploymentRepository.findById(deploymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DEPLOYMENT_NOT_FOUND, "Deployment [" + deploymentId + "] not found"));
        checkProjectAccess(entity.getProjectId());
        if (ACTIVE_LIFECYCLE_STATUSES.contains(entity.getStatus())
                && lifecycleWorker.hasTrackedRuntime(entity.getId())
                && !lifecycleWorker.isRuntimeAlive(entity.getId())) {
            reconcileSingleOrphan(entity);
        }
        return toResponse(entity);
    }

    @Override
    public List<DeploymentResponse> listDeployments(String projectId) {
        checkProjectAccess(projectId);
        List<DeploymentEntity> list = deploymentRepository.findByProjectIdOrderByStartedAtDesc(projectId);
        for (DeploymentEntity entity : list) {
            if (ACTIVE_LIFECYCLE_STATUSES.contains(entity.getStatus())
                    && lifecycleWorker.hasTrackedRuntime(entity.getId())
                    && !lifecycleWorker.isRuntimeAlive(entity.getId())) {
                reconcileSingleOrphan(entity);
            }
        }
        return list.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public String getDeploymentLogs(String deploymentId) {
        return getDeploymentLogs(deploymentId, 0L);
    }

    @Override
    @Transactional(readOnly = true)
    public String getDeploymentLogs(String deploymentId, Long fromCursor) {
        DeploymentEntity entity = deploymentRepository.findById(deploymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DEPLOYMENT_NOT_FOUND, "Deployment [" + deploymentId + "] not found"));
        checkProjectAccess(entity.getProjectId());

        String combined = buildCombinedLogs(entity);

        if (fromCursor != null && fromCursor > 0) {
            if (fromCursor >= combined.length()) {
                return "";
            }
            return combined.substring(fromCursor.intValue());
        }
        return combined;
    }

    private String buildCombinedLogs(DeploymentEntity entity) {
        String baseLogs = entity.getLogOutput() != null ? entity.getLogOutput() : "";
        String liveLogs = lifecycleWorker.getLiveProcessLogs(entity.getId());
        if (liveLogs != null && !liveLogs.isBlank() && !baseLogs.contains(liveLogs)) {
            return baseLogs + "\n" + liveLogs;
        }
        return baseLogs;
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

    @Override
    public DeploymentResponse probeDeployment(String deploymentId) {
        DeploymentEntity entity = deploymentRepository.findById(deploymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DEPLOYMENT_NOT_FOUND, "Deployment [" + deploymentId + "] not found"));
        checkProjectAccess(entity.getProjectId());

        if (entity.getPort() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Deployment [" + deploymentId + "] does not have an allocated port");
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
            transitionState(entity, DeploymentStatus.RUNNING);
        } else if (!healthy && DeploymentStatus.RUNNING.name().equals(entity.getStatus())) {
            entity.setErrorMessage("Health check probe failed: " + localProbeUrl + " is unresponsive (latency: " + elapsed + "ms)");
        }
        entity.setUpdatedAt(LocalDateTime.now());
        deploymentRepository.save(entity);

        DeploymentResponse resp = toResponse(entity);
        resp.setProbeLatencyMs(elapsed);
        resp.setHealthy(healthy);
        return resp;
    }
}
