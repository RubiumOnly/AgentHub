package com.agenthub.execution.application;

import com.agenthub.audit.infrastructure.entity.ArtifactEntity;
import com.agenthub.audit.infrastructure.repository.ArtifactRepository;
import com.agenthub.domain.workspace.service.JGitWorkspaceManager;
import com.agenthub.domain.workspace.service.WorkspaceResolver;
import com.agenthub.execution.domain.model.CancelTokenRegistry;
import com.agenthub.execution.domain.model.StepRunStatus;
import com.agenthub.execution.domain.model.WorkflowRunStatus;
import com.agenthub.execution.dto.RunEventView;
import com.agenthub.execution.dto.StartRunCommand;
import com.agenthub.execution.dto.StepRunView;
import com.agenthub.execution.dto.WorkflowRunView;
import com.agenthub.execution.infrastructure.entity.RunEventEntity;
import com.agenthub.execution.infrastructure.entity.StepRunEntity;
import com.agenthub.execution.infrastructure.entity.WorkflowRunEntity;
import com.agenthub.execution.infrastructure.repository.RunEventRepository;
import com.agenthub.execution.infrastructure.repository.StepRunRepository;
import com.agenthub.execution.infrastructure.repository.WorkflowRunRepository;
import com.agenthub.execution.scheduler.ExecutionScheduler;
import com.agenthub.execution.service.ExecutionStateMachine;
import com.agenthub.execution.service.RunEventBroadcaster;
import com.agenthub.identity.infrastructure.security.ResourceAccessGuard;
import com.agenthub.project.infrastructure.entity.ProjectEntity;
import com.agenthub.project.infrastructure.repository.ProjectRepository;
import com.agenthub.project.infrastructure.repository.WorkspaceRepository;
import com.agenthub.shared.context.RequestContext;
import com.agenthub.shared.exception.BusinessException;
import com.agenthub.shared.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ExecutionApplicationService implements ExecutionApplication {

    private static final Logger log = LoggerFactory.getLogger(ExecutionApplicationService.class);

    private final WorkflowRunRepository workflowRunRepository;
    private final StepRunRepository stepRunRepository;
    private final RunEventRepository runEventRepository;
    private final ProjectRepository projectRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceResolver workspaceResolver;
    private final JGitWorkspaceManager gitManager;
    private final ArtifactRepository artifactRepository;
    private final ResourceAccessGuard accessGuard;
    private final ExecutionStateMachine stateMachine;
    private final CancelTokenRegistry cancelTokenRegistry;
    private final RunEventBroadcaster broadcaster;
    private final ExecutionScheduler executionScheduler;

    public ExecutionApplicationService(WorkflowRunRepository workflowRunRepository,
                                       StepRunRepository stepRunRepository,
                                       RunEventRepository runEventRepository,
                                       ProjectRepository projectRepository,
                                       WorkspaceRepository workspaceRepository,
                                       WorkspaceResolver workspaceResolver,
                                       JGitWorkspaceManager gitManager,
                                       ArtifactRepository artifactRepository,
                                       ResourceAccessGuard accessGuard,
                                       ExecutionStateMachine stateMachine,
                                       CancelTokenRegistry cancelTokenRegistry,
                                       RunEventBroadcaster broadcaster,
                                       ExecutionScheduler executionScheduler) {
        this.workflowRunRepository = workflowRunRepository;
        this.stepRunRepository = stepRunRepository;
        this.runEventRepository = runEventRepository;
        this.projectRepository = projectRepository;
        this.workspaceRepository = workspaceRepository;
        this.workspaceResolver = workspaceResolver;
        this.gitManager = gitManager;
        this.artifactRepository = artifactRepository;
        this.accessGuard = accessGuard;
        this.stateMachine = stateMachine;
        this.cancelTokenRegistry = cancelTokenRegistry;
        this.broadcaster = broadcaster;
        this.executionScheduler = executionScheduler;
    }

    @Override
    @Transactional
    public WorkflowRunView startRun(StartRunCommand cmd) {
        if (cmd == null || cmd.getProjectId() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Project ID must not be null");
        }

        ProjectEntity project = projectRepository.findById(cmd.getProjectId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND, "Project not found: " + cmd.getProjectId()));
        accessGuard.checkOwnership(project.getOwnerId(), RequestContext.get().getUserId());

        // Idempotency check
        if (cmd.getIdempotencyKey() != null && !cmd.getIdempotencyKey().isBlank()) {
            Optional<WorkflowRunEntity> existing = workflowRunRepository.findByIdempotencyKey(cmd.getIdempotencyKey());
            if (existing.isPresent()) {
                return toRunView(existing.get());
            }
        }

        String runId = "run-" + UUID.randomUUID().toString().substring(0, 8);
        WorkflowRunEntity run = new WorkflowRunEntity(
                runId,
                cmd.getProjectId(),
                cmd.getDefinitionId() != null ? cmd.getDefinitionId() : "def-default",
                "RUNNING",
                cmd.getIdempotencyKey()
        );
        run.setStartedAt(LocalDateTime.now());
        run.setCorrelationId(RequestContext.get().getCorrelationId());
        workflowRunRepository.save(run);

        // Record initial event through broadcaster (produces sequence 1)
        broadcaster.publishEvent(runId, "RUN_STARTED", "Workflow execution started");

        // Establish JGit baseline commit if workspace exists
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

        return toRunView(run);
    }

    @Override
    @Transactional(readOnly = true)
    public WorkflowRunView getRunById(String runId) {
        WorkflowRunEntity run = workflowRunRepository.findById(runId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RUN_NOT_FOUND, "Run not found: " + runId));
        ProjectEntity project = projectRepository.findById(run.getProjectId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND, "Project not found: " + run.getProjectId()));
        accessGuard.checkOwnership(project.getOwnerId(), RequestContext.get().getUserId());
        return toRunView(run);
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkflowRunView> listRunsByProjectId(String projectId) {
        ProjectEntity project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND, "Project not found: " + projectId));
        accessGuard.checkOwnership(project.getOwnerId(), RequestContext.get().getUserId());
        return workflowRunRepository.findByProjectIdOrderByCreatedAtDesc(projectId).stream()
                .map(this::toRunView)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<StepRunView> listStepRuns(String runId) {
        WorkflowRunEntity run = workflowRunRepository.findById(runId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RUN_NOT_FOUND, "Run not found: " + runId));
        ProjectEntity project = projectRepository.findById(run.getProjectId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND, "Project not found: " + run.getProjectId()));
        accessGuard.checkOwnership(project.getOwnerId(), RequestContext.get().getUserId());
        return stepRunRepository.findByRunIdOrderByCreatedAtAsc(runId).stream()
                .map(this::toStepView)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public RunEventView appendEvent(String runId, String eventType, String payload) {
        WorkflowRunEntity run = workflowRunRepository.findById(runId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RUN_NOT_FOUND, "Run not found: " + runId));
        ProjectEntity project = projectRepository.findById(run.getProjectId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND, "Project not found: " + run.getProjectId()));
        accessGuard.checkOwnership(project.getOwnerId(), RequestContext.get().getUserId());

        RunEventEntity event = broadcaster.publishEvent(runId, eventType, payload);
        return toEventView(event);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RunEventView> listEvents(String runId, Long afterSeq) {
        WorkflowRunEntity run = workflowRunRepository.findById(runId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RUN_NOT_FOUND, "Run not found: " + runId));
        ProjectEntity project = projectRepository.findById(run.getProjectId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND, "Project not found: " + run.getProjectId()));
        accessGuard.checkOwnership(project.getOwnerId(), RequestContext.get().getUserId());

        List<RunEventEntity> events;
        if (afterSeq != null && afterSeq > 0) {
            events = runEventRepository.findByRunIdAndSequenceNumGreaterThanOrderBySequenceNumAsc(runId, afterSeq);
        } else {
            events = runEventRepository.findByRunIdOrderBySequenceNumAsc(runId);
        }
        return events.stream().map(this::toEventView).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public WorkflowRunView cancelRun(String runId, String reason) {
        WorkflowRunEntity run = workflowRunRepository.findById(runId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RUN_NOT_FOUND, "Run not found: " + runId));
        ProjectEntity project = projectRepository.findById(run.getProjectId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND, "Project not found: " + run.getProjectId()));
        accessGuard.checkOwnership(project.getOwnerId(), RequestContext.get().getUserId());

        String cancelReason = reason != null && !reason.isBlank() ? reason : "User cancelled execution";

        // Signal CancelToken
        cancelTokenRegistry.cancel(runId, cancelReason);

        // Cancel running and pending steps
        List<StepRunEntity> steps = stepRunRepository.findByRunIdOrderByCreatedAtAsc(runId);
        for (StepRunEntity step : steps) {
            StepRunStatus status = StepRunStatus.fromString(step.getStatus());
            if (!status.isTerminal()) {
                stateMachine.transitionStep(step.getId(), StepRunStatus.CANCELLED, cancelReason);
            }
        }

        // Transition run state
        WorkflowRunEntity updated = stateMachine.transitionRun(runId, WorkflowRunStatus.CANCELLED, cancelReason);
        broadcaster.publishEvent(runId, "RUN_CANCELLED", "{\"reason\":\"" + cancelReason + "\"}");

        return toRunView(updated);
    }

    @Override
    @Transactional
    public WorkflowRunView pauseRun(String runId) {
        WorkflowRunEntity run = workflowRunRepository.findById(runId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RUN_NOT_FOUND, "Run not found: " + runId));
        ProjectEntity project = projectRepository.findById(run.getProjectId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND, "Project not found: " + run.getProjectId()));
        accessGuard.checkOwnership(project.getOwnerId(), RequestContext.get().getUserId());

        WorkflowRunEntity updated = stateMachine.transitionRun(runId, WorkflowRunStatus.PAUSED, "Paused by user");
        broadcaster.publishEvent(runId, "RUN_PAUSED", "{\"reason\":\"Paused by user\"}");
        return toRunView(updated);
    }

    @Override
    @Transactional
    public WorkflowRunView resumeRun(String runId) {
        WorkflowRunEntity run = workflowRunRepository.findById(runId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RUN_NOT_FOUND, "Run not found: " + runId));
        ProjectEntity project = projectRepository.findById(run.getProjectId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND, "Project not found: " + run.getProjectId()));
        accessGuard.checkOwnership(project.getOwnerId(), RequestContext.get().getUserId());

        WorkflowRunEntity updated = stateMachine.transitionRun(runId, WorkflowRunStatus.RUNNING, "Resumed by user");
        broadcaster.publishEvent(runId, "RUN_RESUMED", "{\"reason\":\"Resumed by user\"}");
        return toRunView(updated);
    }

    @Override
    @Transactional
    public StepRunView retryStep(String stepRunId) {
        StepRunEntity step = stepRunRepository.findById(stepRunId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STEP_RUN_NOT_FOUND, "Step run not found: " + stepRunId));
        WorkflowRunEntity run = workflowRunRepository.findById(step.getRunId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RUN_NOT_FOUND, "Run not found: " + step.getRunId()));
        ProjectEntity project = projectRepository.findById(run.getProjectId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND, "Project not found: " + run.getProjectId()));
        accessGuard.checkOwnership(project.getOwnerId(), RequestContext.get().getUserId());

        StepRunEntity updated = stateMachine.transitionStep(stepRunId, StepRunStatus.PENDING, "Retry requested by user");
        broadcaster.publishEvent(run.getId(), "STEP_RETRY_INITIATED", "{\"stepRunId\":\"" + stepRunId + "\"}");
        return toStepView(updated);
    }

    @Override
    public SseEmitter subscribeRunStream(String runId, Long lastEventId) {
        WorkflowRunEntity run = workflowRunRepository.findById(runId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RUN_NOT_FOUND, "Run not found: " + runId));
        ProjectEntity project = projectRepository.findById(run.getProjectId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND, "Project not found: " + run.getProjectId()));
        accessGuard.checkOwnership(project.getOwnerId(), RequestContext.get().getUserId());

        return broadcaster.subscribe(runId, lastEventId);
    }

    private WorkflowRunView toRunView(WorkflowRunEntity entity) {
        return new WorkflowRunView(
                entity.getId(),
                entity.getProjectId(),
                entity.getDefinitionId(),
                entity.getStatus(),
                entity.getIdempotencyKey(),
                entity.getStartedAt(),
                entity.getFinishedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getCancelReason(),
                entity.getCancelledAt(),
                entity.getCorrelationId()
        );
    }

    private StepRunView toStepView(StepRunEntity entity) {
        return new StepRunView(
                entity.getId(),
                entity.getRunId(),
                entity.getNodeId(),
                entity.getStatus(),
                entity.getAttempt(),
                entity.getInputRef(),
                entity.getOutputRef(),
                entity.getErrorMessage(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getStartedAt(),
                entity.getFinishedAt(),
                entity.getDurationMs(),
                entity.getCorrelationId()
        );
    }

    private RunEventView toEventView(RunEventEntity entity) {
        return new RunEventView(
                entity.getId(),
                entity.getRunId(),
                entity.getSequenceNum(),
                entity.getEventType(),
                entity.getPayload(),
                entity.getCreatedAt()
        );
    }
}
