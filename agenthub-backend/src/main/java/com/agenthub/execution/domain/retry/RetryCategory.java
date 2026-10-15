package com.agenthub.execution.domain.retry;

public enum RetryCategory {
    RETRYABLE_TRANSIENT,
    FATAL_NON_RETRYABLE,
    APPROVAL_REQUIRED,
    NONE
}
