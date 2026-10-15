package com.agenthub.execution.api;

import com.agenthub.adapter.common.Result;
import com.agenthub.execution.application.ExecutionApplication;
import com.agenthub.execution.dto.RunEventView;
import com.agenthub.execution.dto.RunEvidenceBundleView;
import com.agenthub.execution.dto.StartRunCommand;
import com.agenthub.execution.dto.StepRunView;
import com.agenthub.execution.dto.WorkflowRunView;
import com.agenthub.shared.context.RequestContext;
import com.agenthub.shared.contract.ApiContractHeaders;
import com.agenthub.shared.contract.PageRequestQuery;
import com.agenthub.shared.contract.PageResultView;
import com.agenthub.shared.exception.BusinessException;
import com.agenthub.shared.exception.ErrorCode;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.temporal.ChronoUnit;
import java.util.Comparator;
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
    public ResponseEntity<Result<WorkflowRunView>> startRun(
            @RequestBody StartRunCommand cmd,
            @RequestHeader(value = ApiContractHeaders.HEADER_IDEMPOTENCY_KEY, required = false) String idempotencyKeyHeader) {
        String currentUserId = RequestContext.get().getUserId();
        if (currentUserId == null || currentUserId.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Authentication required to start workflow run");
        }
        if ((cmd.getIdempotencyKey() == null || cmd.getIdempotencyKey().isBlank())
                && idempotencyKeyHeader != null && !idempotencyKeyHeader.isBlank()) {
            cmd.setIdempotencyKey(idempotencyKeyHeader.trim());
        }
        WorkflowRunView run = executionApplication.startRun(cmd);
        String etag = computeRunEtag(run);
        return ResponseEntity.ok()
                .header(HttpHeaders.ETAG, etag)
                .body(Result.ok(run));
    }

    @GetMapping("/runs/{runId}")
    public ResponseEntity<Result<WorkflowRunView>> getRun(
            @PathVariable("runId") String runId,
            @RequestHeader(value = ApiContractHeaders.HEADER_IF_NONE_MATCH, required = false) String ifNoneMatch) {
        WorkflowRunView run = executionApplication.getRunById(runId);
        String etag = computeRunEtag(run);
        if (ApiContractHeaders.matchesEtag(ifNoneMatch, etag)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .header(HttpHeaders.ETAG, etag)
                    .build();
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.ETAG, etag)
                .body(Result.ok(run));
    }

    @GetMapping("/runs/{runId}/operations")
    public Result<com.agenthub.execution.dto.RunOperationsView> getRunOperations(@PathVariable("runId") String runId) {
        com.agenthub.execution.dto.RunOperationsView view = executionApplication.getRunOperations(runId);
        return Result.ok(view);
    }

    @GetMapping("/projects/{projectId}/runs")
    public Result<List<WorkflowRunView>> listRunsByProject(@PathVariable("projectId") String projectId) {
        List<WorkflowRunView> runs = executionApplication.listRunsByProjectId(projectId);
        return Result.ok(runs);
    }

    @GetMapping("/projects/{projectId}/runs/page")
    public Result<PageResultView<WorkflowRunView>> listRunsByProjectPaged(
            @PathVariable("projectId") String projectId,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(value = "sortBy", required = false) String sortBy,
            @RequestParam(value = "sortDir", required = false) String sortDir,
            @RequestParam(value = "status", required = false) String status) {
        PageRequestQuery query = PageRequestQuery.of(page, size, sortBy, sortDir);
        List<WorkflowRunView> runs = executionApplication.listRunsByProjectId(projectId);
        if (status != null && !status.isBlank()) {
            runs = runs.stream()
                    .filter(r -> status.trim().equalsIgnoreCase(r.getStatus()))
                    .toList();
        }
        Comparator<WorkflowRunView> cmp;
        if ("status".equalsIgnoreCase(query.getSortBy())) {
            cmp = Comparator.comparing(r -> r.getStatus() != null ? r.getStatus().toLowerCase() : "");
        } else if ("updatedAt".equalsIgnoreCase(query.getSortBy())) {
            cmp = Comparator.comparing(r -> r.getUpdatedAt() != null ? r.getUpdatedAt().toString() : "");
        } else {
            cmp = Comparator.comparing(r -> r.getCreatedAt() != null ? r.getCreatedAt().toString() : "");
        }
        if ("DESC".equalsIgnoreCase(query.getSortDir())) {
            cmp = cmp.reversed();
        }
        runs = runs.stream().sorted(cmp).toList();
        return Result.ok(PageResultView.slice(runs, query));
    }

    private String computeRunEtag(WorkflowRunView run) {
        if (run == null) {
            return "W/\"run-empty\"";
        }
        String ts = run.getUpdatedAt() != null ? run.getUpdatedAt().truncatedTo(ChronoUnit.SECONDS).toString()
                : (run.getCreatedAt() != null ? run.getCreatedAt().truncatedTo(ChronoUnit.SECONDS).toString() : "");
        int fingerprint = java.util.Objects.hash(
                ts,
                run.getStatus(),
                run.getCancelReason(),
                run.getStartedAt() != null ? run.getStartedAt().truncatedTo(ChronoUnit.SECONDS).toString() : "",
                run.getFinishedAt() != null ? run.getFinishedAt().truncatedTo(ChronoUnit.SECONDS).toString() : "",
                run.getCancelledAt() != null ? run.getCancelledAt().truncatedTo(ChronoUnit.SECONDS).toString() : "",
                run.getCorrelationId(),
                run.getDefinitionId()
        );
        return "W/\"run-" + run.getId() + "-" + run.getStatus() + "-" + Integer.toHexString(fingerprint) + "\"";
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

    /**
     * Phase F: Export sanitized, tamper-evident Run Evidence Bundle JSON.
     */
    @GetMapping("/runs/{runId}/evidence")
    public Result<RunEvidenceBundleView> getRunEvidence(@PathVariable("runId") String runId) {
        RunEvidenceBundleView bundle = executionApplication.exportRunEvidenceBundle(runId);
        return Result.ok(bundle);
    }

    /**
     * Phase F: Download sanitized Run Evidence Bundle as a standalone JSON attachment.
     */
    @GetMapping(value = "/runs/{runId}/evidence/download", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RunEvidenceBundleView> downloadRunEvidence(@PathVariable("runId") String runId) {
        RunEvidenceBundleView bundle = executionApplication.exportRunEvidenceBundle(runId);
        String safeFilename = "evidence-bundle-" + runId.replaceAll("[^a-zA-Z0-9_-]", "") + ".json";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + safeFilename + "\"")
                .header("X-Evidence-Checksum", bundle.getChecksum())
                .header("X-Execution-Mode", bundle.getExecutionMode())
                .body(bundle);
    }
}
