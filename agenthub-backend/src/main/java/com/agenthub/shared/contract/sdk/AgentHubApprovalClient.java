package com.agenthub.shared.contract.sdk;

import com.agenthub.orchestration.dto.ApprovalDecisionCommand;
import com.agenthub.orchestration.dto.ApprovalView;

import java.util.List;

/**
 * Standard public SDK contract for human-in-the-loop approvals.
 */
public interface AgentHubApprovalClient {

    List<ApprovalView> listApprovalsByRunId(String runId);

    ApprovalView getApprovalById(String approvalId);

    ApprovalView decideApproval(ApprovalDecisionCommand command);
}
