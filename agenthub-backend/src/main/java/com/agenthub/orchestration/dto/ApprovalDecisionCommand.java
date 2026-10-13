package com.agenthub.orchestration.dto;

import com.agenthub.shared.contract.BoundedContext;
import com.agenthub.shared.contract.ContextCommand;

public class ApprovalDecisionCommand implements ContextCommand {
    private String decision; // APPROVED or REJECTED
    private String reason;
    private String comments;

    public ApprovalDecisionCommand() {}

    public ApprovalDecisionCommand(String decision, String reason) {
        this.decision = decision;
        this.reason = reason;
        this.comments = reason;
    }

    @Override
    public BoundedContext boundedContext() {
        return BoundedContext.ORCHESTRATION;
    }

    public String getDecision() { return decision; }
    public void setDecision(String decision) { this.decision = decision; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getComments() { return comments; }
    public void setComments(String comments) { this.comments = comments; }
}
