// ============================================================================
// AgentHub Projects API Module (Bounded Context: project)
// ============================================================================

import { CreateProjectRequest, ProjectView } from "@/types";
import { API_BASE, fetchJson, RequestOptions } from "./errors";

export interface PagedResponse<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
  nextCursor?: string | null;
  sortBy: string;
  sortDirection: string;
}

export const projectsApi = {
  async listProjects(options?: RequestOptions): Promise<ProjectView[]> {
    return fetchJson<ProjectView[]>(`${API_BASE}/api/projects`, options);
  },

  async listProjectsPage(
    page = 1,
    size = 20,
    sortBy = "createdAt",
    sortDir: "ASC" | "DESC" = "DESC"
  ): Promise<PagedResponse<ProjectView>> {
    const qs = new URLSearchParams({
      page: String(page),
      size: String(size),
      sortBy,
      sortDir,
    });
    return fetchJson<PagedResponse<ProjectView>>(`${API_BASE}/api/projects/page?${qs.toString()}`);
  },

  async createProject(cmd: CreateProjectRequest, idempotencyKey?: string): Promise<ProjectView> {
    return fetchJson<ProjectView>(`${API_BASE}/api/projects`, {
      method: "POST",
      idempotencyKey,
      body: JSON.stringify(cmd),
    });
  },

  async getProject(projectId: string, options?: RequestOptions): Promise<ProjectView> {
    return fetchJson<ProjectView>(`${API_BASE}/api/projects/${encodeURIComponent(projectId)}`, options);
  },
};
