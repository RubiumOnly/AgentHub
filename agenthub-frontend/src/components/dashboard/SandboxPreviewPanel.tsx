"use client";

import React, { useState, useEffect, useRef } from "react";
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
  Clock,
  AlertTriangle,
  PackageCheck,
  Cpu,
} from "lucide-react";

interface SandboxPreviewPanelProps {
  deployment?: DeploymentResponse | null;
  projectId?: string;
  onRefresh?: () => void;
  className?: string;
}

const SUPPORTED_TEMPLATES = [
  { value: "STATIC_WEB_TEMPLATE", label: "静态 Web 交付模板", target: "STATIC_PREVIEW" },
  { value: "NODE_SERVICE_TEMPLATE", label: "Node.js 服务模板", target: "LOCAL_PROCESS" },
  { value: "JAVA_SPRING_TEMPLATE", label: "Java Spring 模板", target: "LOCAL_PROCESS" },
] as const;

export function SandboxPreviewPanel({
  deployment,
  projectId = "proj-default",
  onRefresh,
  className = "",
}: SandboxPreviewPanelProps) {
  const [activeSubTab, setActiveSubTab] = useState<"preview" | "logs">("preview");
  const [logs, setLogs] = useState<string>("");
  const [selectedTemplate, setSelectedTemplate] = useState<string>("STATIC_WEB_TEMPLATE");
  const [artifactIdInput, setArtifactIdInput] = useState<string>("");
  const [actionError, setActionError] = useState<string | null>(null);
  const [isDeploying, setIsDeploying] = useState(false);
  const [isStopping, setIsStopping] = useState(false);
  const [iframeKey, setIframeKey] = useState(0);
  const logCursorRef = useRef<number>(0);
  const trackedDeploymentIdRef = useRef<string | null>(null);

  const currentStatus = deployment?.status || "STOPPED";
  const currentPort = deployment?.port;
  const previewUrl = deployment?.url;

  // Fetch authentic deployment logs with incremental offset cursor support
  const fetchLogs = async (resetCursor = false) => {
    if (deployment?.id) {
      if (resetCursor || trackedDeploymentIdRef.current !== deployment.id) {
        trackedDeploymentIdRef.current = deployment.id;
        logCursorRef.current = 0;
      }
      try {
        const cursor = logCursorRef.current;
        const logChunk = await apiClient.getDeploymentLogs(deployment.id, cursor > 0 ? cursor : undefined);
        if (cursor === 0) {
          if (logChunk && logChunk.trim().length > 0) {
            setLogs(logChunk);
            logCursorRef.current = logChunk.length;
          } else {
            setLogs(`[SANDBOX] 部署实例 ID: ${deployment.id}
[STATUS] 状态: ${deployment.status}
[TARGET] 部署目标: ${deployment.target} (${deployment.sandboxType})
[LOG] 暂无增量控制台输出。`);
          }
        } else if (logChunk && logChunk.length > 0) {
          setLogs((prev) => {
            const next = prev + logChunk;
            logCursorRef.current = next.length;
            return next;
          });
        }
      } catch (err: any) {
        setLogs(`[ERROR] 无法读取部署日志: ${err.message || "网络或权限异常"}`);
      }
    } else {
      trackedDeploymentIdRef.current = null;
      logCursorRef.current = 0;
      setLogs(`[SANDBOX] 当前项目暂无活跃部署实例。选择受控模板并点击右上角“一键启动部署”即可在隔离沙箱中运行产物。`);
    }
  };

  useEffect(() => {
    fetchLogs(true);
  }, [deployment?.id, deployment?.status]);

  const handleDeploy = async () => {
    setIsDeploying(true);
    setActionError(null);
    try {
      const matchedTemplate =
        SUPPORTED_TEMPLATES.find((t) => t.value === selectedTemplate) || SUPPORTED_TEMPLATES[0];
      const req: CreateDeploymentRequest = {
        projectId,
        templateType: matchedTemplate.value,
        target: matchedTemplate.target,
        sandboxType: "LOCAL_PROCESS",
        healthCheckPath: "/",
        ...(artifactIdInput.trim() ? { artifactId: artifactIdInput.trim() } : {}),
      };
      await apiClient.createDeployment(req);
      setIframeKey((prev) => prev + 1);
      onRefresh?.();
      await fetchLogs(true);
    } catch (e: any) {
      setActionError(e?.message || "启动沙箱部署失败，请检查产物门禁状态或端口配额");
    } finally {
      setIsDeploying(false);
    }
  };

  const handleStop = async () => {
    if (!deployment?.id) return;
    setIsStopping(true);
    setActionError(null);
    try {
      await apiClient.stopDeployment(deployment.id);
      onRefresh?.();
      await fetchLogs(true);
    } catch (e: any) {
      setActionError(e?.message || "停止沙箱服务失败");
    } finally {
      setIsStopping(false);
    }
  };

  const handleReloadFrame = () => {
    setIframeKey((prev) => prev + 1);
    fetchLogs(false);
  };

  const lifecycleLabel =
    deployment?.lifecycleTrace ||
    "CREATED ➔ BUILD ➔ START ➔ HEALTH_CHECK ➔ RUNNING ➔ STOP ➔ CLEANUP";

  return (
    <BentoCard
      title="沙箱预览与部署控制台 (Sandbox & Preview)"
      subtitle={`生命周期: ${lifecycleLabel} ${
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
          {deployment?.terminationReason && currentStatus !== "RUNNING" && (
            <span className="text-[10px] font-mono px-2 py-0.5 rounded-full bg-amber-950/60 text-amber-300 border border-amber-500/30">
              {deployment.terminationReason}
            </span>
          )}
        </div>
      }
      actions={
        <div className="flex items-center space-x-1.5 text-xs">
          <select
            aria-label="部署模板选择"
            value={selectedTemplate}
            onChange={(e) => setSelectedTemplate(e.target.value)}
            disabled={isDeploying || currentStatus === "RUNNING"}
            className="px-2 py-1 rounded-lg bg-zinc-900 border border-zinc-800 text-[11px] text-zinc-300 focus:outline-none focus:border-indigo-500/50 disabled:opacity-50"
          >
            {SUPPORTED_TEMPLATES.map((tpl) => (
              <option key={tpl.value} value={tpl.value}>
                {tpl.label}
              </option>
            ))}
          </select>

          <input
            type="text"
            value={artifactIdInput}
            onChange={(e) => setArtifactIdInput(e.target.value)}
            disabled={isDeploying || currentStatus === "RUNNING"}
            placeholder="绑定产物 ID (可选)"
            className="w-28 px-2 py-1 rounded-lg bg-zinc-900 border border-zinc-800 text-[11px] font-mono text-zinc-300 placeholder:text-zinc-600 focus:outline-none focus:border-indigo-500/50 disabled:opacity-50"
          />

          {currentStatus === "RUNNING" ? (
            <button
              onClick={handleStop}
              disabled={isStopping}
              className="flex items-center space-x-1 px-2.5 py-1 rounded-lg bg-rose-950/40 hover:bg-rose-950/70 text-rose-300 border border-rose-500/30 text-[11px] font-medium transition disabled:opacity-50"
            >
              <Square className="w-3 h-3 fill-rose-300" />
              <span>停止服务</span>
            </button>
          ) : currentStatus === "BUILDING" || currentStatus === "BUILD" || currentStatus === "START" || currentStatus === "HEALTH_CHECK" ? (
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
            title="刷新沙箱与增量日志"
            className="p-1.5 rounded-lg border border-zinc-800 hover:border-zinc-700 bg-zinc-900/80 text-zinc-400 hover:text-zinc-200"
          >
            <RotateCcw className="w-3 h-3" />
          </button>
        </div>
      }
      className={`h-full ${className}`}
      bodyClassName="flex flex-col p-0 overflow-hidden"
    >
      {/* Error Banner */}
      {actionError && (
        <div className="bg-rose-950/60 border-b border-rose-500/30 px-3 py-1.5 flex items-center justify-between text-[11px] text-rose-200 shrink-0">
          <div className="flex items-center space-x-1.5 truncate">
            <AlertTriangle className="w-3.5 h-3.5 text-rose-400 shrink-0" />
            <span className="truncate">{actionError}</span>
          </div>
          <button
            onClick={() => setActionError(null)}
            className="text-rose-400 hover:text-rose-200 ml-2 font-mono text-[10px]"
          >
            关闭
          </button>
        </div>
      )}

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
            onClick={() => {
              setActiveSubTab("logs");
              fetchLogs(false);
            }}
            className={`px-2.5 py-1 rounded-lg text-xs font-medium transition ${
              activeSubTab === "logs"
                ? "bg-indigo-600/20 text-indigo-300 border border-indigo-500/30 font-semibold"
                : "text-zinc-400 hover:text-zinc-200"
            }`}
          >
            部署日志 (Deploy Logs)
          </button>
        </div>

        {/* Browser Live Address Bar */}
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
                  : currentStatus === "FAILED"
                  ? `沙箱部署未就绪 (${deployment?.terminationReason || "FAILED"})`
                  : "当前无运行中的沙箱实例"}
              </p>
              <p className="text-[11px] text-zinc-500 mt-1 max-w-sm">
                {deployment?.errorMessage ||
                  "选择部署模板并点击右上角“一键启动部署”即可在隔离沙箱环境中启动并实时预览"}
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
        <div className="flex items-center space-x-3 truncate">
          <span>Sandbox: {deployment?.sandboxType || "LOCAL_PROCESS"}</span>
          <span>Template: {deployment?.templateType || selectedTemplate}</span>
          {deployment?.artifactId && (
            <span className="inline-flex items-center space-x-1 text-zinc-400">
              <PackageCheck className="w-3 h-3 text-indigo-400" />
              <span>{deployment.artifactId}</span>
            </span>
          )}
          {(deployment?.processId || deployment?.containerId) && (
            <span className="inline-flex items-center space-x-1 text-zinc-400">
              <Cpu className="w-3 h-3 text-emerald-400" />
              <span>
                {deployment?.processId ? `PID:${deployment.processId}` : ""}
                {deployment?.processId && deployment?.containerId ? " · " : ""}
                {deployment?.containerId || ""}
              </span>
            </span>
          )}
        </div>
        <div className="flex items-center space-x-1.5 shrink-0">
          <ShieldCheck className="w-3 h-3 text-emerald-400" />
          <span>Non-Root Sandbox Guard Active</span>
        </div>
      </div>
    </BentoCard>
  );
}
