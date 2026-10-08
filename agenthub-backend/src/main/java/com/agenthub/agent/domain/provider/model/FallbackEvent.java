package com.agenthub.agent.domain.provider.model;

import java.time.LocalDateTime;

/**
 * Event emitted when an LLM provider fails and traffic falls back to a secondary backup provider.
 */
public class FallbackEvent {

    private String failedProviderId;
    private String failedProviderName;
    private String failedModel;
    private String targetProviderId;
    private String targetProviderName;
    private String targetModel;
    private String reason;
    private int statusCode;
    private LocalDateTime timestamp;

    public FallbackEvent() {
        this.timestamp = LocalDateTime.now();
    }

    public FallbackEvent(String failedProviderId,
                         String failedProviderName,
                         String failedModel,
                         String targetProviderId,
                         String targetProviderName,
                         String targetModel,
                         String reason,
                         int statusCode) {
        this.failedProviderId = failedProviderId;
        this.failedProviderName = failedProviderName;
        this.failedModel = failedModel;
        this.targetProviderId = targetProviderId;
        this.targetProviderName = targetProviderName;
        this.targetModel = targetModel;
        this.reason = reason;
        this.statusCode = statusCode;
        this.timestamp = LocalDateTime.now();
    }

    public String toAlertMessage() {
        return String.format("[PROVIDER_FALLBACK] Provider '%s' (%s) failed with status %d (%s); falling back to backup provider '%s' (%s)",
                failedProviderName, failedModel, statusCode, reason, targetProviderName, targetModel);
    }

    public String getFailedProviderId() { return failedProviderId; }
    public String getFailedProviderName() { return failedProviderName; }
    public String getFailedModel() { return failedModel; }
    public String getTargetProviderId() { return targetProviderId; }
    public String getTargetProviderName() { return targetProviderName; }
    public String getTargetModel() { return targetModel; }
    public String getReason() { return reason; }
    public int getStatusCode() { return statusCode; }
    public LocalDateTime getTimestamp() { return timestamp; }
}
