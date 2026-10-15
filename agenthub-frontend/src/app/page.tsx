"use client";

import React, { useState, useEffect, useCallback } from "react";
import {
  WorkflowRunView, StepRunView, ApprovalView, TeamView, TokenSummaryView,
  ProviderView, DeploymentResponse, FileDiffEntry, ProjectView, UserView, OrchestrationPlanView,
} from "@/types";
import {
  authApi, projectsApi, workspacesApi, workflowsApi, runsApi, approvalsApi,
  deploymentsApi, getStoredUser, setStoredUser, setStoredToken, isDemoMode,
  setDemoMode, MOCK_RUN, MOCK_STEPS, MOCK_APPROVAL, MOCK_TEAM, MOCK_PROVIDERS,
  MOCK_TOKEN_SUMMARY, MOCK_DEPLOYMENT, MOCK_DIFFS,
} from "@/services/api";
import { DashboardTabId, ProjectShell } from "@/components/dashboard/ProjectShell";
import { RunWorkspacePanel } from "@/components/dashboard/RunWorkspacePanel";
import { ReviewSidebar } from "@/components/dashboard/ReviewSidebar";
import { DeploymentPanel } from "@/components/dashboard/DeploymentPanel";
import { RunOperationsPanel } from "@/components/dashboard/RunOperationsPanel";
import { OrchestratorPlanModal } from "@/components/workflow/OrchestratorPlanModal";

export default function AgentHubExecutiveApp() {
  const [backendUp, setBackendUp] = useState(false);
  const [isDemo, setIsDemo] = useState(false);
  const [currentUser, setCurrentUser] = useState<UserView | null>(null);
  const [isAuthModalOpen, setIsAuthModalOpen] = useState(false);
  const [isCreateProjectModalOpen, setIsCreateProjectModalOpen] = useState(false);
  const [isPlanModalOpen, setIsPlanModalOpen] = useState(false);

  const [projects, setProjects] = useState<ProjectView[]>([]);
  const [selectedProjectId, setSelectedProjectId] = useState<string>("");
  const [activeWorkspaceId, setActiveWorkspaceId] = useState<string>("");
  const [activeDefinitionId, setActiveDefinitionId] = useState<string>("");
  const [selectedRunId, setSelectedRunId] = useState<string>("");
  const [activeTab, setActiveTab] = useState<DashboardTabId>("overview");

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

  const syncDashboardData = useCallback(async () => {
    setSyncErrorMessage(null);

    try {
      const health = await authApi.getHealth();
      setBackendUp(Boolean(health && health.status === "UP"));
    } catch {
      setBackendUp(false);
    }

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

    try {
      let currentProjId = selectedProjectId;
      try {
        const userProjects = await projectsApi.listProjects();
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
      } catch {}

      let currentWorkspaceId = activeWorkspaceId;
      if (currentProjId) {
        try {
          const ws = await workspacesApi.getWorkspace(currentProjId);
          if (ws?.id) {
            currentWorkspaceId = ws.id;
            setActiveWorkspaceId(ws.id);
          }
        } catch {}
      } else {
        currentWorkspaceId = "";
        setActiveWorkspaceId("");
      }

      let currentRunId = selectedRunId;
      if (currentProjId) {
        try {
          const projectRuns = await runsApi.listRunsByProject(currentProjId);
          if (projectRuns && projectRuns.length > 0) {
            if (!projectRuns.some((r) => r.id === selectedRunId)) {
              currentRunId = projectRuns[0].id;
              setSelectedRunId(currentRunId);
            }
          } else {
            currentRunId = "";
            setSelectedRunId("");
          }
        } catch {
          currentRunId = "";
          setSelectedRunId("");
        }
      } else {
        currentRunId = "";
        setSelectedRunId("");
      }

      const [
        defsData,
        runData,
        stepsData,
        approvalsData,
        tokensData,
        provsData,
        depsData,
        diffData,
        teamsData,
      ] = await Promise.allSettled([
        workflowsApi.listDefinitions().catch(() => []),
        currentRunId ? runsApi.getRun(currentRunId) : Promise.resolve(null),
        currentRunId ? runsApi.listStepRuns(currentRunId) : Promise.resolve([]),
        currentRunId ? approvalsApi.listApprovals(currentRunId) : Promise.resolve([]),
        workflowsApi.getTokenSummary().catch(() => null),
        workflowsApi.listProviders().catch(() => []),
        deploymentsApi.listDeployments(currentProjId).catch(() => []),
        currentWorkspaceId ? workspacesApi.getStructuredDiff(currentWorkspaceId).catch(() => null) : Promise.resolve(null),
        currentProjId ? workflowsApi.listTeams(currentProjId).catch(() => []) : Promise.resolve([]),
      ]);

      if (defsData.status === "fulfilled" && defsData.value.length > 0 && !activeDefinitionId) {
        setActiveDefinitionId(defsData.value[0].id);
      }
      setRun(runData.status === "fulfilled" ? runData.value : null);
      setSteps(stepsData.status === "fulfilled" && stepsData.value ? stepsData.value : []);
      if (approvalsData.status === "fulfilled" && approvalsData.value.length > 0) {
        setApproval(approvalsData.value.find((a) => a.status === "PENDING") || approvalsData.value[0]);
      } else {
        setApproval(null);
      }
      setTeam(teamsData.status === "fulfilled" && teamsData.value.length > 0 ? teamsData.value[0] : null);
      if (tokensData.status === "fulfilled") setTokenSummary(tokensData.value);
      if (provsData.status === "fulfilled") setProviders(provsData.value);
      setDeployment(
        depsData.status === "fulfilled" && depsData.value.length > 0 ? depsData.value[0] : null
      );
      setDiffs(
        diffData.status === "fulfilled" && diffData.value?.entries ? diffData.value.entries : []
      );
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : "网络请求失败";
      setSyncErrorMessage(`看板同步出现异常: ${msg}`);
    }
  }, [selectedProjectId, selectedRunId, activeWorkspaceId, activeDefinitionId]);

  useEffect(() => {
    syncDashboardData();
  }, [syncDashboardData]);

  const handleToggleDemoMode = () => {
    const nextMode = !isDemo;
    setDemoMode(nextMode);
    setIsDemo(nextMode);
    if (!nextMode) setTeam(null);
  };

  const handleRunWorkflow = async () => {
    setSyncErrorMessage(null);

    if (isDemo) {
      setIsRunningWorkflow(true);
      setApproval(MOCK_APPROVAL);
      setSteps(MOCK_STEPS);
      setTimeout(() => {
        syncDashboardData();
        setIsRunningWorkflow(false);
      }, 1000);
      return;
    }

    if (!selectedProjectId) {
      setIsCreateProjectModalOpen(true);
      setSyncErrorMessage("请先创建或选择受控研发项目后再启动执行");
      return;
    }

    // Phase 3: 打开 Orchestrator 结构化 Plan 规划与工具确权门禁模态框
    setIsPlanModalOpen(true);
  };

  const handlePlanConfirmedAndLaunch = async (plan: OrchestrationPlanView) => {
    setIsRunningWorkflow(true);
    setSyncErrorMessage(null);
    try {
      const newRun = await runsApi.startRun({
        projectId: selectedProjectId,
        definitionId: activeDefinitionId || "wf-enterprise-auth-delivery",
        idempotencyKey: `idem-${selectedProjectId}-${plan.planId}-${Date.now()}`,
        inputs: { planId: plan.planId, requirement: plan.requirement || plan.taskGoal || "", templateType: plan.templateType },
      });
      if (newRun?.id) {
        setRun(newRun);
        setSelectedRunId(newRun.id);
        setSteps([]);
        setApproval(null);
        setTimeout(() => syncDashboardData(), 800);
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : "请求异常";
      setSyncErrorMessage(`启动工作流失败: ${msg}`);
    } finally {
      setIsRunningWorkflow(false);
    }
  };

  const handleApprovalDecided = (decision: "APPROVED" | "REJECTED") => {
    if (isDemo) {
      if (approval) setApproval({ ...approval, status: decision, decision });
      if (run) setRun({ ...run, status: decision === "APPROVED" ? "RUNNING" : "FAILED" });
      return;
    }
    setTimeout(() => syncDashboardData(), 600);
  };

  const handleCardAction = (action: string) => {
    if (action.includes("Diff")) setActiveTab("diff");
    else if (action.includes("审批")) setActiveTab("dag");
    else setActiveTab("overview");
  };

  const activeRunId = run?.id || selectedRunId;

  return (
    <ProjectShell
      backendUp={backendUp}
      isDemo={isDemo}
      currentUser={currentUser}
      projects={projects}
      selectedProjectId={selectedProjectId}
      activeRunId={activeRunId}
      activeTab={activeTab}
      run={run}
      steps={steps}
      approval={approval}
      team={team}
      tokenSummary={tokenSummary}
      providers={providers}
      deployment={deployment}
      isRunningWorkflow={isRunningWorkflow}
      syncErrorMessage={syncErrorMessage}
      isAuthModalOpen={isAuthModalOpen}
      isCreateProjectModalOpen={isCreateProjectModalOpen}
      onSelectProject={setSelectedProjectId}
      onSelectTab={setActiveTab}
      onToggleDemoMode={handleToggleDemoMode}
      onOpenAuthModal={() => setIsAuthModalOpen(true)}
      onCloseAuthModal={() => setIsAuthModalOpen(false)}
      onAuthSuccess={(user) => {
        setCurrentUser(user);
        syncDashboardData();
      }}
      onOpenCreateProjectModal={() => setIsCreateProjectModalOpen(true)}
      onCloseCreateProjectModal={() => setIsCreateProjectModalOpen(false)}
      onProjectCreated={(newProject) => {
        setSelectedProjectId(newProject.id);
        if (newProject.workspaceId) setActiveWorkspaceId(newProject.workspaceId);
        syncDashboardData();
      }}
      onLogout={async () => {
        await authApi.logout();
        setCurrentUser(null);
        syncDashboardData();
      }}
      onRefreshAll={syncDashboardData}
      onRunWorkflow={handleRunWorkflow}
      onExportEvidence={async () => {
        if (!activeRunId) return;
        try {
          await runsApi.exportRunEvidenceFile(activeRunId);
        } catch (err: unknown) {
          const msg = err instanceof Error ? err.message : "请求异常";
          setSyncErrorMessage(`导出运行证据包失败: ${msg}`);
        }
      }}
      onApprovalDecided={handleApprovalDecided}
    >
      {activeTab === "overview" && (
        <div className="flex-1 space-y-4 pb-6 overflow-y-auto">
          <RunWorkspacePanel
            mode="overview-top"
            runId={activeRunId}
            workspaceId={activeWorkspaceId}
            steps={steps}
            team={team}
            isRunningWorkflow={isRunningWorkflow}
            onRunWorkflow={handleRunWorkflow}
            onRefresh={syncDashboardData}
            onCardAction={handleCardAction}
          />
          <ReviewSidebar
            mode="overview-middle"
            diffs={diffs}
            workspaceId={activeWorkspaceId}
            team={team}
            onRefresh={syncDashboardData}
            onCardAction={handleCardAction}
          />
          <DeploymentPanel
            mode="overview-bottom"
            providers={providers}
            tokenSummary={tokenSummary}
            deployment={deployment}
            projectId={selectedProjectId}
            onRefresh={syncDashboardData}
          />
        </div>
      )}

      {(activeTab === "dag" || activeTab === "swarm" || activeTab === "files") && (
        <RunWorkspacePanel
          mode={activeTab}
          runId={activeRunId}
          workspaceId={activeWorkspaceId}
          steps={steps}
          team={team}
          isRunningWorkflow={isRunningWorkflow}
          onRunWorkflow={handleRunWorkflow}
          onRefresh={syncDashboardData}
          onCardAction={handleCardAction}
        />
      )}

      {activeTab === "diff" && (
        <ReviewSidebar
          mode="diff"
          diffs={diffs}
          workspaceId={activeWorkspaceId}
          team={team}
          onRefresh={syncDashboardData}
          onCardAction={handleCardAction}
        />
      )}

      {activeTab === "operations" && (
        <div className="flex-1 overflow-y-auto pb-6">
          <RunOperationsPanel
            runId={activeRunId}
            onRefreshParent={syncDashboardData}
          />
        </div>
      )}

      {(activeTab === "providers" || activeTab === "sandbox") && (
        <DeploymentPanel
          mode={activeTab}
          providers={providers}
          tokenSummary={tokenSummary}
          deployment={deployment}
          projectId={selectedProjectId}
          onRefresh={syncDashboardData}
        />
      )}

      <OrchestratorPlanModal
        isOpen={isPlanModalOpen}
        onClose={() => setIsPlanModalOpen(false)}
        projectId={selectedProjectId}
        workspaceId={activeWorkspaceId}
        onPlanConfirmedAndLaunch={handlePlanConfirmedAndLaunch}
      />
    </ProjectShell>
  );
}
