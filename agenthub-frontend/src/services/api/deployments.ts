// ============================================================================
// AgentHub Sandbox Deployments API Module (Bounded Context: sandbox)
// ============================================================================

import { CreateDeploymentRequest, DeploymentResponse } from "@/types";
import { API_BASE, fetchJson } from "./errors";

export const deploymentsApi = {
  async listDeployments(projectId?: string): Promise<DeploymentResponse[]> {
    if (!projectId) return [];
    return fetchJson<DeploymentResponse[]>(
      `${API_BASE}/api/deployments?projectId=${encodeURIComponent(projectId)}`
    );
  },

  async createDeployment(req: CreateDeploymentRequest, idempotencyKey?: string): Promise<DeploymentResponse> {
    return fetchJson<DeploymentResponse>(`${API_BASE}/api/deployments`, {
      method: "POST",
      idempotencyKey,
      body: JSON.stringify(req),
    });
  },

  async stopDeployment(id: string): Promise<DeploymentResponse> {
    return fetchJson<DeploymentResponse>(`${API_BASE}/api/deployments/${encodeURIComponent(id)}/stop`, {
      method: "POST",
    });
  },

  async getDeploymentLogs(id: string, offset?: number): Promise<string> {
    const query = offset !== undefined && offset > 0 ? `?offset=${offset}` : "";
    return fetchJson<string>(`${API_BASE}/api/deployments/${encodeURIComponent(id)}/logs${query}`);
  },
};
