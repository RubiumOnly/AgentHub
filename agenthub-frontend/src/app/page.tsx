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
} from "@/types";
import {
  apiClient,
  DEFAULT_WORKSPACE_PATH,
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

import {
  LayoutDashboard,
  Layers,
  Terminal,
  Users,
  GitCompare,
  Cpu,
  Server,
  FolderTree,
} from "lucide-react";

export default function AgentHubExecutiveApp() {
  const [backendUp, setBackendUp] = useState(false);
  const [activeTab, setActiveTab] = useState<
    "overview" | "dag" | "swarm" | "diff" | "providers" | "sandbox" | "files"
  >("overview");

  // Core Domain State
  const [run, setRun] = useState<WorkflowRunView | null>(MOCK_RUN);
  const [steps, setSteps] = useState<StepRunView[]>(MOCK_STEPS);
  const [approval, setApproval] = useState<ApprovalView | null>(MOCK_APPROVAL);
  const [team, setTeam] = useState<TeamView | null>(MOCK_TEAM);
  const [tokenSummary, setTokenSummary] = useState<TokenSummaryView | null>(
    MOCK_TOKEN_SUMMARY
  );
  const [providers, setProviders] = useState<ProviderView[]>(MOCK_PROVIDERS);
  const [deployment, setDeployment] = useState<DeploymentResponse | null>(
    MOCK_DEPLOYMENT
  );
  const [diffs, setDiffs] = useState<FileDiffEntry[]>(MOCK_DIFFS);

  const [isRunningWorkflow, setIsRunningWorkflow] = useState(false);

  // Initialize and sync data from backend
  const syncDashboardData = useCallback(async () => {
    try {
      const health = await apiClient.getHealth();
      if (health) {
        setBackendUp(true);
      } else {
        setBackendUp(false);
      }
    } catch {
      setBackendUp(false);
    }

    try {
      const [
        runData,
        stepsData,
        approvalsData,
        teamData,
        tokensData,
        provsData,
        depsData,
        diffsData,
      ] = await Promise.all([
        apiClient.getRun("run-exec-94218a"),
        apiClient.listStepRuns("run-exec-94218a"),
        apiClient.listApprovals("run-exec-94218a"),
        apiClient.getTeam("team-dev-swarm"),
        apiClient.getTokenSummary(),
        apiClient.listProviders(),
        apiClient.listDeployments("proj-default"),
        apiClient.getDiff(DEFAULT_WORKSPACE_PATH),
      ]);

      if (runData) setRun(runData);
      if (stepsData && stepsData.length > 0) setSteps(stepsData);
      if (approvalsData && approvalsData.length > 0) setApproval(approvalsData[0]);
      if (teamData) setTeam(teamData);
      if (tokensData) setTokenSummary(tokensData);
      if (provsData && provsData.length > 0) setProviders(provsData);
      if (depsData && depsData.length > 0) setDeployment(depsData[0]);
      if (diffsData && diffsData.length > 0) setDiffs(diffsData);
    } catch (err) {
      console.warn("Sync encountered error; fallback mock data maintained:", err);
    }
  }, []);

  useEffect(() => {
    syncDashboardData();
  }, [syncDashboardData]);

  // Workflow trigger handler
  const handleRunWorkflow = async () => {
    setIsRunningWorkflow(true);
    try {
      // Execute or refresh workflow state
      setTimeout(() => {
        syncDashboardData();
        setIsRunningWorkflow(false);
      }, 1500);
    } catch {
      setIsRunningWorkflow(false);
    }
  };

  // Approval decision callback
  const handleApprovalDecided = (decision: "APPROVED" | "REJECTED") => {
    if (approval) {
      setApproval({
        ...approval,
        status: decision,
        decision,
      });
    }

    // Update steps
    setSteps((prev) =>
      prev.map((s) => {
        if (s.nodeId === "node-security-gate" || s.id === "step-5-approval") {
          return {
            ...s,
            status: decision === "APPROVED" ? "SUCCEEDED" : "FAILED",
          };
        }
        if (
          decision === "APPROVED" &&
          (s.nodeId === "node-sandbox-deploy" || s.id === "step-6-deploy")
        ) {
          return {
            ...s,
            status: "RUNNING",
          };
        }
        return s;
      })
    );
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

  return (
    <div className="flex flex-col min-h-screen bg-[#09090b] text-zinc-100 font-sans ambient-glow selection:bg-indigo-600 selection:text-white">
      {/* 1. Global Navigation Header */}
      <HeaderNav
        backendUp={backendUp}
        circuitBreakerStatus={providers[0]?.circuitStatus || "CLOSED"}
        activeRunId={run?.id || "run-exec-94218a"}
        onRefreshAll={syncDashboardData}
        onRunWorkflow={handleRunWorkflow}
        isRunning={isRunningWorkflow}
      />

      {/* 2. Main Executive View Area */}
      <main className="flex-1 flex flex-col p-4 md:p-6 space-y-4 max-w-[1720px] w-full mx-auto overflow-hidden">
        {/* KPI Bento Metric Grid (5 Micro Cards) */}
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
          {/* TAB 1: Bento Overview (High-Density Multi-Panel Bento Grid) */}
          {activeTab === "overview" && (
            <div className="flex-1 space-y-4 pb-6 overflow-y-auto">
              {/* Row 1: Hero DAG Canvas (7 cols) + SSE Stream Terminal (5 cols) */}
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
                    runId={run?.id || "run-exec-94218a"}
                    onRefresh={syncDashboardData}
                  />
                </div>
              </div>

              {/* Row 2: Swarm Timeline (6 cols) + JGit Unified Diff Reviewer (6 cols) */}
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
                    onRefresh={syncDashboardData}
                  />
                </div>
              </div>

              {/* Row 3: Provider Dynamic Router (6 cols) + Sandbox & Preview Deployment (6 cols) */}
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
                  runId={run?.id || "run-exec-94218a"}
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
                onRefresh={syncDashboardData}
              />
            </div>
          )}

          {/* TAB 7: Workspace Explorer Files */}
          {activeTab === "files" && (
            <div className="flex-1 h-[680px]">
              <WorkspaceExplorer workspacePath={DEFAULT_WORKSPACE_PATH} />
            </div>
          )}
        </div>
      </main>
    </div>
  );
}
