package com.agenthub.orchestration.infrastructure.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "approvals")
public class ApprovalEntity {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "run_id", nullable = false, length = 64)
    private String runId;

    @Column(name = "step_run_id", length = 64)
    private String stepRunId;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "requested_by", length = 64)
    private String requestedBy;

    @Column(name = "reviewed_by", length = 64)
    private String reviewedBy;

    @Column(name = "decision_reason", length = 512)
    private String decisionReason;

    @Column(length = 32)
    private String decision;

    @Column(length = 512)
    private String comments;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public ApprovalEntity() {}

    public ApprovalEntity(String id, String runId, String stepRunId, String status, String requestedBy) {
        this.id = id;
        this.runId = runId;
        this.stepRunId = stepRunId;
        this.status = status != null ? status : "PENDING";
        this.requestedBy = requestedBy;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }
    public String getStepRunId() { return stepRunId; }
    public void setStepRunId(String stepRunId) { this.stepRunId = stepRunId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRequestedBy() { return requestedBy; }
    public void setRequestedBy(String requestedBy) { this.requestedBy = requestedBy; }
    public String getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(String reviewedBy) { this.reviewedBy = reviewedBy; }
    public String getDecisionReason() { return decisionReason; }
    public void setDecisionReason(String decisionReason) { this.decisionReason = decisionReason; }
    public String getDecision() { return decision; }
    public void setDecision(String decision) { this.decision = decision; }
    public String getComments() { return comments; }
    public void setComments(String comments) { this.comments = comments; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
