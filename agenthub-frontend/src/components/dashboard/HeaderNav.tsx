"use client";

import React from "react";
import {
  ShieldCheck,
  Cpu,
  Layers,
  FolderGit2,
  RefreshCw,
  Sparkles,
} from "lucide-react";

interface HeaderNavProps {
  backendUp: boolean;
  circuitBreakerStatus?: string;
  activeRunId?: string;
  onRefreshAll?: () => void;
  onRunWorkflow?: () => void;
  isRunning?: boolean;
}

export function HeaderNav({
  backendUp,
  circuitBreakerStatus = "CLOSED",
  activeRunId = "run-exec-94218a",
  onRefreshAll,
  onRunWorkflow,
  isRunning = false,
}: HeaderNavProps) {
  return (
    <header className="h-14 border-b border-zinc-800/80 bg-zinc-950/80 backdrop-blur-md px-6 flex items-center justify-between z-30 shrink-0">
      {/* Brand & Project context */}
      <div className="flex items-center space-x-3.5">
        <div className="relative flex items-center justify-center w-8 h-8 rounded-lg bg-indigo-600/90 border border-indigo-400/30 text-white font-mono font-bold text-sm shadow-md shadow-indigo-600/25">
          AH
          <div className="absolute -bottom-0.5 -right-0.5 w-2 h-2 rounded-full bg-emerald-400 ring-2 ring-zinc-950" />
        </div>

        <div className="flex items-center space-x-2">
          <span className="font-semibold tracking-tight text-sm text-zinc-100">
            AgentHub
          </span>
          <span className="text-[11px] font-mono px-2 py-0.5 rounded-full bg-indigo-500/10 text-indigo-300 border border-indigo-500/20">
            Executive Bento
          </span>
        </div>

        <div className="hidden md:flex items-center space-x-2 pl-3 border-l border-zinc-800/80 text-xs text-zinc-400">
          <FolderGit2 className="w-3.5 h-3.5 text-zinc-500" />
          <span className="font-mono text-[11px] text-zinc-300">proj-default</span>
          <span className="text-zinc-600">/</span>
          <span className="font-mono text-[11px] text-zinc-400 truncate max-w-[140px]">
            {activeRunId}
          </span>
        </div>
      </div>

      {/* Real-time Infrastructure Pills */}
      <div className="flex items-center space-x-2.5 text-xs">
        {/* Backend status */}
        <div className="flex items-center space-x-2 px-3 py-1 rounded-full bg-zinc-900/90 border border-zinc-800/80 text-zinc-300">
          <span
            className={`w-2 h-2 rounded-full ${
              backendUp ? "bg-emerald-400 animate-pulse" : "bg-amber-400"
            }`}
          />
          <span className="font-mono text-[11px]">
            Spring Boot 3.3 : {backendUp ? "UP" : "CONNECTED"}
          </span>
        </div>

        {/* Dynamic Provider Circuit Breaker */}
        <div className="hidden sm:flex items-center space-x-1.5 px-3 py-1 rounded-full bg-zinc-900/90 border border-zinc-800/80 text-zinc-300">
          <Cpu className="w-3.5 h-3.5 text-indigo-400" />
          <span className="text-[11px]">Router Circuit:</span>
          <span
            className={`font-mono text-[10px] font-medium px-1.5 py-0.2 rounded ${
              circuitBreakerStatus === "CLOSED"
                ? "bg-emerald-950/50 text-emerald-300"
                : circuitBreakerStatus === "HALF_OPEN"
                ? "bg-amber-950/50 text-amber-300"
                : "bg-rose-950/50 text-rose-300"
            }`}
          >
            {circuitBreakerStatus}
          </span>
        </div>

        {/* Security & Sandbox firewall */}
        <div className="hidden lg:flex items-center space-x-1.5 px-2.5 py-1 rounded-full bg-zinc-900/90 border border-zinc-800/80 text-zinc-400">
          <ShieldCheck className="w-3.5 h-3.5 text-emerald-400" />
          <span className="text-[11px]">Sandboxed Non-Root</span>
        </div>

        {/* Global Action: Refresh */}
        {onRefreshAll && (
          <button
            onClick={onRefreshAll}
            title="刷新数据看板"
            className="p-1.5 rounded-lg border border-zinc-800 hover:border-zinc-700 bg-zinc-900/80 text-zinc-400 hover:text-zinc-200 transition-colors"
          >
            <RefreshCw className="w-3.5 h-3.5" />
          </button>
        )}

        {/* Global Action: Quick Run */}
        {onRunWorkflow && (
          <button
            onClick={onRunWorkflow}
            disabled={isRunning}
            className="flex items-center space-x-1.5 px-3 py-1.5 rounded-lg bg-indigo-600 hover:bg-indigo-500 text-white font-medium text-xs shadow-md shadow-indigo-600/20 transition-all disabled:opacity-50"
          >
            <Sparkles className="w-3.5 h-3.5" />
            <span>{isRunning ? "协同中..." : "触发执行"}</span>
          </button>
        )}
      </div>
    </header>
  );
}
