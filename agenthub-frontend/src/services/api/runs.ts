// ============================================================================
// AgentHub Runs, StepRuns, Events & Evidence API Module (Bounded Context: execution)
// ============================================================================

import {
  ApprovalView,
  DeploymentResponse,
  FileDiffEntry,
  ProviderView,
  RunEventView,
  RunEvidenceBundleView,
  RunOperationsView,
  StartRunRequest,
  StepRunView,
  TeamView,
  TokenSummaryView,
  WorkflowRunView,
} from "@/types";
import { getStoredToken, isDemoMode } from "./auth";
import { API_BASE, DEFAULT_PREVIEW_URL, fetchJson, RequestOptions } from "./errors";
import { PagedResponse } from "./projects";

// ============================================================================
// Explicit Demo Mode Fixtures (ONLY used when isDemoMode() === true)
// ============================================================================
export const MOCK_RUN: WorkflowRunView = {
  id: "run-exec-94218a",
  projectId: "proj-demo-simulated",
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
    errorMessage: "等待安全架构师批准基线合并与沙箱发布",
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
    costPerMillionInput: 3.0,
    costPerMillionOutput: 15.0,
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
    costPerMillionOutput: 0.3,
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
    costPerMillionOutput: 0.6,
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
  id: "team-demo-simulated",
  projectId: "proj-demo-simulated",
  name: "全栈自主变更协同团队 (Demo Simulated)",
  description: "基于三层拓扑与四层防死循环熔断的多 Agent 协同网络",
  topology: "HIERARCHICAL",
  leaderAgentId: "Orchestrator",
  maxTurns: 12,
  status: "ACTIVE",
  members: [
    {
      id: "mem-1",
      teamId: "team-demo-simulated",
      agentInstanceId: "Orchestrator",
      role: "协同规划中枢",
      roleType: "ORCHESTRATOR",
      sortOrder: 1,
      canDelegate: true,
      responsibilities: "DAG 拆解、任务派发与审批门禁拦截",
    },
    {
      id: "mem-2",
      teamId: "team-demo-simulated",
      agentInstanceId: "BackendArchitect",
      role: "后端领域架构师",
      roleType: "ARCHITECT",
      sortOrder: 2,
      canDelegate: false,
      responsibilities: "Spring Boot 3.3 / DDD 四层设计与 REST 契约实现",
    },
    {
      id: "mem-3",
      teamId: "team-demo-simulated",
      agentInstanceId: "FrontendEngineer",
      role: "前端美学工程师",
      roleType: "CODER",
      sortOrder: 3,
      canDelegate: false,
      responsibilities: "Next.js 14 App Router / Bento Grid 交互落地",
    },
    {
      id: "mem-4",
      teamId: "team-demo-simulated",
      agentInstanceId: "QAAuditor",
      role: "QA 与质量守门人",
      roleType: "REVIEWER",
      sortOrder: 4,
      canDelegate: false,
      responsibilities: "JGit Unified Diff 审查与五维边界测试验证",
    },
    {
      id: "mem-5",
      teamId: "team-demo-simulated",
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
  projectId: "proj-demo-simulated",
  artifactId: "art-bundle-prod-v1",
  status: "RUNNING",
  target: "STATIC_PREVIEW",
  sandboxType: "LOCAL_PROCESS",
  port: 18080,
  url: DEFAULT_PREVIEW_URL,
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
+        AuthUser user = authService.authenticate(req.getUsername(), req.getPassword());
+        String token = jwtTokenProvider.generateToken(user);
+        return Result.ok(new LoginResponse(token, user.toView()));
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
+    expiration-ms: 86400000`,
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
+    }
+}`,
  },
];

export const runsApi = {
  async startRun(req: StartRunRequest): Promise<WorkflowRunView> {
    const idempotencyKey = req.idempotencyKey || `idem-${Date.now()}`;
    return fetchJson<WorkflowRunView>(`${API_BASE}/api/executions/runs`, {
      method: "POST",
      idempotencyKey,
      body: JSON.stringify({ ...req, idempotencyKey }),
    });
  },

  async getRun(runId: string, options?: RequestOptions): Promise<WorkflowRunView> {
    return fetchJson<WorkflowRunView>(`${API_BASE}/api/executions/runs/${encodeURIComponent(runId)}`, options);
  },

  async listRunsByProject(projectId: string): Promise<WorkflowRunView[]> {
    if (!projectId) return [];
    return fetchJson<WorkflowRunView[]>(
      `${API_BASE}/api/executions/projects/${encodeURIComponent(projectId)}/runs`
    );
  },

  async listRunsPage(
    projectId: string,
    page = 1,
    size = 20,
    sortBy = "createdAt",
    sortDir: "ASC" | "DESC" = "DESC",
    status?: string
  ): Promise<PagedResponse<WorkflowRunView>> {
    const qs = new URLSearchParams({
      page: String(page),
      size: String(size),
      sortBy,
      sortDir,
    });
    if (status) qs.set("status", status);
    return fetchJson<PagedResponse<WorkflowRunView>>(
      `${API_BASE}/api/executions/projects/${encodeURIComponent(projectId)}/runs/page?${qs.toString()}`
    );
  },

  async listStepRuns(runId: string): Promise<StepRunView[]> {
    if (!runId) return [];
    return fetchJson<StepRunView[]>(`${API_BASE}/api/executions/runs/${encodeURIComponent(runId)}/steps`);
  },

  async listEvents(runId: string, afterSeq?: number): Promise<RunEventView[]> {
    if (!runId) return [];
    const query = afterSeq !== undefined ? `?afterSeq=${afterSeq}` : "";
    return fetchJson<RunEventView[]>(
      `${API_BASE}/api/executions/runs/${encodeURIComponent(runId)}/events${query}`
    );
  },

  async cancelRun(runId: string, reason?: string): Promise<WorkflowRunView> {
    return fetchJson<WorkflowRunView>(`${API_BASE}/api/executions/runs/${encodeURIComponent(runId)}/cancel`, {
      method: "POST",
      body: JSON.stringify({ reason: reason || "Manual cancellation from UI" }),
    });
  },

  async pauseRun(runId: string): Promise<WorkflowRunView> {
    return fetchJson<WorkflowRunView>(`${API_BASE}/api/executions/runs/${encodeURIComponent(runId)}/pause`, {
      method: "POST",
    });
  },

  async resumeRun(runId: string): Promise<WorkflowRunView> {
    return fetchJson<WorkflowRunView>(`${API_BASE}/api/executions/runs/${encodeURIComponent(runId)}/resume`, {
      method: "POST",
    });
  },

  async retryStep(runId: string, stepRunId: string): Promise<StepRunView> {
    return fetchJson<StepRunView>(`${API_BASE}/api/executions/runs/${encodeURIComponent(runId)}/steps/${encodeURIComponent(stepRunId)}/retry`, {
      method: "POST",
    });
  },

  async reworkStep(runId: string, stepRunId: string, feedback?: string): Promise<StepRunView> {
    if (isDemoMode()) {
      const step = MOCK_STEPS.find((s) => s.id === stepRunId) || MOCK_STEPS[0];
      return {
        ...step,
        status: "PENDING",
        attempt: (step.attempt || 1) + 1,
        outputRef: `Rework feedback: ${feedback || "Rework requested"}`,
      };
    }
    if (!runId || !stepRunId) throw new Error("runId and stepRunId are required");
    return fetchJson<StepRunView>(`${API_BASE}/api/executions/runs/${encodeURIComponent(runId)}/steps/${encodeURIComponent(stepRunId)}/rework`, {
      method: "POST",
      body: JSON.stringify({ feedback: feedback || "Rework requested by reviewer" }),
    });
  },

  async rejectRun(runId: string, reason?: string): Promise<WorkflowRunView> {
    if (isDemoMode()) {
      return {
        ...MOCK_RUN,
        status: "REJECTED",
        cancelReason: reason || "Run rejected during review",
      };
    }
    if (!runId) throw new Error("runId is required");
    return fetchJson<WorkflowRunView>(`${API_BASE}/api/executions/runs/${encodeURIComponent(runId)}/reject`, {
      method: "POST",
      body: JSON.stringify({ reason: reason || "Run rejected during review" }),
    });
  },

  async getRunOperations(runId: string): Promise<RunOperationsView> {
    if (!runId) throw new Error("runId is required");
    return fetchJson<RunOperationsView>(`${API_BASE}/api/executions/runs/${encodeURIComponent(runId)}/operations`);
  },

  async getRunEvidence(runId: string): Promise<RunEvidenceBundleView> {
    return fetchJson<RunEvidenceBundleView>(`${API_BASE}/api/executions/runs/${encodeURIComponent(runId)}/evidence`);
  },

  async exportRunEvidence(runId: string): Promise<RunEvidenceBundleView> {
    if (!runId) throw new Error("runId is required");
    return fetchJson<RunEvidenceBundleView>(`${API_BASE}/api/executions/runs/${encodeURIComponent(runId)}/evidence/export`);
  },

  async exportRunEvidenceFile(runId: string): Promise<RunEvidenceBundleView> {
    if (isDemoMode()) {
      const demoBundle: RunEvidenceBundleView = {
        schemaVersion: "agenthub.evidence.v1",
        bundleId: `evd-${runId}`,
        exportedAt: new Date().toISOString(),
        simulated: true,
        executionMode: "DEMO_SIMULATED",
        checksum: "demo-simulated-offline-preview-bundle",
        project: {
          id: MOCK_RUN.projectId,
          name: "AgentHub Offline Demo Project",
          ownerId: "user-demo",
          workspaceId: "ws-demo",
          gitBaselineCommit: "demo-baseline-000000",
        },
        workflowDslSnapshot: JSON.stringify({ version: "1.0", name: "Offline Demo Workflow", simulated: true }),
        run: { ...MOCK_RUN, id: runId },
        steps: MOCK_STEPS,
        events: [],
        providerRuntime: {
          runtimeTypes: ["MOCK"],
          providerTypes: ["MOCK_SIMULATOR"],
          models: ["mock-offline-v1"],
          simulated: true,
          totalPromptTokens: 0,
          totalCompletionTokens: 0,
          totalTokens: 0,
          totalEstimatedCost: 0,
          tokenUsageRecords: [],
        },
        gitDiff: {
          workspaceId: "ws-demo",
          baseCommit: "demo-baseline-000000",
          targetCommit: "demo-snapshot-111111",
          totalFilesChanged: MOCK_DIFFS.length,
          totalLinesAdded: MOCK_DIFFS.reduce((acc, d) => acc + (d.linesAdded ?? d.addedLines ?? 0), 0),
          totalLinesDeleted: MOCK_DIFFS.reduce((acc, d) => acc + (d.linesDeleted ?? d.deletedLines ?? 0), 0),
          entries: MOCK_DIFFS,
          clean: false,
        },
        artifacts: [],
        testReportSummary: {
          status: "SIMULATED",
          totalSteps: MOCK_STEPS.length,
          succeededSteps: MOCK_STEPS.filter((s) => s.status === "SUCCEEDED").length,
          failedSteps: MOCK_STEPS.filter((s) => s.status === "FAILED").length,
          skippedSteps: 0,
          cancelledSteps: 0,
          verificationSummary: `Offline Demo Mode simulated bundle (${runId})`,
          logs: ["[DEMO_SIMULATED] Exported in frontend offline demo mode"],
        },
        approvals: [MOCK_APPROVAL],
        deployments: [
          {
            id: MOCK_DEPLOYMENT.id,
            projectId: MOCK_DEPLOYMENT.projectId,
            artifactId: MOCK_DEPLOYMENT.artifactId,
            status: MOCK_DEPLOYMENT.status,
            target: MOCK_DEPLOYMENT.target,
            sandboxType: MOCK_DEPLOYMENT.sandboxType,
            templateType: MOCK_DEPLOYMENT.templateType || "STATIC_WEB_TEMPLATE",
            url: MOCK_DEPLOYMENT.url,
            port: MOCK_DEPLOYMENT.port,
            startedAt: MOCK_DEPLOYMENT.startedAt,
            finishedAt: MOCK_DEPLOYMENT.finishedAt,
          },
        ],
      };
      if (typeof window !== "undefined") {
        const blob = new Blob([JSON.stringify(demoBundle, null, 2)], { type: "application/json" });
        const url = URL.createObjectURL(blob);
        const a = document.createElement("a");
        a.href = url;
        a.download = `evidence-bundle-${runId}-demo-simulated.json`;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);
      }
      return demoBundle;
    }

    const token = getStoredToken();
    const headers: Record<string, string> = {};
    if (token) {
      headers["Authorization"] = `Bearer ${token}`;
    }
    const res = await fetch(`${API_BASE}/api/executions/runs/${encodeURIComponent(runId)}/evidence/download`, {
      credentials: "include",
      headers,
    });
    if (!res.ok) {
      return runsApi.getRunEvidence(runId);
    }
    const rawJson = await res.text();
    const bundle = JSON.parse(rawJson) as RunEvidenceBundleView;
    if (typeof window !== "undefined") {
      const blob = new Blob([rawJson], { type: "application/json" });
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = `evidence-bundle-${runId}.json`;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      URL.revokeObjectURL(url);
    }
    return bundle;
  },
};
