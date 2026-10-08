package com.agenthub.execution.api;

import com.agenthub.adapter.common.Result;
import com.agenthub.execution.application.ExecutionApplication;
import com.agenthub.execution.dto.RunEventView;
import com.agenthub.execution.dto.StartRunCommand;
import com.agenthub.execution.dto.StepRunView;
import com.agenthub.execution.dto.WorkflowRunView;
import com.agenthub.shared.context.RequestContext;
import com.agenthub.shared.exception.BusinessException;
import com.agenthub.shared.exception.ErrorCode;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/executions")
public class ExecutionController {

    private final ExecutionApplication executionApplication;

    public ExecutionController(ExecutionApplication executionApplication) {
        this.executionApplication = executionApplication;
    }

    public static class AppendEventRequest {
        public String eventType;
        public String payload;
    }

    public static class CancelRunRequest {
        public String reason;
    }

    @PostMapping("/runs")
    public Result<WorkflowRunView> startRun(@RequestBody StartRunCommand cmd) {
        String currentUserId = RequestContext.get().getUserId();
        if (currentUserId == null || currentUserId.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Authentication required to start workflow run");
        }
        WorkflowRunView run = executionApplication.startRun(cmd);
        return Result.ok(run);
    }

    @GetMapping("/runs/{runId}")
    public Result<WorkflowRunView> getRun(@PathVariable("runId") String runId) {
        WorkflowRunView run = executionApplication.getRunById(runId);
        return Result.ok(run);
    }

    @GetMapping("/projects/{projectId}/runs")
    public Result<List<WorkflowRunView>> listRunsByProject(@PathVariable("projectId") String projectId) {
        List<WorkflowRunView> runs = executionApplication.listRunsByProjectId(projectId);
        return Result.ok(runs);
    }

    @GetMapping("/runs/{runId}/steps")
    public Result<List<StepRunView>> listStepRuns(@PathVariable("runId") String runId) {
        List<StepRunView> steps = executionApplication.listStepRuns(runId);
        return Result.ok(steps);
    }

    @GetMapping("/runs/{runId}/events")
    public Result<List<RunEventView>> listEvents(
            @PathVariable("runId") String runId,
            @RequestParam(value = "afterSeq", required = false) Long afterSeq) {
        List<RunEventView> events = executionApplication.listEvents(runId, afterSeq);
        return Result.ok(events);
    }

    @PostMapping("/runs/{runId}/events")
    public Result<RunEventView> appendEvent(
            @PathVariable("runId") String runId,
            @RequestBody AppendEventRequest req) {
        String currentUserId = RequestContext.get().getUserId();
        if (currentUserId == null || currentUserId.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Authentication required to append run events");
        }
        RunEventView event = executionApplication.appendEvent(runId, req.eventType, req.payload);
        return Result.ok(event);
    }

    /**
     * Real-time SSE streaming endpoint for workflow events with Last-Event-ID reconnection support.
     */
    @GetMapping(value = "/runs/{runId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamEvents(
            @PathVariable("runId") String runId,
            @RequestHeader(value = "Last-Event-ID", required = false) String lastEventIdHeader,
            @RequestParam(value = "lastEventId", required = false) Long lastEventIdParam) {

        Long cursor = null;
        if (lastEventIdHeader != null && !lastEventIdHeader.isBlank()) {
            try {
                cursor = Long.parseLong(lastEventIdHeader.trim());
            } catch (NumberFormatException ignored) {}
        }
        if (cursor == null) {
            cursor = lastEventIdParam;
        }

        return executionApplication.subscribeRunStream(runId, cursor);
    }

    @PostMapping("/runs/{runId}/cancel")
    public Result<WorkflowRunView> cancelRun(
            @PathVariable("runId") String runId,
            @RequestBody(required = false) CancelRunRequest req) {
        String currentUserId = RequestContext.get().getUserId();
        if (currentUserId == null || currentUserId.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Authentication required to cancel run");
        }
        String reason = req != null && req.reason != null ? req.reason : "User cancelled execution";
        WorkflowRunView cancelled = executionApplication.cancelRun(runId, reason);
        return Result.ok(cancelled);
    }

    @PostMapping("/runs/{runId}/pause")
    public Result<WorkflowRunView> pauseRun(@PathVariable("runId") String runId) {
        String currentUserId = RequestContext.get().getUserId();
        if (currentUserId == null || currentUserId.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Authentication required to pause run");
        }
        WorkflowRunView paused = executionApplication.pauseRun(runId);
        return Result.ok(paused);
    }

    @PostMapping("/runs/{runId}/resume")
    public Result<WorkflowRunView> resumeRun(@PathVariable("runId") String runId) {
        String currentUserId = RequestContext.get().getUserId();
        if (currentUserId == null || currentUserId.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Authentication required to resume run");
        }
        WorkflowRunView resumed = executionApplication.resumeRun(runId);
        return Result.ok(resumed);
    }

    @PostMapping("/runs/{runId}/steps/{stepRunId}/retry")
    public Result<StepRunView> retryStep(
            @PathVariable("runId") String runId,
            @PathVariable("stepRunId") String stepRunId) {
        String currentUserId = RequestContext.get().getUserId();
        if (currentUserId == null || currentUserId.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Authentication required to retry step");
        }
        StepRunView step = executionApplication.retryStep(stepRunId);
        return Result.ok(step);
    }

    @PostMapping("/steps/{stepRunId}/retry")
    public Result<StepRunView> retryStepDirect(@PathVariable("stepRunId") String stepRunId) {
        String currentUserId = RequestContext.get().getUserId();
        if (currentUserId == null || currentUserId.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Authentication required to retry step");
        }
        StepRunView step = executionApplication.retryStep(stepRunId);
        return Result.ok(step);
    }
}
