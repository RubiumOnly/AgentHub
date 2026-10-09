"use client";

import React from "react";
import {
  WorkflowRunView,
  StepRunView,
  TeamView,
  TokenSummaryView,
  ProviderView,
  DeploymentResponse,
} from "@/types";
import { MetricCard } from "@/components/common/MetricCard";
import { isDemoMode } from "@/services/api";
import {
  Activity,
  Users,
  Coins,
  Cpu,
  Server,
} from "lucide-react";

interface MetricBentoGridProps {
  run?: WorkflowRunView | null;
  steps?: StepRunView[];
  team?: TeamView | null;
  tokenSummary?: TokenSummaryView | null;
  providers?: ProviderView[];
  deployment?: DeploymentResponse | null;
  onCardClick?: (tabKey: string) => void;
}

export function MetricBentoGrid({
  run,
  steps = [],
  team,
  tokenSummary,
  providers = [],
  deployment,
  onCardClick,
}: MetricBentoGridProps) {
  const isDemo = isDemoMode();

  // Compute step stats
  const totalSteps = steps.length || (isDemo ? 7 : 0);
  const completedSteps = steps.filter((s) => s.status === "SUCCEEDED").length;
  const waitingApproval = steps.some((s) => s.status === "WAITING_APPROVAL");
  const runStatus = waitingApproval
    ? "WAITING_APPROVAL"
    : run?.status || (isDemo ? "RUNNING" : "IDLE");

  // Provider lowest latency
  const activeProviders = providers.filter((p) => p.status === "ACTIVE");
  const minLatency =
    activeProviders.length > 0
      ? Math.min(...activeProviders.map((p) => p.avgLatencyMs || 999))
      : (isDemo ? 245 : 0);

  // Format currency
  const cost = tokenSummary?.totalEstimatedCost || (isDemo ? 0.0218 : 0);
  const costStr = `$${cost.toFixed(4)}`;
  const totalTokens = tokenSummary?.totalTokens || (isDemo ? 116370 : 0);

  return (
    <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-5 gap-3 shrink-0">
      {/* 1. Workflow Execution Status */}
      <MetricCard
        title="工作流执行状态"
        value={runStatus}
        tag={`${completedSteps}/${totalSteps} Steps`}
        icon={<Activity className="w-4 h-4" />}
        subtext={
          waitingApproval
            ? "⚠️ 挂起中: 待人工审查审批"
            : `当前 Run: ${run?.id || "run-exec-94218a"}`
        }
        statusDotColor={
          waitingApproval
            ? "bg-amber-400"
            : runStatus === "SUCCEEDED"
            ? "bg-emerald-400"
            : "bg-sky-400"
        }
        onClick={() => onCardClick?.("canvas")}
      />

      {/* 2. Multi-Agent Swarm Mode */}
      <MetricCard
        title="协同网络拓扑"
        value={team?.topology || "HIERARCHICAL"}
        tag={`${team?.members?.length || 5} Agents`}
        icon={<Users className="w-4 h-4" />}
        subtext={`Leader: ${team?.leaderAgentId || "Orchestrator"} (配额 ${team?.maxTurns || 12} 轮)`}
        statusDotColor="bg-indigo-400"
        onClick={() => onCardClick?.("swarm")}
      />

      {/* 3. Token Accounting & Real-time Cost */}
      <MetricCard
        title="Token 消耗与实时成本"
        value={costStr}
        tag={`${(totalTokens / 1000).toFixed(1)}k Tokens`}
        icon={<Coins className="w-4 h-4" />}
        subtext={`Prompt: ${((tokenSummary?.totalPromptTokens || 84920) / 1000).toFixed(1)}k / Out: ${((tokenSummary?.totalCompletionTokens || 31450) / 1000).toFixed(1)}k`}
        statusDotColor="bg-emerald-400"
        onClick={() => onCardClick?.("providers")}
      />

      {/* 4. Model Provider & Latency */}
      <MetricCard
        title="Provider 路由与延迟"
        value={`${minLatency} ms`}
        tag={`${activeProviders.length || 4} 在线`}
        icon={<Cpu className="w-4 h-4" />}
        subtext="熔断器: CLOSED 极速负载分流"
        statusDotColor="bg-emerald-400"
        onClick={() => onCardClick?.("providers")}
      />

      {/* 5. Sandbox Deployment Runtime */}
      <MetricCard
        title="沙箱预览与部署端口"
        value={
          deployment?.port
            ? `:${deployment.port}`
            : deployment?.status === "RUNNING"
            ? ":18080"
            : "STANDBY"
        }
        tag={deployment?.status || "RUNNING"}
        icon={<Server className="w-4 h-4" />}
        subtext={
          deployment?.status === "RUNNING"
            ? "非特权隔离进程 / 端口原子互斥"
            : "未启动部署沙箱"
        }
        statusDotColor={
          deployment?.status === "RUNNING" ? "bg-emerald-400" : "bg-zinc-500"
        }
        onClick={() => onCardClick?.("sandbox")}
      />
    </div>
  );
}
