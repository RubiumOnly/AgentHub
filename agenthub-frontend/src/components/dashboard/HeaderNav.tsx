"use client";

import React from "react";
import { UserView, ProjectView } from "@/types";
import {
  ShieldCheck,
  Cpu,
  FolderGit2,
  RefreshCw,
  Sparkles,
  User,
  LogOut,
  LogIn,
  AlertTriangle,
  Play,
} from "lucide-react";

interface HeaderNavProps {
  backendUp: boolean;
  circuitBreakerStatus?: string;
  activeProjectId?: string;
  activeRunId?: string;
  projects?: ProjectView[];
  onSelectProject?: (projectId: string) => void;
  currentUser?: UserView | null;
  isDemoMode: boolean;
  onToggleDemoMode: () => void;
  onOpenAuthModal: () => void;
  onLogout: () => void;
  onRefreshAll?: () => void;
  onRunWorkflow?: () => void;
  isRunning?: boolean;
}

export function HeaderNav({
  backendUp,
  circuitBreakerStatus = "CLOSED",
  activeProjectId = "proj-default",
  activeRunId,
  projects = [],
  onSelectProject,
  currentUser,
  isDemoMode,
  onToggleDemoMode,
  onOpenAuthModal,
  onLogout,
  onRefreshAll,
  onRunWorkflow,
  isRunning = false,
}: HeaderNavProps) {
  return (
    <header className="h-14 border-b border-zinc-800/80 bg-zinc-950/80 backdrop-blur-md px-4 md:px-6 flex items-center justify-between z-30 shrink-0">
      {/* Brand & Project context */}
      <div className="flex items-center space-x-3.5">
        <div className="relative flex items-center justify-center w-8 h-8 rounded-lg bg-indigo-600/90 border border-indigo-400/30 text-white font-mono font-bold text-sm shadow-md shadow-indigo-600/25">
          AH
          <div
            className={`absolute -bottom-0.5 -right-0.5 w-2 h-2 rounded-full ${
              backendUp ? "bg-emerald-400 ring-2 ring-zinc-950" : "bg-rose-500 ring-2 ring-zinc-950"
            }`}
          />
        </div>

        <div className="flex items-center space-x-2">
          <span className="font-semibold tracking-tight text-sm text-zinc-100">
            AgentHub
          </span>
          <span className="text-[11px] font-mono px-2 py-0.5 rounded-full bg-indigo-500/10 text-indigo-300 border border-indigo-500/20">
            v2.0
          </span>
        </div>

        {/* Project Selector & Active Run ID */}
        <div className="hidden md:flex items-center space-x-2 pl-3 border-l border-zinc-800/80 text-xs text-zinc-400">
          <FolderGit2 className="w-3.5 h-3.5 text-zinc-500 shrink-0" />
          {projects.length > 0 ? (
            <select
              value={activeProjectId}
              onChange={(e) => onSelectProject?.(e.target.value)}
              className="bg-zinc-900 border border-zinc-800 text-zinc-200 text-xs rounded-md px-2 py-1 font-mono focus:outline-none focus:border-indigo-500"
            >
              {projects.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.name} ({p.id})
                </option>
              ))}
            </select>
          ) : (
            <span className="font-mono text-[11px] text-zinc-300">{activeProjectId}</span>
          )}
          {activeRunId && (
            <>
              <span className="text-zinc-600">/</span>
              <span className="font-mono text-[11px] text-zinc-400 truncate max-w-[140px]">
                {activeRunId}
              </span>
            </>
          )}
        </div>
      </div>

      {/* Right Controls & Infrastructure Pills */}
      <div className="flex items-center space-x-2 text-xs">
        {/* Mode Badge (Live Product vs Explicit Demo Mode) */}
        {isDemoMode ? (
          <button
            onClick={onToggleDemoMode}
            title="点击切换到真实产品模式"
            className="flex items-center space-x-1.5 px-2.5 py-1 rounded-full bg-amber-950/60 border border-amber-500/40 text-amber-300 text-[11px] font-medium transition hover:bg-amber-900/60"
          >
            <AlertTriangle className="w-3 h-3 text-amber-400 animate-pulse" />
            <span>离线演示/模拟模式</span>
          </button>
        ) : (
          <button
            onClick={onToggleDemoMode}
            title="点击切换到离线演示模式"
            className="hidden sm:flex items-center space-x-1.5 px-2.5 py-1 rounded-full bg-emerald-950/40 border border-emerald-500/30 text-emerald-300 text-[11px] font-medium transition hover:bg-emerald-900/40"
          >
            <span className="w-1.5 h-1.5 rounded-full bg-emerald-400" />
            <span>真实产品模式</span>
          </button>
        )}

        {/* Backend Status Pill */}
        <div className="hidden lg:flex items-center space-x-2 px-2.5 py-1 rounded-full bg-zinc-900/90 border border-zinc-800 text-zinc-300">
          <span
            className={`w-2 h-2 rounded-full ${
              backendUp ? "bg-emerald-400 animate-pulse" : "bg-rose-500"
            }`}
          />
          <span className="font-mono text-[11px]">
            {backendUp ? "API UP" : "API OFFLINE"}
          </span>
        </div>

        {/* Refresh Button */}
        {onRefreshAll && (
          <button
            onClick={onRefreshAll}
            title="刷新数据看板"
            className="p-1.5 rounded-lg border border-zinc-800 hover:border-zinc-700 bg-zinc-900/80 text-zinc-400 hover:text-zinc-200 transition-colors"
          >
            <RefreshCw className="w-3.5 h-3.5" />
          </button>
        )}

        {/* User Auth Action */}
        {currentUser ? (
          <div className="flex items-center space-x-2 pl-1 border-l border-zinc-800/80">
            <div className="flex items-center space-x-1.5 px-2 py-1 rounded-lg bg-zinc-900 border border-zinc-800 text-zinc-200 text-xs font-mono">
              <User className="w-3.5 h-3.5 text-indigo-400" />
              <span className="truncate max-w-[110px]">
                {currentUser.displayName || currentUser.email.split("@")[0]}
              </span>
            </div>
            <button
              onClick={onLogout}
              title="退出登录"
              className="p-1.5 rounded-lg border border-zinc-800 hover:border-rose-500/40 bg-zinc-900/80 text-zinc-400 hover:text-rose-400 transition-colors"
            >
              <LogOut className="w-3.5 h-3.5" />
            </button>
          </div>
        ) : (
          <button
            onClick={onOpenAuthModal}
            className="flex items-center space-x-1.5 px-3 py-1.5 rounded-lg border border-indigo-500/40 bg-indigo-950/30 hover:bg-indigo-900/50 text-indigo-300 font-medium text-xs transition"
          >
            <LogIn className="w-3.5 h-3.5" />
            <span>登录 / 注册</span>
          </button>
        )}

        {/* Run Workflow CTA */}
        {onRunWorkflow && (
          <button
            onClick={onRunWorkflow}
            disabled={isRunning}
            className="flex items-center space-x-1.5 px-3.5 py-1.5 rounded-lg bg-indigo-600 hover:bg-indigo-500 text-white font-medium text-xs shadow-md shadow-indigo-600/20 transition-all disabled:opacity-50"
          >
            {isRunning ? (
              <>
                <Sparkles className="w-3.5 h-3.5 animate-spin" />
                <span>执行中...</span>
              </>
            ) : (
              <>
                <Play className="w-3.5 h-3.5" />
                <span>触发执行</span>
              </>
            )}
          </button>
        )}
      </div>
    </header>
  );
}
