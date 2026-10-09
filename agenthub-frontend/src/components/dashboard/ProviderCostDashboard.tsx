"use client";

import React, { useState } from "react";
import { BentoCard } from "@/components/common/BentoCard";
import { StatusBadge } from "@/components/common/StatusBadge";
import { ProviderView, TokenSummaryView, RouteDecisionView } from "@/types";
import { apiClient, isDemoMode, MOCK_TOKEN_SUMMARY, MOCK_PROVIDERS } from "@/services/api";
import {
  Cpu,
  Coins,
  Activity,
  Zap,
  CheckCircle,
  AlertTriangle,
  RotateCcw,
  Sparkles,
  ArrowRight,
  ShieldCheck,
} from "lucide-react";

interface ProviderCostDashboardProps {
  providers?: ProviderView[];
  tokenSummary?: TokenSummaryView | null;
  onRefresh?: () => void;
  className?: string;
}

export function ProviderCostDashboard({
  providers = [],
  tokenSummary,
  onRefresh,
  className = "",
}: ProviderCostDashboardProps) {
  const [testCapability, setTestCapability] = useState("code,reasoning");
  const [routeDecision, setRouteDecision] = useState<RouteDecisionView | null>(null);
  const [isRouting, setIsRouting] = useState(false);

  const isDemo = isDemoMode();
  const summary = tokenSummary || (isDemo ? MOCK_TOKEN_SUMMARY : {
    totalTokens: 0,
    totalPromptTokens: 0,
    totalCompletionTokens: 0,
    totalEstimatedCost: 0,
    totalInvocations: 0,
  });

  const totalTokens = summary.totalTokens;
  const promptTokens = summary.totalPromptTokens;
  const completionTokens = summary.totalCompletionTokens;
  const totalCost = summary.totalEstimatedCost;

  const handleSimulateRoute = async () => {
    setIsRouting(true);
    try {
      const decision = await apiClient.evaluateRoute(testCapability);
      if (decision) {
        setRouteDecision(decision);
      } else if (isDemo) {
        setRouteDecision({
          selectedProviderId: "prov-deepseek",
          selectedModel: "deepseek-chat",
          reason: "动态加权最优：具备 code+reasoning 能力，优先级 100，健康延迟 245ms，熔断器 CLOSED",
          candidateChain: ["deepseek-chat", "claude-3-5-sonnet", "gemini-1.5-flash"],
        });
      }
    } catch (err: any) {
      if (isDemo) {
        setRouteDecision({
          selectedProviderId: "prov-deepseek",
          selectedModel: "deepseek-chat",
          reason: "动态加权最优：具备 code+reasoning 能力，优先级 100，健康延迟 245ms",
          candidateChain: ["deepseek-chat", "claude-3-5-sonnet"],
        });
      } else {
        setRouteDecision({
          selectedProviderId: "NONE",
          selectedModel: "N/A",
          reason: `路由评估失败: ${err.message || "后端不可用或未配置可用 Provider"}`,
          candidateChain: [],
        });
      }
    } finally {
      setIsRouting(false);
    }
  };

  return (
    <BentoCard
      title="模型 Provider 动态路由与 Token 成本 (Router & Cost)"
      subtitle="阶段 5 抽象：统一 SPI 契约、多维度加权路由、三态熔断器与精细化成本核算"
      icon={<Cpu className="w-4 h-4 text-indigo-400" />}
      badge={
        <div className="flex items-center space-x-1.5">
          <span className="text-[10px] font-mono px-2 py-0.5 rounded-full bg-zinc-800 text-zinc-300 border border-zinc-700/60">
            Providers: {providers.length}
          </span>
          <span className="text-[10px] font-mono px-2 py-0.5 rounded-full bg-emerald-950/40 text-emerald-300 border border-emerald-500/30">
            Total: ${(totalCost).toFixed(4)}
          </span>
        </div>
      }
      actions={
        onRefresh && (
          <button
            onClick={onRefresh}
            title="刷新 Provider 状态"
            className="p-1.5 rounded-lg border border-zinc-800 hover:border-zinc-700 bg-zinc-900/80 text-zinc-400 hover:text-zinc-200"
          >
            <RotateCcw className="w-3 h-3" />
          </button>
        )
      }
      className={`h-full ${className}`}
      bodyClassName="flex flex-col p-4 space-y-4 overflow-y-auto"
    >
      {/* Top Banner: Token & Cost Breakdown */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-2.5 shrink-0">
        <div className="p-3 rounded-xl border border-zinc-800/80 bg-zinc-950/60">
          <div className="text-[11px] text-zinc-400 font-medium">累计调用次数</div>
          <div className="text-lg font-bold font-mono text-zinc-100 mt-1">
            {tokenSummary?.totalInvocations || 42}{" "}
            <span className="text-xs font-normal text-zinc-500">runs</span>
          </div>
        </div>

        <div className="p-3 rounded-xl border border-zinc-800/80 bg-zinc-950/60">
          <div className="text-[11px] text-zinc-400 font-medium">输入 Token (Prompt)</div>
          <div className="text-lg font-bold font-mono text-sky-400 mt-1">
            {(promptTokens / 1000).toFixed(1)}k
          </div>
        </div>

        <div className="p-3 rounded-xl border border-zinc-800/80 bg-zinc-950/60">
          <div className="text-[11px] text-zinc-400 font-medium">输出 Token (Completion)</div>
          <div className="text-lg font-bold font-mono text-purple-400 mt-1">
            {(completionTokens / 1000).toFixed(1)}k
          </div>
        </div>

        <div className="p-3 rounded-xl border border-emerald-500/30 bg-emerald-950/20">
          <div className="text-[11px] text-emerald-300 font-medium flex items-center space-x-1">
            <Coins className="w-3.5 h-3.5" />
            <span>实时核算总费用</span>
          </div>
          <div className="text-lg font-bold font-mono text-emerald-400 mt-1">
            ${totalCost.toFixed(4)}
          </div>
        </div>
      </div>

      {/* Provider Matrix Cards */}
      <div className="space-y-2.5">
        <div className="flex items-center justify-between text-xs font-medium text-zinc-400">
          <span>注册 Provider 节点与三态熔断器状态</span>
          <span className="text-[11px] font-mono text-zinc-500">
            自动脱敏: sk-*** / env:INJECTION
          </span>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-2.5">
          {providers.map((p) => {
            const isClosed = p.circuitStatus === "CLOSED";
            const isHalfOpen = p.circuitStatus === "HALF_OPEN";

            return (
              <div
                key={p.id}
                className="rounded-xl border border-zinc-800/80 bg-zinc-950/70 p-3 hover:border-zinc-700 transition"
              >
                <div className="flex items-center justify-between mb-1.5">
                  <div className="truncate">
                    <span className="font-semibold text-xs text-zinc-100 truncate block">
                      {p.model}
                    </span>
                    <span className="text-[10px] font-mono text-zinc-500">
                      {p.providerType}
                    </span>
                  </div>
                  <StatusBadge status={p.circuitStatus} size="sm" />
                </div>

                <div className="mt-2 space-y-1 font-mono text-[10px] text-zinc-400">
                  <div className="flex justify-between">
                    <span>平均延迟:</span>
                    <span
                      className={`font-semibold ${
                        p.avgLatencyMs < 200
                          ? "text-emerald-400"
                          : p.avgLatencyMs < 400
                          ? "text-sky-400"
                          : "text-amber-400"
                      }`}
                    >
                      {p.avgLatencyMs} ms
                    </span>
                  </div>

                  <div className="flex justify-between">
                    <span>权重 / 优先级:</span>
                    <span className="text-zinc-200">
                      w={p.weight} / prio={p.priority}
                    </span>
                  </div>

                  <div className="flex justify-between">
                    <span>牌价 (In/Out 1M):</span>
                    <span className="text-zinc-300">
                      ${p.costPerMillionInput} / ${p.costPerMillionOutput}
                    </span>
                  </div>

                  {p.capabilities && (
                    <div className="pt-1.5 border-t border-zinc-800/60 flex flex-wrap gap-1">
                      {p.capabilities.split(",").map((cap) => (
                        <span
                          key={cap}
                          className="px-1.5 py-0.2 rounded bg-zinc-800/80 text-zinc-400 text-[9px]"
                        >
                          {cap}
                        </span>
                      ))}
                    </div>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      </div>

      {/* Dynamic Route Simulation Box */}
      <div className="rounded-xl border border-zinc-800/90 bg-zinc-950/80 p-3.5 space-y-2.5">
        <div className="flex items-center justify-between text-xs">
          <div className="flex items-center space-x-1.5 font-semibold text-zinc-200">
            <Zap className="w-3.5 h-3.5 text-amber-400" />
            <span>智能动态加权路由决策推演</span>
          </div>
          <span className="text-[10px] font-mono text-zinc-500">POST /api/providers/route</span>
        </div>

        <div className="flex items-center space-x-2">
          <input
            type="text"
            value={testCapability}
            onChange={(e) => setTestCapability(e.target.value)}
            className="flex-1 rounded-lg border border-zinc-800 bg-zinc-900 px-2.5 py-1.5 text-xs text-zinc-200 font-mono focus:border-indigo-500 focus:outline-none"
            placeholder="所需能力集，如 code,reasoning 或 fast"
          />
          <button
            onClick={handleSimulateRoute}
            disabled={isRouting}
            className="px-3.5 py-1.5 rounded-lg bg-indigo-600 hover:bg-indigo-500 text-white font-medium text-xs shadow-md shadow-indigo-600/20 transition disabled:opacity-50 flex items-center space-x-1.5 shrink-0"
          >
            <Sparkles className="w-3.5 h-3.5" />
            <span>{isRouting ? "推演中..." : "推演路由"}</span>
          </button>
        </div>

        {routeDecision && (
          <div className="rounded-lg border border-indigo-500/30 bg-indigo-950/20 p-2.5 text-xs space-y-1">
            <div className="flex items-center space-x-2 font-semibold text-indigo-300">
              <CheckCircle className="w-4 h-4 text-emerald-400" />
              <span>决策优选节点: {routeDecision.selectedModel}</span>
              <span className="text-[10px] font-mono text-zinc-400">
                ({routeDecision.selectedProviderId})
              </span>
            </div>
            <p className="text-[11px] text-zinc-300 leading-relaxed">
              {routeDecision.reason}
            </p>
            {routeDecision.candidateChain && (
              <div className="pt-1 text-[10px] font-mono text-zinc-400 flex items-center space-x-1.5">
                <span>备选容灾链路:</span>
                {routeDecision.candidateChain.map((m, i) => (
                  <span key={i} className="flex items-center space-x-1">
                    <span className="text-zinc-200">{m}</span>
                    {i < (routeDecision.candidateChain?.length || 0) - 1 && (
                      <ArrowRight className="w-2.5 h-2.5 text-zinc-600" />
                    )}
                  </span>
                ))}
              </div>
            )}
          </div>
        )}
      </div>
    </BentoCard>
  );
}
