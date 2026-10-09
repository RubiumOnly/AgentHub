"use client";

import React, { useState } from "react";
import { BentoCard } from "@/components/common/BentoCard";
import { FileDiffEntry, StructuredDiff } from "@/types";
import { apiClient, DEFAULT_WORKSPACE_PATH } from "@/services/api";
import {
  GitCompare,
  RotateCcw,
  FileCode,
  FilePlus,
  FileEdit,
  Trash2,
  ShieldCheck,
  AlertOctagon,
  Check,
  Clock,
  Layers,
} from "lucide-react";

interface DiffArtifactReviewerProps {
  diffs?: FileDiffEntry[];
  structuredDiff?: StructuredDiff | null;
  onRefresh?: () => void;
  className?: string;
}

export function DiffArtifactReviewer({
  diffs = [],
  structuredDiff,
  onRefresh,
  className = "",
}: DiffArtifactReviewerProps) {
  const [selectedEntryIndex, setSelectedEntryIndex] = useState(0);
  const [isReverting, setIsReverting] = useState(false);
  const [showConfirmModal, setShowConfirmModal] = useState(false);
  const [revertSuccess, setRevertSuccess] = useState(false);

  const entries = diffs.length > 0 ? diffs : [];
  const currentEntry = entries[selectedEntryIndex] || entries[0];

  const totalAdded = entries.reduce((acc, cur) => acc + (cur.linesAdded || 0), 0);
  const totalDeleted = entries.reduce((acc, cur) => acc + (cur.linesDeleted || 0), 0);

  const handleRevertToBaseline = async () => {
    setIsReverting(true);
    try {
      await apiClient.revertArtifact("art-jgit-diff-snapshot");
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
      subtitle={`工作区: ${DEFAULT_WORKSPACE_PATH} | 基线快照隔离保护`}
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
          <button
            onClick={() => setShowConfirmModal(true)}
            className="flex items-center space-x-1 px-2.5 py-1 rounded-lg bg-rose-950/40 hover:bg-rose-950/70 text-rose-300 border border-rose-500/30 text-[11px] font-medium transition"
          >
            <RotateCcw className="w-3 h-3" />
            <span>回滚至基线 (Revert)</span>
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

      {/* Right Column: Unified Diff Viewer */}
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
              </div>
            </div>

            <div className="flex-1 overflow-auto p-2 bg-zinc-950 font-mono text-[11px] leading-relaxed">
              {renderDiffLines(currentEntry.diffContent)}
            </div>
          </>
        ) : (
          <div className="flex-1 flex items-center justify-center text-xs text-zinc-600 font-mono">
            暂无文件差异对比
          </div>
        )}
      </div>

      {/* Revert Safety Confirmation Modal */}
      {showConfirmModal && (
        <div className="absolute inset-0 bg-black/70 backdrop-blur-sm z-30 flex items-center justify-center p-4">
          <div className="max-w-md w-full rounded-2xl border border-rose-500/40 bg-zinc-950 p-5 shadow-2xl">
            <div className="flex items-center space-x-2.5 text-rose-400 font-semibold text-sm mb-2">
              <AlertOctagon className="w-5 h-5" />
              <span>安全回滚确认 (Revert to Git Baseline)</span>
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

      {/* Revert Success Banner */}
      {revertSuccess && (
        <div className="absolute top-3 right-3 rounded-xl border border-emerald-500/40 bg-emerald-950/90 px-3.5 py-2 text-xs text-emerald-300 flex items-center space-x-2 z-40 shadow-xl">
          <Check className="w-4 h-4 text-emerald-400" />
          <span>工作区已安全回滚至 Git 基线快照！</span>
        </div>
      )}
    </BentoCard>
  );
}
