"use client";

import React, { useState, useEffect, useCallback } from "react";
import {
  RunOperationsView,
  StructuredDiff,
  ArtifactLineageView,
  PendingDecisionItem,
  StepLineageNode,
  ArtifactView,
  QualityGateReport,
} from "@/types";
import { runsApi } from "@/services/api/runs";
import { artifactsApi } from "@/services/api/artifacts";
import { approvalsApi } from "@/services/api/approvals";
import { StructuredDiffReviewModal } from "@/components/dashboard/StructuredDiffReviewModal";
import {
  Activity,
  AlertCircle,
  AlertTriangle,
  ArrowRight,
  BrainCircuit,
  Check,
  CheckCircle2,
  Clock,
  Compass,
  FileCode,
  GitCommit,
  GitPullRequest,
  Layers,
  Pause,
  Play,
  RefreshCw,
  RotateCcw,
  ShieldAlert,
  ShieldCheck,
  Wrench,
  X,
  XCircle,
} from "lucide-react";

interface RunOperationsPanelProps {
  runId: string;
  onRefreshParent?: () => void;
}

export function RunOperationsPanel({ runId, onRefreshParent }: RunOperationsPanelProps) {
  const [operations, setOperations] = useState<RunOperationsView | null>(null);
  const [loading, setLoading] = useState(false);
  const [actionLoading, setActionLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  // Structured Diff Modal state
  const [selectedArtifactId, setSelectedArtifactId] = useState<string | null>(null);
  const [artifactDiff, setArtifactDiff] = useState<StructuredDiff | null>(null);
  const [isDiffModalOpen, setIsDiffModalOpen] = useState(false);

  // Phase 5: Step Rework state
  const [showReworkModal, setShowReworkModal] = useState(false);
  const [reworkStepRunId, setReworkStepRunId] = useState<string | null>(null);
  const [reworkStepNodeId, setReworkStepNodeId] = useState<string>("");
  const [reworkFeedback, setReworkFeedback] = useState<string>("");
  const [isReworking, setIsReworking] = useState(false);

  // Phase 5: Quality Gate Detail Modal state
  const [selectedGateReport, setSelectedGateReport] = useState<QualityGateReport | null>(null);
  const [showGateDetailModal, setShowGateDetailModal] = useState(false);

  const fetchOperations = useCallback(async () => {
    if (!runId) return;
    setLoading(true);
    setErrorMessage(null);
    try {
      const data = await runsApi.getRunOperations(runId);
      setOperations(data);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : "获取运行运维视图失败";
      setErrorMessage(msg);
    } finally {
      setLoading(false);
    }
  }, [runId]);

  useEffect(() => {
    fetchOperations();
  }, [fetchOperations]);

  const handlePause = async () => {
    if (!runId) return;
    setActionLoading(true);
    try {
      await runsApi.pauseRun(runId);
      await fetchOperations();
      onRefreshParent?.();
    } catch (err: unknown) {
      alert("暂停运行失败: " + (err instanceof Error ? err.message : String(err)));
    } finally {
      setActionLoading(false);
    }
  };

  const handleResume = async () => {
    if (!runId) return;
    setActionLoading(true);
    try {
      await runsApi.resumeRun(runId);
      await fetchOperations();
      onRefreshParent?.();
    } catch (err: unknown) {
      alert("恢复运行失败: " + (err instanceof Error ? err.message : String(err)));
    } finally {
      setActionLoading(false);
    }
  };

  const handleApproveGate = async (approvalId: string) => {
    setActionLoading(true);
    try {
      await approvalsApi.approve(approvalId, "current-operator", "通过运维面板批准准入");
      await fetchOperations();
      onRefreshParent?.();
    } catch (err: unknown) {
      alert("批准审批门禁失败: " + (err instanceof Error ? err.message : String(err)));
    } finally {
      setActionLoading(false);
    }
  };

  const handleRejectGate = async (approvalId: string, reason: string) => {
    setActionLoading(true);
    try {
      await approvalsApi.reject(approvalId, "current-operator", reason || "通过运维面板驳回");
      await fetchOperations();
      onRefreshParent?.();
    } catch (err: unknown) {
      alert("驳回审批门禁失败: " + (err instanceof Error ? err.message : String(err)));
    } finally {
      setActionLoading(false);
    }
  };

  const handleCancelRun = async () => {
    if (!runId) return;
    const reason = prompt("请输入取消执行的原因:", "用户通过运维控制台取消");
    if (!reason) return;
    setActionLoading(true);
    try {
      await runsApi.cancelRun(runId, reason);
      await fetchOperations();
      onRefreshParent?.();
    } catch (err: unknown) {
      alert("取消运行失败: " + (err instanceof Error ? err.message : String(err)));
    } finally {
      setActionLoading(false);
    }
  };

  const handleRejectRun = async () => {
    if (!runId) return;
    const reason = prompt("请输入全盘驳回此 Run 的原因 (工作区将安全重置至基线快照):", "交付物未达到质量门禁标准");
    if (!reason) return;
    setActionLoading(true);
    try {
      await runsApi.rejectRun(runId, reason);
      await fetchOperations();
      onRefreshParent?.();
    } catch (err: unknown) {
      alert("全盘驳回失败: " + (err instanceof Error ? err.message : String(err)));
    } finally {
      setActionLoading(false);
    }
  };

  const handleOpenRework = (stepRunId: string, nodeId: string) => {
    setReworkStepRunId(stepRunId);
    setReworkStepNodeId(nodeId);
    setReworkFeedback("请补充针对核心业务逻辑的边界防御测试用例与异常防御");
    setShowReworkModal(true);
  };

  const handleConfirmRework = async () => {
    if (!runId || !reworkStepRunId) return;
    setIsReworking(true);
    try {
      await runsApi.reworkStep(runId, reworkStepRunId, reworkFeedback.trim());
      setShowReworkModal(false);
      setReworkStepRunId(null);
      await fetchOperations();
      onRefreshParent?.();
    } catch (err: unknown) {
      alert("请求重做失败: " + (err instanceof Error ? err.message : String(err)));
    } finally {
      setIsReworking(false);
    }
  };

  const handleRetryStep = async (stepRunId: string) => {
    if (!runId || !stepRunId) return;
    setActionLoading(true);
    try {
      await runsApi.retryStep(runId, stepRunId);
      await fetchOperations();
      onRefreshParent?.();
    } catch (err: unknown) {
      alert("重试步骤失败: " + (err instanceof Error ? err.message : String(err)));
    } finally {
      setActionLoading(false);
    }
  };

  const handleInspectDiff = async (artifactId: string) => {
    setSelectedArtifactId(artifactId);
    try {
      const diff = await artifactsApi.getArtifactDiff(artifactId);
      setArtifactDiff(diff);
      setIsDiffModalOpen(true);
    } catch (err: unknown) {
      alert("加载产物 Diff 失败: " + (err instanceof Error ? err.message : String(err)));
    }
  };

  const handleAcceptArtifact = async (artifactId: string) => {
    try {
      await artifactsApi.acceptArtifact(artifactId, "current-operator", "通过运维面板批准");
      await fetchOperations();
      onRefreshParent?.();
    } catch (err: unknown) {
      alert("批准产物失败: " + (err instanceof Error ? err.message : String(err)));
    }
  };

  const handleRejectArtifact = async (artifactId: string, reason: string) => {
    try {
      await artifactsApi.rejectArtifact(artifactId, "current-operator", reason);
      await fetchOperations();
      onRefreshParent?.();
    } catch (err: unknown) {
      alert("拒绝产物失败: " + (err instanceof Error ? err.message : String(err)));
    }
  };

  const handleRevertArtifact = async (artifactId: string) => {
    try {
      await artifactsApi.revertArtifact(artifactId);
      await fetchOperations();
      onRefreshParent?.();
    } catch (err: unknown) {
      alert("回滚产物失败: " + (err instanceof Error ? err.message : String(err)));
    }
  };

  if (!runId) {
    return (
      <div className="bg-slate-900 border border-slate-800 rounded-xl p-8 text-center text-slate-400">
        <Compass className="w-10 h-10 mx-auto text-slate-600 mb-3" />
        <p className="text-sm">未选择任何 Workflow Run，请先在左侧选择或启动执行</p>
      </div>
    );
  }

  const run = operations?.workflowRun;
  const status = operations?.unifiedStatus || run?.status || "UNKNOWN";
  const nextAction = operations?.nextAction || "N/A";
  const requiresIntervention = operations?.requiresHumanIntervention || false;
  const pendingDecisions = operations?.pendingDecisions || [];
  const memory = operations?.memorySummary;
  const lineage = operations?.artifactLineage;
  const toolEffects = operations?.toolSideEffects || [];

  return (
    <div className="space-y-4">
      {/* 1. Header / Unified State Graph Banner */}
      <div className="bg-slate-900/90 border border-slate-800 rounded-xl p-4 shadow-sm backdrop-blur">
        <div className="flex flex-wrap items-center justify-between gap-4">
          <div className="flex items-center space-x-3">
            <div className="p-2 rounded-lg bg-indigo-950/60 border border-indigo-800/80 text-indigo-400">
              <Activity className="w-5 h-5" />
            </div>
            <div>
              <div className="flex items-center space-x-2">
                <h3 className="text-sm font-semibold text-slate-100">
                  Run Operations & 状态图中枢
                </h3>
                <span className="text-xs font-mono text-slate-400">({runId})</span>
              </div>
              <p className="text-xs text-slate-400 mt-0.5">
                Phase 4 单一状态图校验 · At-Least-Once 重试策略 · 产物血缘与无损回滚
              </p>
            </div>
          </div>

          {/* Action buttons */}
          <div className="flex items-center space-x-2">
            <button
              onClick={fetchOperations}
              disabled={loading}
              className="p-1.5 rounded-lg border border-slate-700 bg-slate-800 text-slate-300 hover:text-white hover:bg-slate-700 transition"
              title="刷新运维指标"
            >
              <RefreshCw className={`w-4 h-4 ${loading ? "animate-spin" : ""}`} />
            </button>

            {status === "RUNNING" && (
              <button
                onClick={handlePause}
                disabled={actionLoading}
                className="flex items-center space-x-1.5 px-3 py-1.5 rounded-lg text-xs font-medium border border-amber-600/60 bg-amber-950/40 text-amber-300 hover:bg-amber-900/60 transition"
              >
                <Pause className="w-3.5 h-3.5" />
                <span>暂停 Run</span>
              </button>
            )}

            {status === "PAUSED" && (
              <button
                onClick={handleResume}
                disabled={actionLoading}
                className="flex items-center space-x-1.5 px-3 py-1.5 rounded-lg text-xs font-medium border border-emerald-600/60 bg-emerald-950/40 text-emerald-300 hover:bg-emerald-900/60 transition"
              >
                <Play className="w-3.5 h-3.5" />
                <span>恢复执行</span>
              </button>
            )}

            {(status === "RUNNING" || status === "PAUSED" || status === "WAITING_APPROVAL") && (
              <button
                onClick={handleCancelRun}
                disabled={actionLoading}
                className="flex items-center space-x-1.5 px-3 py-1.5 rounded-lg text-xs font-medium border border-rose-600/60 bg-rose-950/40 text-rose-300 hover:bg-rose-900/60 transition"
              >
                <XCircle className="w-3.5 h-3.5" />
                <span>取消 Run</span>
              </button>
            )}

            {status !== "REJECTED" && (
              <button
                onClick={handleRejectRun}
                disabled={actionLoading}
                className="flex items-center space-x-1.5 px-3 py-1.5 rounded-lg text-xs font-medium border border-rose-800/80 bg-rose-950/70 text-rose-300 hover:bg-rose-900 transition"
                title="全盘驳回此 Run，工作区强一致安全重置至基线快照"
              >
                <RotateCcw className="w-3.5 h-3.5" />
                <span>全盘驳回</span>
              </button>
            )}
          </div>
        </div>

        {/* State Graph Status Pill & Next Action */}
        <div className="mt-4 grid grid-cols-1 md:grid-cols-3 gap-3 pt-3 border-t border-slate-800/80">
          <div className="flex items-center space-x-2.5">
            <span className="text-xs text-slate-400">统一状态:</span>
            <span
              className={`px-2.5 py-0.5 rounded-full text-xs font-mono font-medium border ${
                status === "SUCCEEDED"
                  ? "bg-emerald-950/60 text-emerald-400 border-emerald-700/60"
                  : status === "FAILED"
                  ? "bg-rose-950/60 text-rose-400 border-rose-700/60"
                  : status === "WAITING_APPROVAL"
                  ? "bg-amber-950/60 text-amber-400 border-amber-700/60"
                  : status === "PAUSED"
                  ? "bg-purple-950/60 text-purple-400 border-purple-700/60"
                  : "bg-sky-950/60 text-sky-400 border-sky-700/60"
              }`}
            >
              {status}
            </span>
          </div>

          <div className="flex items-center space-x-2.5">
            <span className="text-xs text-slate-400">下一步调度策略:</span>
            <span className="text-xs font-mono font-semibold text-slate-200 truncate bg-slate-800/70 px-2 py-0.5 rounded border border-slate-700/50">
              {nextAction}
            </span>
          </div>

          <div className="flex items-center space-x-2.5">
            <span className="text-xs text-slate-400">人工介入门禁:</span>
            {requiresIntervention ? (
              <span className="flex items-center space-x-1 text-xs text-amber-400 font-medium">
                <AlertCircle className="w-3.5 h-3.5" />
                <span>需人工确认</span>
              </span>
            ) : (
              <span className="flex items-center space-x-1 text-xs text-emerald-400 font-medium">
                <ShieldCheck className="w-3.5 h-3.5" />
                <span>全自动流转</span>
              </span>
            )}
          </div>
        </div>
      </div>

      {/* Error notification if any */}
      {errorMessage && (
        <div className="p-3 bg-rose-950/40 border border-rose-800/80 rounded-xl text-xs text-rose-300 flex items-center space-x-2">
          <AlertCircle className="w-4 h-4 shrink-0 text-rose-400" />
          <span>{errorMessage}</span>
        </div>
      )}

      {/* 2. Pending Decisions Alert (if waiting for human approval/decision) */}
      {pendingDecisions.length > 0 && (
        <div className="bg-amber-950/20 border border-amber-800/60 rounded-xl p-4">
          <div className="flex items-center space-x-2 text-amber-400 text-sm font-semibold mb-2">
            <ShieldAlert className="w-4 h-4" />
            <span>待处理的人工决策与审批门禁 ({pendingDecisions.length})</span>
          </div>
          <div className="space-y-2">
            {pendingDecisions.map((decision, idx) => (
              <div
                key={idx}
                className="bg-slate-900/80 border border-slate-800 rounded-lg p-3 flex flex-wrap items-center justify-between gap-3"
              >
                <div>
                  <div className="flex items-center space-x-2">
                    <span className="text-xs font-mono text-indigo-400 bg-indigo-950/80 px-1.5 py-0.5 rounded border border-indigo-900">
                      {decision.decisionType}
                    </span>
                    <span className="text-xs font-medium text-slate-200">{decision.nodeName}</span>
                    <span className="text-[11px] text-slate-400 font-mono">
                      (角色: {decision.assignedRole})
                    </span>
                  </div>
                  <p className="text-xs text-slate-300 mt-1">{decision.description}</p>
                </div>

                {decision.decisionType === "APPROVAL_GATE" && (
                  <div className="flex items-center space-x-2">
                    <button
                      disabled={actionLoading}
                      onClick={async () => {
                        await handleApproveGate(decision.referenceId);
                      }}
                      className="px-3 py-1 rounded bg-emerald-600 hover:bg-emerald-500 text-white text-xs font-medium transition disabled:opacity-50"
                    >
                      批准准入
                    </button>
                    <button
                      disabled={actionLoading}
                      onClick={async () => {
                        const reason = prompt("请输入拒绝理由:");
                        if (reason) await handleRejectGate(decision.referenceId, reason);
                      }}
                      className="px-3 py-1 rounded bg-rose-600 hover:bg-rose-500 text-white text-xs font-medium transition disabled:opacity-50"
                    >
                      驳回拦截
                    </button>
                  </div>
                )}

                {decision.decisionType === "RUN_PAUSED" && (
                  <div className="flex items-center space-x-2">
                    <button
                      disabled={actionLoading}
                      onClick={handleResume}
                      className="px-3 py-1 rounded bg-emerald-600 hover:bg-emerald-500 text-white text-xs font-medium transition disabled:opacity-50"
                    >
                      恢复执行
                    </button>
                    <button
                      disabled={actionLoading}
                      onClick={handleCancelRun}
                      className="px-3 py-1 rounded bg-rose-600 hover:bg-rose-500 text-white text-xs font-medium transition disabled:opacity-50"
                    >
                      取消运行
                    </button>
                  </div>
                )}
              </div>
            ))}
          </div>
        </div>
      )}

      {/* 3. Bento Grid: Hierarchical Memory & Lease Status */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {/* Hierarchical 3-tier Memory Card */}
        <div className="bg-slate-900 border border-slate-800 rounded-xl p-4">
          <div className="flex items-center justify-between mb-3">
            <div className="flex items-center space-x-2">
              <BrainCircuit className="w-4 h-4 text-sky-400" />
              <h4 className="text-xs font-semibold text-slate-200">
                三层拓扑分层上下文记忆 (Tier-3 Memory)
              </h4>
            </div>
            <span className="text-[11px] font-mono text-slate-400">
              {memory?.totalTiers || 3} Tiers Active
            </span>
          </div>

          <div className="grid grid-cols-2 gap-2 text-xs">
            <div className="bg-slate-950/60 p-2.5 rounded-lg border border-slate-800/80">
              <span className="text-slate-400 block text-[11px]">总步骤 / 压缩步骤</span>
              <span className="text-slate-200 font-mono font-semibold">
                {memory ? `${memory.totalSteps} 步 / ${memory.compressedSteps} 压缩` : "未初始化"}
              </span>
            </div>

            <div className="bg-slate-950/60 p-2.5 rounded-lg border border-slate-800/80">
              <span className="text-slate-400 block text-[11px]">预估 Token / 预算状态</span>
              <span
                className={`font-mono font-semibold ${
                  memory?.budgetExceeded ? "text-rose-400" : "text-emerald-400"
                }`}
              >
                {memory ? `${memory.estimatedTokens} tok` : "0 tok"}
                {memory?.budgetExceeded ? " (超额)" : " (正常)"}
              </span>
            </div>
          </div>

          <div className="mt-3 text-xs bg-slate-950/40 p-2.5 rounded-lg border border-slate-800/60 flex items-center justify-between">
            <span className="text-slate-400">AOCI 认知状态:</span>
            <span className="font-mono text-slate-300">
              {memory?.aociCognitionStatus || "PASSIVE_CONTEXT_MANAGED"}
            </span>
          </div>
        </div>

        {/* Distributed Lease & Error Classification */}
        <div className="bg-slate-900 border border-slate-800 rounded-xl p-4">
          <div className="flex items-center justify-between mb-3">
            <div className="flex items-center space-x-2">
              <Clock className="w-4 h-4 text-purple-400" />
              <h4 className="text-xs font-semibold text-slate-200">
                At-Least-Once 租约与重试防御
              </h4>
            </div>
            <span className="text-[11px] font-mono text-slate-400">
              重试计数: {operations?.totalRetries || 0}
            </span>
          </div>

          <div className="grid grid-cols-2 gap-2 text-xs">
            <div className="bg-slate-950/60 p-2.5 rounded-lg border border-slate-800/80">
              <span className="text-slate-400 block text-[11px]">锁租约持有者</span>
              <span className="text-slate-200 font-mono text-[11px] truncate block">
                {operations?.leaseStatus?.leaseOwner || "无持有者"}
              </span>
            </div>

            <div className="bg-slate-950/60 p-2.5 rounded-lg border border-slate-800/80">
              <span className="text-slate-400 block text-[11px]">错误分类归因</span>
              <span
                className={`font-mono font-medium text-[11px] ${
                  operations?.failureCategory === "RETRYABLE_TRANSIENT"
                    ? "text-sky-400"
                    : operations?.failureCategory === "APPROVAL_REQUIRED"
                    ? "text-amber-400"
                    : operations?.failureCategory === "FATAL_NON_RETRYABLE"
                    ? "text-rose-400"
                    : "text-slate-400"
                }`}
              >
                {operations?.failureCategory || "NONE_DETECTED"}
              </span>
            </div>
          </div>

          {operations?.failureReason && (
            <div className="mt-3 text-xs bg-rose-950/30 p-2.5 rounded-lg border border-rose-900/50 text-rose-300">
              <span className="font-semibold block text-[11px] text-rose-400">异常详细诊断:</span>
              <p className="font-mono text-[11px] truncate mt-0.5">{operations.failureReason}</p>
            </div>
          )}
        </div>
      </div>

      {/* 4. Artifact Lineage Graph & JGit Structured Diff Review */}
      <div className="bg-slate-900 border border-slate-800 rounded-xl p-4">
        <div className="flex items-center justify-between mb-3">
          <div className="flex items-center space-x-2">
            <GitPullRequest className="w-4 h-4 text-emerald-400" />
            <h4 className="text-xs font-semibold text-slate-200">
              产物全生命周期血缘图谱 (Artifact Lineage)
            </h4>
          </div>
          <div className="flex items-center space-x-3 text-xs text-slate-400 font-mono">
            <span>基线: {lineage?.baselineCommitHash?.substring(0, 8) || "N/A"}</span>
            <span>→</span>
            <span>当前: {lineage?.currentCommitHash?.substring(0, 8) || "N/A"}</span>
            {lineage?.isReverted && (
              <span className="px-1.5 py-0.5 rounded bg-rose-950 text-rose-400 border border-rose-800 text-[10px]">
                已回滚
              </span>
            )}
          </div>
        </div>

        {lineage && lineage.stepNodes && lineage.stepNodes.length > 0 ? (
          <div className="space-y-2">
            {lineage.stepNodes.map((node, idx) => (
              <div
                key={idx}
                className="bg-slate-950/60 border border-slate-800/80 rounded-lg p-3 flex flex-wrap items-center justify-between gap-3 text-xs"
              >
                <div className="flex items-center space-x-3">
                  <div className="font-mono text-slate-400 text-xs">{idx + 1}.</div>
                  <div>
                    <div className="flex items-center space-x-2">
                      <span className="font-medium text-slate-200">{node.stepId}</span>
                      <span className="text-[10px] font-mono px-1.5 py-0.2 rounded bg-slate-800 text-slate-300">
                        {node.status}
                      </span>
                      {node.agentRole && (
                        <span className="text-[10px] font-mono px-1.5 py-0.2 rounded bg-indigo-950 text-indigo-300 border border-indigo-800">
                          [{node.agentRole}]
                        </span>
                      )}
                      {node.commitHash && (
                        <span className="flex items-center space-x-1 font-mono text-[11px] text-indigo-400">
                          <GitCommit className="w-3 h-3" />
                          <span>{node.commitHash.substring(0, 7)}</span>
                        </span>
                      )}
                      {/* Phase 5 Quality Gate Badge */}
                      {node.qualityGateReport && (
                        <button
                          onClick={() => {
                            setSelectedGateReport(node.qualityGateReport!);
                            setShowGateDetailModal(true);
                          }}
                          className={`flex items-center space-x-1 text-[10px] font-mono px-1.5 py-0.2 rounded border transition ${
                            node.qualityGateReport.overallStatus === "PASSED"
                              ? "bg-emerald-950/70 text-emerald-400 border-emerald-800"
                              : "bg-rose-950/70 text-rose-400 border-rose-800"
                          }`}
                          title="查看质量门禁明细"
                        >
                          <ShieldCheck className="w-2.5 h-2.5" />
                          <span>门禁: {node.qualityGateReport.overallStatus} ({node.qualityGateReport.compositeScore}分)</span>
                        </button>
                      )}
                      {node.reviewStatus === "GATE_FAILED" && (
                        <span className="text-[10px] font-mono px-1.5 py-0.2 rounded bg-rose-950 text-rose-400 border border-rose-800 font-semibold">
                          GATE_FAILED (门禁阻断)
                        </span>
                      )}
                    </div>
                    {node.changedFiles && node.changedFiles.length > 0 && (
                      <p className="text-[11px] text-slate-400 mt-1">
                        修改文件 ({node.changedFiles.length}): {node.changedFiles.join(", ")}
                      </p>
                    )}
                    {node.reworkFeedback && (
                      <div className="mt-1 text-[11px] text-amber-300/90 font-mono bg-amber-950/40 border border-amber-800/40 px-2 py-0.5 rounded">
                        重做反馈: {node.reworkFeedback}
                      </div>
                    )}
                  </div>
                </div>

                <div className="flex items-center space-x-2">
                  {/* Phase 5 Step Rework Button */}
                  <button
                    onClick={() => handleOpenRework(node.stepRunId || node.stepId, node.stepId)}
                    disabled={actionLoading}
                    className="flex items-center space-x-1 px-2.5 py-1 rounded bg-amber-950/70 hover:bg-amber-900 border border-amber-700/60 text-amber-300 text-xs font-mono transition"
                    title="对该步骤请求重做并提供指导意见"
                  >
                    <RotateCcw className="w-3 h-3" />
                    <span>请求重做</span>
                  </button>

                  {node.artifactId ? (
                    <button
                      onClick={() => handleInspectDiff(node.artifactId!)}
                      className="flex items-center space-x-1 px-2.5 py-1 rounded bg-indigo-950/80 hover:bg-indigo-900 border border-indigo-700/60 text-indigo-300 text-xs font-mono transition"
                    >
                      <FileCode className="w-3.5 h-3.5" />
                      <span>查看 JGit Diff</span>
                    </button>
                  ) : (
                    <span className="text-slate-500 text-[11px]">无关联产物快照</span>
                  )}
                </div>
              </div>
            ))}
          </div>
        ) : (
          <div className="text-center py-6 text-slate-500 text-xs">
            暂无产物血缘节点，请执行产生文件修改的 Agent 步骤
          </div>
        )}
      </div>

      {/* 5. Tool Side-Effects Allowlist Auditing */}
      {toolEffects.length > 0 && (
        <div className="bg-slate-900 border border-slate-800 rounded-xl p-4">
          <div className="flex items-center space-x-2 mb-3">
            <Wrench className="w-4 h-4 text-amber-400" />
            <h4 className="text-xs font-semibold text-slate-200">
              受控工具调用副作用审计与收据 (Tool Side Effects)
            </h4>
          </div>

          <div className="space-y-2">
            {toolEffects.map((effect, idx) => (
              <div
                key={idx}
                className="bg-slate-950/60 border border-slate-800/80 rounded-lg p-2.5 text-xs font-mono"
              >
                <div className="flex items-center justify-between text-slate-300">
                  <span className="font-semibold text-amber-300">Tool: {effect.toolName}</span>
                  <span className="text-slate-400">{effect.status || "COMPLETED"}</span>
                </div>
                {effect.command && (
                  <div className="mt-1 text-slate-400 text-[11px] truncate">
                    $ {effect.command}
                  </div>
                )}
                {effect.targetFiles && effect.targetFiles.length > 0 && (
                  <div className="mt-1 text-slate-400 text-[11px]">
                    Files: {effect.targetFiles.join(", ")}
                  </div>
                )}
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Structured Diff Review Modal */}
      {isDiffModalOpen && (
        <StructuredDiffReviewModal
          isOpen={isDiffModalOpen}
          onClose={() => setIsDiffModalOpen(false)}
          diff={artifactDiff}
          artifactId={selectedArtifactId || undefined}
          onAccept={handleAcceptArtifact}
          onReject={handleRejectArtifact}
          onRevert={handleRevertArtifact}
        />
      )}

      {/* Phase 5: Step Rework Modal */}
      {showReworkModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 backdrop-blur-sm p-4">
          <div className="max-w-lg w-full rounded-2xl border border-amber-600/50 bg-slate-950 p-5 shadow-2xl flex flex-col text-slate-100">
            <div className="flex items-center justify-between pb-3 border-b border-slate-800">
              <div className="flex items-center space-x-2">
                <RotateCcw className="w-4 h-4 text-amber-400" />
                <span className="font-semibold text-sm">请求步骤重做 (Step Rework Feedback)</span>
              </div>
              <button
                onClick={() => setShowReworkModal(false)}
                className="text-slate-400 hover:text-white p-1 rounded hover:bg-slate-800"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            <div className="py-3 text-xs space-y-3">
              <div className="p-2.5 rounded bg-slate-900 border border-slate-800">
                <span className="text-slate-400 block text-[11px]">目标步骤:</span>
                <span className="font-mono text-amber-300 font-semibold">{reworkStepNodeId}</span>
              </div>

              <div>
                <label className="text-slate-300 block mb-1 font-medium">
                  审查员指导反馈 (将压入步骤记忆并重置状态为 PENDING):
                </label>
                <textarea
                  rows={4}
                  value={reworkFeedback}
                  onChange={(e) => setReworkFeedback(e.target.value)}
                  placeholder="例如：请补充针对核心业务逻辑的边界防御测试用例，并处理潜在的空指针与并发竞态..."
                  className="w-full p-2.5 rounded-lg bg-slate-900 border border-slate-700 text-xs text-slate-200 placeholder:text-slate-600 focus:outline-none focus:border-amber-500 font-sans"
                />
              </div>
            </div>

            <div className="pt-3 border-t border-slate-800 flex justify-end space-x-2">
              <button
                onClick={() => setShowReworkModal(false)}
                className="px-3 py-1.5 rounded-lg border border-slate-700 text-xs text-slate-400 hover:text-white"
              >
                取消
              </button>
              <button
                onClick={handleConfirmRework}
                disabled={isReworking || !reworkFeedback.trim()}
                className="px-4 py-1.5 rounded-lg bg-amber-600 hover:bg-amber-500 text-white font-semibold text-xs flex items-center space-x-1.5 transition disabled:opacity-50"
              >
                {isReworking ? (
                  <Clock className="w-3.5 h-3.5 animate-spin" />
                ) : (
                  <RotateCcw className="w-3.5 h-3.5" />
                )}
                <span>确认下发重做</span>
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Phase 5: Quality Gate Detail Inspection Modal */}
      {showGateDetailModal && selectedGateReport && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/75 backdrop-blur-sm p-4">
          <div className="max-w-2xl w-full rounded-2xl border border-slate-800 bg-slate-950 p-5 shadow-2xl flex flex-col max-h-[85vh] overflow-hidden text-slate-100">
            <div className="flex items-center justify-between pb-3 border-b border-slate-800">
              <div className="flex items-center space-x-2.5">
                <ShieldCheck className="w-5 h-5 text-indigo-400" />
                <div>
                  <h3 className="text-sm font-semibold text-slate-100">
                    Phase 5 质量门禁多维防御细则
                  </h3>
                  <p className="text-[10px] font-mono text-slate-400 mt-0.5">
                    产物 ID: {selectedGateReport.artifactId} | 指纹: {selectedGateReport.sha256Fingerprint.substring(0, 16)}...
                  </p>
                </div>
              </div>
              <button
                onClick={() => setShowGateDetailModal(false)}
                className="text-slate-400 hover:text-white p-1 rounded hover:bg-slate-800"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            <div className="my-3 p-3 rounded-xl bg-slate-900 border border-slate-800 flex items-center justify-between">
              <div>
                <span className="text-xs text-slate-400 block">门禁加权总分</span>
                <span className="text-2xl font-bold font-mono text-indigo-400">
                  {selectedGateReport.compositeScore} / 100 分
                </span>
              </div>
              <div className="flex items-center space-x-3 text-xs font-mono">
                <span className="text-emerald-400">{selectedGateReport.passedRules} 通过</span>
                <span className="text-rose-400">{selectedGateReport.failedRules} 阻断</span>
              </div>
            </div>

            <div className="flex-1 overflow-y-auto space-y-2 pr-1">
              {selectedGateReport.checkItems.map((item, idx) => (
                <div
                  key={idx}
                  className={`p-3 rounded-xl border text-xs ${
                    item.status === "PASSED"
                      ? "bg-slate-900/40 border-slate-800 text-slate-300"
                      : "bg-rose-950/30 border-rose-700/60 text-rose-200"
                  }`}
                >
                  <div className="flex items-center justify-between mb-1">
                    <span className="font-semibold text-slate-200">{item.ruleName}</span>
                    <span
                      className={`text-[10px] font-mono px-2 py-0.5 rounded font-semibold ${
                        item.status === "PASSED"
                          ? "bg-emerald-950 text-emerald-400 border border-emerald-800"
                          : "bg-rose-950 text-rose-400 border border-rose-800"
                      }`}
                    >
                      {item.status}
                    </span>
                  </div>
                  <p className="text-[11px] text-slate-400 mt-0.5">{item.message}</p>
                </div>
              ))}
            </div>

            <div className="mt-3 pt-3 border-t border-slate-800 flex justify-end">
              <button
                onClick={() => setShowGateDetailModal(false)}
                className="px-3 py-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs"
              >
                关闭
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
