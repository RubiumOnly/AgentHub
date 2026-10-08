package com.agenthub.orchestration.domain.dsl;

import java.io.Serializable;

public class RetryPolicyDsl implements Serializable {
    private int maxRetries = 0;
    private long backoffMs = 500L;

    public RetryPolicyDsl() {}

    public RetryPolicyDsl(int maxRetries, long backoffMs) {
        this.maxRetries = maxRetries;
        this.backoffMs = backoffMs;
    }

    public int getMaxRetries() { return maxRetries; }
    public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }
    public long getBackoffMs() { return backoffMs; }
    public void setBackoffMs(long backoffMs) { this.backoffMs = backoffMs; }
}
