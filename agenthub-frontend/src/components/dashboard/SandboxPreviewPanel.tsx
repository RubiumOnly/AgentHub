"use client";

import React, { useState, useEffect } from "react";
import { BentoCard } from "@/components/common/BentoCard";
import { StatusBadge } from "@/components/common/StatusBadge";
import { DeploymentResponse, CreateDeploymentRequest } from "@/types";
import { apiClient } from "@/services/api";
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
  projectId?: string;
  onRefresh?: () => void;
  className?: string;
}

export function SandboxPreviewPanel({
  deployment,
  projectId = "proj-default",
  onRefresh,
  className = "",
}: SandboxPreviewPanelProps) {
  const [activeSubTab, setActiveSubTab] = useState<"preview" | "logs">("preview");
  const [logs, setLogs] = useState<string>("");
  const [isDeploying, setIsDeploying] = useState(false);
  const [isStopping, setIsStopping] = useState(false);
  const [iframeKey, setIframeKey] = useState(0);

  const currentStatus = deployment?.status || "STOPPED";
  const currentPort = deployment?.port;
  const previewUrl = deployment?.url;

  // Fetch authentic deployment logs from API
  const fetchLogs = async () => {
    if (deployment?.id) {
      try {
        const logText = await apiClient.getDeploymentLogs(deployment.id);
        if (logText && logText.trim().length > 0) {
          setLogs(logText);
        } else {
          setLogs(`[SANDBOX] 部署实例 ID: ${deployment.id}
[STATUS] 状态: ${deployment.status}
[TARGET] 部署目标: ${deployment.target} (${deployment.sandboxType})
[LOG] 暂无增量控制台输出。`);
        }
      } catch (err: any) {
        setLogs(`[ERROR] 无法读取部署日志: ${err.message || "网络或权限异常"}`);
      }
    } else {
      setLogs(`[SANDBOX] 当前项目暂无活跃部署实例。点击右上角“一键启动部署”即可在隔离沙箱中运行产物。`);
    }
  };

  useEffect(() => {
    fetchLogs();
  }, [deployment?.id]);

  const handleDeploy = async () => {
    setIsDeploying(true);
    try {
      const req: CreateDeploymentRequest = {
        projectId,
        target: "STATIC_PREVIEW",
        sandboxType: "LOCAL_PROCESS",
        buildCommand: "npm run build --prefix frontend",
        startCommand: "npm run start",
        port: currentPort || 18080,
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
      subtitle={`生命周期状态机: CREATED ➔ BUILDING ➔ RUNNING ➔ STOPPED ${
        currentPort ? `| 隔离端口: :${currentPort}` : ""
      }`}
      icon={<Server className="w-4 h-4 text-emerald-400" />}
      badge={
        <div className="flex items-center space-x-1.5">
          <StatusBadge status={currentStatus} size="sm" />
          {currentPort && (
            <span className="text-[10px] font-mono px-2 py-0.5 rounded-full bg-zinc-800 text-zinc-300 border border-zinc-700/60">
              Port :{currentPort}
            </span>
          )}
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
            部署日志 (Deploy Logs)
          </button>
        </div>

        {/* Browser Mock Address Bar */}
        <div className="flex items-center space-x-2">
          {previewUrl ? (
            <a
              href={previewUrl}
              target="_blank"
              rel="noreferrer"
              className="flex items-center space-x-1.5 px-2.5 py-1 rounded-lg bg-zinc-900 border border-zinc-800 text-[11px] font-mono text-zinc-300 hover:text-white hover:border-zinc-700 transition"
            >
              <span className="truncate max-w-[200px]">{previewUrl}</span>
              <ExternalLink className="w-3 h-3 text-zinc-400 shrink-0" />
            </a>
          ) : (
            <span className="text-[11px] font-mono text-zinc-500">
              [未就绪: 无活跃预览 URL]
            </span>
          )}
        </div>
      </div>

      {/* Main Content Area */}
      <div className="flex-1 bg-zinc-950/90 relative overflow-hidden flex flex-col">
        {activeSubTab === "preview" ? (
          previewUrl && currentStatus === "RUNNING" ? (
            <div className="w-full h-full relative">
              <iframe
                key={iframeKey}
                src={previewUrl}
                title="Sandbox Preview Frame"
                className="w-full h-full border-0 bg-white"
                sandbox="allow-scripts allow-same-origin allow-forms"
              />
            </div>
          ) : (
            <div className="flex-1 flex flex-col items-center justify-center p-6 text-center text-zinc-500">
              <Server className="w-10 h-10 text-zinc-700 mb-3" />
              <p className="text-xs font-medium text-zinc-300">
                {currentStatus === "BUILDING"
                  ? "沙箱正在构建应用产物并分配端口，请稍候..."
                  : "当前无运行中的沙箱实例"}
              </p>
              <p className="text-[11px] text-zinc-500 mt-1 max-w-sm">
                点击右上角“一键启动部署”即可在隔离沙箱环境中启动并实时预览
              </p>
            </div>
          )
        ) : (
          <div className="flex-1 p-3 font-mono text-[11px] text-zinc-300 whitespace-pre-wrap overflow-y-auto leading-relaxed select-text">
            {logs}
          </div>
        )}
      </div>

      {/* Bottom Status bar */}
      <div className="border-t border-zinc-800/60 bg-zinc-900/80 px-3 py-1.5 flex items-center justify-between text-[11px] font-mono text-zinc-500">
        <div className="flex items-center space-x-3">
          <span>Sandbox: {deployment?.sandboxType || "LOCAL_PROCESS"}</span>
          <span>Target: {deployment?.target || "STATIC_PREVIEW"}</span>
        </div>
        <div className="flex items-center space-x-1.5">
          <ShieldCheck className="w-3 h-3 text-emerald-400" />
          <span>Non-Root Sandbox Guard Active</span>
        </div>
      </div>
    </BentoCard>
  );
}
