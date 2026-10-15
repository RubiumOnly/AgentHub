package com.agenthub.execution.domain.model;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Strict state definitions and allowed transitions for WorkflowRun.
 * States: PENDING, RUNNING, PAUSED, WAITING_APPROVAL, SUCCEEDED, FAILED, CANCELLED, TIMED_OUT
 */
public enum WorkflowRunStatus {
    PENDING,
    RUNNING,
    PAUSED,
    WAITING_APPROVAL,
    SUCCEEDED,
    FAILED,
    CANCELLED,
    TIMED_OUT,
    REJECTED;

    private static final Map<WorkflowRunStatus, Set<WorkflowRunStatus>> ALLOWED_TRANSITIONS;

    static {
        ALLOWED_TRANSITIONS = Map.of(
                PENDING, EnumSet.of(RUNNING, CANCELLED, REJECTED),
                RUNNING, EnumSet.of(PAUSED, WAITING_APPROVAL, SUCCEEDED, FAILED, CANCELLED, TIMED_OUT, REJECTED),
                PAUSED, EnumSet.of(RUNNING, CANCELLED, REJECTED),
                WAITING_APPROVAL, EnumSet.of(RUNNING, CANCELLED, FAILED, REJECTED),
                SUCCEEDED, EnumSet.of(REJECTED), // Rejection during final artifact/quality gate review
                FAILED, EnumSet.of(RUNNING, REJECTED), // Allows explicit restart/retry or definitive rejection
                CANCELLED, Collections.emptySet(),
                TIMED_OUT, EnumSet.of(RUNNING, REJECTED), // Allows restart/retry after timeout or rejection
                REJECTED, Collections.emptySet()
        );
    }

    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED || this == CANCELLED || this == TIMED_OUT || this == REJECTED;
    }

    public boolean canTransitionTo(WorkflowRunStatus target) {
        return com.agenthub.execution.domain.state.UnifiedExecutionStateGraph.canTransitionRun(this, target);
    }

    public static WorkflowRunStatus fromString(String value) {
        if (value == null || value.isBlank()) {
            return PENDING;
        }
        String normalized = value.trim().toUpperCase();
        if ("QUEUED".equals(normalized)) {
            return PENDING;
        }
        try {
            return WorkflowRunStatus.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown WorkflowRunStatus: " + value);
        }
    }
}
