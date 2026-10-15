// ============================================================================
// AgentHub Artifacts & JGit Audit API Module (Bounded Context: audit)
// ============================================================================

import { ArtifactLineageView, ArtifactView, PullRequestDraftView, QualityGateReport, StructuredDiff } from "@/types";
import { isDemoMode } from "./auth";
import { API_BASE, fetchJson } from "./errors";

export const MOCK_QUALITY_GATE_REPORT: QualityGateReport = {
  reportId: "qg-rep-demo-pass",
  artifactId: "art-jgit-diff-snapshot",
  runId: "run-exec-94218a",
  overallStatus: "PASSED",
  compositeScore: 96,
  totalRules: 5,
  passedRules: 5,
  failedRules: 0,
  warningRules: 0,
  sha256Fingerprint: "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
  evaluatedAt: new Date().toISOString(),
  checkItems: [
    {
      ruleType: "BUILD_VERIFICATION",
      ruleName: "后端 Maven / 前端 Next 构建完整性验证",
      status: "PASSED",
      weight: 25,
      message: "Maven 构建通过，字节码生成无异常",
    },
    {
      ruleType: "UNIT_TEST_REGRESSION",
      ruleName: "单元测试与全量回归套件绿灯门禁",
      status: "PASSED",
      weight: 30,
      message: "回归测试全部通过 (8/8 green)",
    },
    {
      ruleType: "STATIC_ANALYSIS_GUARD",
      ruleName: "分层架构与静态规范守卫",
      status: "PASSED",
      weight: 15,
      message: "分层依赖合规，无循环依赖与违规调用",
    },
    {
      ruleType: "SECRET_LEAK_SCAN",
      ruleName: "硬编码密钥与敏感凭证扫描",
      status: "PASSED",
      weight: 20,
      message: "未检测到硬编码 API Key、私钥或密码",
    },
    {
      ruleType: "DIFF_BUDGET_GUARD",
      ruleName: "单次变更规模防御 (Diff Budget)",
      status: "PASSED",
      weight: 10,
      message: "变更规模在安全预算阈值内 (新增 < 2000 行)",
    },
  ],
};

export const MOCK_PR_DRAFT: PullRequestDraftView = {
  draftId: "pr-draft-demo-1",
  artifactId: "art-jgit-diff-snapshot",
  runId: "run-exec-94218a",
  targetBranch: "main",
  title: "feat(auth): 增加基于租约的企业级统一认证与凭据脱敏",
  summary: "本变更通过受控工作区实现了企业级统一认证与凭据脱敏逻辑，已完成回归测试套件且通过质量门禁评测。",
  riskAssessment: "LOW_RISK: 无破坏性 Schema 修改，接口向后兼容，已通过 5 大维度质量门禁。",
  qualityGateStatus: "PASSED",
  qualityGateScore: 96,
  changedFiles: [
    "src/main/java/com/agenthub/identity/service/AuthService.java",
    "src/test/java/com/agenthub/identity/AuthServiceTest.java",
  ],
  totalLinesAdded: 45,
  totalLinesDeleted: 8,
  generatedAt: new Date().toISOString(),
};

export const artifactsApi = {
  async getArtifact(artifactId: string): Promise<ArtifactView> {
    return fetchJson<ArtifactView>(`${API_BASE}/api/audit/artifacts/${encodeURIComponent(artifactId)}`);
  },

  async listArtifactsByRun(runId: string): Promise<ArtifactView[]> {
    if (!runId) return [];
    if (isDemoMode()) {
      return [
        {
          id: "art-jgit-diff-snapshot",
          runId,
          stepId: "step-4-qa",
          type: "CODE_DIFF",
          commitHash: "demo-snapshot-111111",
          reviewStatus: "PENDING",
        },
      ];
    }
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

  async getQualityGateReport(artifactId: string): Promise<QualityGateReport> {
    if (isDemoMode()) {
      return { ...MOCK_QUALITY_GATE_REPORT, artifactId };
    }
    if (!artifactId) throw new Error("artifactId is required");
    return fetchJson<QualityGateReport>(`${API_BASE}/api/audit/artifacts/${encodeURIComponent(artifactId)}/quality-gate`);
  },

  async evaluateQualityGate(artifactId: string): Promise<QualityGateReport> {
    if (isDemoMode()) {
      return { ...MOCK_QUALITY_GATE_REPORT, artifactId };
    }
    if (!artifactId) throw new Error("artifactId is required");
    return fetchJson<QualityGateReport>(`${API_BASE}/api/audit/artifacts/${encodeURIComponent(artifactId)}/quality-gate/evaluate`, {
      method: "POST",
    });
  },

  async getPullRequestDraft(artifactId: string): Promise<PullRequestDraftView> {
    if (isDemoMode()) {
      return { ...MOCK_PR_DRAFT, artifactId };
    }
    if (!artifactId) throw new Error("artifactId is required");
    return fetchJson<PullRequestDraftView>(`${API_BASE}/api/audit/artifacts/${encodeURIComponent(artifactId)}/pr-draft`);
  },

  async revertSingleFile(artifactId: string, filePath: string): Promise<ArtifactView> {
    if (!artifactId || !filePath) throw new Error("artifactId and filePath are required");
    return fetchJson<ArtifactView>(`${API_BASE}/api/audit/artifacts/${encodeURIComponent(artifactId)}/revert-file?filePath=${encodeURIComponent(filePath)}`, {
      method: "POST",
      body: JSON.stringify({ filePath }),
    });
  },
};
