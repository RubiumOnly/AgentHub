package com.agenthub.execution.domain.retry;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeoutException;

/**
 * Classifies execution errors into:
 * - RETRYABLE_TRANSIENT (network timeout, rate limit 429, temporary server error -> auto-retry with backoff)
 * - APPROVAL_REQUIRED (unauthorized high-impact tool, approval gate, budget violation -> human decision required)
 * - FATAL_NON_RETRYABLE (syntax error, schema violation, file corrupted -> immediate fail)
 */
public class ExecutionErrorClassifier {

    public static class ClassificationResult {
        private final RetryCategory category;
        private final String reason;
        private final boolean canAutoRetry;

        public ClassificationResult(RetryCategory category, String reason, boolean canAutoRetry) {
            this.category = category;
            this.reason = reason;
            this.canAutoRetry = canAutoRetry;
        }

        public RetryCategory getCategory() { return category; }
        public String getReason() { return reason; }
        public boolean isCanAutoRetry() { return canAutoRetry; }
        public boolean isAutoRetryable() { return canAutoRetry; }
    }

    public static ClassificationResult classify(Throwable t) {
        if (t == null) {
            return new ClassificationResult(RetryCategory.NONE, "No error", false);
        }

        String msg = t.getMessage() != null ? t.getMessage().toLowerCase() : "";
        String exClassName = t.getClass().getSimpleName();

        // 1. Approval / Permission / Policy required
        if (msg.contains("forbidden") || msg.contains("unauthorized") || msg.contains("approval")
                || msg.contains("high-impact tool") || msg.contains("permission denied")
                || msg.contains("quota exceeded") || msg.contains("budget limit")) {
            return new ClassificationResult(RetryCategory.APPROVAL_REQUIRED, t.getMessage(), false);
        }

        // 2. Transient retryable
        if (t instanceof TimeoutException
                || t instanceof SocketTimeoutException
                || msg.contains("timeout")
                || msg.contains("timed out")
                || msg.contains("429")
                || msg.contains("rate limit")
                || msg.contains("connection reset")
                || msg.contains("temporarily unavailable")
                || msg.contains("503")
                || msg.contains("502")
                || msg.contains("504")
                || msg.contains("retryable")) {
            return new ClassificationResult(RetryCategory.RETRYABLE_TRANSIENT, t.getMessage(), true);
        }

        // 3. Fatal non-retryable
        return new ClassificationResult(RetryCategory.FATAL_NON_RETRYABLE, t.getMessage(), false);
    }
}
