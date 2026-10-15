"use client";

import React from "react";
import {
  ApprovalView,
  DeploymentResponse,
  ProjectView,
  ProviderView,
  StepRunView,
  TeamView,
  TokenSummaryView,
  UserView,
  WorkflowRunView,
} from "@/types";
import { HeaderNav } from "@/components/dashboard/HeaderNav";
import { MetricBentoGrid } from "@/components/dashboard/MetricBentoGrid";
import { ApprovalActionCard } from "@/components/dashboard/ApprovalActionCard";
import { AuthModal } from "@/components/auth/AuthModal";
import { CreateProjectModal } from "@/components/project/CreateProjectModal";
import {
  Activity,
  AlertCircle,
  Cpu,
  FolderTree,
  GitCompare,
  Layers,
  LayoutDashboard,
  Plus,
  Server,
  Users,
  HardDrive,
  ShieldCheck,
} from "lucide-react";

export type DashboardTabId =
  | "overview"
  | "dag"
  | "swarm"
  | "diff"
  | "providers"
  | "sandbox"
  | "files"
  | "operations"
  | "team"
  | "ops";

interface ProjectShellProps {
  backendUp: boolean;
  isDemo: boolean;
  currentUser: UserView | null;
  projects: ProjectView[];
  selectedProjectId: string;
  activeRunId: string;
  activeTab: DashboardTabId;
  run: WorkflowRunView | null;
  steps: StepRunView[];
  approval: ApprovalView | null;
  team: TeamView | null;
  tokenSummary: TokenSummaryView | null;
  providers: ProviderView[];
  deployment: DeploymentResponse | null;
  isRunningWorkflow: boolean;
  syncErrorMessage: string | null;
  isAuthModalOpen: boolean;
  isCreateProjectModalOpen: boolean;
  onSelectProject: (projectId: string) => void;
  onSelectTab: (tab: DashboardTabId) => void;
  onToggleDemoMode: () => void;
  onOpenAuthModal: () => void;
  onCloseAuthModal: () => void;
  onAuthSuccess: (user: UserView) => void;
  onOpenCreateProjectModal: () => void;
  onCloseCreateProjectModal: () => void;
  onProjectCreated: (project: ProjectView) => void;
  onLogout: () => void;
  onRefreshAll: () => void;
  onRunWorkflow: () => void;
  onExportEvidence: () => void;
  onApprovalDecided: (decision: "APPROVED" | "REJECTED") => void;
  children: React.ReactNode;
}

const TAB_ITEMS: Array<{ id: DashboardTabId; label: string; icon: React.ComponentType<{ className?: string }> }> = [
  { id: "overview", label: "Bento 全景看板", icon: LayoutDashboard },
  { id: "dag", label: "DAG 拓扑与执行", icon: Layers },
  { id: "swarm", label: "多智能体协同", icon: Users },
  { id: "diff", label: "JGit 行级代码审查", icon: GitCompare },
  { id: "operations", label: "运维决策与血缘", icon: Activity },
  { id: "team", label: "团队角色与权限", icon: ShieldCheck },
  { id: "ops", label: "系统灾备与运维", icon: HardDrive },
  { id: "providers", label: "模型路由与成本", icon: Cpu },
  { id: "sandbox", label: "沙箱预览与部署", icon: Server },
  { id: "files", label: "工作区文件树", icon: FolderTree },
];

export function ProjectShell({
  backendUp,
  isDemo,
  currentUser,
  projects,
  selectedProjectId,
  activeRunId,
  activeTab,
  run,
  steps,
  approval,
  team,
  tokenSummary,
  providers,
  deployment,
  isRunningWorkflow,
  syncErrorMessage,
  isAuthModalOpen,
  isCreateProjectModalOpen,
  onSelectProject,
  onSelectTab,
  onToggleDemoMode,
  onOpenAuthModal,
  onCloseAuthModal,
  onAuthSuccess,
  onOpenCreateProjectModal,
  onCloseCreateProjectModal,
  onProjectCreated,
  onLogout,
  onRefreshAll,
  onRunWorkflow,
  onExportEvidence,
  onApprovalDecided,
  children,
}: ProjectShellProps) {
  return (
    <div className="flex flex-col min-h-screen bg-[#09090b] text-zinc-100 font-sans ambient-glow selection:bg-indigo-600 selection:text-white">
      <HeaderNav
        backendUp={backendUp}
        circuitBreakerStatus={providers[0]?.circuitStatus || "CLOSED"}
        activeProjectId={selectedProjectId}
        activeRunId={activeRunId}
        projects={projects}
        onSelectProject={onSelectProject}
        onOpenCreateProjectModal={onOpenCreateProjectModal}
        currentUser={currentUser}
        isDemoMode={isDemo}
        onToggleDemoMode={onToggleDemoMode}
        onOpenAuthModal={onOpenAuthModal}
        onLogout={onLogout}
        onRefreshAll={onRefreshAll}
        onRunWorkflow={onRunWorkflow}
        onExportEvidence={onExportEvidence}
        isRunning={isRunningWorkflow}
      />

      <main className="flex-1 flex flex-col p-4 md:p-6 space-y-4 max-w-[1720px] w-full mx-auto overflow-hidden">
        {syncErrorMessage && (
          <div className="flex items-center space-x-2 rounded-xl border border-rose-500/40 bg-rose-950/40 p-3 text-xs text-rose-300">
            <AlertCircle className="w-4 h-4 text-rose-400 shrink-0" />
            <span className="flex-1">{syncErrorMessage}</span>
            <button
              onClick={onRefreshAll}
              className="px-2 py-0.5 rounded bg-rose-900/60 hover:bg-rose-800 text-[11px] font-mono"
            >
              重试同步
            </button>
          </div>
        )}

        {!isDemo && projects.length === 0 && (
          <div className="rounded-2xl border border-indigo-500/30 bg-gradient-to-r from-indigo-950/40 via-zinc-900/60 to-indigo-950/20 p-5 shadow-xl flex flex-col md:flex-row items-center justify-between gap-4">
            <div className="flex items-center space-x-3.5">
              <div className="w-10 h-10 rounded-xl bg-indigo-600/20 border border-indigo-500/30 flex items-center justify-center text-indigo-400 shrink-0">
                <FolderTree className="w-5 h-5" />
              </div>
              <div>
                <h4 className="text-sm font-semibold text-zinc-100">
                  开启您的首个多 Agent 研发项目
                </h4>
                <p className="text-xs text-zinc-400 mt-0.5">
                  当前账户尚未创建项目。创建项目后将自动分配受控工作区、建立 JGit 基线与多 Agent 协同流程。
                </p>
              </div>
            </div>
            <button
              onClick={onOpenCreateProjectModal}
              className="flex items-center space-x-1.5 px-4 py-2 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-white text-xs font-medium shadow-md shadow-indigo-600/30 transition shrink-0"
            >
              <Plus className="w-4 h-4" />
              <span>新建研发项目</span>
            </button>
          </div>
        )}

        {isDemo && (
          <div className="flex items-center justify-between rounded-xl border border-amber-500/40 bg-amber-950/30 px-3.5 py-2 text-xs text-amber-200">
            <div className="flex items-center space-x-2">
              <span className="w-2 h-2 rounded-full bg-amber-400 animate-pulse shrink-0" />
              <span>
                <strong>离线演示模式已激活 (Demo Mode)</strong>：当前展示受控模拟数据集，未连接真实执行内核。
              </span>
            </div>
            <button
              onClick={onToggleDemoMode}
              className="px-2.5 py-1 rounded-lg bg-amber-600/30 hover:bg-amber-600/50 text-amber-300 font-semibold text-[11px] transition"
            >
              切换至真实产品模式
            </button>
          </div>
        )}

        <MetricBentoGrid
          run={run}
          steps={steps}
          team={team}
          tokenSummary={tokenSummary}
          providers={providers}
          deployment={deployment}
          onCardClick={(tabKey) => {
            if (tabKey === "canvas") onSelectTab("dag");
            else if (tabKey === "swarm") onSelectTab("swarm");
            else if (tabKey === "providers") onSelectTab("providers");
            else if (tabKey === "sandbox") onSelectTab("sandbox");
          }}
        />

        {approval && approval.status === "PENDING" && (
          <ApprovalActionCard
            approval={approval}
            onDecided={onApprovalDecided}
            className="shrink-0 animate-in fade-in slide-in-from-top-2 duration-300"
          />
        )}

        <div className="flex items-center justify-between border-b border-zinc-800/80 pb-2 shrink-0 overflow-x-auto">
          <div className="flex items-center space-x-1 text-xs">
            {TAB_ITEMS.map((tab) => {
              const Icon = tab.icon;
              const isActive = activeTab === tab.id;
              return (
                <button
                  key={tab.id}
                  onClick={() => onSelectTab(tab.id)}
                  className={`flex items-center space-x-2 px-3.5 py-2 rounded-xl text-xs font-medium transition-all duration-150 shrink-0 ${
                    isActive
                      ? "bg-zinc-800 text-zinc-100 shadow-sm border border-zinc-700/70 font-semibold"
                      : "text-zinc-400 hover:text-zinc-200 hover:bg-zinc-900/60"
                  }`}
                >
                  <Icon className={`w-3.5 h-3.5 ${isActive ? "text-indigo-400" : "text-zinc-400"}`} />
                  <span>{tab.label}</span>
                </button>
              );
            })}
          </div>
          <div className="hidden lg:flex items-center space-x-2 text-[11px] font-mono text-zinc-400">
            <span>API Contract v1 · UTC · ETag · Idempotency</span>
          </div>
        </div>

        <div className="flex-1 min-h-0 flex flex-col">{children}</div>
      </main>

      <AuthModal
        isOpen={isAuthModalOpen}
        onClose={onCloseAuthModal}
        onSuccess={onAuthSuccess}
      />

      <CreateProjectModal
        isOpen={isCreateProjectModalOpen}
        onClose={onCloseCreateProjectModal}
        onSuccess={onProjectCreated}
      />
    </div>
  );
}
