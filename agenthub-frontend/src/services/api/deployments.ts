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

export const opsApi = {
  async getMetricsSummary(): Promise<import("@/types").OpsMetricsSummaryView> {
    return fetchJson<import("@/types").OpsMetricsSummaryView>(`${API_BASE}/api/system/metrics/summary`);
  },

  async queryAuditLogs(
    eventType?: string,
    userId?: string,
    page = 1,
    size = 20
  ): Promise<import("./projects").PagedResponse<import("@/types").AuthAuditLogView>> {
    const params = new URLSearchParams({
      page: String(page),
      size: String(size),
    });
    if (eventType) params.set("eventType", eventType);
    if (userId) params.set("userId", userId);
    return fetchJson<import("./projects").PagedResponse<import("@/types").AuthAuditLogView>>(
      `${API_BASE}/api/system/audit-logs?${params.toString()}`
    );
  },

  async triggerBackup(backupDir?: string, encryptionPassphrase?: string): Promise<import("@/types").BackupManifest> {
    return fetchJson<import("@/types").BackupManifest>(`${API_BASE}/api/system/backup`, {
      method: "POST",
      body: JSON.stringify({ backupDir, encryptionPassphrase }),
    });
  },

  async verifyBackup(backupDir: string, encryptionPassphrase?: string): Promise<import("@/types").BackupManifest> {
    return fetchJson<import("@/types").BackupManifest>(`${API_BASE}/api/system/backup/verify`, {
      method: "POST",
      body: JSON.stringify({ backupDir, encryptionPassphrase }),
    });
  },

  async restoreBackup(
    backupDir: string,
    targetWorkspaceRoot?: string,
    encryptionPassphrase?: string
  ): Promise<import("@/types").RestoreVerificationReport> {
    return fetchJson<import("@/types").RestoreVerificationReport>(`${API_BASE}/api/system/backup/restore`, {
      method: "POST",
      body: JSON.stringify({ backupDir, targetWorkspaceRoot, encryptionPassphrase }),
    });
  },
};
