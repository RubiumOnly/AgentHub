package com.agenthub.orchestration.domain.approval;

public enum ApprovalDecision {
    APPROVED,
    REJECTED;

    public static ApprovalDecision fromString(String val) {
        if (val == null) return REJECTED;
        if ("APPROVE".equalsIgnoreCase(val) || "APPROVED".equalsIgnoreCase(val)) {
            return APPROVED;
        }
        return REJECTED;
    }
}
