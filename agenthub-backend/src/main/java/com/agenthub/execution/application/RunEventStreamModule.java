package com.agenthub.execution.application;

import com.agenthub.execution.dto.RunEventView;
import com.agenthub.execution.infrastructure.entity.RunEventEntity;
import com.agenthub.execution.infrastructure.entity.WorkflowRunEntity;
import com.agenthub.execution.infrastructure.repository.WorkflowRunRepository;
import com.agenthub.execution.service.RunEventBroadcaster;
import com.agenthub.identity.infrastructure.security.ResourceAccessGuard;
import com.agenthub.project.infrastructure.entity.ProjectEntity;
import com.agenthub.project.infrastructure.repository.ProjectRepository;
import com.agenthub.shared.context.RequestContext;
import com.agenthub.shared.exception.BusinessException;
import com.agenthub.shared.exception.ErrorCode;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Internal deep module responsible for appending RunEvents and subscribing to
 * cursor-replayable SSE streams.
 */
@Component
public class RunEventStreamModule {

    private final WorkflowRunRepository workflowRunRepository;
    private final ProjectRepository projectRepository;
    private final ResourceAccessGuard accessGuard;
    private final RunEventBroadcaster broadcaster;

    public RunEventStreamModule(WorkflowRunRepository workflowRunRepository,
                                ProjectRepository projectRepository,
                                ResourceAccessGuard accessGuard,
                                RunEventBroadcaster broadcaster) {
        this.workflowRunRepository = workflowRunRepository;
        this.projectRepository = projectRepository;
        this.accessGuard = accessGuard;
        this.broadcaster = broadcaster;
    }

    @Transactional
    public RunEventView appendEvent(String runId, String eventType, String payload) {
        WorkflowRunEntity run = workflowRunRepository.findById(runId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RUN_NOT_FOUND, "Run not found: " + runId));
        ProjectEntity project = projectRepository.findById(run.getProjectId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND, "Project not found: " + run.getProjectId()));
        accessGuard.checkOwnership(project.getOwnerId(), RequestContext.get().getUserId());

        RunEventEntity event = broadcaster.publishEvent(runId, eventType, payload);
        return RunQueryModule.toEventView(event);
    }

    public SseEmitter subscribeRunStream(String runId, Long lastEventId) {
        WorkflowRunEntity run = workflowRunRepository.findById(runId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RUN_NOT_FOUND, "Run not found: " + runId));
        ProjectEntity project = projectRepository.findById(run.getProjectId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND, "Project not found: " + run.getProjectId()));
        accessGuard.checkOwnership(project.getOwnerId(), RequestContext.get().getUserId());

        return broadcaster.subscribe(runId, lastEventId);
    }
}
