// ============================================================================
// AgentHub Artifacts & JGit Audit API Module (Bounded Context: audit)
// ============================================================================

import { ArtifactView } from "@/types";
import { API_BASE, fetchJson } from "./errors";

export const artifactsApi = {
  async getArtifact(artifactId: string): Promise<ArtifactView> {
    return fetchJson<ArtifactView>(`${API_BASE}/api/audit/artifacts/${encodeURIComponent(artifactId)}`);
  },

  async listArtifactsByRun(runId: string): Promise<ArtifactView[]> {
    if (!runId) return [];
    return fetchJson<ArtifactView[]>(`${API_BASE}/api/audit/artifacts?runId=${encodeURIComponent(runId)}`);
  },

  async acceptArtifact(artifactId: string, reviewer?: string, comment?: string): Promise<ArtifactView> {
    return fetchJson<ArtifactView>(`${API_BASE}/api/audit/artifacts/${encodeURIComponent(artifactId)}/accept`, {
      method: "POST",
      body: JSON.stringify({ reviewer, comment }),
    });
  },

  async rejectArtifact(artifactId: string, reviewer?: string, comment?: string): Promise<ArtifactView> {
    return fetchJson<ArtifactView>(`${API_BASE}/api/audit/artifacts/${encodeURIComponent(artifactId)}/reject`, {
      method: "POST",
      body: JSON.stringify({ reviewer, comment }),
    });
  },

  async revertArtifact(artifactId: string): Promise<ArtifactView> {
    return fetchJson<ArtifactView>(`${API_BASE}/api/audit/artifacts/${encodeURIComponent(artifactId)}/revert`, {
      method: "POST",
    });
  },
};
