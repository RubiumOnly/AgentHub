"use client";

import React, { useState, useEffect, useCallback } from "react";
import { BentoCard } from "@/components/common/BentoCard";
import {
  FileDiffEntry,
  StructuredDiff,
  QualityGateReport,
  QualityGateCheckItem,
  PullRequestDraftView,
} from "@/types";
import { apiClient } from "@/services/api";
import {
  GitCompare,
  RotateCcw,
  FileCode,
  FilePlus,
  FileEdit,
  Trash2,
  ShieldCheck,
  ShieldAlert,
  AlertOctagon,
  Check,
  Clock,
  Layers,
  GitPullRequest,
  ExternalLink,
  Copy,
  Download,
  AlertTriangle,
  X,
} from "lucide-react";

interface DiffArtifactReviewerProps {
  diffs?: FileDiffEntry[];
  structuredDiff?: StructuredDiff | null;
  workspaceId?: string;
  runId?: string;
  artifactId?: string;
  onRefresh?: () => void;
  className?: string;
}

export function DiffArtifactReviewer({
  diffs = [],
  structuredDiff,
  workspaceId,
  runId,
  artifactId,
  onRefresh,
  className = "",
}: DiffArtifactReviewerProps) {
  const [selectedEntryIndex, setSelectedEntryIndex] = useState(0);
  const [isReverting, setIsReverting] = useState(false);
  const [showConfirmModal, setShowConfirmModal] = useState(false);
  const [revertSuccess, setRevertSuccess] = useState(false);

  // Phase 5: Quality Gate Report state
  const [qualityGateReport, setQualityGateReport] = useState<QualityGateReport | null>(null);
  const [isLoadingGate, setIsLoadingGate] = useState(false);
  const [showGateDrawer, setShowGateDrawer] = useState(false);

  // Phase 5: Single-file revert state
  const [isSingleFileReverting, setIsSingleFileReverting] = useState(false);
  const [showSingleFileConfirm, setShowSingleFileConfirm] = useState(false);
  const [singleFileSuccess, setSingleFileSuccess] = useState<string | null>(null);

  // Phase 5: PR Draft state
  const [showPrDraftModal, setShowPrDraftModal] = useState(false);
  const [prDraft, setPrDraft] = useState<PullRequestDraftView | null>(null);
  const [isLoadingPrDraft, setIsLoadingPrDraft] = useState(false);
  const [prCopied, setPrCopied] = useState(false);

  // Resolved artifact ID
  const [effectiveArtifactId, setEffectiveArtifactId] = useState<string>(
    artifactId || "art-jgit-diff-snapshot"
  );

  const entries = diffs.length > 0 ? diffs : [];
  const currentEntry = entries[selectedEntryIndex] || entries[0];

  const totalAdded = entries.reduce((acc, cur) => acc + (cur.linesAdded || 0), 0);
  const totalDeleted = entries.reduce((acc, cur) => acc + (cur.linesDeleted || 0), 0);

  // Resolve active artifact if artifactId is not given
  useEffect(() => {
    if (artifactId) {
      setEffectiveArtifactId(artifactId);
    } else if (runId) {
      apiClient.listArtifactsByRun(runId).then((artifacts) => {
        if (artifacts && artifacts.length > 0) {
          setEffectiveArtifactId(artifacts[0].id);
        }
      }).catch(() => {});
    }
  }, [artifactId, runId]);

  // Fetch Quality Gate report
  const fetchQualityGate = useCallback(async (artId: string) => {
    if (!artId) return;
    setIsLoadingGate(true);
    try {
      const report = await apiClient.getQualityGateReport(artId);
      setQualityGateReport(report);
    } catch {
      try {
        const evaluated = await apiClient.evaluateQualityGate(artId);
        setQualityGateReport(evaluated);
      } catch (e) {
        console.warn("Quality gate report not available:", e);
      }
    } finally {
      setIsLoadingGate(false);
    }
  }, []);

  useEffect(() => {
    if (effectiveArtifactId) {
      fetchQualityGate(effectiveArtifactId);
    }
  }, [effectiveArtifactId, fetchQualityGate]);

  const handleRevertToBaseline = async () => {
    setIsReverting(true);
    try {
      await apiClient.revertArtifact(effectiveArtifactId);
      setRevertSuccess(true);
      setShowConfirmModal(false);
      setTimeout(() => {
        setRevertSuccess(false);
        onRefresh?.();
      }, 2000);
    } catch (e) {
      console.warn("Revert failed:", e);
    } finally {
      setIsReverting(false);
    }
  };

  const handleRevertSingleFile = async () => {
    if (!currentEntry) return;
    const targetPath = currentEntry.newPath && currentEntry.newPath !== "/dev/null"
      ? currentEntry.newPath
      : currentEntry.oldPath;
    if (!targetPath) return;

    setIsSingleFileReverting(true);
    try {
      await apiClient.revertSingleFile(effectiveArtifactId, targetPath);
      setSingleFileSuccess(`文件 ${targetPath.split("/").pop()} 已精准回滚至基线提交！`);
      setShowSingleFileConfirm(false);
      setTimeout(() => {
        setSingleFileSuccess(null);
        onRefresh?.();
      }, 2000);
    } catch (e) {
      console.warn("Single file revert failed:", e);
      alert("单文件回滚失败: " + (e instanceof Error ? e.message : String(e)));
    } finally {
      setIsSingleFileReverting(false);
    }
  };

  const handleOpenPrDraft = async () => {
    setShowPrDraftModal(true);
    setIsLoadingPrDraft(true);
    try {
      const draft = await apiClient.getPullRequestDraft(effectiveArtifactId);
      setPrDraft(draft);
    } catch (e) {
      console.warn("Fetch PR draft failed:", e);
    } finally {
      setIsLoadingPrDraft(false);
    }
  };

  const handleCopyPrMarkdown = () => {
    if (!prDraft) return;
    const changedFiles = prDraft.changedFiles || [];
    const filesCount = changedFiles.length || prDraft.changedFilesCount || 0;
    const gateStatus = prDraft.qualityGateStatus || prDraft.qualityGateReport?.overallStatus || "PASSED";
    const gateScore = prDraft.qualityGateScore ?? prDraft.qualityGateReport?.compositeScore ?? 100;
    const added = prDraft.totalLinesAdded ?? prDraft.addedLines ?? 0;
    const deleted = prDraft.totalLinesDeleted ?? prDraft.deletedLines ?? 0;
    const summary = prDraft.summary || prDraft.summaryMarkdown || "";
    const risk = prDraft.riskAssessment || (prDraft.canMergeSafely ? "LOW_RISK: 质量门禁全部通过" : "MEDIUM_RISK: 建议人工审查");
    const fileListText = changedFiles.length > 0 ? changedFiles.map((f) => `- ${f}`).join("\n") : `共 ${filesCount} 个文件`;

    const md = `# ${prDraft.title}\n\n## 关联目标\n- 目标分支: \`${prDraft.targetBranch}\`\n- 关联 Run: \`${prDraft.runId}\`\n- 质量门禁: **${gateStatus}** (${gateScore}分)\n\n## 变更摘要\n${summary}\n\n## 风险评估\n${risk}\n\n## 变更文件 (${filesCount})\n${fileListText}\n\n代码统计: +${added} / -${deleted}`;
    navigator.clipboard.writeText(md);
    setPrCopied(true);
    setTimeout(() => setPrCopied(false), 2000);
  };

  const handleExportPrDraftJson = () => {
    if (!prDraft) return;
    const blob = new Blob([JSON.stringify(prDraft, null, 2)], { type: "application/json" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `pr-draft-${prDraft.draftId || prDraft.runId}.json`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  };

  const getChangeIcon = (type: string) => {
    switch (type) {
      case "ADD":
        return <FilePlus className="w-3.5 h-3.5 text-emerald-400" />;
      case "DELETE":
        return <Trash2 className="w-3.5 h-3.5 text-rose-400" />;
      default:
        return <FileEdit className="w-3.5 h-3.5 text-amber-400" />;
    }
  };

  // Render unified diff with syntax colors
  const renderDiffLines = (diffContent: string) => {
    if (!diffContent) return null;
    const lines = diffContent.split("\n");

    return lines.map((line, idx) => {
      let lineStyle = "text-zinc-300";
      let bgStyle = "bg-transparent";

      if (line.startsWith("+") && !line.startsWith("+++")) {
        lineStyle = "text-emerald-300 font-medium";
        bgStyle = "bg-emerald-950/30";
      } else if (line.startsWith("-") && !line.startsWith("---")) {
        lineStyle = "text-rose-300 font-medium";
        bgStyle = "bg-rose-950/30";
      } else if (line.startsWith("@@")) {
        lineStyle = "text-sky-400 font-semibold";
        bgStyle = "bg-sky-950/20";
      }

      return (
        <div
          key={idx}
          className={`flex items-start px-2 py-0.5 hover:bg-zinc-800/40 font-mono text-[11px] leading-tight select-text ${bgStyle}`}
        >
          <span className="w-8 text-zinc-600 select-none shrink-0 text-right pr-2">
            {idx + 1}
          </span>
          <span className={`flex-1 break-all whitespace-pre-wrap ${lineStyle}`}>
            {line}
          </span>
        </div>
      );
    });
  };

  return (
    <BentoCard
      title="受控工作区与 JGit 行级审查 (Diff & Baseline)"
      subtitle={workspaceId ? `工作区: ${workspaceId} | 基线快照隔离保护` : "工作区: 未选定 | 基线快照隔离保护"}
      icon={<GitCompare className="w-4 h-4 text-emerald-400" />}
      badge={
        <div className="flex items-center space-x-2 text-[10px] font-mono">
          <span className="text-zinc-400">{entries.length} 个文件变更</span>
          <span className="text-emerald-400 font-semibold">+{totalAdded}</span>
          <span className="text-rose-400 font-semibold">-{totalDeleted}</span>
        </div>
      }
      actions={
        <div className="flex items-center space-x-1.5">
          {/* Phase 5 Quality Gate Badge Button */}
          {qualityGateReport && (
            <button
              onClick={() => setShowGateDrawer(true)}
              className={`flex items-center space-x-1 px-2 py-1 rounded-lg border text-[11px] font-mono transition ${
                qualityGateReport.overallStatus === "PASSED"
                  ? "bg-emerald-950/40 text-emerald-300 border-emerald-500/40 hover:bg-emerald-900/60"
                  : qualityGateReport.overallStatus === "WARNING"
                  ? "bg-amber-950/40 text-amber-300 border-amber-500/40 hover:bg-amber-900/60"
                  : "bg-rose-950/40 text-rose-300 border-rose-500/40 hover:bg-rose-900/60"
              }`}
              title="查看 Phase 5 质量门禁评测详情"
            >
              {qualityGateReport.overallStatus === "PASSED" ? (
                <ShieldCheck className="w-3.5 h-3.5 text-emerald-400" />
              ) : (
                <ShieldAlert className="w-3.5 h-3.5 text-rose-400" />
              )}
              <span>门禁: {qualityGateReport.overallStatus} ({qualityGateReport.compositeScore}分)</span>
            </button>
          )}

          {/* Phase 5 PR Draft Button */}
          <button
            onClick={handleOpenPrDraft}
            className="flex items-center space-x-1 px-2.5 py-1 rounded-lg bg-indigo-950/40 hover:bg-indigo-950/70 text-indigo-300 border border-indigo-500/30 text-[11px] font-medium transition"
            title="生成 GitHub / GitLab Pull Request 草稿规范"
          >
            <GitPullRequest className="w-3 h-3" />
            <span>PR 草稿</span>
          </button>

          {/* Revert Whole Workspace to Baseline */}
          <button
            onClick={() => setShowConfirmModal(true)}
            className="flex items-center space-x-1 px-2.5 py-1 rounded-lg bg-rose-950/40 hover:bg-rose-950/70 text-rose-300 border border-rose-500/30 text-[11px] font-medium transition"
          >
            <RotateCcw className="w-3 h-3" />
            <span>全量回滚基线</span>
          </button>

          {onRefresh && (
            <button
              onClick={onRefresh}
              title="重新比对 JGit Diff"
              className="p-1.5 rounded-lg border border-zinc-800 hover:border-zinc-700 bg-zinc-900/80 text-zinc-400 hover:text-zinc-200"
            >
              <RotateCcw className="w-3 h-3" />
            </button>
          )}
        </div>
      }
      className={`h-full ${className}`}
      bodyClassName="flex flex-col md:flex-row p-0 overflow-hidden relative"
    >
      {/* Left Column: Changed Files List */}
      <div className="w-full md:w-64 border-b md:border-b-0 md:border-r border-zinc-800/80 bg-zinc-950/50 flex flex-col shrink-0 overflow-y-auto">
        <div className="p-2.5 border-b border-zinc-800/60 text-[11px] font-mono text-zinc-400 flex items-center justify-between">
          <span>变更文件列表</span>
          <span className="px-1.5 py-0.2 rounded bg-zinc-800 text-[10px]">
            {entries.length}
          </span>
        </div>

        <div className="p-2 space-y-1">
          {entries.length === 0 ? (
            <div className="p-4 text-center text-xs text-zinc-500 font-mono">
              当前工作区与基线一致，暂无 Diff。
            </div>
          ) : (
            entries.map((entry, idx) => (
              <button
                key={idx}
                onClick={() => setSelectedEntryIndex(idx)}
                className={`w-full text-left p-2 rounded-xl border text-xs transition-all ${
                  selectedEntryIndex === idx
                    ? "bg-indigo-600/15 border-indigo-500/40 text-indigo-200"
                    : "border-transparent text-zinc-400 hover:bg-zinc-900 hover:text-zinc-200"
                }`}
              >
                <div className="flex items-center space-x-1.5 mb-1">
                  {getChangeIcon(entry.changeType)}
                  <span className="font-mono text-[11px] font-medium truncate">
                    {entry.newPath.split("/").pop()}
                  </span>
                </div>
                <div className="text-[10px] text-zinc-500 font-mono truncate pl-5 flex items-center justify-between">
                  <span className="truncate">{entry.newPath}</span>
                  <span className="shrink-0 ml-1">
                    <span className="text-emerald-400">+{entry.linesAdded}</span>{" "}
                    <span className="text-rose-400">-{entry.linesDeleted}</span>
                  </span>
                </div>
              </button>
            ))
          )}
        </div>
      </div>

      {/* Right Column: Unified Diff Viewer & Single-File Revert Button */}
      <div className="flex-1 flex flex-col min-w-0 bg-zinc-950/90 overflow-hidden">
        {currentEntry ? (
          <>
            <div className="border-b border-zinc-800/80 bg-zinc-900/60 px-3 py-2 flex items-center justify-between text-xs font-mono">
              <span className="text-zinc-200 truncate font-semibold">
                {currentEntry.newPath}
              </span>
              <div className="flex items-center space-x-2 shrink-0">
                <span className="px-1.5 py-0.5 rounded bg-zinc-800 text-[10px] text-zinc-300">
                  {currentEntry.changeType}
                </span>
                <span className="text-emerald-400">+{currentEntry.linesAdded}</span>
                <span className="text-rose-400">-{currentEntry.linesDeleted}</span>

                {/* Phase 5: Single-file Revert Button */}
                <button
                  onClick={() => setShowSingleFileConfirm(true)}
                  disabled={isSingleFileReverting}
                  className="flex items-center space-x-1 px-2 py-0.5 rounded bg-zinc-800 hover:bg-rose-950/60 hover:text-rose-300 border border-zinc-700/60 hover:border-rose-500/40 text-[10px] text-zinc-300 transition"
                  title="仅将当前单个文件恢复为 Git 基线版本，保留其他修改"
                >
                  <RotateCcw className="w-2.5 h-2.5" />
                  <span>单文件回滚</span>
                </button>
              </div>
            </div>

            <div className="flex-1 overflow-auto p-2 bg-zinc-950 font-mono text-[11px] leading-relaxed">
              {renderDiffLines(currentEntry.diffContent || currentEntry.diffText || "")}
            </div>
          </>
        ) : (
          <div className="flex-1 flex items-center justify-center text-xs text-zinc-600 font-mono">
            暂无文件差异对比
          </div>
        )}
      </div>

      {/* Single-File Revert Safety Confirmation Modal */}
      {showSingleFileConfirm && currentEntry && (
        <div className="absolute inset-0 bg-black/70 backdrop-blur-sm z-30 flex items-center justify-center p-4">
          <div className="max-w-md w-full rounded-2xl border border-rose-500/40 bg-zinc-950 p-5 shadow-2xl">
            <div className="flex items-center space-x-2.5 text-rose-400 font-semibold text-sm mb-2">
              <AlertOctagon className="w-5 h-5" />
              <span>单文件精准回滚确认 (Single File Revert)</span>
            </div>
            <p className="text-xs text-zinc-300 leading-relaxed mb-2">
              此操作将通过 JGit 指针仅将文件 <code className="text-rose-300 font-mono bg-zinc-900 px-1 py-0.5 rounded">{currentEntry.newPath}</code> 恢复至基线 Commit 状态。
            </p>
            <p className="text-[11px] text-emerald-400/90 leading-relaxed mb-4">
              ✓ 其他变更文件将被完整保留，不受任何干扰；系统将记录 <span className="font-mono">FILE_REVERTED</span> 审计事件。
            </p>
            <div className="flex justify-end space-x-2">
              <button
                onClick={() => setShowSingleFileConfirm(false)}
                className="px-3 py-1.5 rounded-lg border border-zinc-800 text-xs text-zinc-400 hover:text-white"
              >
                取消
              </button>
              <button
                onClick={handleRevertSingleFile}
                disabled={isSingleFileReverting}
                className="px-4 py-1.5 rounded-lg bg-rose-600 hover:bg-rose-500 text-white font-semibold text-xs flex items-center space-x-1.5"
              >
                {isSingleFileReverting ? (
                  <Clock className="w-3.5 h-3.5 animate-spin" />
                ) : (
                  <RotateCcw className="w-3.5 h-3.5" />
                )}
                <span>确认回滚此文件</span>
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Whole-Workspace Revert Safety Confirmation Modal */}
      {showConfirmModal && (
        <div className="absolute inset-0 bg-black/70 backdrop-blur-sm z-30 flex items-center justify-center p-4">
          <div className="max-w-md w-full rounded-2xl border border-rose-500/40 bg-zinc-950 p-5 shadow-2xl">
            <div className="flex items-center space-x-2.5 text-rose-400 font-semibold text-sm mb-2">
              <AlertOctagon className="w-5 h-5" />
              <span>全量安全回滚确认 (Revert to Git Baseline)</span>
            </div>
            <p className="text-xs text-zinc-300 leading-relaxed mb-4">
              此操作将通过 JGit 将当前工作区所有改动安全重置回基线提交快照，未提交的 Agent 代码变更将被安全废弃。确认继续？
            </p>
            <div className="flex justify-end space-x-2">
              <button
                onClick={() => setShowConfirmModal(false)}
                className="px-3 py-1.5 rounded-lg border border-zinc-800 text-xs text-zinc-400 hover:text-white"
              >
                取消
              </button>
              <button
                onClick={handleRevertToBaseline}
                disabled={isReverting}
                className="px-4 py-1.5 rounded-lg bg-rose-600 hover:bg-rose-500 text-white font-semibold text-xs flex items-center space-x-1.5"
              >
                {isReverting ? (
                  <Clock className="w-3.5 h-3.5 animate-spin" />
                ) : (
                  <RotateCcw className="w-3.5 h-3.5" />
                )}
                <span>确认回滚基线</span>
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Phase 5: Quality Gate Report Drawer / Modal */}
      {showGateDrawer && qualityGateReport && (
        <div className="absolute inset-0 bg-black/75 backdrop-blur-sm z-30 flex items-center justify-center p-4">
          <div className="max-w-2xl w-full rounded-2xl border border-zinc-800 bg-zinc-950 p-5 shadow-2xl flex flex-col max-h-[85vh] overflow-hidden text-zinc-100">
            <div className="flex items-center justify-between pb-3 border-b border-zinc-800">
              <div className="flex items-center space-x-2.5">
                <ShieldCheck className="w-5 h-5 text-indigo-400" />
                <div>
                  <h3 className="text-sm font-semibold text-zinc-100">
                    Phase 5 质量门禁多维防御评测 (Quality Gate Report)
                  </h3>
                  <p className="text-[10px] font-mono text-zinc-400 mt-0.5">
                    产物 ID: {qualityGateReport.artifactId} | SHA-256: {qualityGateReport.sha256Fingerprint.substring(0, 16)}...
                  </p>
                </div>
              </div>
              <button
                onClick={() => setShowGateDrawer(false)}
                className="text-zinc-400 hover:text-white p-1 rounded hover:bg-zinc-800"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            {/* Score & Summary Banner */}
            <div className="my-3 p-3 rounded-xl bg-zinc-900/80 border border-zinc-800 flex items-center justify-between">
              <div>
                <span className="text-xs text-zinc-400 block">综合评测得分</span>
                <div className="flex items-baseline space-x-2">
                  <span className="text-2xl font-bold font-mono text-indigo-400">
                    {qualityGateReport.compositeScore}
                  </span>
                  <span className="text-xs text-zinc-500 font-mono">/ 100 分</span>
                </div>
              </div>

              <div className="flex items-center space-x-3 text-xs font-mono">
                <div className="text-right">
                  <span className="text-emerald-400 font-semibold">{qualityGateReport.passedRules}</span>
                  <span className="text-zinc-500"> 通过</span>
                </div>
                {qualityGateReport.warningRules > 0 && (
                  <div className="text-right">
                    <span className="text-amber-400 font-semibold">{qualityGateReport.warningRules}</span>
                    <span className="text-zinc-500"> 警告</span>
                  </div>
                )}
                <div className="text-right">
                  <span className="text-rose-400 font-semibold">{qualityGateReport.failedRules}</span>
                  <span className="text-zinc-500"> 阻断</span>
                </div>
              </div>
            </div>

            {/* Check Items List */}
            <div className="flex-1 overflow-y-auto space-y-2 pr-1">
              {qualityGateReport.checkItems.map((item, idx) => (
                <div
                  key={idx}
                  className={`p-3 rounded-xl border text-xs ${
                    item.status === "PASSED"
                      ? "bg-zinc-900/40 border-zinc-800/80 text-zinc-300"
                      : item.status === "WARNING"
                      ? "bg-amber-950/20 border-amber-600/40 text-amber-200"
                      : "bg-rose-950/30 border-rose-600/50 text-rose-200"
                  }`}
                >
                  <div className="flex items-center justify-between mb-1">
                    <div className="flex items-center space-x-2">
                      <span className="font-semibold text-zinc-200">{item.ruleName}</span>
                      <span className="text-[10px] font-mono px-1.5 py-0.2 rounded bg-zinc-800 text-zinc-400">
                        权重: {item.weight}%
                      </span>
                    </div>
                    <span
                      className={`text-[10px] font-mono font-semibold px-2 py-0.5 rounded ${
                        item.status === "PASSED"
                          ? "bg-emerald-950 text-emerald-400 border border-emerald-800"
                          : item.status === "WARNING"
                          ? "bg-amber-950 text-amber-400 border border-amber-800"
                          : "bg-rose-950 text-rose-400 border border-rose-800"
                      }`}
                    >
                      {item.status}
                    </span>
                  </div>
                  <p className="text-[11px] leading-relaxed text-zinc-400 mt-0.5">{item.message}</p>
                  {item.status === "FAILED" && (
                    <div className="mt-2 flex items-center space-x-1 text-[11px] text-rose-300 bg-rose-950/40 border border-rose-800/60 p-2 rounded">
                      <AlertTriangle className="w-3.5 h-3.5 shrink-0 text-rose-400" />
                      <span>阻断警告：该违规项目导致交付物评审状态被强制置为 GATE_FAILED，阻断受控沙箱发布与外部交付！</span>
                    </div>
                  )}
                </div>
              ))}
            </div>

            <div className="mt-4 pt-3 border-t border-zinc-800 flex justify-between items-center text-xs">
              <span className="text-[11px] text-zinc-500 font-mono">
                评测时间: {new Date(qualityGateReport.evaluatedAt).toLocaleString()}
              </span>
              <button
                onClick={() => fetchQualityGate(effectiveArtifactId)}
                disabled={isLoadingGate}
                className="px-3 py-1.5 rounded-lg bg-zinc-800 hover:bg-zinc-700 text-zinc-200 font-medium transition flex items-center space-x-1.5"
              >
                <RotateCcw className={`w-3 h-3 ${isLoadingGate ? "animate-spin" : ""}`} />
                <span>重新评估门禁</span>
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Phase 5: PR Draft Modal */}
      {showPrDraftModal && (
        <div className="absolute inset-0 bg-black/75 backdrop-blur-sm z-30 flex items-center justify-center p-4">
          <div className="max-w-2xl w-full rounded-2xl border border-zinc-800 bg-zinc-950 p-5 shadow-2xl flex flex-col max-h-[85vh] overflow-hidden text-zinc-100">
            <div className="flex items-center justify-between pb-3 border-b border-zinc-800">
              <div className="flex items-center space-x-2.5">
                <GitPullRequest className="w-5 h-5 text-indigo-400" />
                <div>
                  <h3 className="text-sm font-semibold text-zinc-100">
                    GitHub / GitLab 交付 PR 草稿规范 (Pull Request Draft)
                  </h3>
                  <p className="text-[10px] font-mono text-zinc-400 mt-0.5">
                    目标分支: {prDraft?.targetBranch || "main"} · 门禁评分: {prDraft?.qualityGateScore ?? "N/A"} 分
                  </p>
                </div>
              </div>
              <button
                onClick={() => setShowPrDraftModal(false)}
                className="text-zinc-400 hover:text-white p-1 rounded hover:bg-zinc-800"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            {isLoadingPrDraft ? (
              <div className="py-12 flex flex-col items-center justify-center text-zinc-500 text-xs">
                <Clock className="w-6 h-6 animate-spin mb-2 text-indigo-400" />
                <span>正在生成标准 PR 草稿规范与交付凭据...</span>
              </div>
            ) : prDraft ? (
              <div className="flex-1 overflow-y-auto space-y-3 py-3 pr-1 text-xs">
                <div>
                  <label className="text-zinc-400 block mb-1 font-medium">PR 建议标题</label>
                  <div className="p-2.5 rounded-lg bg-zinc-900 border border-zinc-800 font-mono text-zinc-200">
                    {prDraft.title}
                  </div>
                </div>

                <div>
                  <label className="text-zinc-400 block mb-1 font-medium">变更动机与实现摘要</label>
                  <div className="p-2.5 rounded-lg bg-zinc-900 border border-zinc-800 text-zinc-300 leading-relaxed whitespace-pre-wrap">
                    {prDraft.summary || prDraft.summaryMarkdown || "自动化质量门禁与受控交付流水线生成"}
                  </div>
                </div>

                <div>
                  <label className="text-zinc-400 block mb-1 font-medium">安全与架构风险评估</label>
                  <div className="p-2.5 rounded-lg bg-zinc-900 border border-zinc-800 text-emerald-400 font-mono leading-relaxed">
                    {prDraft.riskAssessment || (prDraft.canMergeSafely ? "LOW_RISK: 质量门禁全部通过" : "RISK: 存在未通过门禁项")}
                  </div>
                </div>

                <div>
                  <label className="text-zinc-400 block mb-1 font-medium">
                    涉及变更文件 ({prDraft.changedFiles?.length ?? prDraft.changedFilesCount ?? 0})
                  </label>
                  <div className="p-2.5 rounded-lg bg-zinc-900 border border-zinc-800 font-mono text-[11px] text-zinc-400 space-y-1">
                    {(prDraft.changedFiles && prDraft.changedFiles.length > 0) ? (
                      prDraft.changedFiles.map((f, i) => (
                        <div key={i} className="truncate">
                          • {f}
                        </div>
                      ))
                    ) : (
                      <div className="text-zinc-500 italic">共 {prDraft.changedFilesCount ?? 0} 个变更文件</div>
                    )}
                    <div className="pt-1.5 border-t border-zinc-800 flex items-center space-x-3 text-xs">
                      <span className="text-emerald-400">+{prDraft.totalLinesAdded ?? prDraft.addedLines ?? 0} 行</span>
                      <span className="text-rose-400">-{prDraft.totalLinesDeleted ?? prDraft.deletedLines ?? 0} 行</span>
                    </div>
                  </div>
                </div>
              </div>
            ) : (
              <div className="py-8 text-center text-zinc-500 text-xs">未能获取到 PR 草稿</div>
            )}

            <div className="mt-4 pt-3 border-t border-zinc-800 flex justify-end space-x-2">
              <button
                onClick={handleCopyPrMarkdown}
                disabled={!prDraft}
                className="px-3 py-1.5 rounded-lg bg-zinc-800 hover:bg-zinc-700 text-zinc-200 text-xs font-medium transition flex items-center space-x-1.5"
              >
                {prCopied ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
                <span>{prCopied ? "已复制 Markdown" : "复制 Markdown"}</span>
              </button>

              <button
                onClick={handleExportPrDraftJson}
                disabled={!prDraft}
                className="px-3 py-1.5 rounded-lg bg-indigo-600 hover:bg-indigo-500 text-white text-xs font-semibold transition flex items-center space-x-1.5 shadow-md shadow-indigo-900"
              >
                <Download className="w-3.5 h-3.5" />
                <span>导出 PR 草稿 JSON</span>
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Feedback Toast Notifications */}
      {revertSuccess && (
        <div className="absolute top-3 right-3 rounded-xl border border-emerald-500/40 bg-emerald-950/90 px-3.5 py-2 text-xs text-emerald-300 flex items-center space-x-2 z-40 shadow-xl">
          <Check className="w-4 h-4 text-emerald-400" />
          <span>工作区已安全回滚至 Git 基线快照！</span>
        </div>
      )}

      {singleFileSuccess && (
        <div className="absolute top-3 right-3 rounded-xl border border-emerald-500/40 bg-emerald-950/90 px-3.5 py-2 text-xs text-emerald-300 flex items-center space-x-2 z-40 shadow-xl">
          <Check className="w-4 h-4 text-emerald-400" />
          <span>{singleFileSuccess}</span>
        </div>
      )}
    </BentoCard>
  );
}
