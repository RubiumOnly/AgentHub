package com.agenthub.identity.dto;

import java.time.LocalDateTime;

/**
 * View model representing an immutable security audit event.
 */
public class AuthAuditLogView {

    private String id;
    private String eventType;
    private String userId;
    private String email;
    private String ipAddress;
    private String details;
    private LocalDateTime createdAt;

    public AuthAuditLogView() {}

    public AuthAuditLogView(String id, String eventType, String userId, String email, String ipAddress, String details, LocalDateTime createdAt) {
        this.id = id;
        this.eventType = eventType;
        this.userId = userId;
        this.email = email;
        this.ipAddress = ipAddress;
        this.details = details;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
