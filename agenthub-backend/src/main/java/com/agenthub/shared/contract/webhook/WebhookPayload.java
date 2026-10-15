package com.agenthub.shared.contract.webhook;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Standardized Webhook Delivery Payload for external CI/CD, Slack, Jira, or webhook consumers.
 * Strictly exposes versioned DTO values without leaking internal JPA entities or server paths.
 */
public class WebhookPayload {

    private String eventId;
    private String eventType;
    private String occurredAt;
    private String resourceType;
    private String resourceId;
    private String projectId;
    private Map<String, Object> data;

    public WebhookPayload() {
    }

    public WebhookPayload(String eventId,
                          String eventType,
                          String occurredAt,
                          String resourceType,
                          String resourceId,
                          String projectId,
                          Map<String, Object> data) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.occurredAt = occurredAt;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.projectId = projectId;
        this.data = data != null ? Collections.unmodifiableMap(new LinkedHashMap<>(data)) : Collections.emptyMap();
    }

    public static WebhookPayload create(WebhookEventType type,
                                        String resourceId,
                                        String projectId,
                                        Map<String, Object> data) {
        return new WebhookPayload(
                UUID.randomUUID().toString(),
                type != null ? type.getEventName() : "unknown",
                Instant.now().toString(),
                type != null ? type.getResourceType() : "UNKNOWN",
                resourceId,
                projectId,
                data
        );
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(String occurredAt) {
        this.occurredAt = occurredAt;
    }

    public String getResourceType() {
        return resourceType;
    }

    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }

    public String getResourceId() {
        return resourceId;
    }

    public void setResourceId(String resourceId) {
        this.resourceId = resourceId;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public void setData(Map<String, Object> data) {
        this.data = data != null ? Collections.unmodifiableMap(new LinkedHashMap<>(data)) : Collections.emptyMap();
    }
}
