package com.agenthub.execution.application;

import com.agenthub.execution.dto.RunEventView;
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
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Internal deep module responsible for read-only WorkflowRun, StepRun, and RunEvent queries
 * with strict project ownership verification.
 */
@Component
public class RunQueryModule {

    private final WorkflowRunRepository workflowRunRepository;
    private final StepRunRepository stepRunRepository;
    private final RunEventRepository runEventRepository;
    private final ProjectRepository projectRepository;
    private final ResourceAccessGuard accessGuard;
    private final com.agenthub.identity.infrastructure.security.ResourceOwnershipVerifier ownershipVerifier;

    public RunQueryModule(WorkflowRunRepository workflowRunRepository,
                          StepRunRepository stepRunRepository,
                          RunEventRepository runEventRepository,
                          ProjectRepository projectRepository,
                          ResourceAccessGuard accessGuard) {
        this(workflowRunRepository, stepRunRepository, runEventRepository, projectRepository, accessGuard, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public RunQueryModule(WorkflowRunRepository workflowRunRepository,
                          StepRunRepository stepRunRepository,
                          RunEventRepository runEventRepository,
                          ProjectRepository projectRepository,
                          ResourceAccessGuard accessGuard,
                          @org.springframework.beans.factory.annotation.Autowired(required = false) com.agenthub.identity.infrastructure.security.ResourceOwnershipVerifier ownershipVerifier) {
        this.workflowRunRepository = workflowRunRepository;
        this.stepRunRepository = stepRunRepository;
        this.runEventRepository = runEventRepository;
        this.projectRepository = projectRepository;
        this.accessGuard = accessGuard;
        this.ownershipVerifier = ownershipVerifier != null ? ownershipVerifier
                : (accessGuard != null ? new com.agenthub.identity.infrastructure.security.ResourceOwnershipVerifier(accessGuard, projectRepository, null, null, workflowRunRepository, null, null) : null);
    }

    @Transactional(readOnly = true)
    public WorkflowRunView getRunById(String runId) {
        WorkflowRunEntity run = ownershipVerifier != null ? ownershipVerifier.verifyRunAccess(runId) : verifyRunAccessFallback(runId);
        return toRunView(run);
    }

    @Transactional(readOnly = true)
    public List<WorkflowRunView> listRunsByProjectId(String projectId) {
        if (ownershipVerifier != null) {
            ownershipVerifier.verifyProjectAccess(projectId);
        } else {
            ProjectEntity project = projectRepository.findById(projectId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND, "Project not found: " + projectId));
            accessGuard.checkOwnership(project.getOwnerId(), RequestContext.get().getUserId());
        }
        return workflowRunRepository.findByProjectIdOrderByCreatedAtDesc(projectId).stream()
                .map(RunQueryModule::toRunView)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<StepRunView> listStepRuns(String runId) {
        if (ownershipVerifier != null) {
            ownershipVerifier.verifyRunAccess(runId);
        } else {
            verifyRunAccessFallback(runId);
        }
        return stepRunRepository.findByRunIdOrderByCreatedAtAsc(runId).stream()
                .map(RunQueryModule::toStepView)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<RunEventView> listEvents(String runId, Long afterSeq) {
        if (ownershipVerifier != null) {
            ownershipVerifier.verifyRunAccess(runId);
        } else {
            verifyRunAccessFallback(runId);
        }

        List<RunEventEntity> events;
        if (afterSeq != null && afterSeq > 0) {
            events = runEventRepository.findByRunIdAndSequenceNumGreaterThanOrderBySequenceNumAsc(runId, afterSeq);
        } else {
            events = runEventRepository.findByRunIdOrderBySequenceNumAsc(runId);
        }
        return events.stream().map(RunQueryModule::toEventView).collect(Collectors.toList());
    }

    private WorkflowRunEntity verifyRunAccessFallback(String runId) {
        WorkflowRunEntity run = workflowRunRepository.findById(runId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RUN_NOT_FOUND, "Run not found: " + runId));
        ProjectEntity project = projectRepository.findById(run.getProjectId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND, "Project not found: " + run.getProjectId()));
        accessGuard.checkOwnership(project.getOwnerId(), RequestContext.get().getUserId());
        return run;
    }

    public static WorkflowRunView toRunView(WorkflowRunEntity entity) {
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

    public static StepRunView toStepView(StepRunEntity entity) {
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

    public static RunEventView toEventView(RunEventEntity entity) {
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
