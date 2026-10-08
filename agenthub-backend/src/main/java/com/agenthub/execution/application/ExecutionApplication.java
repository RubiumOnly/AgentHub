package com.agenthub.execution.application;

import com.agenthub.execution.dto.RunEventView;
import com.agenthub.execution.dto.StartRunCommand;
import com.agenthub.execution.dto.StepRunView;
import com.agenthub.execution.dto.WorkflowRunView;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

public interface ExecutionApplication {
    WorkflowRunView startRun(StartRunCommand cmd);
    WorkflowRunView getRunById(String runId);
    List<WorkflowRunView> listRunsByProjectId(String projectId);
    List<StepRunView> listStepRuns(String runId);
    RunEventView appendEvent(String runId, String eventType, String payload);
    List<RunEventView> listEvents(String runId, Long afterSeq);

    // Phase 3 Extensions
    WorkflowRunView cancelRun(String runId, String reason);
    WorkflowRunView pauseRun(String runId);
    WorkflowRunView resumeRun(String runId);
    StepRunView retryStep(String stepRunId);
    SseEmitter subscribeRunStream(String runId, Long lastEventId);
}
