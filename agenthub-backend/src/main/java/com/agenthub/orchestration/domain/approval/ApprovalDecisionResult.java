package com.agenthub.orchestration.domain.approval;

public class ApprovalDecisionResult {
    private final ApprovalDecision decision;
    private final String reason;
    private final String reviewer;

    public ApprovalDecisionResult(ApprovalDecision decision, String reason, String reviewer) {
        this.decision = decision;
        this.reason = reason;
        this.reviewer = reviewer;
    }

    public ApprovalDecision getDecision() { return decision; }
    public String getReason() { return reason; }
    public String getReviewer() { return reviewer; }
}
