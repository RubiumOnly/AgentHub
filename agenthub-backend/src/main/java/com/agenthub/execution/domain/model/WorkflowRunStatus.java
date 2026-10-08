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
    TIMED_OUT;

    private static final Map<WorkflowRunStatus, Set<WorkflowRunStatus>> ALLOWED_TRANSITIONS;

    static {
        ALLOWED_TRANSITIONS = Map.of(
                PENDING, EnumSet.of(RUNNING, CANCELLED),
                RUNNING, EnumSet.of(PAUSED, WAITING_APPROVAL, SUCCEEDED, FAILED, CANCELLED, TIMED_OUT),
                PAUSED, EnumSet.of(RUNNING, CANCELLED),
                WAITING_APPROVAL, EnumSet.of(RUNNING, CANCELLED, FAILED),
                SUCCEEDED, Collections.emptySet(),
                FAILED, EnumSet.of(RUNNING), // Allows explicit restart/retry of a failed run
                CANCELLED, Collections.emptySet(),
                TIMED_OUT, Collections.emptySet()
        );
    }

    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED || this == CANCELLED || this == TIMED_OUT;
    }

    public boolean canTransitionTo(WorkflowRunStatus target) {
        if (target == null) return false;
        if (this == target) return true; // Idempotent same-state check
        Set<WorkflowRunStatus> allowed = ALLOWED_TRANSITIONS.getOrDefault(this, Collections.emptySet());
        return allowed.contains(target);
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
