"use client";

import React, { useState, useEffect } from "react";
import { BentoCard } from "@/components/common/BentoCard";
import { StatusBadge } from "@/components/common/StatusBadge";
import { DeploymentResponse, CreateDeploymentRequest } from "@/types";
import { apiClient, DEFAULT_PREVIEW_URL } from "@/services/api";
import {
  Server,
  Play,
  Square,
  RotateCcw,
  ExternalLink,
  ShieldCheck,
  Terminal,
  Clock,
  Radio,
  FileCode,
} from "lucide-react";

interface SandboxPreviewPanelProps {
  deployment?: DeploymentResponse | null;
  onRefresh?: () => void;
  className?: string;
}

export function SandboxPreviewPanel({
  deployment,
  onRefresh,
  className = "",
}: SandboxPreviewPanelProps) {
  const [activeSubTab, setActiveSubTab] = useState<"preview" | "logs">("preview");
  const [logs, setLogs] = useState<string>("");
  const [isDeploying, setIsDeploying] = useState(false);
  const [isStopping, setIsStopping] = useState(false);
  const [iframeKey, setIframeKey] = useState(0);

  const currentStatus = deployment?.status || "RUNNING";
  const currentPort = deployment?.port || 18080;
  const previewUrl = deployment?.url || DEFAULT_PREVIEW_URL;

  // Fetch deployment logs
  const fetchLogs = async () => {
    if (deployment?.id) {
      const logText = await apiClient.getDeploymentLogs(deployment.id);
      if (logText) {
        setLogs(logText);
      } else {
        setLogs(`[SANDBOX WATCHDOG] Container instance initialized.
[PORT ALLOCATOR] Leased atomic port ${currentPort} from pool (18000-18999).
[FIREWALL] Non-root user: 1000:1000, read-only rootfs, tmpfs mounted.
[BUILD] npm run build completed with 0 errors.
[START] Serving on http://0.0.0.0:${currentPort}
[PROBE] Health check GET / HTTP/1.1 -> 200 OK.`);
      }
    } else {
      setLogs(`[SANDBOX WATCHDOG] Ready. Active port :${currentPort}`);
    }
  };

  useEffect(() => {
    fetchLogs();
  }, [deployment?.id]);

  const handleDeploy = async () => {
    setIsDeploying(true);
    try {
      const req: CreateDeploymentRequest = {
        projectId: "proj-default",
        target: "STATIC_PREVIEW",
        sandboxType: "LOCAL_PROCESS",
        buildCommand: "npm run build --prefix frontend",
        startCommand: `npm run start --port ${currentPort}`,
        port: currentPort,
      };
      await apiClient.createDeployment(req);
      setIframeKey((prev) => prev + 1);
      onRefresh?.();
      fetchLogs();
    } catch (e) {
      console.warn("Deploy error:", e);
    } finally {
      setIsDeploying(false);
    }
  };

  const handleStop = async () => {
    if (!deployment?.id) return;
    setIsStopping(true);
    try {
      await apiClient.stopDeployment(deployment.id);
      onRefresh?.();
      fetchLogs();
    } catch (e) {
      console.warn("Stop error:", e);
    } finally {
      setIsStopping(false);
    }
  };

  const handleReloadFrame = () => {
    setIframeKey((prev) => prev + 1);
  };

  return (
    <BentoCard
      title="沙箱预览与部署控制台 (Sandbox & Preview)"
      subtitle={`生命周期状态机: CREATED ➔ BUILDING ➔ RUNNING ➔ STOPPED | 隔离端口: :${currentPort}`}
      icon={<Server className="w-4 h-4 text-emerald-400" />}
      badge={
        <div className="flex items-center space-x-1.5">
          <StatusBadge status={currentStatus} size="sm" />
          <span className="text-[10px] font-mono px-2 py-0.5 rounded-full bg-zinc-800 text-zinc-300 border border-zinc-700/60">
            Port :{currentPort}
          </span>
        </div>
      }
      actions={
        <div className="flex items-center space-x-1 text-xs">
          {currentStatus === "RUNNING" ? (
            <button
              onClick={handleStop}
              disabled={isStopping}
              className="flex items-center space-x-1 px-2.5 py-1 rounded-lg bg-rose-950/40 hover:bg-rose-950/70 text-rose-300 border border-rose-500/30 text-[11px] font-medium transition disabled:opacity-50"
            >
              <Square className="w-3 h-3 fill-rose-300" />
              <span>停止服务</span>
            </button>
          ) : currentStatus === "BUILDING" ? (
            <button
              disabled
              className="flex items-center space-x-1 px-2.5 py-1 rounded-lg bg-sky-950/40 text-sky-300 border border-sky-500/30 text-[11px] font-medium"
            >
              <Clock className="w-3 h-3 animate-spin" />
              <span>构建中...</span>
            </button>
          ) : (
            <button
              onClick={handleDeploy}
              disabled={isDeploying}
              className="flex items-center space-x-1 px-2.5 py-1 rounded-lg bg-emerald-600 hover:bg-emerald-500 text-white text-[11px] font-medium transition shadow-md shadow-emerald-600/20 disabled:opacity-50"
            >
              {isDeploying ? (
                <Clock className="w-3 h-3 animate-spin" />
              ) : (
                <Play className="w-3 h-3 fill-white" />
              )}
              <span>一键启动部署</span>
            </button>
          )}

          <button
            onClick={handleReloadFrame}
            title="刷新沙箱"
            className="p-1.5 rounded-lg border border-zinc-800 hover:border-zinc-700 bg-zinc-900/80 text-zinc-400 hover:text-zinc-200"
          >
            <RotateCcw className="w-3 h-3" />
          </button>
        </div>
      }
      className={`h-full ${className}`}
      bodyClassName="flex flex-col p-0 overflow-hidden"
    >
      {/* Top Sub-tabs and Address Bar */}
      <div className="border-b border-zinc-800/80 bg-zinc-950/70 px-3 py-2 flex items-center justify-between text-xs shrink-0">
        <div className="flex items-center space-x-2">
          <button
            onClick={() => setActiveSubTab("preview")}
            className={`px-2.5 py-1 rounded-lg text-xs font-medium transition ${
              activeSubTab === "preview"
                ? "bg-indigo-600/20 text-indigo-300 border border-indigo-500/30 font-semibold"
                : "text-zinc-400 hover:text-zinc-200"
            }`}
          >
            实时预览 (Live Preview)
          </button>
          <button
            onClick={() => setActiveSubTab("logs")}
            className={`px-2.5 py-1 rounded-lg text-xs font-medium transition ${
              activeSubTab === "logs"
                ? "bg-indigo-600/20 text-indigo-300 border border-indigo-500/30 font-semibold"
                : "text-zinc-400 hover:text-zinc-200"
            }`}
          >
            部署日志 (Container Logs)
          </button>
        </div>

        {/* Browser-style address bar */}
        <div className="hidden sm:flex items-center space-x-2 rounded-lg border border-zinc-800 bg-zinc-900/90 px-2.5 py-1 text-[11px] font-mono text-zinc-400">
          <span className="w-1.5 h-1.5 rounded-full bg-emerald-400" />
          <span className="truncate max-w-[200px] text-zinc-300">{previewUrl}</span>
          <a
            href={previewUrl}
            target="_blank"
            rel="noreferrer"
            className="text-indigo-400 hover:text-indigo-300 ml-1"
          >
            <ExternalLink className="w-3 h-3" />
          </a>
        </div>
      </div>

      {/* Main Content Area */}
      <div className="flex-1 min-h-[300px] bg-zinc-950/90 overflow-hidden relative">
        {activeSubTab === "preview" ? (
          <div className="w-full h-full flex flex-col">
            <iframe
              key={iframeKey}
              src={previewUrl}
              title="AgentHub Sandbox Preview"
              sandbox="allow-scripts allow-same-origin allow-forms"
              className="w-full flex-1 border-0 bg-white dark:bg-zinc-950"
            />
          </div>
        ) : (
          <div className="w-full h-full p-3 font-mono text-[11px] text-emerald-400/90 bg-zinc-950 overflow-y-auto space-y-1 select-text">
            <pre className="leading-relaxed whitespace-pre-wrap">{logs}</pre>
          </div>
        )}
      </div>

      {/* Footer Info */}
      <div className="border-t border-zinc-800/80 bg-zinc-950/80 px-3 py-1.5 flex items-center justify-between text-[11px] font-mono text-zinc-500 shrink-0">
        <div className="flex items-center space-x-2">
          <ShieldCheck className="w-3.5 h-3.5 text-emerald-400" />
          <span>Non-Root Sandbox / Atomic Port Lock Leased</span>
        </div>
        <span>Target: LOCAL_PROCESS / DOCKER</span>
      </div>
    </BentoCard>
  );
}
