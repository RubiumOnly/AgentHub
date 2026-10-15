// ============================================================================
// AgentHub Projects API Module (Bounded Context: project)
// ============================================================================

import {
  CreateProjectRequest,
  ProjectView,
  GitSourceConfig,
  OnboardingSessionView,
  OnboardingStep,
  ContextSourceView,
  ProjectUserRoleView,
  ProjectMemberView,
  AddProjectMemberCommand,
  UpdateMemberRoleCommand,
} from "@/types";
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

  // Onboarding Wizard API
  async startOnboarding(cmd: {
    projectName: string;
    description?: string;
    sourceConfig: GitSourceConfig;
  }): Promise<OnboardingSessionView> {
    return fetchJson<OnboardingSessionView>(`${API_BASE}/api/projects/onboarding/start`, {
      method: "POST",
      body: JSON.stringify(cmd),
    });
  },

  async getOnboardingSession(sessionId: string): Promise<OnboardingSessionView> {
    return fetchJson<OnboardingSessionView>(`${API_BASE}/api/projects/onboarding/${encodeURIComponent(sessionId)}`);
  },

  async executeOnboardingStep(
    sessionId: string,
    cmd: { step?: OnboardingStep; selectedBranch?: string; customRules?: string }
  ): Promise<OnboardingSessionView> {
    return fetchJson<OnboardingSessionView>(`${API_BASE}/api/projects/onboarding/${encodeURIComponent(sessionId)}/step`, {
      method: "POST",
      body: JSON.stringify(cmd),
    });
  },

  async retryOnboardingStep(sessionId: string, step?: OnboardingStep): Promise<OnboardingSessionView> {
    const query = step ? `?step=${encodeURIComponent(step)}` : "";
    return fetchJson<OnboardingSessionView>(
      `${API_BASE}/api/projects/onboarding/${encodeURIComponent(sessionId)}/retry${query}`,
      { method: "POST" }
    );
  },

  async abortOnboarding(sessionId: string): Promise<OnboardingSessionView> {
    return fetchJson<OnboardingSessionView>(
      `${API_BASE}/api/projects/onboarding/${encodeURIComponent(sessionId)}/abort`,
      { method: "POST" }
    );
  },

  async completeOnboarding(sessionId: string): Promise<ProjectView> {
    return fetchJson<ProjectView>(
      `${API_BASE}/api/projects/onboarding/${encodeURIComponent(sessionId)}/complete`,
      { method: "POST" }
    );
  },

  // Context Sources API
  async listContextSources(projectId: string): Promise<ContextSourceView[]> {
    return fetchJson<ContextSourceView[]>(`${API_BASE}/api/projects/${encodeURIComponent(projectId)}/context-sources`);
  },

  async registerContextSource(
    projectId: string,
    cmd: {
      sourceType: string;
      name: string;
      relativePath?: string;
      content: string;
      commitHash?: string;
      securityLevel?: string;
      metadata?: Record<string, string>;
    }
  ): Promise<ContextSourceView> {
    return fetchJson<ContextSourceView>(`${API_BASE}/api/projects/${encodeURIComponent(projectId)}/context-sources`, {
      method: "POST",
      body: JSON.stringify(cmd),
    });
  },

  // Team RBAC API
  async getCurrentUserRole(projectId: string): Promise<ProjectUserRoleView> {
    return fetchJson<ProjectUserRoleView>(`${API_BASE}/api/teams/projects/${encodeURIComponent(projectId)}/roles/me`);
  },

  async listProjectMembers(projectId: string): Promise<ProjectMemberView[]> {
    return fetchJson<ProjectMemberView[]>(`${API_BASE}/api/teams/projects/${encodeURIComponent(projectId)}/members`);
  },

  async addProjectMember(projectId: string, cmd: AddProjectMemberCommand): Promise<ProjectMemberView> {
    return fetchJson<ProjectMemberView>(`${API_BASE}/api/teams/projects/${encodeURIComponent(projectId)}/members`, {
      method: "POST",
      body: JSON.stringify(cmd),
    });
  },

  async updateMemberRole(projectId: string, memberId: string, cmd: UpdateMemberRoleCommand): Promise<ProjectMemberView> {
    return fetchJson<ProjectMemberView>(
      `${API_BASE}/api/teams/projects/${encodeURIComponent(projectId)}/members/${encodeURIComponent(memberId)}/role`,
      {
        method: "PUT",
        body: JSON.stringify(cmd),
      }
    );
  },

  async removeProjectMember(projectId: string, memberId: string): Promise<void> {
    await fetchJson<void>(
      `${API_BASE}/api/teams/projects/${encodeURIComponent(projectId)}/members/${encodeURIComponent(memberId)}`,
      {
        method: "DELETE",
      }
    );
  },
};
