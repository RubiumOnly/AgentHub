// ============================================================================
// AgentHub Sandbox Deployments API Module (Bounded Context: sandbox)
// ============================================================================

import { CreateDeploymentRequest, DeploymentResponse } from "@/types";
import { isDemoMode } from "./auth";
import { API_BASE, DEFAULT_PREVIEW_URL, fetchJson } from "./errors";

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

  async probeDeployment(id: string): Promise<DeploymentResponse> {
    if (isDemoMode()) {
      return {
        id: id || "dep-demo-preview",
        projectId: "proj-demo-simulated",
        artifactId: "art-jgit-diff-snapshot",
        status: "RUNNING",
        target: "STATIC_PREVIEW",
        sandboxType: "LOCAL_PROCESS",
        templateType: "STATIC_WEB_TEMPLATE",
        url: DEFAULT_PREVIEW_URL,
        port: 18000,
        hasLogs: true,
        startedAt: new Date(Date.now() - 60000).toISOString(),
        finishedAt: undefined,
        createdAt: new Date(Date.now() - 70000).toISOString(),
        lifecycleTrace: "CREATED ➔ BUILD ➔ START ➔ HEALTH_CHECK ➔ RUNNING (Active Probe Latency: 12ms)",
      };
    }
    if (!id) throw new Error("deployment id is required");
    return fetchJson<DeploymentResponse>(`${API_BASE}/api/deployments/${encodeURIComponent(id)}/probe`, {
      method: "POST",
    });
  },
};
