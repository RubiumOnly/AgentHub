"use client";

import React, { useState } from "react";
import { apiClient } from "@/services/api";
import {
  GitSourceConfig,
  GitSourceType,
  OnboardingSessionView,
  OnboardingStep,
  ProjectView,
} from "@/types";
import {
  FolderPlus,
  FolderGit2,
  X,
  Loader2,
  FileText,
  AlertCircle,
  Sparkles,
  CheckCircle2,
  ArrowRight,
  RotateCcw,
  Ban,
  ShieldCheck,
  Cpu,
  FileCode2,
  Terminal,
  Check,
  ShieldAlert,
} from "lucide-react";

export interface ProjectOnboardingModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSuccess: (project: ProjectView) => void;
}

const STEP_LABELS: Record<OnboardingStep, { title: string; desc: string }> = {
  CONNECT_SOURCE: { title: "代码源连接", desc: "配置本地或远端受控 Git 仓库源" },
  VERIFY_PERMISSIONS: { title: "凭据权限校验", desc: "验证仓库读写权限与脱敏凭据" },
  SELECT_DEFAULT_BRANCH: { title: "默认分支选择", desc: "确认主分支并创建隔离特性分支" },
  CREATE_WORKSPACE: { title: "受控工作区分配", desc: "初始化受控目录、JGit 隔离与配额策略" },
  SCAN_ENVIRONMENT: { title: "技术栈环境扫描", desc: "探测语言、构建体系、测试框架与安全边界" },
  GENERATE_PROJECT_RULES: { title: "工程规约生成", desc: "生成并确权 PROJECT_RULES 领域防御规范" },
  CREATE_INITIAL_BASELINE: { title: "初始基线确权", desc: "建立受控 Git 初始快照与 Context Source" },
};

const STEPS_ORDER: OnboardingStep[] = [
  "CONNECT_SOURCE",
  "VERIFY_PERMISSIONS",
  "SELECT_DEFAULT_BRANCH",
  "CREATE_WORKSPACE",
  "SCAN_ENVIRONMENT",
  "GENERATE_PROJECT_RULES",
  "CREATE_INITIAL_BASELINE",
];

export function ProjectOnboardingModal({
  isOpen,
  onClose,
  onSuccess,
}: ProjectOnboardingModalProps) {
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [sourceType, setSourceType] = useState<GitSourceType>("LOCAL_DIR");
  const [sourcePath, setSourcePath] = useState("");
  const [secretRef, setSecretRef] = useState("");
  const [defaultBranch, setDefaultBranch] = useState("main");
  const [customRules, setCustomRules] = useState("");

  const [session, setSession] = useState<OnboardingSessionView | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  if (!isOpen) return null;

  const currentStep = session?.currentStep || "CONNECT_SOURCE";
  const currentStepIdx = session
    ? STEPS_ORDER.indexOf(currentStep)
    : 0;

  // 启动向导 (Step 1)
  const handleStartOnboarding = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!name.trim()) {
      setErrorMessage("请填写项目名称");
      return;
    }
    if (!sourcePath.trim()) {
      setErrorMessage(sourceType === "LOCAL_DIR" ? "请填写本地目录相对路径" : "请填写 Git 远程仓库 URL");
      return;
    }

    if (secretRef.trim().startsWith("ghp_") || secretRef.trim().startsWith("github_pat_")) {
      setErrorMessage("凭据安全拦截：禁止在 secretRef 填入明文 GitHub Token，请使用受控凭据别名（如 secret-github-team）");
      return;
    }

    setIsLoading(true);
    setErrorMessage(null);

    const sourceConfig: GitSourceConfig = {
      sourceType,
      localPath: sourceType === "LOCAL_DIR" ? sourcePath.trim() : undefined,
      location: sourceType === "LOCAL_DIR" ? sourcePath.trim() : undefined,
      remoteUrl: sourceType === "REMOTE_GIT" ? sourcePath.trim() : undefined,
      repoUrl: sourceType === "REMOTE_GIT" ? sourcePath.trim() : undefined,
      secretRef: secretRef.trim() || undefined,
      defaultBranch: defaultBranch.trim() || "main",
    };

    try {
      const res = await apiClient.startOnboarding({
        projectName: name.trim(),
        description: description.trim() || undefined,
        sourceConfig,
      });
      setSession(res);
      if (res.generatedRules) {
        setCustomRules(res.generatedRules);
      }
    } catch (err: any) {
      setErrorMessage(err.message || "启动项目接入向导失败");
    } finally {
      setIsLoading(false);
    }
  };

  // 执行向导下一步
  const handleNextStep = async () => {
    if (!session) return;
    setIsLoading(true);
    setErrorMessage(null);

    try {
      const cmd: {
        step: OnboardingStep;
        selectedBranch?: string;
        customRules?: string;
      } = {
        step: session.currentStep,
      };

      if (session.currentStep === "SELECT_DEFAULT_BRANCH") {
        cmd.selectedBranch = defaultBranch.trim() || "main";
      } else if (session.currentStep === "GENERATE_PROJECT_RULES") {
        cmd.customRules = customRules.trim() || undefined;
      }

      const updated = await apiClient.executeOnboardingStep(session.sessionId, cmd);
      setSession(updated);
      if (updated.generatedRules && !customRules) {
        setCustomRules(updated.generatedRules);
      }
    } catch (err: any) {
      setErrorMessage(err.message || "执行当前接入步骤失败");
    } finally {
      setIsLoading(false);
    }
  };

  // 重试当前步骤
  const handleRetry = async () => {
    if (!session) return;
    setIsLoading(true);
    setErrorMessage(null);
    try {
      const updated = await apiClient.retryOnboardingStep(session.sessionId, session.currentStep);
      setSession(updated);
    } catch (err: any) {
      setErrorMessage(err.message || "重试失败");
    } finally {
      setIsLoading(false);
    }
  };

  // 中止向导
  const handleAbort = async () => {
    if (!session) {
      onClose();
      return;
    }
    setIsLoading(true);
    try {
      await apiClient.abortOnboarding(session.sessionId);
    } catch {
      // ignore abort error
    } finally {
      setIsLoading(false);
      onClose();
    }
  };

  // 完成向导
  const handleComplete = async () => {
    if (!session) return;
    setIsLoading(true);
    setErrorMessage(null);
    try {
      const project = await apiClient.completeOnboarding(session.sessionId);
      onSuccess(project);
      onClose();
    } catch (err: any) {
      setErrorMessage(err.message || "完成接入并建立项目失败");
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-md animate-in fade-in duration-200">
      <div className="relative w-full max-w-4xl max-h-[90vh] flex flex-col rounded-2xl border border-zinc-800 bg-zinc-950 p-6 shadow-2xl ring-1 ring-zinc-700/50 backdrop-blur-xl overflow-hidden">
        {/* Glow accent */}
        <div className="pointer-events-none absolute inset-x-0 -top-px h-1 bg-gradient-to-r from-transparent via-indigo-500/80 to-transparent" />

        {/* Header */}
        <div className="flex items-center justify-between pb-4 border-b border-zinc-800/80">
          <div className="flex items-center space-x-3">
            <div className="w-10 h-10 rounded-xl bg-indigo-600/20 border border-indigo-500/30 flex items-center justify-center text-indigo-400 shadow-inner">
              <FolderGit2 className="w-5 h-5" />
            </div>
            <div>
              <div className="flex items-center space-x-2">
                <h3 className="text-base font-bold text-zinc-100 tracking-tight">
                  受控项目接入向导 (7-Step Controlled Onboarding)
                </h3>
                {session && (
                  <span className="px-2 py-0.5 rounded text-[10px] font-mono bg-indigo-950 text-indigo-300 border border-indigo-800">
                    {session.sessionId}
                  </span>
                )}
              </div>
              <p className="text-xs text-zinc-400 font-mono mt-0.5">
                环境扫描 · 凭据脱敏 · 隔离工作区 · 工程规约 · 初始 Git 基线确权
              </p>
            </div>
          </div>
          <button
            onClick={session ? handleAbort : onClose}
            className="p-1.5 rounded-lg text-zinc-400 hover:text-zinc-200 hover:bg-zinc-800/80 transition-colors"
            title="关闭向导"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        {/* Step Progress Bar (Bento Grid Style) */}
        <div className="py-4 border-b border-zinc-800/80">
          <div className="grid grid-cols-7 gap-2">
            {STEPS_ORDER.map((stepKey, idx) => {
              const isDone = session ? (session.status === "COMPLETED" || idx < currentStepIdx) : false;
              const isCurrent = session ? (session.status !== "COMPLETED" && idx === currentStepIdx) : idx === 0;
              const isFailed = session?.status === "FAILED" && isCurrent;

              return (
                <div
                  key={stepKey}
                  className={`p-2 rounded-xl border flex flex-col justify-between transition-all ${
                    isDone
                      ? "bg-emerald-950/20 border-emerald-500/30 text-emerald-400"
                      : isFailed
                      ? "bg-rose-950/30 border-rose-500/40 text-rose-300"
                      : isCurrent
                      ? "bg-indigo-950/40 border-indigo-500/50 text-indigo-300 shadow-sm shadow-indigo-500/10"
                      : "bg-zinc-900/30 border-zinc-800/50 text-zinc-500"
                  }`}
                >
                  <div className="flex items-center justify-between text-[10px] font-mono font-medium">
                    <span>0{idx + 1}</span>
                    {isDone ? (
                      <Check className="w-3 h-3 text-emerald-400" />
                    ) : isCurrent ? (
                      isLoading ? (
                        <Loader2 className="w-3 h-3 animate-spin text-indigo-400" />
                      ) : (
                        <div className="w-2 h-2 rounded-full bg-indigo-400 animate-pulse" />
                      )
                    ) : null}
                  </div>
                  <div className="text-[11px] font-semibold tracking-tight mt-1 truncate">
                    {STEP_LABELS[stepKey].title}
                  </div>
                </div>
              );
            })}
          </div>
        </div>

        {/* Error Banner */}
        {errorMessage && (
          <div className="my-3 flex items-center space-x-2 rounded-xl border border-rose-500/40 bg-rose-950/40 p-3 text-xs text-rose-300">
            <AlertCircle className="w-4 h-4 shrink-0 text-rose-400" />
            <span className="flex-1 font-mono">{errorMessage}</span>
          </div>
        )}

        {/* Content Area */}
        <div className="flex-1 overflow-y-auto py-4 space-y-4 pr-1">
          {!session ? (
            /* Step 1 Form */
            <form onSubmit={handleStartOnboarding} className="space-y-4">
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs font-medium text-zinc-300 mb-1">
                    项目名称 (Project Name) <span className="text-rose-400">*</span>
                  </label>
                  <input
                    type="text"
                    required
                    value={name}
                    onChange={(e) => setName(e.target.value)}
                    placeholder="例如: customer-billing-service"
                    className="w-full px-3 py-2 rounded-xl border border-zinc-800 bg-zinc-900/90 text-xs text-zinc-100 placeholder-zinc-500 focus:outline-none focus:border-indigo-500 font-mono"
                  />
                </div>
                <div>
                  <label className="block text-xs font-medium text-zinc-300 mb-1">
                    代码源类型 (Source Type)
                  </label>
                  <div className="grid grid-cols-2 gap-2">
                    <button
                      type="button"
                      onClick={() => setSourceType("LOCAL_DIR")}
                      className={`px-3 py-2 rounded-xl text-xs font-medium border text-center transition ${
                        sourceType === "LOCAL_DIR"
                          ? "bg-indigo-600/20 border-indigo-500 text-indigo-300"
                          : "bg-zinc-900/60 border-zinc-800 text-zinc-400 hover:text-zinc-200"
                      }`}
                    >
                      本地目录 (Local Dir)
                    </button>
                    <button
                      type="button"
                      onClick={() => setSourceType("REMOTE_GIT")}
                      className={`px-3 py-2 rounded-xl text-xs font-medium border text-center transition ${
                        sourceType === "REMOTE_GIT"
                          ? "bg-indigo-600/20 border-indigo-500 text-indigo-300"
                          : "bg-zinc-900/60 border-zinc-800 text-zinc-400 hover:text-zinc-200"
                      }`}
                    >
                      远端 Git (Remote Git)
                    </button>
                  </div>
                </div>
              </div>

              <div>
                <label className="block text-xs font-medium text-zinc-300 mb-1">
                  {sourceType === "LOCAL_DIR" ? "本地相对路径 (Local Path)" : "远程 Git URL"} <span className="text-rose-400">*</span>
                </label>
                <input
                  type="text"
                  required
                  value={sourcePath}
                  onChange={(e) => setSourcePath(e.target.value)}
                  placeholder={
                    sourceType === "LOCAL_DIR"
                      ? "相对受控目录，例如: demo-template 或 repositories/repo-a"
                      : "https://github.com/myorg/payment-core.git"
                  }
                  className="w-full px-3 py-2 rounded-xl border border-zinc-800 bg-zinc-900/90 text-xs text-zinc-100 placeholder-zinc-500 focus:outline-none focus:border-indigo-500 font-mono"
                />
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs font-medium text-zinc-300 mb-1">
                    凭据引用标识 (Secret Reference)
                  </label>
                  <input
                    type="text"
                    value={secretRef}
                    onChange={(e) => setSecretRef(e.target.value)}
                    placeholder="例如: secret-github-team (严禁填入明文 Token)"
                    className="w-full px-3 py-2 rounded-xl border border-zinc-800 bg-zinc-900/90 text-xs text-zinc-100 placeholder-zinc-500 focus:outline-none focus:border-indigo-500 font-mono"
                  />
                  <span className="text-[10px] text-zinc-500 mt-0.5 block">
                    全链路凭据安全脱敏，支持系统 Secret Vault 引用
                  </span>
                </div>
                <div>
                  <label className="block text-xs font-medium text-zinc-300 mb-1">
                    默认拉取分支 (Default Branch)
                  </label>
                  <input
                    type="text"
                    value={defaultBranch}
                    onChange={(e) => setDefaultBranch(e.target.value)}
                    placeholder="main"
                    className="w-full px-3 py-2 rounded-xl border border-zinc-800 bg-zinc-900/90 text-xs text-zinc-100 placeholder-zinc-500 focus:outline-none focus:border-indigo-500 font-mono"
                  />
                </div>
              </div>

              <div>
                <label className="block text-xs font-medium text-zinc-300 mb-1">
                  项目描述 (Description)
                </label>
                <textarea
                  rows={2}
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                  placeholder="简述该服务的设计职责与所属业务领域..."
                  className="w-full px-3 py-2 rounded-xl border border-zinc-800 bg-zinc-900/90 text-xs text-zinc-100 placeholder-zinc-500 focus:outline-none focus:border-indigo-500 resize-none"
                />
              </div>

              <div className="pt-2 flex items-center justify-end space-x-2">
                <button
                  type="button"
                  onClick={onClose}
                  className="px-4 py-2 rounded-xl text-xs font-medium text-zinc-400 hover:text-zinc-200 hover:bg-zinc-800/60 transition"
                >
                  取消
                </button>
                <button
                  type="submit"
                  disabled={isLoading}
                  className="flex items-center space-x-1.5 px-5 py-2 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-white text-xs font-medium shadow-md shadow-indigo-600/30 transition disabled:opacity-50"
                >
                  {isLoading ? (
                    <>
                      <Loader2 className="w-3.5 h-3.5 animate-spin" />
                      <span>正在连接代码源...</span>
                    </>
                  ) : (
                    <>
                      <span>开启 7 步受控接入</span>
                      <ArrowRight className="w-3.5 h-3.5" />
                    </>
                  )}
                </button>
              </div>
            </form>
          ) : (
            /* Steps Interactive Workspace View */
            <div className="space-y-4">
              {/* Bento Details Card */}
              <div className="grid grid-cols-3 gap-3">
                <div className="p-3 rounded-xl border border-zinc-800/80 bg-zinc-900/40">
                  <span className="text-[10px] text-zinc-500 uppercase font-mono block mb-1">
                    受控工作区 ID
                  </span>
                  <span className="text-xs font-mono text-zinc-200">
                    {session.workspaceId || "待分配 (Step 4)"}
                  </span>
                </div>
                <div className="p-3 rounded-xl border border-zinc-800/80 bg-zinc-900/40">
                  <span className="text-[10px] text-zinc-500 uppercase font-mono block mb-1">
                    隔离特性分支
                  </span>
                  <span className="text-xs font-mono text-indigo-300">
                    {session.isolatedBranch || "待拉取 (Step 4)"}
                  </span>
                </div>
                <div className="p-3 rounded-xl border border-zinc-800/80 bg-zinc-900/40">
                  <span className="text-[10px] text-zinc-500 uppercase font-mono block mb-1">
                    当前初始基线 Commit
                  </span>
                  <span className="text-xs font-mono text-emerald-400 truncate block">
                    {session.baselineCommit ? session.baselineCommit.substring(0, 10) : "待确权 (Step 7)"}
                  </span>
                </div>
              </div>

              {/* Step 5 Scan Results Display */}
              {session.scanResult && (
                <div className="p-4 rounded-xl border border-indigo-500/20 bg-indigo-950/20 space-y-3">
                  <div className="flex items-center space-x-2 text-xs font-semibold text-indigo-300">
                    <Cpu className="w-4 h-4 text-indigo-400" />
                    <span>技术栈环境扫描识别结果 (Environment Scan)</span>
                  </div>
                  <div className="grid grid-cols-3 gap-2 text-xs">
                    <div className="bg-zinc-900/80 p-2.5 rounded-lg border border-zinc-800">
                      <span className="text-[10px] text-zinc-500 block">编程语言 / 框架</span>
                      <span className="font-medium text-zinc-200">
                        {session.scanResult.language} · {session.scanResult.framework}
                      </span>
                    </div>
                    <div className="bg-zinc-900/80 p-2.5 rounded-lg border border-zinc-800">
                      <span className="text-[10px] text-zinc-500 block">构建工具 / 测试</span>
                      <span className="font-medium text-zinc-200">
                        {session.scanResult.buildTool} · {session.scanResult.testFramework || session.scanResult.testRunner || "N/A"}
                      </span>
                    </div>
                    <div className="bg-zinc-900/80 p-2.5 rounded-lg border border-zinc-800">
                      <span className="text-[10px] text-zinc-500 block">建议构建指令</span>
                      <span className="font-mono text-zinc-200 text-[11px]">
                        {session.scanResult.suggestedBuildCommand || "mvn clean test"}
                      </span>
                    </div>
                  </div>
                  {session.scanResult.securityNotice && (
                    <div className="text-[11px] font-mono text-zinc-400 bg-black/40 p-2.5 rounded-lg border border-zinc-800 flex items-start space-x-2">
                      <ShieldCheck className="w-4 h-4 text-emerald-400 shrink-0 mt-0.5" />
                      <span>{session.scanResult.securityNotice}</span>
                    </div>
                  )}
                </div>
              )}

              {/* Step 6 Rules Editor / Preview */}
              {(currentStep === "GENERATE_PROJECT_RULES" || session.status === "COMPLETED") && (
                <div className="p-4 rounded-xl border border-zinc-800 bg-zinc-900/40 space-y-2">
                  <div className="flex items-center justify-between text-xs font-semibold text-zinc-200">
                    <div className="flex items-center space-x-2">
                      <FileCode2 className="w-4 h-4 text-indigo-400" />
                      <span>PROJECT_RULES.md (工程与领域架构防御规约)</span>
                    </div>
                    <span className="text-[10px] text-zinc-500 font-mono">
                      自动绑定为版本化 Context Source
                    </span>
                  </div>
                  <textarea
                    rows={6}
                    value={customRules}
                    onChange={(e) => setCustomRules(e.target.value)}
                    disabled={session.status === "COMPLETED"}
                    placeholder="# 项目工程规约..."
                    className="w-full p-2.5 rounded-lg border border-zinc-800 bg-zinc-950 font-mono text-xs text-zinc-300 focus:outline-none focus:border-indigo-500 resize-none leading-relaxed"
                  />
                </div>
              )}

              {/* Steps Audit Log */}
              <div className="p-3 rounded-xl border border-zinc-800/80 bg-zinc-950/60 space-y-2">
                <div className="flex items-center space-x-1.5 text-xs font-semibold text-zinc-300">
                  <Terminal className="w-3.5 h-3.5 text-zinc-400" />
                  <span>向导执行凭据与审计记录 (Execution Logs)</span>
                </div>
                <div className="space-y-1.5 max-h-36 overflow-y-auto">
                  {session.steps.map((record) => (
                    <div
                      key={record.step}
                      className="flex items-center justify-between text-xs py-1 px-2 rounded bg-zinc-900/50 border border-zinc-800/40 font-mono"
                    >
                      <div className="flex items-center space-x-2 truncate">
                        {record.status === "SUCCESS" ? (
                          <CheckCircle2 className="w-3.5 h-3.5 text-emerald-400 shrink-0" />
                        ) : record.status === "FAILED" ? (
                          <ShieldAlert className="w-3.5 h-3.5 text-rose-400 shrink-0" />
                        ) : (
                          <Loader2 className="w-3.5 h-3.5 text-indigo-400 animate-spin shrink-0" />
                        )}
                        <span className="text-zinc-300 font-semibold">{record.step}:</span>
                        <span className="text-zinc-400 truncate">{record.summary}</span>
                      </div>
                      <span className="text-[10px] text-zinc-500 shrink-0 ml-2">
                        {record.finishedAt ? new Date(record.finishedAt).toLocaleTimeString() : "进行中"}
                      </span>
                    </div>
                  ))}
                </div>
              </div>

              {/* Bottom Actions */}
              <div className="pt-2 flex items-center justify-between">
                <button
                  type="button"
                  onClick={handleAbort}
                  disabled={isLoading}
                  className="flex items-center space-x-1 px-3 py-2 rounded-xl text-xs font-medium text-rose-400 hover:text-rose-300 hover:bg-rose-950/30 border border-rose-900/40 transition disabled:opacity-50"
                >
                  <Ban className="w-3.5 h-3.5" />
                  <span>中止接入</span>
                </button>

                <div className="flex items-center space-x-2">
                  {session.status === "FAILED" && (
                    <button
                      type="button"
                      onClick={handleRetry}
                      disabled={isLoading}
                      className="flex items-center space-x-1 px-4 py-2 rounded-xl bg-amber-600 hover:bg-amber-500 text-white text-xs font-medium shadow transition disabled:opacity-50"
                    >
                      <RotateCcw className="w-3.5 h-3.5" />
                      <span>重试当前步骤</span>
                    </button>
                  )}

                  {session.status === "COMPLETED" ? (
                    <button
                      type="button"
                      onClick={handleComplete}
                      disabled={isLoading}
                      className="flex items-center space-x-1.5 px-5 py-2 rounded-xl bg-emerald-600 hover:bg-emerald-500 text-white text-xs font-medium shadow-md shadow-emerald-600/30 transition disabled:opacity-50"
                    >
                      <Check className="w-4 h-4" />
                      <span>完成并进入项目交付看板</span>
                    </button>
                  ) : (
                    <button
                      type="button"
                      onClick={handleNextStep}
                      disabled={isLoading || session.status === "FAILED"}
                      className="flex items-center space-x-1.5 px-5 py-2 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-white text-xs font-medium shadow-md shadow-indigo-600/30 transition disabled:opacity-50"
                    >
                      {isLoading ? (
                        <>
                          <Loader2 className="w-3.5 h-3.5 animate-spin" />
                          <span>正在执行 {STEP_LABELS[currentStep]?.title}...</span>
                        </>
                      ) : (
                        <>
                          <span>执行第 {currentStepIdx + 1} 步: {STEP_LABELS[currentStep]?.title}</span>
                          <ArrowRight className="w-3.5 h-3.5" />
                        </>
                      )}
                    </button>
                  )}
                </div>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

export interface CreateProjectModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSuccess: (project: ProjectView) => void;
}

export function CreateProjectModal({ isOpen, onClose, onSuccess }: CreateProjectModalProps) {
  const [useWizard, setUseWizard] = useState(false);
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [isLoading, setIsLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  if (!isOpen) return null;

  if (useWizard) {
    return (
      <ProjectOnboardingModal
        isOpen={isOpen}
        onClose={() => {
          setUseWizard(false);
          onClose();
        }}
        onSuccess={onSuccess}
      />
    );
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!name.trim()) {
      setErrorMessage("请填写项目名称");
      return;
    }
    setIsLoading(true);
    setErrorMessage(null);
    try {
      const project = await apiClient.createProject({
        name: name.trim(),
        description: description.trim() || undefined,
      });
      setName("");
      setDescription("");
      onSuccess(project);
      onClose();
    } catch (err: any) {
      setErrorMessage(err.message || "创建项目失败，请检查网络或后端服务");
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70 backdrop-blur-md animate-in fade-in duration-200">
      <div className="relative w-full max-w-md rounded-2xl border border-zinc-800 bg-zinc-950/95 p-6 shadow-2xl ring-1 ring-zinc-700/50 backdrop-blur-xl">
        {/* Glow accent */}
        <div className="pointer-events-none absolute inset-x-0 -top-px h-1 bg-gradient-to-r from-transparent via-indigo-500/60 to-transparent" />

        {/* Close button */}
        <button
          onClick={onClose}
          className="absolute top-4 right-4 p-1.5 rounded-lg text-zinc-400 hover:text-zinc-200 hover:bg-zinc-800/80 transition-colors"
        >
          <X className="w-4 h-4" />
        </button>

        {/* Header */}
        <div className="flex items-center space-x-3 mb-5">
          <div className="w-10 h-10 rounded-xl bg-indigo-600/20 border border-indigo-500/30 flex items-center justify-center text-indigo-400 shadow-inner">
            <FolderPlus className="w-5 h-5" />
          </div>
          <div>
            <h3 className="text-base font-bold text-zinc-100 tracking-tight">
              新建研发项目 (New Project)
            </h3>
            <p className="text-xs text-zinc-400 font-mono mt-0.5">
              自动分配独立受控工作区与 JGit 基线保护
            </p>
          </div>
        </div>

        {/* Wizard Switch Banner */}
        <div className="mb-4 p-3 rounded-xl border border-indigo-500/30 bg-indigo-950/20 flex items-center justify-between">
          <div className="flex items-center space-x-2 text-xs text-indigo-300">
            <FolderGit2 className="w-4 h-4 text-indigo-400 shrink-0" />
            <span>接入已有 Git 仓库并执行环境扫描与规约确权？</span>
          </div>
          <button
            type="button"
            onClick={() => setUseWizard(true)}
            className="text-xs font-medium text-indigo-400 hover:text-indigo-200 underline underline-offset-2 shrink-0 ml-2"
          >
            开启 7 步向导 &rarr;
          </button>
        </div>

        {/* Error message */}
        {errorMessage && (
          <div className="mb-4 flex items-center space-x-2 rounded-xl border border-rose-500/40 bg-rose-950/30 p-2.5 text-xs text-rose-300">
            <AlertCircle className="w-4 h-4 shrink-0 text-rose-400" />
            <span className="flex-1">{errorMessage}</span>
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-xs font-medium text-zinc-300 mb-1.5">
              项目名称 (Project Name) <span className="text-rose-400">*</span>
            </label>
            <div className="relative">
              <div className="pointer-events-none absolute inset-y-0 left-0 pl-3 flex items-center text-zinc-500">
                <FolderGit2 className="w-4 h-4" />
              </div>
              <input
                type="text"
                required
                value={name}
                onChange={(e) => setName(e.target.value)}
                placeholder="例如: agenthub-auth-service"
                className="w-full pl-9 pr-3 py-2 rounded-xl border border-zinc-800 bg-zinc-900/90 text-sm text-zinc-100 placeholder-zinc-500 focus:outline-none focus:border-indigo-500 focus:ring-1 focus:ring-indigo-500 transition-colors font-mono"
              />
            </div>
          </div>

          <div>
            <label className="block text-xs font-medium text-zinc-300 mb-1.5">
              项目描述 (Description)
            </label>
            <div className="relative">
              <div className="pointer-events-none absolute top-2.5 left-3 text-zinc-500">
                <FileText className="w-4 h-4" />
              </div>
              <textarea
                rows={3}
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                placeholder="简述项目目标、技术栈或交付范围..."
                className="w-full pl-9 pr-3 py-2 rounded-xl border border-zinc-800 bg-zinc-900/90 text-sm text-zinc-100 placeholder-zinc-500 focus:outline-none focus:border-indigo-500 focus:ring-1 focus:ring-indigo-500 transition-colors resize-none"
              />
            </div>
          </div>

          <div className="pt-2 flex items-center justify-end space-x-2">
            <button
              type="button"
              onClick={onClose}
              className="px-3.5 py-2 rounded-xl text-xs font-medium text-zinc-400 hover:text-zinc-200 hover:bg-zinc-800/60 transition-colors"
            >
              取消
            </button>
            <button
              type="submit"
              disabled={isLoading}
              className="flex items-center space-x-1.5 px-4 py-2 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-white text-xs font-medium shadow-md shadow-indigo-600/30 transition disabled:opacity-50"
            >
              {isLoading ? (
                <>
                  <Loader2 className="w-3.5 h-3.5 animate-spin" />
                  <span>创建中...</span>
                </>
              ) : (
                <>
                  <Sparkles className="w-3.5 h-3.5" />
                  <span>立即创建</span>
                </>
              )}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
