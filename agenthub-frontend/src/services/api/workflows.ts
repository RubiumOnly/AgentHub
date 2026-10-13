// ============================================================================
// AgentHub Workflows, Agent Routing & Collaboration API Module
// Bounded Contexts: orchestration, agent (plus isolated team/conversation extensions)
// ============================================================================

import {
  ConversationView,
  MessageView,
  ProviderView,
  RouteDecisionView,
  TeamView,
  TokenSummaryView,
  WorkflowDefinitionView,
} from "@/types";
import { API_BASE, fetchJson } from "./errors";

export const workflowsApi = {
  async listDefinitions(): Promise<WorkflowDefinitionView[]> {
    return fetchJson<WorkflowDefinitionView[]>(`${API_BASE}/api/workflows/definitions`);
  },

  async validateDsl(dsl: unknown): Promise<{ valid: boolean; errors?: string[] }> {
    return fetchJson(`${API_BASE}/api/workflows/definitions/validate`, {
      method: "POST",
      body: JSON.stringify(dsl),
    });
  },

  async listProviders(): Promise<ProviderView[]> {
    return fetchJson<ProviderView[]>(`${API_BASE}/api/providers`);
  },

  async getTokenSummary(): Promise<TokenSummaryView> {
    return fetchJson<TokenSummaryView>(`${API_BASE}/api/token-usages/summary`);
  },

  async evaluateRoute(capabilities: string = "code,reasoning"): Promise<RouteDecisionView> {
    const req = {
      messages: [{ role: "user", content: "Optimize SQL index" }],
      metadata: { capabilities },
    };
    return fetchJson<RouteDecisionView>(`${API_BASE}/api/providers/route`, {
      method: "POST",
      body: JSON.stringify(req),
    });
  },

  async getTeam(teamId: string): Promise<TeamView | null> {
    if (!teamId) return null;
    return fetchJson<TeamView>(`${API_BASE}/api/teams/${encodeURIComponent(teamId)}`);
  },

  async listConversations(projectId?: string): Promise<ConversationView[]> {
    const query = projectId ? `?projectId=${encodeURIComponent(projectId)}` : "";
    return fetchJson<ConversationView[]>(`${API_BASE}/api/conversations${query}`);
  },

  async getMessages(convId: string): Promise<MessageView[]> {
    return fetchJson<MessageView[]>(`${API_BASE}/api/conversations/${encodeURIComponent(convId)}/messages`);
  },

  async sendMessage(convId: string, content: string, senderId = "Developer"): Promise<MessageView> {
    return fetchJson<MessageView>(`${API_BASE}/api/conversations/${encodeURIComponent(convId)}/messages`, {
      method: "POST",
      body: JSON.stringify({
        senderId,
        senderType: "USER",
        content,
      }),
    });
  },
};
