package com.agenthub.runtime.port;

import java.time.LocalDateTime;

/**
 * Handle returned when an Agent runtime execution is initiated.
 */
public class ExecutionHandle {
    private final String handleId;
    private final String runId;
    private final String stepRunId;
    private final String runtimeType;
    private final LocalDateTime startedAt;
    private volatile boolean cancelled;

    public ExecutionHandle(String handleId, String runId, String stepRunId, String runtimeType) {
        this.handleId = handleId;
        this.runId = runId;
        this.stepRunId = stepRunId;
        this.runtimeType = runtimeType;
        this.startedAt = LocalDateTime.now();
        this.cancelled = false;
    }

    public String getHandleId() { return handleId; }
    public String getRunId() { return runId; }
    public String getStepRunId() { return stepRunId; }
    public String getRuntimeType() { return runtimeType; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public boolean isCancelled() { return cancelled; }
    public void markCancelled() { this.cancelled = true; }
}
