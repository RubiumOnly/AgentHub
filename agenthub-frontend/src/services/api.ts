// ============================================================================
// AgentHub Unified API Client Barrel (Phase 1 Modular Convergence)
// Composes auth, projects, workspaces, workflows, runs, approvals, artifacts,
// deployments, stream, and errors resource modules.
// ============================================================================

import { approvalsApi } from "./api/approvals";
import { artifactsApi } from "./api/artifacts";
import {
  authApi,
  getStoredToken,
  getStoredUser,
  isDemoMode,
  setDemoMode,
  setStoredToken,
  setStoredUser,
} from "./api/auth";
import { deploymentsApi } from "./api/deployments";
import {
  API_BASE,
  ApiError,
  createCancelableRequest,
  createEmptyResourceState,
  DEFAULT_PREVIEW_URL,
  DEFAULT_PROJECT_ID,
  DEFAULT_WORKSPACE_PATH,
  EmptyResourceState,
  fetchJson,
  RequestOptions,
} from "./api/errors";
import { PagedResponse, projectsApi } from "./api/projects";
import {
  MOCK_APPROVAL,
  MOCK_DEPLOYMENT,
  MOCK_DIFFS,
  MOCK_PROVIDERS,
  MOCK_RUN,
  MOCK_STEPS,
  MOCK_TEAM,
  MOCK_TOKEN_SUMMARY,
  runsApi,
} from "./api/runs";
import { streamApi, StreamConnectionOptions } from "./api/stream";
import { workflowsApi } from "./api/workflows";
import { workspacesApi } from "./api/workspaces";

export {
  API_BASE,
  DEFAULT_WORKSPACE_PATH,
  DEFAULT_PROJECT_ID,
  DEFAULT_PREVIEW_URL,
  ApiError,
  fetchJson,
  createCancelableRequest,
  createEmptyResourceState,
  getStoredToken,
  setStoredToken,
  getStoredUser,
  setStoredUser,
  isDemoMode,
  setDemoMode,
  authApi,
  projectsApi,
  workspacesApi,
  workflowsApi,
  runsApi,
  approvalsApi,
  artifactsApi,
  deploymentsApi,
  streamApi,
  MOCK_RUN,
  MOCK_STEPS,
  MOCK_APPROVAL,
  MOCK_PROVIDERS,
  MOCK_TOKEN_SUMMARY,
  MOCK_TEAM,
  MOCK_DEPLOYMENT,
  MOCK_DIFFS,
};

export type { EmptyResourceState, RequestOptions, PagedResponse, StreamConnectionOptions };

export const apiClient = {
  ...authApi,
  ...projectsApi,
  ...workspacesApi,
  ...workflowsApi,
  ...runsApi,
  ...approvalsApi,
  ...artifactsApi,
  ...deploymentsApi,
  ...streamApi,
};
