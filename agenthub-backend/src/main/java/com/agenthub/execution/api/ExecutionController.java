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
import org.springframework.web.bind.annotation.*;

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
}
