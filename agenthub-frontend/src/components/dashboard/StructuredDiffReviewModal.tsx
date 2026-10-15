"use client";

import React, { useState, useEffect } from "react";
import { FileDiffEntry, StructuredDiff, QualityGateReport, PullRequestDraftView } from "@/types";
import { apiClient } from "@/services/api";
import {
  ShieldCheck,
  ShieldAlert,
  RotateCcw,
  GitPullRequest,
  Copy,
  Check,
  Clock,
  Download,
  AlertOctagon,
  X,
} from "lucide-react";

interface StructuredDiffReviewModalProps {
  isOpen: boolean;
  onClose: () => void;
  diff: StructuredDiff | null;
  artifactId?: string;
  onAccept?: (artifactId: string) => Promise<void>;
  onReject?: (artifactId: string, reason: string) => Promise<void>;
  onRevert?: (artifactId: string) => Promise<void>;
  onRefresh?: () => void;
}

export const StructuredDiffReviewModal: React.FC<StructuredDiffReviewModalProps> = ({
  isOpen,
  onClose,
  diff,
  artifactId,
  onAccept,
  onReject,
  onRevert,
  onRefresh,
}) => {
  const [selectedFileIdx, setSelectedFileIdx] = useState<number>(0);
  const [rejectReason, setRejectReason] = useState<string>("");
  const [showRejectInput, setShowRejectInput] = useState<boolean>(false);
  const [actionLoading, setActionLoading] = useState<boolean>(false);

  // Phase 5: Quality Gate & PR Draft states
  const [qualityGate, setQualityGate] = useState<QualityGateReport | null>(null);
  const [loadingGate, setLoadingGate] = useState<boolean>(false);

  const [isSingleFileReverting, setIsSingleFileReverting] = useState<boolean>(false);
  const [singleFileSuccess, setSingleFileSuccess] = useState<string | null>(null);

  const [showPrModal, setShowPrModal] = useState<boolean>(false);
  const [prDraft, setPrDraft] = useState<PullRequestDraftView | null>(null);
  const [loadingPrDraft, setLoadingPrDraft] = useState<boolean>(false);
  const [prCopied, setPrCopied] = useState<boolean>(false);

  // Fetch quality gate report when artifactId is provided
  useEffect(() => {
    if (isOpen && artifactId) {
      setLoadingGate(true);
      apiClient
        .getQualityGateReport(artifactId)
        .then((report) => setQualityGate(report))
        .catch(() => {
          apiClient
            .evaluateQualityGate(artifactId)
            .then((evaluated) => setQualityGate(evaluated))
            .catch(() => setQualityGate(null));
        })
        .finally(() => setLoadingGate(false));
    } else {
      setQualityGate(null);
    }
  }, [isOpen, artifactId]);

  if (!isOpen || !diff) return null;

  const entries = diff.entries || [];
  const selectedEntry: FileDiffEntry | undefined = entries[selectedFileIdx];

  const handleAccept = async () => {
    if (!artifactId || !onAccept) return;
    setActionLoading(true);
    try {
      await onAccept(artifactId);
      onClose();
    } finally {
      setActionLoading(false);
    }
  };

  const handleReject = async () => {
    if (!artifactId || !onReject) return;
    if (!rejectReason.trim()) {
      alert("请填写驳回原因");
      return;
    }
    setActionLoading(true);
    try {
      await onReject(artifactId, rejectReason.trim());
      onClose();
    } finally {
      setActionLoading(false);
    }
  };

  const handleRevert = async () => {
    if (!artifactId || !onRevert) return;
    if (!confirm("确定要将此产物关联文件全量无损回滚到基线 Commit 状态吗？此操作不会丢失工作区未提交的其他改动。")) {
      return;
    }
    setActionLoading(true);
    try {
      await onRevert(artifactId);
      onClose();
    } finally {
      setActionLoading(false);
    }
  };

  const handleSingleFileRevert = async () => {
    if (!artifactId || !selectedEntry) return;
    const path = selectedEntry.newPath !== "/dev/null" ? selectedEntry.newPath : selectedEntry.oldPath;
    if (!path) return;

    if (!confirm(`确定仅将单文件 [${path}] 回滚至基线 Commit 状态吗？其他文件不受影响。`)) {
      return;
    }

    setIsSingleFileReverting(true);
    try {
      await apiClient.revertSingleFile(artifactId, path);
      setSingleFileSuccess(`文件 ${path.split("/").pop()} 已成功回滚至基线！`);
      setTimeout(() => {
        setSingleFileSuccess(null);
        onRefresh?.();
      }, 2000);
    } catch (err: unknown) {
      alert("单文件回滚失败: " + (err instanceof Error ? err.message : String(err)));
    } finally {
      setIsSingleFileReverting(false);
    }
  };

  const handleLoadPrDraft = async () => {
    if (!artifactId) return;
    setShowPrModal(true);
    setLoadingPrDraft(true);
    try {
      const draft = await apiClient.getPullRequestDraft(artifactId);
      setPrDraft(draft);
    } catch (err: unknown) {
      console.warn("Load PR draft error:", err);
    } finally {
      setLoadingPrDraft(false);
    }
  };

  const handleCopyPrMarkdown = () => {
    if (!prDraft) return;
    const md = `# ${prDraft.title}\n\n## 关联目标\n- 目标分支: \`${prDraft.targetBranch}\`\n- 关联 Run: \`${prDraft.runId}\`\n- 质量门禁: **${prDraft.qualityGateStatus}** (${prDraft.qualityGateScore}分)\n\n## 变更摘要\n${prDraft.summary}\n\n## 风险评估\n${prDraft.riskAssessment}\n\n## 变更文件 (${prDraft.changedFiles.length})\n${prDraft.changedFiles.map((f) => `- ${f}`).join("\n")}\n\n代码统计: +${prDraft.totalLinesAdded} / -${prDraft.totalLinesDeleted}`;
    navigator.clipboard.writeText(md);
    setPrCopied(true);
    setTimeout(() => setPrCopied(false), 2000);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm p-4">
      <div className="bg-slate-900 border border-slate-800 rounded-xl shadow-2xl w-full max-w-5xl h-[85vh] flex flex-col overflow-hidden text-slate-100 relative">
        {/* Header */}
        <div className="px-6 py-4 border-b border-slate-800 flex items-center justify-between bg-slate-950/40">
          <div className="flex items-center space-x-3">
            <span className="text-sm font-semibold tracking-wide text-indigo-400 bg-indigo-950/60 border border-indigo-800 px-2.5 py-0.5 rounded">
              JGit 结构化 Diff 审查
            </span>
            <span className="text-xs text-slate-400 font-mono">
              基线: {diff.baselineCommitHash ? diff.baselineCommitHash.substring(0, 8) : "无"} → 工作区 HEAD:{" "}
              {diff.headCommitHash ? diff.headCommitHash.substring(0, 8) : "Working Tree"}
            </span>

            {/* Quality Gate Badge */}
            {qualityGate && (
              <span
                className={`flex items-center space-x-1 text-[11px] font-mono px-2 py-0.5 rounded border ${
                  qualityGate.overallStatus === "PASSED"
                    ? "bg-emerald-950/70 text-emerald-300 border-emerald-700"
                    : qualityGate.overallStatus === "WARNING"
                    ? "bg-amber-950/70 text-amber-300 border-amber-700"
                    : "bg-rose-950/70 text-rose-300 border-rose-700"
                }`}
              >
                {qualityGate.overallStatus === "PASSED" ? (
                  <ShieldCheck className="w-3.5 h-3.5 text-emerald-400" />
                ) : (
                  <ShieldAlert className="w-3.5 h-3.5 text-rose-400" />
                )}
                <span>门禁: {qualityGate.overallStatus} ({qualityGate.compositeScore}分)</span>
              </span>
            )}
          </div>

          <div className="flex items-center space-x-4">
            <div className="flex items-center space-x-3 text-xs font-mono">
              <span className="text-emerald-400 font-medium">+{diff.totalLinesAdded}</span>
              <span className="text-rose-400 font-medium">-{diff.totalLinesDeleted}</span>
              <span className="text-slate-400">({diff.totalFilesChanged} 个变更文件)</span>
            </div>
            <button
              onClick={onClose}
              className="text-slate-400 hover:text-white transition-colors p-1 rounded hover:bg-slate-800"
            >
              ✕
            </button>
          </div>
        </div>

        {/* Content Body */}
        <div className="flex-1 flex overflow-hidden">
          {/* File Selector Sidebar */}
          <div className="w-80 border-r border-slate-800 bg-slate-950/20 overflow-y-auto p-3 space-y-1.5">
            <div className="text-xs font-medium text-slate-400 px-2 py-1 uppercase tracking-wider">
              变更文件列表 ({entries.length})
            </div>
            {entries.length === 0 ? (
              <div className="text-xs text-slate-500 italic p-3">无文件变更记录</div>
            ) : (
              entries.map((entry, idx) => {
                const isSelected = idx === selectedFileIdx;
                const path = entry.newPath && entry.newPath !== "/dev/null" ? entry.newPath : entry.oldPath;
                const filename = path.split("/").pop() || path;
                const dir = path.substring(0, path.lastIndexOf("/"));

                return (
                  <button
                    key={idx}
                    onClick={() => setSelectedFileIdx(idx)}
                    className={`w-full text-left px-3 py-2 rounded-lg text-xs transition-all flex flex-col space-y-1 border ${
                      isSelected
                        ? "bg-indigo-950/50 border-indigo-700/80 text-white"
                        : "bg-slate-900/40 border-slate-800/60 text-slate-300 hover:bg-slate-800/50"
                    }`}
                  >
                    <div className="flex items-center justify-between w-full">
                      <span className="font-medium truncate max-w-[170px]" title={path}>
                        {filename}
                      </span>
                      <span
                        className={`text-[10px] px-1.5 py-0.5 rounded uppercase font-semibold ${
                          entry.changeType === "ADD"
                            ? "bg-emerald-950 text-emerald-400 border border-emerald-800"
                            : entry.changeType === "DELETE"
                            ? "bg-rose-950 text-rose-400 border border-rose-800"
                            : "bg-sky-950 text-sky-400 border border-sky-800"
                        }`}
                      >
                        {entry.changeType}
                      </span>
                    </div>

                    {dir && <span className="text-[10px] text-slate-500 truncate">{dir}</span>}

                    <div className="flex items-center space-x-2 text-[10px] pt-0.5">
                      {((entry.addedLines ?? entry.linesAdded ?? 0) > 0) && (
                        <span className="text-emerald-400">+{entry.addedLines ?? entry.linesAdded}</span>
                      )}
                      {((entry.deletedLines ?? entry.linesDeleted ?? 0) > 0) && (
                        <span className="text-rose-400">-{entry.deletedLines ?? entry.linesDeleted}</span>
                      )}
                      {entry.conflict && (
                        <span className="bg-amber-950 text-amber-300 border border-amber-800 px-1 rounded font-bold">
                          冲突 (CONFLICT)
                        </span>
                      )}
                      {entry.binary && <span className="text-slate-400">二进制</span>}
                    </div>
                  </button>
                );
              })
            )}
          </div>

          {/* Diff Content View */}
          <div className="flex-1 flex flex-col bg-slate-950/60 overflow-hidden">
            {selectedEntry ? (
              <div className="flex-1 flex flex-col overflow-hidden">
                <div className="px-4 py-2 border-b border-slate-800 bg-slate-900/60 flex items-center justify-between text-xs font-mono">
                  <div className="flex items-center space-x-3 truncate">
                    <span className="text-slate-300 truncate font-semibold">
                      {selectedEntry.newPath !== "/dev/null" ? selectedEntry.newPath : selectedEntry.oldPath}
                    </span>
                    {selectedEntry.checksum && (
                      <span className="text-slate-500 text-[10px]">SHA: {selectedEntry.checksum.substring(0, 10)}</span>
                    )}
                  </div>

                  {/* Phase 5: Single-file revert button */}
                  {artifactId && (
                    <button
                      onClick={handleSingleFileRevert}
                      disabled={isSingleFileReverting}
                      className="flex items-center space-x-1 px-2.5 py-1 rounded bg-slate-800 hover:bg-rose-950/70 hover:text-rose-300 border border-slate-700 hover:border-rose-600/60 text-[11px] text-slate-300 transition"
                      title="仅将当前选中文件恢复为 Git 基线版本"
                    >
                      <RotateCcw className={`w-3 h-3 ${isSingleFileReverting ? "animate-spin" : ""}`} />
                      <span>单文件回滚</span>
                    </button>
                  )}
                </div>

                <div className="flex-1 overflow-auto p-4 font-mono text-xs leading-relaxed">
                  {selectedEntry.binary ? (
                    <div className="text-slate-400 italic p-6 text-center">二进制文件差异不予直接文本展示</div>
                  ) : (
                    <pre className="whitespace-pre-wrap select-text">
                      {selectedEntry.diffText ? (
                        selectedEntry.diffText.split("\n").map((line, i) => {
                          const isAdd = line.startsWith("+") && !line.startsWith("+++");
                          const isDel = line.startsWith("-") && !line.startsWith("---");
                          const isHunk = line.startsWith("@@");

                          let lineClass = "text-slate-300";
                          if (isAdd) lineClass = "bg-emerald-950/40 text-emerald-300";
                          else if (isDel) lineClass = "bg-rose-950/40 text-rose-300";
                          else if (isHunk) lineClass = "text-indigo-400 font-semibold bg-indigo-950/30";

                          return (
                            <div key={i} className={`px-2 py-0.5 rounded-sm ${lineClass}`}>
                              {line || " "}
                            </div>
                          );
                        })
                      ) : (
                        <span className="text-slate-500 italic">无文本 Diff 变更内容</span>
                      )}
                    </pre>
                  )}
                </div>
              </div>
            ) : (
              <div className="flex-1 flex items-center justify-center text-slate-500 text-xs italic">
                请选择左侧文件查看结构化 Diff
              </div>
            )}
          </div>
        </div>

        {/* Action Footer */}
        <div className="px-6 py-3 border-t border-slate-800 bg-slate-950/80 flex items-center justify-between">
          <div className="flex items-center space-x-2">
            {artifactId && onRevert && (
              <button
                disabled={actionLoading}
                onClick={handleRevert}
                className="px-3 py-1.5 text-xs font-medium rounded-lg bg-rose-950 text-rose-300 border border-rose-800 hover:bg-rose-900 transition-colors disabled:opacity-50"
              >
                全量回滚至基线
              </button>
            )}

            {/* Phase 5: PR Draft Button */}
            {artifactId && (
              <button
                onClick={handleLoadPrDraft}
                className="px-3 py-1.5 text-xs font-medium rounded-lg bg-indigo-950/80 text-indigo-300 border border-indigo-700/60 hover:bg-indigo-900 transition-colors flex items-center space-x-1.5"
              >
                <GitPullRequest className="w-3.5 h-3.5" />
                <span>生成 PR 草稿</span>
              </button>
            )}
          </div>

          <div className="flex items-center space-x-3">
            {artifactId && onReject && (
              <>
                {showRejectInput ? (
                  <div className="flex items-center space-x-2">
                    <input
                      type="text"
                      placeholder="请输入驳回原因..."
                      value={rejectReason}
                      onChange={(e) => setRejectReason(e.target.value)}
                      className="px-2.5 py-1 text-xs bg-slate-900 border border-slate-700 rounded text-slate-200 focus:outline-none focus:border-rose-500 w-60"
                    />
                    <button
                      disabled={actionLoading}
                      onClick={handleReject}
                      className="px-3 py-1.5 text-xs font-medium rounded-lg bg-rose-600 text-white hover:bg-rose-500 transition-colors disabled:opacity-50"
                    >
                      确认驳回
                    </button>
                    <button
                      onClick={() => setShowRejectInput(false)}
                      className="text-xs text-slate-400 hover:text-white px-1.5"
                    >
                      取消
                    </button>
                  </div>
                ) : (
                  <button
                    disabled={actionLoading}
                    onClick={() => setShowRejectInput(true)}
                    className="px-3 py-1.5 text-xs font-medium rounded-lg bg-slate-800 text-slate-200 border border-slate-700 hover:bg-slate-700 transition-colors disabled:opacity-50"
                  >
                    驳回产物
                  </button>
                )}
              </>
            )}

            {artifactId && onAccept && (
              <button
                disabled={actionLoading}
                onClick={handleAccept}
                className="px-4 py-1.5 text-xs font-medium rounded-lg bg-emerald-600 text-white hover:bg-emerald-500 shadow-md shadow-emerald-950 transition-colors disabled:opacity-50"
              >
                采纳产物
              </button>
            )}

            <button
              onClick={onClose}
              className="px-3 py-1.5 text-xs font-medium rounded-lg bg-slate-800 text-slate-400 hover:text-white transition-colors"
            >
              关闭
            </button>
          </div>
        </div>

        {/* PR Draft Modal Overlay */}
        {showPrModal && (
          <div className="absolute inset-0 bg-black/80 backdrop-blur-sm z-50 flex items-center justify-center p-4">
            <div className="max-w-xl w-full rounded-2xl border border-slate-700 bg-slate-950 p-5 shadow-2xl flex flex-col max-h-[80vh] overflow-hidden text-slate-100">
              <div className="flex items-center justify-between pb-3 border-b border-slate-800">
                <div className="flex items-center space-x-2">
                  <GitPullRequest className="w-4 h-4 text-indigo-400" />
                  <span className="font-semibold text-sm">GitHub / GitLab PR 草稿规范</span>
                </div>
                <button
                  onClick={() => setShowPrModal(false)}
                  className="text-slate-400 hover:text-white p-1 rounded hover:bg-slate-800"
                >
                  <X className="w-4 h-4" />
                </button>
              </div>

              {loadingPrDraft ? (
                <div className="py-12 flex flex-col items-center justify-center text-slate-500 text-xs">
                  <Clock className="w-6 h-6 animate-spin mb-2 text-indigo-400" />
                  <span>正在获取 Pull Request 草稿规范...</span>
                </div>
              ) : prDraft ? (
                <div className="flex-1 overflow-y-auto space-y-3 py-3 pr-1 text-xs">
                  <div>
                    <label className="text-slate-400 block mb-1">建议标题</label>
                    <div className="p-2.5 rounded bg-slate-900 border border-slate-800 font-mono text-slate-200">
                      {prDraft.title}
                    </div>
                  </div>
                  <div>
                    <label className="text-slate-400 block mb-1">变更动机与摘要</label>
                    <div className="p-2.5 rounded bg-slate-900 border border-slate-800 text-slate-300 leading-relaxed whitespace-pre-wrap">
                      {prDraft.summary}
                    </div>
                  </div>
                  <div>
                    <label className="text-slate-400 block mb-1">风险评估</label>
                    <div className="p-2.5 rounded bg-slate-900 border border-slate-800 text-emerald-400 font-mono">
                      {prDraft.riskAssessment}
                    </div>
                  </div>
                </div>
              ) : (
                <div className="py-8 text-center text-slate-500 text-xs">暂无 PR 草稿内容</div>
              )}

              <div className="mt-3 pt-3 border-t border-slate-800 flex justify-end space-x-2">
                <button
                  onClick={handleCopyPrMarkdown}
                  disabled={!prDraft}
                  className="px-3 py-1.5 rounded bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs flex items-center space-x-1.5"
                >
                  {prCopied ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
                  <span>{prCopied ? "已复制" : "复制 Markdown"}</span>
                </button>
              </div>
            </div>
          </div>
        )}

        {/* Success Toast */}
        {singleFileSuccess && (
          <div className="absolute top-4 right-4 z-50 bg-emerald-950 border border-emerald-600/60 text-emerald-300 text-xs px-3.5 py-2 rounded-lg shadow-xl flex items-center space-x-2">
            <Check className="w-4 h-4 text-emerald-400" />
            <span>{singleFileSuccess}</span>
          </div>
        )}
      </div>
    </div>
  );
};
