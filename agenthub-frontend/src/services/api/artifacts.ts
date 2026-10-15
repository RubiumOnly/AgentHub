// ============================================================================
// AgentHub Artifacts & JGit Audit API Module (Bounded Context: audit)
// ============================================================================

import { ArtifactLineageView, ArtifactView, StructuredDiff } from "@/types";
import { API_BASE, fetchJson } from "./errors";

export const artifactsApi = {
  async getArtifact(artifactId: string): Promise<ArtifactView> {
    return fetchJson<ArtifactView>(`${API_BASE}/api/audit/artifacts/${encodeURIComponent(artifactId)}`);
  },

  async listArtifactsByRun(runId: string): Promise<ArtifactView[]> {
    if (!runId) return [];
    return fetchJson<ArtifactView[]>(`${API_BASE}/api/audit/artifacts/run/${encodeURIComponent(runId)}`);
  },

  async getArtifactLineage(runId: string): Promise<ArtifactLineageView> {
    if (!runId) throw new Error("runId is required");
    return fetchJson<ArtifactLineageView>(`${API_BASE}/api/audit/artifacts/lineage/${encodeURIComponent(runId)}`);
  },

  async getArtifactDiff(artifactId: string): Promise<StructuredDiff> {
    if (!artifactId) throw new Error("artifactId is required");
    return fetchJson<StructuredDiff>(`${API_BASE}/api/audit/artifacts/${encodeURIComponent(artifactId)}/diff`);
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
