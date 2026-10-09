"use client";

import React, { useState, useEffect, useCallback } from "react";
import {
  WorkflowRunView,
  StepRunView,
  ApprovalView,
  TeamView,
  TokenSummaryView,
  ProviderView,
  DeploymentResponse,
  FileDiffEntry,
  ProjectView,
  UserView,
} from "@/types";
import {
  apiClient,
  getStoredUser,
  setStoredUser,
  getStoredToken,
  setStoredToken,
  isDemoMode,
  setDemoMode,
  MOCK_RUN,
  MOCK_STEPS,
  MOCK_APPROVAL,
  MOCK_TEAM,
  MOCK_PROVIDERS,
  MOCK_TOKEN_SUMMARY,
  MOCK_DEPLOYMENT,
  MOCK_DIFFS,
} from "@/services/api";

import { HeaderNav } from "@/components/dashboard/HeaderNav";
import { MetricBentoGrid } from "@/components/dashboard/MetricBentoGrid";
import { ApprovalActionCard } from "@/components/dashboard/ApprovalActionCard";
import { LiveExecutionTerminal } from "@/components/dashboard/LiveExecutionTerminal";
import { DagTopologyVisualizer } from "@/components/dashboard/DagTopologyVisualizer";
import { MultiAgentSwarmChat } from "@/components/dashboard/MultiAgentSwarmChat";
import { DiffArtifactReviewer } from "@/components/dashboard/DiffArtifactReviewer";
import { ProviderCostDashboard } from "@/components/dashboard/ProviderCostDashboard";
import { SandboxPreviewPanel } from "@/components/dashboard/SandboxPreviewPanel";
import WorkspaceExplorer from "@/components/WorkspaceExplorer";
import { AuthModal } from "@/components/auth/AuthModal";
import { CreateProjectModal } from "@/components/project/CreateProjectModal";

import {
  LayoutDashboard,
  Layers,
  Terminal,
  Users,
  GitCompare,
  Cpu,
  Server,
  FolderTree,
  AlertCircle,
  Plus,
  Play,
  RotateCcw,
} from "lucide-react";

export default function AgentHubExecutiveApp() {
  const [backendUp, setBackendUp] = useState(false);
  const [isDemo, setIsDemo] = useState(false);
  const [currentUser, setCurrentUser] = useState<UserView | null>(null);
  const [isAuthModalOpen, setIsAuthModalOpen] = useState(false);
  const [isCreateProjectModalOpen, setIsCreateProjectModalOpen] = useState(false);

  // Active Project & Workspace state
  const [projects, setProjects] = useState<ProjectView[]>([]);
  const [selectedProjectId, setSelectedProjectId] = useState<string>("");
  const [activeWorkspaceId, setActiveWorkspaceId] = useState<string>("");

  // Runs
  const [runs, setRuns] = useState<WorkflowRunView[]>([]);
  const [selectedRunId, setSelectedRunId] = useState<string>("");

  // Navigation Tab
  const [activeTab, setActiveTab] = useState<
    "overview" | "dag" | "swarm" | "diff" | "providers" | "sandbox" | "files"
  >("overview");

  // Core Domain State
  const [run, setRun] = useState<WorkflowRunView | null>(null);
  const [steps, setSteps] = useState<StepRunView[]>([]);
  const [approval, setApproval] = useState<ApprovalView | null>(null);
  const [team, setTeam] = useState<TeamView | null>(null);
  const [tokenSummary, setTokenSummary] = useState<TokenSummaryView | null>(null);
  const [providers, setProviders] = useState<ProviderView[]>([]);
  const [deployment, setDeployment] = useState<DeploymentResponse | null>(null);
  const [diffs, setDiffs] = useState<FileDiffEntry[]>([]);

  const [isRunningWorkflow, setIsRunningWorkflow] = useState(false);
  const [syncErrorMessage, setSyncErrorMessage] = useState<string | null>(null);

  // Initialize Auth & Mode on client mount
  useEffect(() => {
    setIsDemo(isDemoMode());
    const stored = getStoredUser();
    if (stored) {
      setCurrentUser(stored);
    }

    const handleUnauthorized = () => {
      setCurrentUser(null);
      setStoredToken(null);
      setStoredUser(null);
      setIsAuthModalOpen(true);
    };

    window.addEventListener("agenthub:unauthorized", handleUnauthorized);
    return () => {
      window.removeEventListener("agenthub:unauthorized", handleUnauthorized);
    };
  }, []);

  // Sync dashboard data from authentic backend or demo fixtures
  const syncDashboardData = useCallback(async () => {
    setSyncErrorMessage(null);

    // 1. Check Backend Health
    try {
      const health = await apiClient.getHealth();
      if (health && health.status === "UP") {
        setBackendUp(true);
      } else {
        setBackendUp(false);
      }
    } catch {
      setBackendUp(false);
    }

    // 2. Demo Mode Branch: populate with verified demo fixtures
    if (isDemoMode()) {
      setRun(MOCK_RUN);
      setSteps(MOCK_STEPS);
      setApproval(MOCK_APPROVAL);
      setTeam(MOCK_TEAM);
      setTokenSummary(MOCK_TOKEN_SUMMARY);
      setProviders(MOCK_PROVIDERS);
      setDeployment(MOCK_DEPLOYMENT);
      setDiffs(MOCK_DIFFS);
      setSelectedRunId(MOCK_RUN.id);
      return;
    }

    // 3. Live Product Mode Branch: load authentic backend data
    try {
      // 3.1 Load Projects for current authenticated user
      let currentProjId = selectedProjectId;
      try {
        const userProjects = await apiClient.listProjects();
        if (userProjects && userProjects.length > 0) {
          setProjects(userProjects);
          if (!userProjects.some((p) => p.id === selectedProjectId)) {
            currentProjId = userProjects[0].id;
            setSelectedProjectId(currentProjId);
          }
        } else {
          setProjects([]);
          currentProjId = "";
          setSelectedProjectId("");
          setActiveWorkspaceId("");
        }
      } catch (err: any) {
        if (err.status !== 401) {
          console.warn("Could not list projects:", err);
        }
      }

      // 3.2 Load Workspace for project
      if (currentProjId) {
        try {
          const ws = await apiClient.getWorkspace(currentProjId);
          if (ws && ws.id) {
            setActiveWorkspaceId(ws.id);
          }
        } catch {}
      } else {
        setActiveWorkspaceId("");
      }

      // 3.3 Load Runs for current project
      let currentRunId = selectedRunId;
      if (currentProjId) {
        try {
          const projectRuns = await apiClient.listRunsByProject(currentProjId);
          if (projectRuns && projectRuns.length > 0) {
            setRuns(projectRuns);
            if (!projectRuns.some((r) => r.id === selectedRunId)) {
              currentRunId = projectRuns[0].id;
              setSelectedRunId(currentRunId);
            }
          } else {
            setRuns([]);
            currentRunId = "";
            setSelectedRunId("");
          }
        } catch {
          setRuns([]);
          currentRunId = "";
          setSelectedRunId("");
        }
      } else {
        setRuns([]);
        currentRunId = "";
        setSelectedRunId("");
      }

      // 3.4 Parallel fetch authentic domain objects
      const [
        runData,
        stepsData,
        approvalsData,
        teamData,
        tokensData,
        provsData,
        depsData,
        diffData,
      ] = await Promise.allSettled([
        currentRunId ? apiClient.getRun(currentRunId) : Promise.resolve(null),
        currentRunId ? apiClient.listStepRuns(currentRunId) : Promise.resolve([]),
        currentRunId ? apiClient.listApprovals(currentRunId) : Promise.resolve([]),
        apiClient.getTeam("team-dev-swarm").catch(() => null),
        apiClient.getTokenSummary().catch(() => null),
        apiClient.listProviders().catch(() => []),
        apiClient.listDeployments(currentProjId).catch(() => []),
        activeWorkspaceId ? apiClient.getStructuredDiff(activeWorkspaceId).catch(() => null) : Promise.resolve(null),
      ]);

      if (runData.status === "fulfilled" && runData.value) {
        setRun(runData.value);
      } else {
        setRun(null);
      }

      if (stepsData.status === "fulfilled" && stepsData.value) {
        setSteps(stepsData.value);
      } else {
        setSteps([]);
      }

      if (approvalsData.status === "fulfilled" && approvalsData.value && approvalsData.value.length > 0) {
        const pending = approvalsData.value.find((a) => a.status === "PENDING") || approvalsData.value[0];
        setApproval(pending);
      } else {
        setApproval(null);
      }

      if (teamData.status === "fulfilled" && teamData.value) {
        setTeam(teamData.value);
      }

      if (tokensData.status === "fulfilled" && tokensData.value) {
        setTokenSummary(tokensData.value);
      }

      if (provsData.status === "fulfilled" && provsData.value) {
        setProviders(provsData.value);
      }

      if (depsData.status === "fulfilled" && depsData.value && depsData.value.length > 0) {
        setDeployment(depsData.value[0]);
      } else {
        setDeployment(null);
      }

      if (diffData.status === "fulfilled" && diffData.value) {
        setDiffs(diffData.value.entries || []);
      } else {
        setDiffs([]);
      }
    } catch (err: any) {
      setSyncErrorMessage(`看板同步出现异常: ${err.message || "网络请求失败"}`);
    }
  }, [selectedProjectId, selectedRunId, activeWorkspaceId]);

  useEffect(() => {
    syncDashboardData();
  }, [syncDashboardData]);

  // Handle Demo Mode Toggle
  const handleToggleDemoMode = () => {
    const nextMode = !isDemo;
    setDemoMode(nextMode);
    setIsDemo(nextMode);
  };

  // Workflow trigger handler
  const handleRunWorkflow = async () => {
    setIsRunningWorkflow(true);
    setSyncErrorMessage(null);

    if (isDemo) {
      setApproval(MOCK_APPROVAL);
      setSteps(MOCK_STEPS);
      setTimeout(() => {
        syncDashboardData();
        setIsRunningWorkflow(false);
      }, 1200);
      return;
    }

    if (!selectedProjectId) {
      setIsCreateProjectModalOpen(true);
      setIsRunningWorkflow(false);
      setSyncErrorMessage("请先创建或选择研发项目后再启动执行");
      return;
    }

    try {
      const newRun = await apiClient.startRun({
        projectId: selectedProjectId,
        definitionId: "wf-enterprise-auth-delivery",
        idempotencyKey: `idem-${Date.now()}`,
      });

      if (newRun && newRun.id) {
        setRun(newRun);
        setSelectedRunId(newRun.id);
        setSteps([]);
        setApproval(null);
        setTimeout(() => {
          syncDashboardData();
        }, 800);
      }
    } catch (err: any) {
      setSyncErrorMessage(`启动工作流失败: ${err.message || "请求异常"}`);
    } finally {
      setIsRunningWorkflow(false);
    }
  };

  // Approval decision callback
  const handleApprovalDecided = async (decision: "APPROVED" | "REJECTED") => {
    if (isDemo) {
      if (approval) {
        setApproval({ ...approval, status: decision, decision });
      }
      if (run) {
        setRun({ ...run, status: decision === "APPROVED" ? "RUNNING" : "FAILED" });
      }
      return;
    }

    // In live mode, refresh authentic status from backend after approval submission
    setTimeout(() => {
      syncDashboardData();
    }, 600);
  };

  // Card action handler
  const handleCardAction = (action: string) => {
    if (action.includes("Diff")) {
      setActiveTab("diff");
    } else if (action.includes("审批")) {
      setActiveTab("dag");
    } else {
      setActiveTab("overview");
    }
  };

  const handleLogout = async () => {
    await apiClient.logout();
    setCurrentUser(null);
    syncDashboardData();
  };

  return (
    <div className="flex flex-col min-h-screen bg-[#09090b] text-zinc-100 font-sans ambient-glow selection:bg-indigo-600 selection:text-white">
      {/* 1. Global Navigation Header */}
      <HeaderNav
        backendUp={backendUp}
        circuitBreakerStatus={providers[0]?.circuitStatus || "CLOSED"}
        activeProjectId={selectedProjectId}
        activeRunId={run?.id || selectedRunId}
        projects={projects}
        onSelectProject={(projId) => {
          setSelectedProjectId(projId);
        }}
        onOpenCreateProjectModal={() => setIsCreateProjectModalOpen(true)}
        currentUser={currentUser}
        isDemoMode={isDemo}
        onToggleDemoMode={handleToggleDemoMode}
        onOpenAuthModal={() => setIsAuthModalOpen(true)}
        onLogout={handleLogout}
        onRefreshAll={syncDashboardData}
        onRunWorkflow={handleRunWorkflow}
        isRunning={isRunningWorkflow}
      />

      {/* 2. Main Executive View Area */}
      <main className="flex-1 flex flex-col p-4 md:p-6 space-y-4 max-w-[1720px] w-full mx-auto overflow-hidden">
        {/* Sync Error Toast */}
        {syncErrorMessage && (
          <div className="flex items-center space-x-2 rounded-xl border border-rose-500/40 bg-rose-950/40 p-3 text-xs text-rose-300">
            <AlertCircle className="w-4 h-4 text-rose-400 shrink-0" />
            <span className="flex-1">{syncErrorMessage}</span>
            <button
              onClick={syncDashboardData}
              className="px-2 py-0.5 rounded bg-rose-900/60 hover:bg-rose-800 text-[11px] font-mono"
            >
              重试同步
            </button>
          </div>
        )}

        {/* Empty project callout for live product mode */}
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
              onClick={() => setIsCreateProjectModalOpen(true)}
              className="flex items-center space-x-1.5 px-4 py-2 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-white text-xs font-medium shadow-md shadow-indigo-600/30 transition shrink-0"
            >
              <Plus className="w-4 h-4" />
              <span>新建研发项目</span>
            </button>
          </div>
        )}

        {/* Demo Mode Notice Banner */}
        {isDemo && (
          <div className="flex items-center justify-between rounded-xl border border-amber-500/40 bg-amber-950/30 px-3.5 py-2 text-xs text-amber-200">
            <div className="flex items-center space-x-2">
              <span className="w-2 h-2 rounded-full bg-amber-400 animate-pulse shrink-0" />
              <span>
                <strong>离线演示模式已激活 (Demo Mode)</strong>：当前展示受控模拟数据集，未连接真实生产执行内核。
              </span>
            </div>
            <button
              onClick={handleToggleDemoMode}
              className="px-2.5 py-1 rounded-lg bg-amber-600/30 hover:bg-amber-600/50 text-amber-300 font-semibold text-[11px] transition"
            >
              切换至真实产品模式
            </button>
          </div>
        )}

        {/* KPI Bento Metric Grid */}
        <MetricBentoGrid
          run={run}
          steps={steps}
          team={team}
          tokenSummary={tokenSummary}
          providers={providers}
          deployment={deployment}
          onCardClick={(tabKey) => {
            if (tabKey === "canvas") setActiveTab("dag");
            else if (tabKey === "swarm") setActiveTab("swarm");
            else if (tabKey === "providers") setActiveTab("providers");
            else if (tabKey === "sandbox") setActiveTab("sandbox");
          }}
        />

        {/* Floating Human-in-the-Loop Approval Action Card (Gatekeeper) */}
        {approval && approval.status === "PENDING" && (
          <ApprovalActionCard
            approval={approval}
            onDecided={handleApprovalDecided}
            className="shrink-0 animate-in fade-in slide-in-from-top-2 duration-300"
          />
        )}

        {/* View Switcher Tabs Bar */}
        <div className="flex items-center justify-between border-b border-zinc-800/80 pb-2 shrink-0 overflow-x-auto">
          <div className="flex items-center space-x-1 text-xs">
            {[
              { id: "overview", label: "Bento 全景看板", icon: LayoutDashboard },
              { id: "dag", label: "DAG 拓扑与执行", icon: Layers },
              { id: "swarm", label: "多智能体协同", icon: Users },
              { id: "diff", label: "JGit 行级代码审查", icon: GitCompare },
              { id: "providers", label: "模型路由与成本", icon: Cpu },
              { id: "sandbox", label: "沙箱预览与部署", icon: Server },
              { id: "files", label: "工作区文件树", icon: FolderTree },
            ].map((tab) => {
              const Icon = tab.icon;
              const isActive = activeTab === tab.id;
              return (
                <button
                  key={tab.id}
                  onClick={() => setActiveTab(tab.id as any)}
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
            <span>Modern Aesthetic: Linear / Vercel Glassmorphism</span>
          </div>
        </div>

        {/* View Content Renderer */}
        <div className="flex-1 min-h-0 flex flex-col">
          {/* TAB 1: Bento Overview */}
          {activeTab === "overview" && (
            <div className="flex-1 space-y-4 pb-6 overflow-y-auto">
              <div className="grid grid-cols-1 lg:grid-cols-12 gap-4 min-h-[380px]">
                <div className="lg:col-span-7 h-[380px]">
                  <DagTopologyVisualizer
                    steps={steps}
                    onRunWorkflow={handleRunWorkflow}
                    isRunning={isRunningWorkflow}
                  />
                </div>
                <div className="lg:col-span-5 h-[380px]">
                  <LiveExecutionTerminal
                    runId={run?.id || selectedRunId}
                    onRefresh={syncDashboardData}
                  />
                </div>
              </div>

              <div className="grid grid-cols-1 lg:grid-cols-12 gap-4 min-h-[460px]">
                <div className="lg:col-span-6 h-[460px]">
                  <MultiAgentSwarmChat
                    team={team}
                    onCardAction={handleCardAction}
                  />
                </div>
                <div className="lg:col-span-6 h-[460px]">
                  <DiffArtifactReviewer
                    diffs={diffs}
                    workspaceId={activeWorkspaceId}
                    onRefresh={syncDashboardData}
                  />
                </div>
              </div>

              <div className="grid grid-cols-1 lg:grid-cols-12 gap-4 min-h-[440px]">
                <div className="lg:col-span-6 h-[440px]">
                  <ProviderCostDashboard
                    providers={providers}
                    tokenSummary={tokenSummary}
                    onRefresh={syncDashboardData}
                  />
                </div>
                <div className="lg:col-span-6 h-[440px]">
                  <SandboxPreviewPanel
                    deployment={deployment}
                    projectId={selectedProjectId}
                    onRefresh={syncDashboardData}
                  />
                </div>
              </div>
            </div>
          )}

          {/* TAB 2: DAG & Execution Focus */}
          {activeTab === "dag" && (
            <div className="flex-1 grid grid-cols-1 lg:grid-cols-12 gap-4 min-h-[640px]">
              <div className="lg:col-span-8 h-[640px]">
                <DagTopologyVisualizer
                  steps={steps}
                  onRunWorkflow={handleRunWorkflow}
                  isRunning={isRunningWorkflow}
                />
              </div>
              <div className="lg:col-span-4 h-[640px]">
                <LiveExecutionTerminal
                  runId={run?.id || selectedRunId}
                  onRefresh={syncDashboardData}
                />
              </div>
            </div>
          )}

          {/* TAB 3: Swarm Chat Timeline Focus */}
          {activeTab === "swarm" && (
            <div className="flex-1 h-[680px]">
              <MultiAgentSwarmChat
                team={team}
                onCardAction={handleCardAction}
              />
            </div>
          )}

          {/* TAB 4: JGit Diff Review Focus */}
          {activeTab === "diff" && (
            <div className="flex-1 h-[680px]">
              <DiffArtifactReviewer
                diffs={diffs}
                workspaceId={activeWorkspaceId}
                onRefresh={syncDashboardData}
              />
            </div>
          )}

          {/* TAB 5: Providers & Cost Focus */}
          {activeTab === "providers" && (
            <div className="flex-1 h-[680px]">
              <ProviderCostDashboard
                providers={providers}
                tokenSummary={tokenSummary}
                onRefresh={syncDashboardData}
              />
            </div>
          )}

          {/* TAB 6: Sandbox Preview Focus */}
          {activeTab === "sandbox" && (
            <div className="flex-1 h-[680px]">
              <SandboxPreviewPanel
                deployment={deployment}
                projectId={selectedProjectId}
                onRefresh={syncDashboardData}
              />
            </div>
          )}

          {/* TAB 7: Workspace Explorer Files */}
          {activeTab === "files" && (
            <div className="flex-1 h-[680px]">
              <WorkspaceExplorer
                workspaceId={activeWorkspaceId}
              />
            </div>
          )}
        </div>
      </main>

      {/* Authentication Modal */}
      <AuthModal
        isOpen={isAuthModalOpen}
        onClose={() => setIsAuthModalOpen(false)}
        onSuccess={(user) => {
          setCurrentUser(user);
          syncDashboardData();
        }}
      />

      {/* Create Project Modal */}
      <CreateProjectModal
        isOpen={isCreateProjectModalOpen}
        onClose={() => setIsCreateProjectModalOpen(false)}
        onSuccess={(newProject) => {
          setSelectedProjectId(newProject.id);
          if (newProject.workspaceId) {
            setActiveWorkspaceId(newProject.workspaceId);
          }
          syncDashboardData();
        }}
      />
    </div>
  );
}
