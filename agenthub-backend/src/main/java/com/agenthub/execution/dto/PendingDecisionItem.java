package com.agenthub.execution.dto;

import java.time.LocalDateTime;

public class PendingDecisionItem {
    private String decisionType;
    private String referenceId;
    private String stepRunId;
    private String nodeName;
    private String description;
    private String assignedRole;
    private LocalDateTime requestedAt;

    public PendingDecisionItem() {}

    public PendingDecisionItem(String decisionType, String referenceId, String stepRunId,
                               String nodeName, String description, String assignedRole,
                               LocalDateTime requestedAt) {
        this.decisionType = decisionType;
        this.referenceId = referenceId;
        this.stepRunId = stepRunId;
        this.nodeName = nodeName;
        this.description = description;
        this.assignedRole = assignedRole;
        this.requestedAt = requestedAt;
    }

    public String getDecisionType() { return decisionType; }
    public void setDecisionType(String decisionType) { this.decisionType = decisionType; }

    public String getReferenceId() { return referenceId; }
    public void setReferenceId(String referenceId) { this.referenceId = referenceId; }

    public String getStepRunId() { return stepRunId; }
    public void setStepRunId(String stepRunId) { this.stepRunId = stepRunId; }

    public String getNodeName() { return nodeName; }
    public void setNodeName(String nodeName) { this.nodeName = nodeName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getAssignedRole() { return assignedRole; }
    public void setAssignedRole(String assignedRole) { this.assignedRole = assignedRole; }

    public LocalDateTime getRequestedAt() { return requestedAt; }
    public void setRequestedAt(LocalDateTime requestedAt) { this.requestedAt = requestedAt; }
}
