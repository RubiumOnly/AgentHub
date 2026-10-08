package com.agenthub.execution.application;

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
import com.agenthub.identity.infrastructure.security.ResourceAccessGuard;
import com.agenthub.project.infrastructure.entity.ProjectEntity;
import com.agenthub.project.infrastructure.repository.ProjectRepository;
import com.agenthub.shared.context.RequestContext;
import com.agenthub.shared.exception.BusinessException;
import com.agenthub.shared.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ExecutionApplicationService implements ExecutionApplication {

    private final WorkflowRunRepository workflowRunRepository;
    private final StepRunRepository stepRunRepository;
    private final RunEventRepository runEventRepository;
    private final ProjectRepository projectRepository;
    private final ResourceAccessGuard accessGuard;

    public ExecutionApplicationService(WorkflowRunRepository workflowRunRepository,
                                      StepRunRepository stepRunRepository,
                                      RunEventRepository runEventRepository,
                                      ProjectRepository projectRepository,
                                      ResourceAccessGuard accessGuard) {
        this.workflowRunRepository = workflowRunRepository;
        this.stepRunRepository = stepRunRepository;
        this.runEventRepository = runEventRepository;
        this.projectRepository = projectRepository;
        this.accessGuard = accessGuard;
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
        workflowRunRepository.save(run);

        // Record initial event
        appendEvent(runId, "RUN_STARTED", "Workflow execution started");

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

        long nextSeq = runEventRepository.countByRunId(runId) + 1;
        String eventId = "evt-" + UUID.randomUUID().toString().substring(0, 8);
        RunEventEntity event = new RunEventEntity(eventId, runId, nextSeq, eventType, payload);
        runEventRepository.save(event);
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
                entity.getUpdatedAt()
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
                entity.getUpdatedAt()
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
