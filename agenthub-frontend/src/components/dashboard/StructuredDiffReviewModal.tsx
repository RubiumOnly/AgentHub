"use client";

import React, { useState } from "react";
import { FileDiffEntry, StructuredDiff } from "@/types";

interface StructuredDiffReviewModalProps {
  isOpen: boolean;
  onClose: () => void;
  diff: StructuredDiff | null;
  artifactId?: string;
  onAccept?: (artifactId: string) => Promise<void>;
  onReject?: (artifactId: string, reason: string) => Promise<void>;
  onRevert?: (artifactId: string) => Promise<void>;
}

export const StructuredDiffReviewModal: React.FC<StructuredDiffReviewModalProps> = ({
  isOpen,
  onClose,
  diff,
  artifactId,
  onAccept,
  onReject,
  onRevert,
}) => {
  const [selectedFileIdx, setSelectedFileIdx] = useState<number>(0);
  const [rejectReason, setRejectReason] = useState<string>("");
  const [showRejectInput, setShowRejectInput] = useState<boolean>(false);
  const [actionLoading, setActionLoading] = useState<boolean>(false);

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
    if (!confirm("确定要将此产物关联文件无损回滚到基线 Commit 状态吗？此操作不会丢失工作区未提交的其他改动。")) {
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

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm p-4">
      <div className="bg-slate-900 border border-slate-800 rounded-xl shadow-2xl w-full max-w-5xl h-[85vh] flex flex-col overflow-hidden text-slate-100">
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
                  <span className="text-slate-300 truncate font-semibold">
                    {selectedEntry.newPath !== "/dev/null" ? selectedEntry.newPath : selectedEntry.oldPath}
                  </span>
                  {selectedEntry.checksum && (
                    <span className="text-slate-500 text-[10px]">SHA: {selectedEntry.checksum.substring(0, 10)}</span>
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
                无损回滚至基线
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
      </div>
    </div>
  );
};
