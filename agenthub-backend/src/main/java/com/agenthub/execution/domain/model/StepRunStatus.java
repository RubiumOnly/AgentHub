package com.agenthub.execution.domain.model;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Strict state definitions and allowed transitions for StepRun.
 * States: PENDING, RUNNING, PAUSED, WAITING_APPROVAL, SUCCEEDED, FAILED, CANCELLED, TIMED_OUT
 */
public enum StepRunStatus {
    PENDING,
    RUNNING,
    PAUSED,
    WAITING_APPROVAL,
    SUCCEEDED,
    FAILED,
    CANCELLED,
    TIMED_OUT;

    private static final Map<StepRunStatus, Set<StepRunStatus>> ALLOWED_TRANSITIONS;

    static {
        ALLOWED_TRANSITIONS = Map.of(
                PENDING, EnumSet.of(RUNNING, CANCELLED),
                RUNNING, EnumSet.of(PAUSED, WAITING_APPROVAL, SUCCEEDED, FAILED, CANCELLED, TIMED_OUT),
                PAUSED, EnumSet.of(RUNNING, CANCELLED),
                WAITING_APPROVAL, EnumSet.of(RUNNING, CANCELLED, FAILED),
                SUCCEEDED, Collections.emptySet(),
                FAILED, EnumSet.of(PENDING, RUNNING), // Allows retry
                CANCELLED, Collections.emptySet(),
                TIMED_OUT, Collections.emptySet()
        );
    }

    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED || this == CANCELLED || this == TIMED_OUT;
    }

    public boolean canTransitionTo(StepRunStatus target) {
        if (target == null) return false;
        if (this == target) return true; // Idempotent same-state check
        Set<StepRunStatus> allowed = ALLOWED_TRANSITIONS.getOrDefault(this, Collections.emptySet());
        return allowed.contains(target);
    }

    public static StepRunStatus fromString(String value) {
        if (value == null || value.isBlank()) {
            return PENDING;
        }
        String normalized = value.trim().toUpperCase();
        if ("READY".equals(normalized)) {
            return PENDING;
        }
        if ("SKIPPED".equals(normalized)) {
            return SUCCEEDED;
        }
        try {
            return StepRunStatus.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown StepRunStatus: " + value);
        }
    }
}
