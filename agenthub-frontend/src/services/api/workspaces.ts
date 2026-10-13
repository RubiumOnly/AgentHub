// ============================================================================
// AgentHub Workspaces API Module (Bounded Context: project)
// Authoritative REST Resource Model: /api/workspaces/{workspaceId}/*
// ============================================================================

import {
  FileDiffEntry,
  StructuredDiff,
  WorkspaceFileNode,
  WorkspaceView,
} from "@/types";
import { API_BASE, fetchJson } from "./errors";

export const workspacesApi = {
  async getWorkspace(projectId: string): Promise<WorkspaceView> {
    return fetchJson<WorkspaceView>(`${API_BASE}/api/projects/${encodeURIComponent(projectId)}/workspace`);
  },

  async getStructuredDiff(workspaceId: string): Promise<StructuredDiff | null> {
    if (!workspaceId) return null;
    return fetchJson<StructuredDiff>(`${API_BASE}/api/workspaces/${encodeURIComponent(workspaceId)}/diff`);
  },

  async listFiles(workspaceId: string, relativePath = ""): Promise<WorkspaceFileNode[]> {
    if (!workspaceId) return [];
    const query = relativePath ? `?path=${encodeURIComponent(relativePath)}` : "";
    return fetchJson<WorkspaceFileNode[]>(
      `${API_BASE}/api/workspaces/${encodeURIComponent(workspaceId)}/tree${query}`
    );
  },

  async getFileContent(workspaceId: string, filePath: string): Promise<string> {
    const res = await fetchJson<{ content?: string } | string>(
      `${API_BASE}/api/workspaces/${encodeURIComponent(workspaceId)}/file?path=${encodeURIComponent(filePath)}`
    );
    if (typeof res === "string") return res;
    return res?.content ?? "";
  },

  async saveFile(workspaceId: string, filePath: string, content: string): Promise<boolean> {
    await fetchJson(`${API_BASE}/api/workspaces/${encodeURIComponent(workspaceId)}/file`, {
      method: "POST",
      body: JSON.stringify({ path: filePath, content }),
    });
    return true;
  },

  async getLockStatus(workspaceId: string): Promise<{
    workspaceId: string;
    ownerId?: string;
    locked: boolean;
    acquiredAt?: number;
    leaseExpiresAt?: number;
  }> {
    return fetchJson(`${API_BASE}/api/workspaces/${encodeURIComponent(workspaceId)}/lock/status`);
  },

  async getDiff(workspaceId?: string): Promise<FileDiffEntry[]> {
    if (!workspaceId) return [];
    try {
      const data = await workspacesApi.getStructuredDiff(workspaceId);
      return data?.entries || [];
    } catch {
      return [];
    }
  },
};
