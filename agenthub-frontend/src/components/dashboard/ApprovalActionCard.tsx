"use client";

import React, { useState } from "react";
import { ApprovalView } from "@/types";
import { apiClient } from "@/services/api";
import {
  ShieldAlert,
  CheckCircle2,
  XCircle,
  Clock,
  UserCheck,
  MessageSquare,
} from "lucide-react";

interface ApprovalActionCardProps {
  approval: ApprovalView | null;
  onDecided?: (decision: "APPROVED" | "REJECTED") => void;
  className?: string;
}

export function ApprovalActionCard({
  approval,
  onDecided,
  className = "",
}: ApprovalActionCardProps) {
  const [reviewer, setReviewer] = useState("SecOpsLead");
  const [reason, setReason] = useState("经 JGit Diff 与安全策略审查，批准工作区变更合并与沙箱运行");
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [submittedStatus, setSubmittedStatus] = useState<"APPROVED" | "REJECTED" | null>(null);

  if (!approval || approval.status !== "PENDING") {
    return null;
  }

  const handleDecision = async (decision: "APPROVED" | "REJECTED") => {
    setIsSubmitting(true);
    try {
      if (decision === "APPROVED") {
        await apiClient.approve(approval.id, reviewer, reason);
      } else {
        await apiClient.reject(approval.id, reviewer, reason);
      }
      setSubmittedStatus(decision);
      onDecided?.(decision);
    } catch (err) {
      console.error("Decision failed:", err);
    } finally {
      setIsSubmitting(false);
    }
  };

  if (submittedStatus) {
    return (
      <div
        className={`rounded-2xl border p-4 backdrop-blur-md transition-all ${
          submittedStatus === "APPROVED"
            ? "border-emerald-500/40 bg-emerald-950/20 text-emerald-200"
            : "border-rose-500/40 bg-rose-950/20 text-rose-200"
        } ${className}`}
      >
        <div className="flex items-center space-x-2 text-sm font-semibold">
          {submittedStatus === "APPROVED" ? (
            <CheckCircle2 className="w-4 h-4 text-emerald-400" />
          ) : (
            <XCircle className="w-4 h-4 text-rose-400" />
          )}
          <span>
            {submittedStatus === "APPROVED"
              ? "审批已通过：门禁解除，流水线已恢复执行！"
              : "审批已驳回：任务已被安全终止。"}
          </span>
        </div>
        <p className="mt-1 text-xs text-zinc-400 font-mono">
          决策人: {reviewer} | 决策单号: {approval.id}
        </p>
      </div>
    );
  }

  return (
    <div
      className={`relative overflow-hidden rounded-2xl border border-amber-500/40 bg-zinc-950/90 p-4 shadow-xl shadow-amber-500/5 backdrop-blur-md ring-1 ring-amber-500/30 transition-all ${className}`}
    >
      {/* Specular glow */}
      <div className="pointer-events-none absolute inset-x-0 top-0 h-0.5 bg-gradient-to-r from-transparent via-amber-400/50 to-transparent" />

      {/* Header */}
      <div className="flex items-start justify-between gap-3">
        <div className="flex items-center space-x-2.5">
          <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-amber-500/20 border border-amber-500/40 text-amber-400">
            <ShieldAlert className="w-4 h-4 animate-pulse" />
          </div>
          <div>
            <div className="flex items-center space-x-2">
              <h4 className="text-xs font-semibold text-zinc-100">
                人工审批挂起 (Human-in-the-Loop Approval Gate)
              </h4>
              <span className="rounded-full bg-amber-500/20 px-2 py-0.5 text-[10px] font-mono font-semibold text-amber-300 border border-amber-500/30">
                WAITING_APPROVAL
              </span>
            </div>
            <p className="text-[11px] text-zinc-400 mt-0.5 font-mono">
              Approval ID: {approval.id} | Step: {approval.stepRunId}
            </p>
          </div>
        </div>

        <div className="text-right text-[11px] font-mono text-zinc-500 shrink-0">
          发起方: <span className="text-zinc-300">{approval.requestedBy}</span>
        </div>
      </div>

      {/* Summary message */}
      <div className="mt-3 rounded-xl border border-zinc-800/80 bg-zinc-900/60 p-2.5 text-xs text-zinc-300">
        <div className="flex items-center space-x-1.5 text-zinc-400 font-medium mb-1">
          <MessageSquare className="w-3.5 h-3.5 text-amber-400" />
          <span>待审批事项说明:</span>
        </div>
        <p className="text-[11px] leading-relaxed text-zinc-200">
          {approval.comments || "JGit 工作区代码变更已就绪，请求安全准入许可。"}
        </p>
      </div>

      {/* Input section: Reviewer & Reason */}
      <div className="mt-3 grid grid-cols-1 md:grid-cols-3 gap-2.5">
        <div className="md:col-span-1">
          <label className="block text-[11px] font-medium text-zinc-400 mb-1 flex items-center space-x-1">
            <UserCheck className="w-3 h-3 text-zinc-500" />
            <span>审查人身份</span>
          </label>
          <input
            type="text"
            value={reviewer}
            onChange={(e) => setReviewer(e.target.value)}
            className="w-full rounded-lg border border-zinc-800 bg-zinc-900/90 px-2.5 py-1.5 text-xs text-zinc-200 focus:border-amber-500 focus:outline-none font-mono"
            placeholder="Reviewer ID"
          />
        </div>

        <div className="md:col-span-2">
          <label className="block text-[11px] font-medium text-zinc-400 mb-1">
            审查决策意见 (Audit Trail Remarks)
          </label>
          <input
            type="text"
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            className="w-full rounded-lg border border-zinc-800 bg-zinc-900/90 px-2.5 py-1.5 text-xs text-zinc-200 focus:border-amber-500 focus:outline-none"
            placeholder="输入决策理由..."
          />
        </div>
      </div>

      {/* Action Buttons: Approve / Reject */}
      <div className="mt-3.5 flex items-center justify-end space-x-2 pt-2 border-t border-zinc-800/60">
        <button
          onClick={() => handleDecision("REJECTED")}
          disabled={isSubmitting}
          className="flex items-center space-x-1.5 rounded-lg border border-rose-500/30 bg-rose-950/30 px-3.5 py-1.5 text-xs font-medium text-rose-300 hover:bg-rose-950/60 transition disabled:opacity-50"
        >
          {isSubmitting ? <Clock className="w-3.5 h-3.5 animate-spin" /> : <XCircle className="w-3.5 h-3.5" />}
          <span>驳回决策 (Reject)</span>
        </button>

        <button
          onClick={() => handleDecision("APPROVED")}
          disabled={isSubmitting}
          className="flex items-center space-x-1.5 rounded-lg bg-emerald-600 px-4 py-1.5 text-xs font-semibold text-white shadow-lg shadow-emerald-600/20 hover:bg-emerald-500 transition disabled:opacity-50"
        >
          {isSubmitting ? <Clock className="w-3.5 h-3.5 animate-spin" /> : <CheckCircle2 className="w-3.5 h-3.5" />}
          <span>批准放行 (Approve)</span>
        </button>
      </div>
    </div>
  );
}
