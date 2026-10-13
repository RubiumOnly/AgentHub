// ============================================================================
// AgentHub Approvals API Module (Bounded Context: orchestration)
// ============================================================================

import { ApprovalView } from "@/types";
import { API_BASE, fetchJson } from "./errors";

export const approvalsApi = {
  async listApprovals(runId: string): Promise<ApprovalView[]> {
    if (!runId) return [];
    return fetchJson<ApprovalView[]>(`${API_BASE}/api/approvals/runs/${encodeURIComponent(runId)}`);
  },

  async approve(approvalId: string, reviewer?: string, reason?: string): Promise<ApprovalView> {
    return fetchJson<ApprovalView>(`${API_BASE}/api/approvals/${encodeURIComponent(approvalId)}/approve`, {
      method: "POST",
      body: JSON.stringify({
        reviewer,
        reason: reason || "Approved by Reviewer",
      }),
    });
  },

  async reject(approvalId: string, reviewer?: string, reason?: string): Promise<ApprovalView> {
    return fetchJson<ApprovalView>(`${API_BASE}/api/approvals/${encodeURIComponent(approvalId)}/reject`, {
      method: "POST",
      body: JSON.stringify({
        reviewer,
        reason: reason || "Rejected by Reviewer",
      }),
    });
  },
};
