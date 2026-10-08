package com.agenthub.orchestration.application;

import com.agenthub.orchestration.dto.ApprovalDecisionCommand;
import com.agenthub.orchestration.dto.ApprovalView;

import java.util.List;

public interface ApprovalApplication {
    ApprovalView getApprovalById(String id);
    List<ApprovalView> listApprovalsByRunId(String runId);
    ApprovalView decide(String approvalId, ApprovalDecisionCommand cmd);
    ApprovalView approve(String approvalId, String reason);
    ApprovalView reject(String approvalId, String reason);
}
