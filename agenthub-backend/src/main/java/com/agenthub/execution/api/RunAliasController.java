package com.agenthub.execution.api;

import com.agenthub.adapter.common.Result;
import com.agenthub.execution.application.ExecutionApplication;
import com.agenthub.execution.dto.StartRunCommand;
import com.agenthub.execution.dto.WorkflowRunView;
import com.agenthub.shared.context.RequestContext;
import com.agenthub.shared.exception.BusinessException;
import com.agenthub.shared.exception.ErrorCode;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Standard REST routing aliases for Workflow Runs under /api/runs.
 */
@RestController
@RequestMapping("/api/runs")
public class RunAliasController {

    private final ExecutionApplication executionApplication;

    public RunAliasController(ExecutionApplication executionApplication) {
        this.executionApplication = executionApplication;
    }

    public static class CancelRunRequest {
        public String reason;
    }

    @PostMapping
    public Result<WorkflowRunView> startRun(@RequestBody StartRunCommand cmd) {
        String currentUserId = RequestContext.get().getUserId();
        if (currentUserId == null || currentUserId.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Authentication required to start workflow run");
        }
        WorkflowRunView run = executionApplication.startRun(cmd);
        return Result.ok(run);
    }

    @GetMapping("/{runId}")
    public Result<WorkflowRunView> getRun(@PathVariable("runId") String runId) {
        WorkflowRunView run = executionApplication.getRunById(runId);
        return Result.ok(run);
    }

    @PostMapping("/{runId}/cancel")
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

    @GetMapping(value = "/{runId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
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
}
