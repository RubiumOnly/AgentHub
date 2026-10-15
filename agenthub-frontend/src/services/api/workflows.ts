// ============================================================================
// AgentHub Workflows, Agent Routing & Collaboration API Module
// Bounded Contexts: orchestration, agent (plus isolated team/conversation extensions)
// ============================================================================

import {
  BudgetCheckCommand,
  BudgetCheckResultView,
  CircuitBreakerView,
  ConfirmPlanCommand,
  ConversationView,
  GeneratePlanCommand,
  MessageView,
  OrchestrationPlanView,
  ProviderHealthView,
  ProviderProbeResultView,
  ProviderView,
  RouteDecisionView,
  TeamView,
  TokenGovernanceView,
  TokenSummaryView,
  ValidateDslResultView,
  WorkflowDefinitionView,
  WorkflowDsl,
  WorkflowTemplateView,
} from "@/types";
import { API_BASE, fetchJson } from "./errors";

export const workflowsApi = {
  async listDefinitions(): Promise<WorkflowDefinitionView[]> {
    return fetchJson<WorkflowDefinitionView[]>(`${API_BASE}/api/workflows/definitions`);
  },

  async listTemplates(): Promise<WorkflowTemplateView[]> {
    return fetchJson<WorkflowTemplateView[]>(`${API_BASE}/api/workflows/templates`);
  },

  async getTemplate(id: string): Promise<WorkflowTemplateView> {
    return fetchJson<WorkflowTemplateView>(`${API_BASE}/api/workflows/templates/${encodeURIComponent(id)}`);
  },

  async instantiateTemplate(
    id: string,
    name?: string,
    description?: string
  ): Promise<WorkflowDefinitionView> {
    return fetchJson<WorkflowDefinitionView>(
      `${API_BASE}/api/workflows/templates/${encodeURIComponent(id)}/instantiate`,
      {
        method: "POST",
        body: JSON.stringify({ name, description }),
      }
    );
  },

  async generatePlan(cmd: GeneratePlanCommand): Promise<OrchestrationPlanView> {
    return fetchJson<OrchestrationPlanView>(`${API_BASE}/api/workflows/orchestrator/plans`, {
      method: "POST",
      body: JSON.stringify(cmd),
    });
  },

  async getPlan(planId: string): Promise<OrchestrationPlanView> {
    return fetchJson<OrchestrationPlanView>(
      `${API_BASE}/api/workflows/orchestrator/plans/${encodeURIComponent(planId)}`
    );
  },

  async confirmPlan(
    planId: string,
    cmd?: ConfirmPlanCommand
  ): Promise<OrchestrationPlanView> {
    return fetchJson<OrchestrationPlanView>(
      `${API_BASE}/api/workflows/orchestrator/plans/${encodeURIComponent(planId)}/confirm`,
      {
        method: "POST",
        body: JSON.stringify(cmd || { confirmed: true }),
      }
    );
  },

  async rejectPlan(planId: string, reason?: string): Promise<OrchestrationPlanView> {
    return fetchJson<OrchestrationPlanView>(
      `${API_BASE}/api/workflows/orchestrator/plans/${encodeURIComponent(planId)}/reject`,
      {
        method: "POST",
        body: JSON.stringify({ reason }),
      }
    );
  },

  async listPlansByProject(projectId: string): Promise<OrchestrationPlanView[]> {
    return fetchJson<OrchestrationPlanView[]>(
      `${API_BASE}/api/workflows/projects/${encodeURIComponent(projectId)}/plans`
    );
  },

  async validateDsl(dsl: WorkflowDsl | unknown): Promise<ValidateDslResultView> {
    return fetchJson<ValidateDslResultView>(`${API_BASE}/api/workflows/definitions/validate`, {
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

  async getProviderHealth(): Promise<ProviderHealthView[]> {
    return fetchJson<ProviderHealthView[]>(`${API_BASE}/api/providers/health`);
  },

  async probeProvider(id: string): Promise<ProviderProbeResultView> {
    return fetchJson<ProviderProbeResultView>(`${API_BASE}/api/providers/${encodeURIComponent(id)}/probe`, {
      method: "POST",
    });
  },

  async getCircuitBreaker(id: string): Promise<CircuitBreakerView> {
    return fetchJson<CircuitBreakerView>(`${API_BASE}/api/providers/${encodeURIComponent(id)}/circuit-breaker`);
  },

  async resetCircuitBreaker(id: string): Promise<CircuitBreakerView> {
    return fetchJson<CircuitBreakerView>(`${API_BASE}/api/providers/${encodeURIComponent(id)}/circuit-breaker/reset`, {
      method: "POST",
    });
  },

  async getTokenGovernance(): Promise<TokenGovernanceView> {
    return fetchJson<TokenGovernanceView>(`${API_BASE}/api/token-usages/governance`);
  },

  async checkBudget(cmd?: BudgetCheckCommand): Promise<BudgetCheckResultView> {
    return fetchJson<BudgetCheckResultView>(`${API_BASE}/api/token-usages/budget/check`, {
      method: "POST",
      body: cmd ? JSON.stringify(cmd) : undefined,
    });
  },

  async getTeam(teamId: string): Promise<TeamView | null> {
    if (!teamId) return null;
    return fetchJson<TeamView>(`${API_BASE}/api/teams/${encodeURIComponent(teamId)}`);
  },

  async listTeams(projectId?: string): Promise<TeamView[]> {
    const query = projectId ? `?projectId=${encodeURIComponent(projectId)}` : "";
    return fetchJson<TeamView[]>(`${API_BASE}/api/teams${query}`);
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
