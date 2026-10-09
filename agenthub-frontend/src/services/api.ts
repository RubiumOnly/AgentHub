// ============================================================================
// AgentHub Unified API Client & Resilience Layer
// Stage B: Frontend De-mocking, Real Authentication & Production Portability
// ============================================================================

import {
  WorkflowRunView,
  StepRunView,
  ApprovalView,
  WorkflowDefinitionView,
  TeamView,
  MessageView,
  ConversationView,
  FileDiffEntry,
  StructuredDiff,
  WorkspaceFileNode,
  ArtifactView,
  ProviderView,
  TokenSummaryView,
  DeploymentResponse,
  CreateDeploymentRequest,
  RouteDecisionView,
  RunEventView,
  ProjectView,
  WorkspaceView,
  UserView,
  AuthTokenView,
  CreateProjectRequest,
  StartRunRequest,
} from "@/types";

// Dynamic API Base URL: defaults to same-origin relative path "" for portable Nginx reverse proxy,
// or uses configured NEXT_PUBLIC_API_URL without trailing slash.
export const API_BASE = (process.env.NEXT_PUBLIC_API_URL || "").replace(/\/$/, "");

export const DEFAULT_WORKSPACE_PATH = "d:/work/agenthub/data/workspaces/default";
export const DEFAULT_PROJECT_ID = "proj-default";
export const DEFAULT_PREVIEW_URL = `${API_BASE}/api/sandbox/preview/default`;

// ============================================================================
// Token & Session Storage Management
// ============================================================================
const TOKEN_KEY = "agenthub_token";
const USER_KEY = "agenthub_user";
const DEMO_MODE_KEY = "agenthub_demo_mode";

export function getStoredToken(): string | null {
  if (typeof window === "undefined") return null;
  return localStorage.getItem(TOKEN_KEY);
}

export function setStoredToken(token: string | null): void {
  if (typeof window === "undefined") return;
  if (token) {
    localStorage.setItem(TOKEN_KEY, token);
    document.cookie = `agenthub_token=${encodeURIComponent(token)}; path=/; max-age=86400; SameSite=Lax`;
  } else {
    localStorage.removeItem(TOKEN_KEY);
    document.cookie = `agenthub_token=; path=/; max-age=0; SameSite=Lax`;
  }
}

export function getStoredUser(): UserView | null {
  if (typeof window === "undefined") return null;
  try {
    const raw = localStorage.getItem(USER_KEY);
    return raw ? JSON.parse(raw) : null;
  } catch {
    return null;
  }
}

export function setStoredUser(user: UserView | null): void {
  if (typeof window === "undefined") return;
  if (user) {
    localStorage.setItem(USER_KEY, JSON.stringify(user));
  } else {
    localStorage.removeItem(USER_KEY);
  }
}

export function isDemoMode(): boolean {
  if (typeof window === "undefined") {
    return process.env.NEXT_PUBLIC_AGENTHUB_DEMO_MODE === "true";
  }
  const override = localStorage.getItem(DEMO_MODE_KEY);
  if (override !== null) {
    return override === "true";
  }
  return process.env.NEXT_PUBLIC_AGENTHUB_DEMO_MODE === "true";
}

export function setDemoMode(enabled: boolean): void {
  if (typeof window === "undefined") return;
  localStorage.setItem(DEMO_MODE_KEY, String(enabled));
}

// Custom Error Class
export class ApiError extends Error {
  status: number;
  code?: number;
  constructor(message: string, status: number, code?: number) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.code = code;
  }
}

// ============================================================================
// Robust Generic Fetch Helper
// ============================================================================
async function fetchJson<T>(url: string, options?: RequestInit): Promise<T> {
  const token = getStoredToken();
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    ...(options?.headers as Record<string, string> || {}),
  };

  // Attach authentic Bearer token when available
  if (token) {
    headers["Authorization"] = `Bearer ${token}`;
  }

  const res = await fetch(url, {
    ...options,
    credentials: "include",
    headers,
  });

  if (res.status === 401) {
    if (typeof window !== "undefined") {
      window.dispatchEvent(new CustomEvent("agenthub:unauthorized"));
    }
    throw new ApiError("认证失效或未登录，请登录后继续操作", 401, 1001);
  }

  if (res.status === 403) {
    throw new ApiError("访问拒绝：无权操作该资源", 403, 1002);
  }

  if (!res.ok) {
    let errMessage = `HTTP 请求失败 (${res.status})`;
    let errCode: number | undefined;
    try {
      const errJson = await res.json();
      if (errJson.message) errMessage = errJson.message;
      if (errJson.code) errCode = errJson.code;
    } catch {}
    throw new ApiError(errMessage, res.status, errCode);
  }

  const json = await res.json();
  return (json.data !== undefined ? json.data : json) as T;
}

// ============================================================================
// Realistic Fallback Fixtures (EXCLUSIVELY FOR EXPLICIT DEMO MODE)
// ============================================================================
export const MOCK_RUN: WorkflowRunView = {
  id: "run-exec-94218a",
  projectId: "proj-default",
  definitionId: "wf-enterprise-auth-delivery",
  status: "RUNNING",
  idempotencyKey: "idem-key-8481a7",
  startedAt: new Date(Date.now() - 120000).toISOString(),
  createdAt: new Date(Date.now() - 125000).toISOString(),
  correlationId: "corr-819a-993f",
};

export const MOCK_STEPS: StepRunView[] = [
  {
    id: "step-1-start",
    runId: "run-exec-94218a",
    nodeId: "node-start",
    status: "SUCCEEDED",
    attempt: 1,
    durationMs: 45,
    startedAt: new Date(Date.now() - 118000).toISOString(),
    finishedAt: new Date(Date.now() - 117955).toISOString(),
  },
  {
    id: "step-2-arch",
    runId: "run-exec-94218a",
    nodeId: "node-backend-architect",
    status: "SUCCEEDED",
    attempt: 1,
    durationMs: 4210,
    outputRef: "art-backend-spec-v1",
    startedAt: new Date(Date.now() - 115000).toISOString(),
    finishedAt: new Date(Date.now() - 110790).toISOString(),
  },
  {
    id: "step-3-frontend",
    runId: "run-exec-94218a",
    nodeId: "node-frontend-engineer",
    status: "SUCCEEDED",
    attempt: 1,
    durationMs: 5120,
    outputRef: "art-frontend-components-v1",
    startedAt: new Date(Date.now() - 110000).toISOString(),
    finishedAt: new Date(Date.now() - 104880).toISOString(),
  },
  {
    id: "step-4-qa",
    runId: "run-exec-94218a",
    nodeId: "node-qa-audit",
    status: "SUCCEEDED",
    attempt: 1,
    durationMs: 1840,
    outputRef: "art-jgit-diff-snapshot",
    startedAt: new Date(Date.now() - 104000).toISOString(),
    finishedAt: new Date(Date.now() - 102160).toISOString(),
  },
  {
    id: "step-4b-condition",
    runId: "run-exec-94218a",
    nodeId: "node-qa-eval",
    status: "SUCCEEDED",
    attempt: 1,
    durationMs: 120,
    startedAt: new Date(Date.now() - 102150).toISOString(),
    finishedAt: new Date(Date.now() - 102030).toISOString(),
  },
  {
    id: "step-4c-fallback",
    runId: "run-exec-94218a",
    nodeId: "node-qa-fallback",
    status: "SKIPPED",
    attempt: 0,
  },
  {
    id: "step-5-approval",
    runId: "run-exec-94218a",
    nodeId: "node-security-gate",
    status: "WAITING_APPROVAL",
    attempt: 1,
    startedAt: new Date(Date.now() - 102000).toISOString(),
    errorMessage: "等待安全架构师批准基线合并与生产沙箱发布",
  },
  {
    id: "step-6-deploy",
    runId: "run-exec-94218a",
    nodeId: "node-sandbox-deploy",
    status: "PENDING",
    attempt: 0,
  },
  {
    id: "step-7-end",
    runId: "run-exec-94218a",
    nodeId: "node-end",
    status: "PENDING",
    attempt: 0,
  },
];

export const MOCK_APPROVAL: ApprovalView = {
  id: "appr-gate-8314e1",
  runId: "run-exec-94218a",
  stepRunId: "step-5-approval",
  status: "PENDING",
  requestedBy: "OrchestratorKernel",
  comments: "JGit 工作区已完成 3 个文件修改 (+148, -12 行)，已通过 QA 边界防御测试，请求准予沙箱容器部署。",
  createdAt: new Date(Date.now() - 95000).toISOString(),
};

export const MOCK_PROVIDERS: ProviderView[] = [
  {
    id: "prov-deepseek",
    providerType: "DEEPSEEK_CHAT",
    model: "deepseek-chat",
    status: "ACTIVE",
    priority: 100,
    weight: 10,
    capabilities: "code,general,reasoning",
    costPerMillionInput: 0.14,
    costPerMillionOutput: 0.28,
    circuitStatus: "CLOSED",
    avgLatencyMs: 245,
  },
  {
    id: "prov-anthropic",
    providerType: "ANTHROPIC_CLAUDE",
    model: "claude-3-5-sonnet-20241022",
    status: "ACTIVE",
    priority: 90,
    weight: 8,
    capabilities: "code,reasoning,long_context",
    costPerMillionInput: 3.00,
    costPerMillionOutput: 15.00,
    circuitStatus: "CLOSED",
    avgLatencyMs: 380,
  },
  {
    id: "prov-gemini",
    providerType: "GOOGLE_GEMINI",
    model: "gemini-1.5-flash",
    status: "ACTIVE",
    priority: 85,
    weight: 6,
    capabilities: "fast,multimodal,general",
    costPerMillionInput: 0.075,
    costPerMillionOutput: 0.30,
    circuitStatus: "CLOSED",
    avgLatencyMs: 160,
  },
  {
    id: "prov-ollama",
    providerType: "OLLAMA_LOCAL",
    model: "qwen2.5-coder:14b",
    status: "ACTIVE",
    priority: 70,
    weight: 5,
    capabilities: "code,offline,zero_cost",
    costPerMillionInput: 0.0,
    costPerMillionOutput: 0.0,
    circuitStatus: "CLOSED",
    avgLatencyMs: 520,
  },
  {
    id: "prov-backup-openai",
    providerType: "OPENAI_COMPATIBLE",
    model: "gpt-4o-mini",
    status: "STANDBY",
    priority: 60,
    weight: 4,
    capabilities: "code,fast",
    costPerMillionInput: 0.15,
    costPerMillionOutput: 0.60,
    circuitStatus: "HALF_OPEN",
    avgLatencyMs: 310,
  },
];

export const MOCK_TOKEN_SUMMARY: TokenSummaryView = {
  totalPromptTokens: 84920,
  totalCompletionTokens: 31450,
  totalTokens: 116370,
  totalEstimatedCost: 0.0218,
  totalInvocations: 42,
};

export const MOCK_TEAM: TeamView = {
  id: "team-dev-swarm",
  projectId: "proj-default",
  name: "🔥 全栈自主开发先锋军团",
  description: "基于三层拓扑与四层防死循环熔断的敏捷多 Agent 协同网络",
  topology: "HIERARCHICAL",
  leaderAgentId: "Orchestrator",
  maxTurns: 12,
  status: "ACTIVE",
  members: [
    {
      id: "mem-1",
      teamId: "team-dev-swarm",
      agentInstanceId: "Orchestrator",
      role: "协同规划中枢",
      roleType: "ORCHESTRATOR",
      sortOrder: 1,
      canDelegate: true,
      responsibilities: "DAG 拆解、任务派发与审批门禁拦截",
    },
    {
      id: "mem-2",
      teamId: "team-dev-swarm",
      agentInstanceId: "BackendArchitect",
      role: "后端领域架构师",
      roleType: "ARCHITECT",
      sortOrder: 2,
      canDelegate: false,
      responsibilities: "Spring Boot 3.3 / DDD 四层设计与 REST 契约实现",
    },
    {
      id: "mem-3",
      teamId: "team-dev-swarm",
      agentInstanceId: "FrontendEngineer",
      role: "前端美学工程师",
      roleType: "CODER",
      sortOrder: 3,
      canDelegate: false,
      responsibilities: "Next.js 14 App Router / Bento Grid 交互落地",
    },
    {
      id: "mem-4",
      teamId: "team-dev-swarm",
      agentInstanceId: "QAAuditor",
      role: "QA 与质量守门人",
      roleType: "REVIEWER",
      sortOrder: 4,
      canDelegate: false,
      responsibilities: "JGit Unified Diff 审查与五维边界测试验证",
    },
    {
      id: "mem-5",
      teamId: "team-dev-swarm",
      agentInstanceId: "SecOpsGovernor",
      role: "安全沙箱运维官",
      roleType: "TESTER",
      sortOrder: 5,
      canDelegate: false,
      responsibilities: "命令防火墙、非特权容器与端口回收治理",
    },
  ],
};

export const MOCK_DEPLOYMENT: DeploymentResponse = {
  id: "dep-inst-71829a",
  projectId: "proj-default",
  artifactId: "art-bundle-prod-v1",
  status: "RUNNING",
  target: "STATIC_PREVIEW",
  sandboxType: "LOCAL_PROCESS",
  port: 18080,
  url: "http://localhost:8080/api/sandbox/preview/default",
  buildCommand: "npm run build --prefix frontend",
  startCommand: "npm run start --port 18080",
  startedAt: new Date(Date.now() - 360000).toISOString(),
  createdAt: new Date(Date.now() - 390000).toISOString(),
  hasLogs: true,
};

export const MOCK_DIFFS: FileDiffEntry[] = [
  {
    oldPath: "src/main/java/com/agenthub/identity/AuthController.java",
    newPath: "src/main/java/com/agenthub/identity/AuthController.java",
    changeType: "MODIFY",
    linesAdded: 48,
    linesDeleted: 6,
    diffContent: `--- a/src/main/java/com/agenthub/identity/AuthController.java
+++ b/src/main/java/com/agenthub/identity/AuthController.java
@@ -24,8 +24,32 @@ public class AuthController {
+    @PostMapping("/login")
+    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest req) {
+        // Rate limit and authenticate credentials safely
+        AuthUser user = authService.authenticate(req.getUsername(), req.getPassword());
+        String token = jwtTokenProvider.generateToken(user);
+        return Result.ok(new LoginResponse(token, user.toView()));
+    }
+
+    @PostMapping("/refresh")
+    public Result<TokenResponse> refreshToken(@RequestHeader("Authorization") String token) {
+        return Result.ok(authService.refreshToken(token));
+    }`,
  },
  {
    oldPath: "src/main/resources/application.yml",
    newPath: "src/main/resources/application.yml",
    changeType: "MODIFY",
    linesAdded: 16,
    linesDeleted: 2,
    diffContent: `--- a/src/main/resources/application.yml
+++ b/src/main/resources/application.yml
@@ -10,2 +10,16 @@
+security:
+  jwt:
+    secret-ref: env:AGENTHUB_JWT_SECRET
+    expiration-ms: 86400000
+sandbox:
+  port-pool:
+    min-port: 18000
+    max-port: 18999`,
  },
  {
    oldPath: "/dev/null",
    newPath: "src/test/java/com/agenthub/AuthSecurityIntegrationTest.java",
    changeType: "ADD",
    linesAdded: 84,
    linesDeleted: 0,
    diffContent: `--- /dev/null
+++ b/src/test/java/com/agenthub/AuthSecurityIntegrationTest.java
@@ -0,0 +84 @@
+package com.agenthub;
+
+import org.junit.jupiter.api.Test;
+import org.springframework.boot.test.context.SpringBootTest;
+
+@SpringBootTest
+public class AuthSecurityIntegrationTest {
+    @Test
+    void shouldEnforceBoundaryRateLimiting() {
+        // Verified safe under high concurrency
+    }
+}`,
  },
];

// ============================================================================
// Real Production API Client
// ============================================================================
export const apiClient = {
  // 1. System Health
  async getHealth(): Promise<{ status: string; uptime?: string; version?: string }> {
    return fetchJson(`${API_BASE}/api/system/health`);
  },

  // 2. Authentication Flow
  async login(email: string, password: string): Promise<AuthTokenView> {
    const res = await fetchJson<AuthTokenView>(`${API_BASE}/api/auth/login`, {
      method: "POST",
      body: JSON.stringify({ email, password }),
    });
    if (res && res.token) {
      setStoredToken(res.token);
      setStoredUser(res.user);
    }
    return res;
  },

  async register(email: string, password: string, displayName?: string): Promise<AuthTokenView> {
    const res = await fetchJson<AuthTokenView>(`${API_BASE}/api/auth/register`, {
      method: "POST",
      body: JSON.stringify({ email, password, displayName: displayName || email.split("@")[0] }),
    });
    if (res && res.token) {
      setStoredToken(res.token);
      setStoredUser(res.user);
    }
    return res;
  },

  async logout(): Promise<void> {
    try {
      await fetchJson(`${API_BASE}/api/auth/logout`, { method: "POST" });
    } catch {}
    setStoredToken(null);
    setStoredUser(null);
  },

  async createStreamTicket(): Promise<string> {
    const data = await fetchJson<{ ticket: string } | string>(`${API_BASE}/api/auth/stream-ticket`, {
      method: "POST",
    });
    if (typeof data === "string") return data;
    return data.ticket;
  },

  // 3. Projects & Workspaces
  async listProjects(): Promise<ProjectView[]> {
    return fetchJson<ProjectView[]>(`${API_BASE}/api/projects`);
  },

  async createProject(cmd: CreateProjectRequest): Promise<ProjectView> {
    return fetchJson<ProjectView>(`${API_BASE}/api/projects`, {
      method: "POST",
      body: JSON.stringify(cmd),
    });
  },

  async getProject(projectId: string): Promise<ProjectView> {
    return fetchJson<ProjectView>(`${API_BASE}/api/projects/${projectId}`);
  },

  async getWorkspace(projectId: string): Promise<WorkspaceView> {
    return fetchJson<WorkspaceView>(`${API_BASE}/api/projects/${projectId}/workspace`);
  },

  async getStructuredDiff(workspaceId: string): Promise<StructuredDiff | null> {
    return fetchJson<StructuredDiff>(`${API_BASE}/api/workspaces/${workspaceId}/diff`);
  },

  async listFiles(workspaceId: string): Promise<WorkspaceFileNode[]> {
    return fetchJson<WorkspaceFileNode[]>(`${API_BASE}/api/workspaces/${workspaceId}/files`);
  },

  async getFileContent(workspaceId: string, filePath: string): Promise<string> {
    return fetchJson<string>(`${API_BASE}/api/workspaces/${workspaceId}/file?path=${encodeURIComponent(filePath)}`);
  },

  async saveFile(workspaceId: string, filePath: string, content: string): Promise<boolean> {
    await fetchJson(`${API_BASE}/api/workspaces/${workspaceId}/file`, {
      method: "POST",
      body: JSON.stringify({ path: filePath, content }),
    });
    return true;
  },

  async getLockStatus(workspaceId: string): Promise<{ workspaceId: string; ownerId?: string; locked: boolean }> {
    return fetchJson(`${API_BASE}/api/workspaces/${workspaceId}/lock/status`);
  },

  // 4. Workflow Definitions & Orchestration
  async listDefinitions(): Promise<WorkflowDefinitionView[]> {
    return fetchJson<WorkflowDefinitionView[]>(`${API_BASE}/api/workflows/definitions`);
  },

  async validateDsl(dsl: any): Promise<{ valid: boolean; errors?: string[] }> {
    return fetchJson(`${API_BASE}/api/workflows/definitions/validate`, {
      method: "POST",
      body: JSON.stringify(dsl),
    });
  },

  // 5. Workflow Runs & Steps
  async startRun(req: StartRunRequest): Promise<WorkflowRunView> {
    return fetchJson<WorkflowRunView>(`${API_BASE}/api/executions/runs`, {
      method: "POST",
      body: JSON.stringify(req),
    });
  },

  async getRun(runId: string): Promise<WorkflowRunView> {
    return fetchJson<WorkflowRunView>(`${API_BASE}/api/executions/runs/${runId}`);
  },

  async listRunsByProject(projectId: string): Promise<WorkflowRunView[]> {
    return fetchJson<WorkflowRunView[]>(`${API_BASE}/api/executions/projects/${projectId}/runs`);
  },

  async listStepRuns(runId: string): Promise<StepRunView[]> {
    return fetchJson<StepRunView[]>(`${API_BASE}/api/executions/runs/${runId}/steps`);
  },

  async listEvents(runId: string, afterSeq?: number): Promise<RunEventView[]> {
    const query = afterSeq !== undefined ? `?afterSeq=${afterSeq}` : "";
    return fetchJson<RunEventView[]>(`${API_BASE}/api/executions/runs/${runId}/events${query}`);
  },

  async cancelRun(runId: string, reason?: string): Promise<WorkflowRunView> {
    return fetchJson<WorkflowRunView>(`${API_BASE}/api/executions/runs/${runId}/cancel`, {
      method: "POST",
      body: JSON.stringify({ reason: reason || "Manual cancellation from Bento UI" }),
    });
  },

  // 6. Human-in-the-Loop Approvals
  async listApprovals(runId: string): Promise<ApprovalView[]> {
    return fetchJson<ApprovalView[]>(`${API_BASE}/api/approvals/runs/${runId}`);
  },

  async approve(approvalId: string, reviewer?: string, reason?: string): Promise<ApprovalView> {
    return fetchJson<ApprovalView>(`${API_BASE}/api/approvals/${approvalId}/approve`, {
      method: "POST",
      body: JSON.stringify({ reason: reason || "Approved by Reviewer" }),
    });
  },

  async reject(approvalId: string, reviewer?: string, reason?: string): Promise<ApprovalView> {
    return fetchJson<ApprovalView>(`${API_BASE}/api/approvals/${approvalId}/reject`, {
      method: "POST",
      body: JSON.stringify({ reason: reason || "Rejected by Reviewer" }),
    });
  },

  // 7. Team Swarm & Conversations
  async getTeam(teamId: string = "team-dev-swarm"): Promise<TeamView> {
    return fetchJson<TeamView>(`${API_BASE}/api/teams/${teamId}`);
  },

  async listConversations(): Promise<ConversationView[]> {
    return fetchJson<ConversationView[]>(`${API_BASE}/api/im/conversations`);
  },

  async getMessages(convId: string): Promise<MessageView[]> {
    return fetchJson<MessageView[]>(`${API_BASE}/api/im/conversations/${convId}/messages`);
  },

  async sendMessage(convId: string, content: string, senderId = "Developer"): Promise<MessageView> {
    return fetchJson<MessageView>(`${API_BASE}/api/im/conversations/${convId}/messages`, {
      method: "POST",
      body: JSON.stringify({
        senderId,
        senderType: "USER",
        content,
      }),
    });
  },

  // 8. Artifacts
  async revertArtifact(artifactId: string): Promise<ArtifactView> {
    return fetchJson<ArtifactView>(`${API_BASE}/api/audit/artifacts/${artifactId}/revert`, {
      method: "POST",
    });
  },

  // 9. Provider Dynamic Routing & Token Usages
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

  // 10. Deployments & Sandbox
  async listDeployments(projectId: string = DEFAULT_PROJECT_ID): Promise<DeploymentResponse[]> {
    return fetchJson<DeploymentResponse[]>(`${API_BASE}/api/deployments?projectId=${encodeURIComponent(projectId)}`);
  },

  async createDeployment(req: CreateDeploymentRequest): Promise<DeploymentResponse> {
    return fetchJson<DeploymentResponse>(`${API_BASE}/api/deployments`, {
      method: "POST",
      body: JSON.stringify(req),
    });
  },

  async stopDeployment(id: string): Promise<DeploymentResponse> {
    return fetchJson<DeploymentResponse>(`${API_BASE}/api/deployments/${id}/stop`, {
      method: "POST",
    });
  },

  async getDeploymentLogs(id: string): Promise<string> {
    return fetchJson<string>(`${API_BASE}/api/deployments/${id}/logs`);
  },

  // 11. Deprecated Workspace Fallback Helpers for Legacy Test Scenarios
  async getDiff(workspacePath: string = DEFAULT_WORKSPACE_PATH): Promise<FileDiffEntry[]> {
    try {
      const data = await fetchJson<FileDiffEntry[]>(
        `${API_BASE}/api/workspace/diff?path=${encodeURIComponent(workspacePath)}`
      );
      return data || [];
    } catch {
      return [];
    }
  },
};
