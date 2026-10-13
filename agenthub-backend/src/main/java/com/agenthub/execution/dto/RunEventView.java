package com.agenthub.execution.dto;

import com.agenthub.shared.contract.BoundedContext;
import com.agenthub.shared.contract.ContextDomainEvent;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

public class RunEventView implements ContextDomainEvent {
    private String id;
    private String runId;
    private Long sequenceNum;
    private String eventType;
    private String payload;
    private LocalDateTime createdAt;

    public RunEventView() {}

    public RunEventView(String id, String runId, Long sequenceNum, String eventType, String payload, LocalDateTime createdAt) {
        this.id = id;
        this.runId = runId;
        this.sequenceNum = sequenceNum;
        this.eventType = eventType;
        this.payload = payload;
        this.createdAt = createdAt;
    }

    @Override
    public BoundedContext boundedContext() {
        return BoundedContext.EXECUTION;
    }

    @Override
    public String eventType() {
        return eventType != null ? eventType : "RUN_EVENT";
    }

    @Override
    public Instant occurredAt() {
        return createdAt != null ? createdAt.toInstant(ZoneOffset.UTC) : Instant.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }
    public Long getSequenceNum() { return sequenceNum; }
    public void setSequenceNum(Long sequenceNum) { this.sequenceNum = sequenceNum; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
