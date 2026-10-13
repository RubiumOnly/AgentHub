"use client";

import React, { useState } from "react";
import {
  WorkflowTemplateType,
  OrchestrationPlanView,
} from "@/types";
import { workflowsApi } from "@/services/api";
import {
  X,
  Loader2,
  Sparkles,
  ShieldCheck,
  ShieldAlert,
  FileCode2,
  CheckCircle2,
  ListTodo,
  Coins,
  Clock,
  ArrowRight,
  Layers,
  Bug,
  PlusCircle,
  AlertTriangle,
} from "lucide-react";

export interface OrchestratorPlanModalProps {
  isOpen: boolean;
  onClose: () => void;
  projectId: string;
  workspaceId: string;
  onPlanConfirmedAndLaunch: (plan: OrchestrationPlanView) => Promise<void>;
}

export function OrchestratorPlanModal({
  isOpen,
  onClose,
  projectId,
  workspaceId,
  onPlanConfirmedAndLaunch,
}: OrchestratorPlanModalProps) {
  const [templateType, setTemplateType] = useState<WorkflowTemplateType>("BUG_FIX_WITH_TESTS");
  const [taskGoal, setTaskGoal] = useState<string>(
    "修复订单取消状态转换时的空指针异常并提供测试"
  );
  const [plan, setPlan] = useState<OrchestrationPlanView | null>(null);
  const [allowedTools, setAllowedTools] = useState<Record<string, boolean>>({
    file_write: true,
    command_exec: true,
    file_read: true,
    code_search: true,
    test_runner: true,
  });

  const [isGenerating, setIsGenerating] = useState(false);
  const [isConfirming, setIsConfirming] = useState(false);
  const [isRejecting, setIsRejecting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [step, setStep] = useState<"CONFIGURE" | "REVIEW">("CONFIGURE");

  if (!isOpen) return null;

  const handleSelectTemplate = (type: WorkflowTemplateType) => {
    setTemplateType(type);
    if (type === "BUG_FIX_WITH_TESTS") {
      setTaskGoal("修复订单取消状态转换时的空指针异常并提供防御性单元测试");
    } else {
      setTaskGoal("新增工作区配额审计与超额告警 REST 端点及集成测试");
    }
  };

  const handleGeneratePlan = async () => {
    if (!taskGoal.trim()) {
      setErrorMessage("请输入清晰明确的研发任务目标");
      return;
    }
    setErrorMessage(null);
    setIsGenerating(true);

    try {
      const generated = await workflowsApi.generatePlan({
        projectId: projectId || "default-proj",
        workspaceId: workspaceId || "default-ws",
        templateType,
        taskGoal: taskGoal.trim(),
      });
      setPlan(generated);
      // 初始化工具权限勾选
      const toolsMap: Record<string, boolean> = {};
      generated.toolPermissions.forEach((t) => {
        toolsMap[t.toolName] = true;
      });
      setAllowedTools(toolsMap);
      setStep("REVIEW");
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : "生成计划请求失败";
      setErrorMessage(`生成 Orchestrator 计划失败: ${msg}`);
    } finally {
      setIsGenerating(false);
    }
  };

  const handleToggleTool = (toolName: string) => {
    setAllowedTools((prev) => ({
      ...prev,
      [toolName]: !prev[toolName],
    }));
  };

  const handleConfirmAndLaunch = async () => {
    if (!plan) return;
    setErrorMessage(null);
    setIsConfirming(true);

    try {
      const confirmedTools = Object.entries(allowedTools)
        .filter(([, allowed]) => allowed)
        .map(([name]) => name);

      const confirmedPlan = await workflowsApi.confirmPlan(plan.planId, {
        confirmed: true,
        allowedHighImpactTools: confirmedTools,
        decisionNote: "Plan confirmed via UI modal with authorized tools",
      });

      setPlan(confirmedPlan);
      await onPlanConfirmedAndLaunch(confirmedPlan);
      onClose();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : "确认启动失败";
      setErrorMessage(`确认计划失败: ${msg}`);
    } finally {
      setIsConfirming(false);
    }
  };

  const handleRejectPlan = async () => {
    if (!plan) return;
    setErrorMessage(null);
    setIsRejecting(true);

    try {
      await workflowsApi.rejectPlan(plan.planId, "User rejected plan in modal");
      setPlan(null);
      setStep("CONFIGURE");
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : "驳回计划失败";
      setErrorMessage(`驳回计划失败: ${msg}`);
    } finally {
      setIsRejecting(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm p-4 overflow-y-auto">
      <div className="relative w-full max-w-3xl rounded-2xl bg-zinc-900 border border-zinc-800 shadow-2xl text-zinc-100 p-6 my-8 flex flex-col max-h-[90vh]">
        {/* Header */}
        <div className="flex items-center justify-between pb-4 border-b border-zinc-800">
          <div className="flex items-center gap-3">
            <div className="p-2.5 rounded-xl bg-indigo-500/10 text-indigo-400 border border-indigo-500/20">
              <Sparkles className="w-5 h-5" />
            </div>
            <div>
              <h2 className="text-lg font-semibold tracking-tight text-white flex items-center gap-2">
                Orchestrator 任务规划与门禁确权
                <span className="text-xs px-2 py-0.5 rounded-full font-normal bg-indigo-500/20 text-indigo-300 border border-indigo-500/30">
                  Phase 3
                </span>
              </h2>
              <p className="text-xs text-zinc-400 mt-0.5">
                版本化 7 阶段 Kahn DAG 编排、上下文预算锁定与高影响工具确权门禁
              </p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="p-2 rounded-lg text-zinc-400 hover:text-white hover:bg-zinc-800 transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Error Alert */}
        {errorMessage && (
          <div className="mt-4 p-3 rounded-lg bg-red-500/10 border border-red-500/30 text-red-300 text-xs flex items-center gap-2">
            <AlertTriangle className="w-4 h-4 shrink-0" />
            <span>{errorMessage}</span>
          </div>
        )}

        {/* Content Body */}
        <div className="flex-1 overflow-y-auto py-4 space-y-5 pr-1">
          {step === "CONFIGURE" ? (
            <>
              {/* Template Selection */}
              <div>
                <label className="block text-xs font-medium text-zinc-300 mb-2">
                  选择工作流模板 (Workflow Template)
                </label>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                  {/* Option 1: BUG_FIX_WITH_TESTS */}
                  <div
                    onClick={() => handleSelectTemplate("BUG_FIX_WITH_TESTS")}
                    className={`cursor-pointer p-4 rounded-xl border transition-all ${
                      templateType === "BUG_FIX_WITH_TESTS"
                        ? "bg-indigo-950/40 border-indigo-500/50 shadow-sm shadow-indigo-500/10"
                        : "bg-zinc-800/40 border-zinc-800 hover:border-zinc-700"
                    }`}
                  >
                    <div className="flex items-center justify-between mb-2">
                      <div className="flex items-center gap-2 text-sm font-medium text-white">
                        <Bug className="w-4 h-4 text-amber-400" />
                        <span>缺陷修复 (BUG_FIX_WITH_TESTS)</span>
                      </div>
                      {templateType === "BUG_FIX_WITH_TESTS" && (
                        <CheckCircle2 className="w-4 h-4 text-indigo-400" />
                      )}
                    </div>
                    <p className="text-xs text-zinc-400 leading-relaxed">
                      7 阶段 Kahn DAG：从需求澄清、Plan 生成、测试先行、修复编码、架构审查到人工审批。
                    </p>
                    <div className="mt-3 flex items-center gap-1.5 flex-wrap">
                      <span className="text-[10px] px-2 py-0.5 rounded bg-zinc-800 text-zinc-300">
                        7 阶段拓扑
                      </span>
                      <span className="text-[10px] px-2 py-0.5 rounded bg-zinc-800 text-zinc-300">
                        4 类 Agent 角色
                      </span>
                      <span className="text-[10px] px-2 py-0.5 rounded bg-amber-500/20 text-amber-300">
                        含审批门禁
                      </span>
                    </div>
                  </div>

                  {/* Option 2: NEW_FEATURE_ENDPOINT */}
                  <div
                    onClick={() => handleSelectTemplate("NEW_FEATURE_ENDPOINT")}
                    className={`cursor-pointer p-4 rounded-xl border transition-all ${
                      templateType === "NEW_FEATURE_ENDPOINT"
                        ? "bg-indigo-950/40 border-indigo-500/50 shadow-sm shadow-indigo-500/10"
                        : "bg-zinc-800/40 border-zinc-800 hover:border-zinc-700"
                    }`}
                  >
                    <div className="flex items-center justify-between mb-2">
                      <div className="flex items-center gap-2 text-sm font-medium text-white">
                        <PlusCircle className="w-4 h-4 text-emerald-400" />
                        <span>端点新增 (NEW_FEATURE_ENDPOINT)</span>
                      </div>
                      {templateType === "NEW_FEATURE_ENDPOINT" && (
                        <CheckCircle2 className="w-4 h-4 text-indigo-400" />
                      )}
                    </div>
                    <p className="text-xs text-zinc-400 leading-relaxed">
                      7 阶段 Kahn DAG：DDD 领域设计、OpenAPI 规范、Controller/Service 编写与集成测试。
                    </p>
                    <div className="mt-3 flex items-center gap-1.5 flex-wrap">
                      <span className="text-[10px] px-2 py-0.5 rounded bg-zinc-800 text-zinc-300">
                        7 阶段拓扑
                      </span>
                      <span className="text-[10px] px-2 py-0.5 rounded bg-zinc-800 text-zinc-300">
                        DDD 四层分层
                      </span>
                      <span className="text-[10px] px-2 py-0.5 rounded bg-indigo-500/20 text-indigo-300">
                        端点交付
                      </span>
                    </div>
                  </div>
                </div>
              </div>

              {/* Task Goal Input */}
              <div>
                <label className="block text-xs font-medium text-zinc-300 mb-2">
                  任务目标描述 (Task Goal)
                </label>
                <textarea
                  value={taskGoal}
                  onChange={(e) => setTaskGoal(e.target.value)}
                  rows={3}
                  placeholder="描述本次研发变更的具体诉求、受影响业务或修复边界..."
                  className="w-full px-3.5 py-2.5 rounded-xl bg-zinc-800/60 border border-zinc-700/60 text-white placeholder-zinc-500 text-sm focus:outline-none focus:border-indigo-500 transition-colors"
                />
              </div>

              {/* Info Banner */}
              <div className="p-3.5 rounded-xl bg-zinc-800/40 border border-zinc-800 flex items-start gap-3">
                <ShieldCheck className="w-5 h-5 text-indigo-400 shrink-0 mt-0.5" />
                <div className="text-xs text-zinc-400 space-y-1">
                  <p className="text-zinc-300 font-medium">Orchestrator 计划治理规范 (AGENTS.md)</p>
                  <p>
                    在执行写文件或系统命令前，Orchestrator 必须先根据任务生成结构化 Plan，
                    冻结上下文配额并等待确认。未经确认前，任何高影响工具调用均会被安全门禁直接拦截。
                  </p>
                </div>
              </div>
            </>
          ) : (
            <>
              {/* REVIEW STEP: Display Generated Plan */}
              {plan && (
                <div className="space-y-4">
                  {/* Plan Overview Card */}
                  <div className="p-4 rounded-xl bg-zinc-800/50 border border-zinc-700/60 flex items-center justify-between">
                    <div>
                      <div className="flex items-center gap-2">
                        <span className="font-mono text-sm text-indigo-300 font-medium">
                          {plan.planId}
                        </span>
                        <span
                          className={`text-[10px] px-2 py-0.5 rounded-full font-medium ${
                            plan.status === "CONFIRMED"
                              ? "bg-emerald-500/20 text-emerald-400 border border-emerald-500/30"
                              : "bg-amber-500/20 text-amber-300 border border-amber-500/30"
                          }`}
                        >
                          {plan.status}
                        </span>
                      </div>
                      <p className="text-xs text-zinc-300 mt-1">{plan.taskGoal}</p>
                    </div>
                    <div className="text-right">
                      <span className="text-[11px] text-zinc-400">模板类型</span>
                      <p className="text-xs font-medium text-white">{plan.templateType}</p>
                    </div>
                  </div>

                  {/* Bento Grid: Scope & Subtasks */}
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                    {/* Affected Files */}
                    <div className="p-3.5 rounded-xl bg-zinc-800/30 border border-zinc-800 space-y-2">
                      <div className="flex items-center gap-2 text-xs font-medium text-zinc-300">
                        <FileCode2 className="w-4 h-4 text-indigo-400" />
                        <span>预测受影响文件 ({plan.affectedFiles.length})</span>
                      </div>
                      <ul className="space-y-1.5">
                        {plan.affectedFiles.map((file, idx) => (
                          <li
                            key={idx}
                            className="text-xs font-mono text-zinc-300 bg-zinc-900/60 px-2.5 py-1 rounded border border-zinc-800/60 truncate"
                          >
                            {file}
                          </li>
                        ))}
                      </ul>
                    </div>

                    {/* Subtasks */}
                    <div className="p-3.5 rounded-xl bg-zinc-800/30 border border-zinc-800 space-y-2">
                      <div className="flex items-center gap-2 text-xs font-medium text-zinc-300">
                        <ListTodo className="w-4 h-4 text-indigo-400" />
                        <span>执行子任务编排 ({plan.subtasks.length})</span>
                      </div>
                      <ul className="space-y-1.5">
                        {plan.subtasks.map((task, idx) => (
                          <li
                            key={idx}
                            className="text-xs text-zinc-300 flex items-center gap-2 bg-zinc-900/60 px-2.5 py-1 rounded border border-zinc-800/60"
                          >
                            <span className="text-[10px] text-zinc-500 font-mono">0{idx + 1}</span>
                            <span className="truncate">{task}</span>
                          </li>
                        ))}
                      </ul>
                    </div>
                  </div>

                  {/* Budget & Quality Gates */}
                  <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                    <div className="p-3 rounded-xl bg-zinc-800/30 border border-zinc-800 flex items-center gap-3">
                      <Coins className="w-4 h-4 text-amber-400 shrink-0" />
                      <div>
                        <span className="text-[10px] text-zinc-400">Token 预算配额</span>
                        <p className="text-xs font-semibold text-white">
                          {plan.budget?.maxTokens?.toLocaleString() || 32000} Tokens
                        </p>
                      </div>
                    </div>

                    <div className="p-3 rounded-xl bg-zinc-800/30 border border-zinc-800 flex items-center gap-3">
                      <Clock className="w-4 h-4 text-sky-400 shrink-0" />
                      <div>
                        <span className="text-[10px] text-zinc-400">超时看门狗上限</span>
                        <p className="text-xs font-semibold text-white">
                          {plan.budget?.timeoutSeconds || 600} 秒
                        </p>
                      </div>
                    </div>

                    <div className="p-3 rounded-xl bg-zinc-800/30 border border-zinc-800 flex items-center gap-3">
                      <Layers className="w-4 h-4 text-emerald-400 shrink-0" />
                      <div>
                        <span className="text-[10px] text-zinc-400">成本约束上限</span>
                        <p className="text-xs font-semibold text-white">
                          ${plan.budget?.costUsdUpperLimit?.toFixed(2) || "0.48"} USD
                        </p>
                      </div>
                    </div>
                  </div>

                  {/* Quality Gates */}
                  <div className="p-3.5 rounded-xl bg-zinc-800/30 border border-zinc-800 space-y-2">
                    <div className="flex items-center gap-2 text-xs font-medium text-zinc-300">
                      <ShieldCheck className="w-4 h-4 text-emerald-400" />
                      <span>强制交付质量门禁</span>
                    </div>
                    <div className="flex flex-wrap gap-2">
                      {plan.qualityGates.map((gate, idx) => (
                        <span
                          key={idx}
                          className="text-[11px] px-2.5 py-1 rounded-md bg-emerald-500/10 text-emerald-300 border border-emerald-500/20 font-mono"
                        >
                          {gate}
                        </span>
                      ))}
                    </div>
                  </div>

                  {/* High-Impact Tool Confirmation Gate */}
                  <div className="p-4 rounded-xl bg-amber-500/5 border border-amber-500/20 space-y-3">
                    <div className="flex items-start gap-2.5">
                      <ShieldAlert className="w-5 h-5 text-amber-400 shrink-0 mt-0.5" />
                      <div>
                        <h4 className="text-xs font-semibold text-amber-200">
                          高影响工具授权与确权 (Tool Gate)
                        </h4>
                        <p className="text-[11px] text-zinc-400 mt-0.5">
                          只有勾选授权的高影响工具才可在本次 Run 中由 Agent 节点调用；未勾选或未授权将被内核阻断。
                        </p>
                      </div>
                    </div>

                    <div className="space-y-2 pt-1">
                      {plan.toolPermissions.map((t) => (
                        <div
                          key={t.toolName}
                          onClick={() => handleToggleTool(t.toolName)}
                          className="flex items-center justify-between p-2.5 rounded-lg bg-zinc-900/60 border border-zinc-800 cursor-pointer hover:border-zinc-700 transition-colors"
                        >
                          <div className="flex items-center gap-2.5">
                            <input
                              type="checkbox"
                              checked={Boolean(allowedTools[t.toolName])}
                              onChange={() => {}}
                              className="rounded border-zinc-700 text-indigo-600 focus:ring-0 cursor-pointer"
                            />
                            <div>
                              <div className="flex items-center gap-2">
                                <span className="font-mono text-xs text-white font-medium">
                                  {t.toolName}
                                </span>
                                {t.highImpact ? (
                                  <span className="text-[10px] px-1.5 py-0.5 rounded bg-rose-500/20 text-rose-300 border border-rose-500/30">
                                    高影响工具
                                  </span>
                                ) : (
                                  <span className="text-[10px] px-1.5 py-0.5 rounded bg-zinc-700 text-zinc-300">
                                    低影响
                                  </span>
                                )}
                              </div>
                              <p className="text-[11px] text-zinc-400 mt-0.5">{t.purpose}</p>
                            </div>
                          </div>
                          <span className="text-[10px] text-zinc-500 font-mono">
                            {t.category}
                          </span>
                        </div>
                      ))}
                    </div>
                  </div>
                </div>
              )}
            </>
          )}
        </div>

        {/* Modal Footer */}
        <div className="flex items-center justify-between pt-4 border-t border-zinc-800">
          {step === "CONFIGURE" ? (
            <>
              <button
                type="button"
                onClick={onClose}
                className="px-4 py-2 rounded-xl text-xs font-medium text-zinc-400 hover:text-white hover:bg-zinc-800 transition-colors"
              >
                取消
              </button>
              <button
                type="button"
                onClick={handleGeneratePlan}
                disabled={isGenerating}
                className="flex items-center gap-2 px-5 py-2 rounded-xl text-xs font-medium bg-indigo-600 text-white hover:bg-indigo-500 disabled:opacity-50 transition-colors shadow-lg shadow-indigo-600/20"
              >
                {isGenerating ? (
                  <>
                    <Loader2 className="w-3.5 h-3.5 animate-spin" />
                    <span>Orchestrator 规划中...</span>
                  </>
                ) : (
                  <>
                    <span>生成执行计划 (Generate Plan)</span>
                    <ArrowRight className="w-3.5 h-3.5" />
                  </>
                )}
              </button>
            </>
          ) : (
            <>
              <div className="flex items-center gap-2">
                <button
                  type="button"
                  onClick={() => setStep("CONFIGURE")}
                  className="px-3.5 py-2 rounded-xl text-xs font-medium text-zinc-400 hover:text-white hover:bg-zinc-800 transition-colors"
                >
                  返回重配
                </button>
                <button
                  type="button"
                  onClick={handleRejectPlan}
                  disabled={isRejecting}
                  className="px-3.5 py-2 rounded-xl text-xs font-medium text-rose-400 hover:bg-rose-500/10 transition-colors"
                >
                  {isRejecting ? "正在驳回..." : "驳回计划"}
                </button>
              </div>
              <button
                type="button"
                onClick={handleConfirmAndLaunch}
                disabled={isConfirming}
                className="flex items-center gap-2 px-5 py-2 rounded-xl text-xs font-medium bg-emerald-600 text-white hover:bg-emerald-500 disabled:opacity-50 transition-colors shadow-lg shadow-emerald-600/20"
              >
                {isConfirming ? (
                  <>
                    <Loader2 className="w-3.5 h-3.5 animate-spin" />
                    <span>确认中并启动...</span>
                  </>
                ) : (
                  <>
                    <CheckCircle2 className="w-3.5 h-3.5" />
                    <span>确认计划并启动执行 (Confirm & Launch)</span>
                  </>
                )}
              </button>
            </>
          )}
        </div>
      </div>
    </div>
  );
}
