package com.agenthub.orchestration.dto;

import java.time.LocalDateTime;

public class ApprovalView {
    private String id;
    private String runId;
    private String stepRunId;
    private String status;
    private String requestedBy;
    private String reviewedBy;
    private String decisionReason;
    private String decision;
    private String comments;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public ApprovalView() {}

    public ApprovalView(String id, String runId, String stepRunId, String status,
                        String requestedBy, String reviewedBy, String decisionReason,
                        String decision, String comments, LocalDateTime reviewedAt,
                        LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.runId = runId;
        this.stepRunId = stepRunId;
        this.status = status;
        this.requestedBy = requestedBy;
        this.reviewedBy = reviewedBy;
        this.decisionReason = decisionReason;
        this.decision = decision;
        this.comments = comments;
        this.reviewedAt = reviewedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
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
