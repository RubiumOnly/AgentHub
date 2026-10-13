package com.agenthub.execution.application;

import com.agenthub.audit.infrastructure.entity.ArtifactEntity;
import com.agenthub.audit.infrastructure.repository.ArtifactRepository;
import com.agenthub.domain.workspace.service.JGitWorkspaceManager;
import com.agenthub.domain.workspace.service.WorkspaceResolver;
import com.agenthub.execution.domain.model.CancelTokenRegistry;
import com.agenthub.execution.domain.model.StepRunStatus;
import com.agenthub.execution.domain.model.WorkflowRunStatus;
import com.agenthub.execution.dto.StartRunCommand;
import com.agenthub.execution.dto.StepRunView;
import com.agenthub.execution.dto.WorkflowRunView;
import com.agenthub.execution.infrastructure.entity.StepRunEntity;
import com.agenthub.execution.infrastructure.entity.WorkflowRunEntity;
import com.agenthub.execution.infrastructure.repository.StepRunRepository;
import com.agenthub.execution.infrastructure.repository.WorkflowRunRepository;
import com.agenthub.execution.service.ExecutionStateMachine;
import com.agenthub.execution.service.RunEventBroadcaster;
import com.agenthub.identity.infrastructure.security.ResourceAccessGuard;
import com.agenthub.orchestration.domain.dsl.WorkflowDsl;
import com.agenthub.orchestration.infrastructure.entity.WorkflowDefinitionEntity;
import com.agenthub.orchestration.infrastructure.repository.WorkflowDefinitionRepository;
import com.agenthub.orchestration.scheduler.DagExecutionEngine;
import com.agenthub.project.infrastructure.entity.ProjectEntity;
import com.agenthub.project.infrastructure.entity.WorkspaceEntity;
import com.agenthub.project.infrastructure.repository.ProjectRepository;
import com.agenthub.project.infrastructure.repository.WorkspaceRepository;
import com.agenthub.shared.context.RequestContext;
import com.agenthub.shared.exception.BusinessException;
import com.agenthub.shared.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Internal deep module responsible for mutating WorkflowRun and StepRun commands
 * (startRun, cancelRun, pauseRun, resumeRun, retryStep) with idempotency, baseline commit
 * creation, and post-commit DAG scheduling.
 */
@Component
public class RunCommandModule {

    private static final Logger log = LoggerFactory.getLogger(RunCommandModule.class);

    private final WorkflowRunRepository workflowRunRepository;
    private final StepRunRepository stepRunRepository;
    private final ProjectRepository projectRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceResolver workspaceResolver;
    private final JGitWorkspaceManager gitManager;
    private final ArtifactRepository artifactRepository;
    private final ResourceAccessGuard accessGuard;
    private final ExecutionStateMachine stateMachine;
    private final CancelTokenRegistry cancelTokenRegistry;
    private final RunEventBroadcaster broadcaster;
    private final com.agenthub.identity.infrastructure.security.ResourceOwnershipVerifier ownershipVerifier;

    @Autowired(required = false)
    private WorkflowDefinitionRepository workflowDefinitionRepository;

    @Autowired(required = false)
    private DagExecutionEngine dagExecutionEngine;

    @Autowired(required = false)
    private ObjectMapper objectMapper;

    public RunCommandModule(WorkflowRunRepository workflowRunRepository,
                            StepRunRepository stepRunRepository,
                            ProjectRepository projectRepository,
                            WorkspaceRepository workspaceRepository,
                            WorkspaceResolver workspaceResolver,
                            JGitWorkspaceManager gitManager,
                            ArtifactRepository artifactRepository,
                            ResourceAccessGuard accessGuard,
                            ExecutionStateMachine stateMachine,
                            CancelTokenRegistry cancelTokenRegistry,
                            RunEventBroadcaster broadcaster) {
        this(workflowRunRepository, stepRunRepository, projectRepository, workspaceRepository,
                workspaceResolver, gitManager, artifactRepository, accessGuard, stateMachine,
                cancelTokenRegistry, broadcaster, null);
    }

    @Autowired
    public RunCommandModule(WorkflowRunRepository workflowRunRepository,
                            StepRunRepository stepRunRepository,
                            ProjectRepository projectRepository,
                            WorkspaceRepository workspaceRepository,
                            WorkspaceResolver workspaceResolver,
                            JGitWorkspaceManager gitManager,
                            ArtifactRepository artifactRepository,
                            ResourceAccessGuard accessGuard,
                            ExecutionStateMachine stateMachine,
                            CancelTokenRegistry cancelTokenRegistry,
                            RunEventBroadcaster broadcaster,
                            @Autowired(required = false) com.agenthub.identity.infrastructure.security.ResourceOwnershipVerifier ownershipVerifier) {
        this.workflowRunRepository = workflowRunRepository;
        this.stepRunRepository = stepRunRepository;
        this.projectRepository = projectRepository;
        this.workspaceRepository = workspaceRepository;
        this.workspaceResolver = workspaceResolver;
        this.gitManager = gitManager;
        this.artifactRepository = artifactRepository;
        this.accessGuard = accessGuard;
        this.stateMachine = stateMachine;
        this.cancelTokenRegistry = cancelTokenRegistry;
        this.broadcaster = broadcaster;
        this.ownershipVerifier = ownershipVerifier != null ? ownershipVerifier
                : (accessGuard != null ? new com.agenthub.identity.infrastructure.security.ResourceOwnershipVerifier(accessGuard, projectRepository, workspaceRepository, null, workflowRunRepository, artifactRepository, null) : null);
    }

    @Transactional
    public WorkflowRunView startRun(StartRunCommand cmd) {
        if (cmd == null || cmd.getProjectId() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Project ID must not be null");
        }

        if (ownershipVerifier != null) {
            ownershipVerifier.verifyProjectAccess(cmd.getProjectId());
        } else {
            ProjectEntity project = projectRepository.findById(cmd.getProjectId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND, "Project not found: " + cmd.getProjectId()));
            accessGuard.checkOwnership(project.getOwnerId(), RequestContext.get().getUserId());
        }

        if (cmd.getIdempotencyKey() != null && !cmd.getIdempotencyKey().isBlank()) {
            Optional<WorkflowRunEntity> existing = workflowRunRepository.findByIdempotencyKey(cmd.getIdempotencyKey());
            if (existing.isPresent()) {
                return RunQueryModule.toRunView(existing.get());
            }
        }

        String runId = "run-" + UUID.randomUUID().toString().substring(0, 8);
        String targetDefId = cmd.getDefinitionId() != null && !cmd.getDefinitionId().isBlank() ? cmd.getDefinitionId() : "def-default";
        WorkflowRunEntity run = new WorkflowRunEntity(
                runId,
                cmd.getProjectId(),
                targetDefId,
                "RUNNING",
                cmd.getIdempotencyKey()
        );
        run.setStartedAt(LocalDateTime.now());
        run.setCorrelationId(RequestContext.get().getCorrelationId());

        WorkflowDsl dslToRun = null;
        if (cmd.getWorkflowDsl() != null) {
            dslToRun = cmd.getWorkflowDsl();
        } else if (workflowDefinitionRepository != null) {
            Optional<WorkflowDefinitionEntity> defOpt = workflowDefinitionRepository.findById(targetDefId);
            if (defOpt.isPresent() && objectMapper != null) {
                try {
                    dslToRun = objectMapper.readValue(defOpt.get().getDslJson(), WorkflowDsl.class);
                } catch (Exception e) {
                    log.warn("Failed to parse DSL JSON for run [{}]: {}", runId, e.getMessage());
                }
            }
        }

        if (dslToRun != null && objectMapper != null) {
            try {
                run.setDslSnapshot(objectMapper.writeValueAsString(dslToRun));
            } catch (Exception ignored) {}
        }

        run.setLeaseOwner(DagExecutionEngine.INSTANCE_WORKER_ID);
        run.setLeaseUntil(LocalDateTime.now().plusSeconds(180));
        run.setHeartbeatAt(LocalDateTime.now());
        run.setAttempt(1);
        workflowRunRepository.save(run);

        broadcaster.publishEvent(runId, "RUN_STARTED", "Workflow execution started");

        workspaceRepository.findByProjectId(cmd.getProjectId()).ifPresent(ws -> {
            try {
                Path root = workspaceResolver.getWorkspaceRoot(ws.getId());
                String author = RequestContext.get().getUserId();
                String baselineHash = gitManager.createBaseline(root.toFile(), runId, author);
                ws.setGitBaselineCommit(baselineHash);
                workspaceRepository.save(ws);

                String artId = "art-base-" + UUID.randomUUID().toString().substring(0, 8);
                ArtifactEntity baselineArtifact = new ArtifactEntity(
                        artId, runId, null, "BASELINE", baselineHash, baselineHash,
                        "{\"baselineCommit\":\"" + baselineHash + "\"}"
                );
                baselineArtifact.setReviewStatus("ACCEPTED");
                artifactRepository.save(baselineArtifact);
                log.info("Established JGit baseline commit [{}] for run [{}] in workspace [{}]", baselineHash, runId, ws.getId());
            } catch (Exception e) {
                log.warn("Failed to initialize git baseline for run {}: {}", runId, e.getMessage());
            }
        });

        if (dagExecutionEngine != null && dslToRun != null) {
            String wsPath = null;
            Optional<WorkspaceEntity> wsOpt = workspaceRepository.findByProjectId(cmd.getProjectId());
            if (wsOpt.isPresent()) {
                try {
                    wsPath = workspaceResolver.getWorkspaceRoot(wsOpt.get().getId()).toAbsolutePath().toString();
                } catch (Exception ignored) {}
            }
            final WorkflowDsl finalDsl = dslToRun;
            final String finalWsPath = wsPath;
            final Map<String, Object> finalInputs = cmd.getInputs();
            long timeout = finalDsl.getTimeoutSeconds() != null && finalDsl.getTimeoutSeconds() > 0 ? finalDsl.getTimeoutSeconds() : 120;
            String currentUserId = RequestContext.get().getUserId();

            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                TransactionSynchronizationManager.registerSynchronization(
                        new TransactionSynchronization() {
                            @Override
                            public void afterCommit() {
                                dagExecutionEngine.executeDag(runId, finalDsl, finalWsPath, finalInputs, timeout, currentUserId);
                            }
                        }
                );
            } else {
                dagExecutionEngine.executeDag(runId, finalDsl, finalWsPath, finalInputs, timeout, currentUserId);
            }
        }

        return RunQueryModule.toRunView(run);
    }

    @Transactional
    public WorkflowRunView cancelRun(String runId, String reason) {
        WorkflowRunEntity run = ownershipVerifier != null ? ownershipVerifier.verifyRunAccess(runId) : verifyRunAccessFallback(runId);

        String cancelReason = reason != null && !reason.isBlank() ? reason : "User cancelled execution";

        cancelTokenRegistry.cancel(runId, cancelReason);

        List<StepRunEntity> steps = stepRunRepository.findByRunIdOrderByCreatedAtAsc(runId);
        for (StepRunEntity step : steps) {
            StepRunStatus status = StepRunStatus.fromString(step.getStatus());
            if (!status.isTerminal()) {
                stateMachine.transitionStep(step.getId(), StepRunStatus.CANCELLED, cancelReason);
            }
        }

        WorkflowRunEntity updated = stateMachine.transitionRun(runId, WorkflowRunStatus.CANCELLED, cancelReason);
        broadcaster.publishEvent(runId, "RUN_CANCELLED", "{\"reason\":\"" + cancelReason + "\"}");

        return RunQueryModule.toRunView(updated);
    }

    @Transactional
    public WorkflowRunView pauseRun(String runId) {
        WorkflowRunEntity run = ownershipVerifier != null ? ownershipVerifier.verifyRunAccess(runId) : verifyRunAccessFallback(runId);

        WorkflowRunEntity updated = stateMachine.transitionRun(runId, WorkflowRunStatus.PAUSED, "Paused by user");
        broadcaster.publishEvent(runId, "RUN_PAUSED", "{\"reason\":\"Paused by user\"}");
        return RunQueryModule.toRunView(updated);
    }

    @Transactional
    public WorkflowRunView resumeRun(String runId) {
        WorkflowRunEntity run = ownershipVerifier != null ? ownershipVerifier.verifyRunAccess(runId) : verifyRunAccessFallback(runId);

        WorkflowRunEntity updated = stateMachine.transitionRun(runId, WorkflowRunStatus.RUNNING, "Resumed by user");
        broadcaster.publishEvent(runId, "RUN_RESUMED", "{\"reason\":\"Resumed by user\"}");
        return RunQueryModule.toRunView(updated);
    }

    @Transactional
    public StepRunView retryStep(String stepRunId) {
        StepRunEntity step = stepRunRepository.findById(stepRunId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STEP_RUN_NOT_FOUND, "Step run not found: " + stepRunId));
        WorkflowRunEntity run = ownershipVerifier != null ? ownershipVerifier.verifyRunAccess(step.getRunId()) : verifyRunAccessFallback(step.getRunId());

        StepRunEntity updated = stateMachine.transitionStep(stepRunId, StepRunStatus.PENDING, "Retry requested by user");
        broadcaster.publishEvent(run.getId(), "STEP_RETRY_INITIATED", "{\"stepRunId\":\"" + stepRunId + "\"}");
        return RunQueryModule.toStepView(updated);
    }

    private WorkflowRunEntity verifyRunAccessFallback(String runId) {
        WorkflowRunEntity run = workflowRunRepository.findById(runId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RUN_NOT_FOUND, "Run not found: " + runId));
        ProjectEntity project = projectRepository.findById(run.getProjectId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND, "Project not found: " + run.getProjectId()));
        accessGuard.checkOwnership(project.getOwnerId(), RequestContext.get().getUserId());
        return run;
    }
}
