"use client";

import React, { useState, useCallback, useMemo } from "react";
import {
  ReactFlow,
  Controls,
  Background,
  BackgroundVariant,
  Node,
  Edge,
  applyNodeChanges,
  applyEdgeChanges,
  OnNodesChange,
  OnEdgesChange,
  Handle,
  Position,
} from "@xyflow/react";
import "@xyflow/react/dist/style.css";
import { BentoCard } from "@/components/common/BentoCard";
import { StatusBadge } from "@/components/common/StatusBadge";
import {
  WorkflowDslNode,
  WorkflowNodeType,
  WorkflowNodeStatus,
  StepRunView,
} from "@/types";
import {
  Play,
  Bot,
  ShieldAlert,
  GitFork,
  GitMerge,
  Flag,
  Sparkles,
  Clock,
  CheckCircle2,
  AlertTriangle,
  Layers,
  ChevronRight,
  Info,
} from "lucide-react";

// --- Node Data & Visualizer Types ---
export interface WorkflowNodeData extends Record<string, unknown> {
  id: string;
  label: string;
  nodeType: WorkflowNodeType;
  status: WorkflowNodeStatus;
  agentPlatform?: string;
  promptTemplate?: string;
  conditionExpression?: string;
  approverRole?: string;
  durationMs?: number;
  attempt?: number;
  outputRef?: string;
  errorMessage?: string;
}

// --- Custom Flow Node Component ---
function CustomWorkflowNode({ data }: { data: WorkflowNodeData }) {
  const getIcon = () => {
    switch (data.nodeType) {
      case "START":
        return <Play className="w-3.5 h-3.5 text-indigo-400" />;
      case "AGENT":
        return <Bot className="w-3.5 h-3.5 text-emerald-400" />;
      case "APPROVAL":
        return <ShieldAlert className="w-3.5 h-3.5 text-amber-400" />;
      case "CONDITION":
        return <GitFork className="w-3.5 h-3.5 text-purple-400" />;
      case "JOIN":
        return <GitMerge className="w-3.5 h-3.5 text-sky-400" />;
      case "END":
        return <Flag className="w-3.5 h-3.5 text-zinc-300" />;
    }
  };

  const getBorderAndGlow = () => {
    switch (data.status) {
      case "RUNNING":
        return "border-sky-500 shadow-md shadow-sky-500/20 ring-1 ring-sky-500/40";
      case "WAITING_APPROVAL":
        return "border-amber-500 shadow-lg shadow-amber-500/25 ring-2 ring-amber-500/50 animate-pulse";
      case "SUCCEEDED":
        return "border-emerald-500/60 shadow-sm shadow-emerald-500/10";
      case "FAILED":
        return "border-rose-500/80 shadow-md shadow-rose-500/20";
      case "SKIPPED":
        return "border-zinc-700/50 opacity-60";
      default:
        return "border-zinc-800 hover:border-zinc-700";
    }
  };

  return (
    <div
      className={`relative min-w-[190px] rounded-xl border bg-zinc-900/90 p-3 backdrop-blur-md transition-all duration-200 select-none ${getBorderAndGlow()}`}
    >
      {/* Target input handle (except START) */}
      {data.nodeType !== "START" && (
        <Handle
          type="target"
          position={Position.Left}
          className="!w-2 !h-2 !bg-zinc-400 !border-zinc-800"
        />
      )}

      {/* Header */}
      <div className="flex items-center justify-between gap-2 mb-1.5">
        <div className="flex items-center space-x-1.5 truncate">
          <div className="p-1 rounded bg-zinc-800/80 shrink-0">{getIcon()}</div>
          <span className="font-semibold text-xs text-zinc-100 truncate">
            {data.label}
          </span>
        </div>
        <span className="text-[9px] font-mono font-medium px-1.5 py-0.2 rounded bg-zinc-800 text-zinc-400 border border-zinc-700/50 shrink-0">
          {data.nodeType}
        </span>
      </div>

      {/* Subtitle / Details */}
      <div className="text-[10px] text-zinc-400 truncate font-mono">
        {data.agentPlatform
          ? `Platform: ${data.agentPlatform}`
          : data.approverRole
          ? `Gate: ${data.approverRole}`
          : `ID: ${data.id}`}
      </div>

      {/* Status Bar */}
      <div className="mt-2 pt-2 border-t border-zinc-800/80 flex items-center justify-between">
        <StatusBadge status={data.status} size="sm" />
        {data.durationMs !== undefined && data.durationMs > 0 && (
          <span className="text-[10px] font-mono text-zinc-500">
            {data.durationMs}ms
          </span>
        )}
      </div>

      {/* Source output handle (except END) */}
      {data.nodeType !== "END" && (
        <Handle
          type="source"
          position={Position.Right}
          className="!w-2 !h-2 !bg-indigo-400 !border-zinc-800"
        />
      )}
    </div>
  );
}

// --- Initial DSL Nodes & Edges ---
const INITIAL_NODES: Node<WorkflowNodeData>[] = [
  {
    id: "node-start",
    type: "customWorkflow",
    position: { x: 30, y: 140 },
    data: {
      id: "node-start",
      label: "开始 (Start)",
      nodeType: "START",
      status: "SUCCEEDED",
      durationMs: 45,
    },
  },
  {
    id: "node-backend-architect",
    type: "customWorkflow",
    position: { x: 260, y: 50 },
    data: {
      id: "node-backend-architect",
      label: "后端领域架构",
      nodeType: "AGENT",
      agentPlatform: "SPRING_AI_API",
      promptTemplate: "设计 RESTful 接口与领域模型，支持 JWT 安全校验",
      status: "SUCCEEDED",
      durationMs: 4210,
      attempt: 1,
      outputRef: "art-backend-spec-v1",
    },
  },
  {
    id: "node-frontend-engineer",
    type: "customWorkflow",
    position: { x: 260, y: 220 },
    data: {
      id: "node-frontend-engineer",
      label: "前端美学组件",
      nodeType: "AGENT",
      agentPlatform: "SPRING_AI_API",
      promptTemplate: "实现 Bento Grid 与 Linear 风格的高质感交互组件",
      status: "SUCCEEDED",
      durationMs: 5120,
      attempt: 1,
      outputRef: "art-frontend-components-v1",
    },
  },
  {
    id: "node-join-sync",
    type: "customWorkflow",
    position: { x: 500, y: 140 },
    data: {
      id: "node-join-sync",
      label: "分支合并 (Join)",
      nodeType: "JOIN",
      status: "SUCCEEDED",
      durationMs: 25,
    },
  },
  {
    id: "node-qa-audit",
    type: "customWorkflow",
    position: { x: 730, y: 140 },
    data: {
      id: "node-qa-audit",
      label: "QA 代码审计",
      nodeType: "AGENT",
      agentPlatform: "JGIT_AUDIT",
      promptTemplate: "执行 JGit Diff 对比、文件变更统计与边界用例防护",
      status: "SUCCEEDED",
      durationMs: 1840,
      attempt: 1,
      outputRef: "art-jgit-diff-snapshot",
    },
  },
  {
    id: "node-security-gate",
    type: "customWorkflow",
    position: { x: 970, y: 140 },
    data: {
      id: "node-security-gate",
      label: "安全决策门禁",
      nodeType: "APPROVAL",
      approverRole: "SecOps / Lead Architect",
      status: "WAITING_APPROVAL",
      attempt: 1,
      errorMessage: "等待人工审批授权准入",
    },
  },
  {
    id: "node-sandbox-deploy",
    type: "customWorkflow",
    position: { x: 1220, y: 140 },
    data: {
      id: "node-sandbox-deploy",
      label: "沙箱容器部署",
      nodeType: "AGENT",
      agentPlatform: "DOCKER_SANDBOX",
      promptTemplate: "启动非特权受限容器，绑定分配端口 18080 并执行健康检查",
      status: "PENDING",
      attempt: 0,
    },
  },
  {
    id: "node-end",
    type: "customWorkflow",
    position: { x: 1460, y: 140 },
    data: {
      id: "node-end",
      label: "产物交付 (End)",
      nodeType: "END",
      status: "PENDING",
      attempt: 0,
    },
  },
];

const INITIAL_EDGES: Edge[] = [
  { id: "e-start-be", source: "node-start", target: "node-backend-architect", animated: false, style: { stroke: "#10b981", strokeWidth: 2 } },
  { id: "e-start-fe", source: "node-start", target: "node-frontend-engineer", animated: false, style: { stroke: "#10b981", strokeWidth: 2 } },
  { id: "e-be-join", source: "node-backend-architect", target: "node-join-sync", animated: false, style: { stroke: "#10b981", strokeWidth: 2 } },
  { id: "e-fe-join", source: "node-frontend-engineer", target: "node-join-sync", animated: false, style: { stroke: "#10b981", strokeWidth: 2 } },
  { id: "e-join-qa", source: "node-join-sync", target: "node-qa-audit", animated: false, style: { stroke: "#10b981", strokeWidth: 2 } },
  { id: "e-qa-gate", source: "node-qa-audit", target: "node-security-gate", animated: true, style: { stroke: "#f59e0b", strokeWidth: 2 } },
  { id: "e-gate-deploy", source: "node-security-gate", target: "node-sandbox-deploy", animated: false, style: { stroke: "#52525b", strokeWidth: 2 } },
  { id: "e-deploy-end", source: "node-sandbox-deploy", target: "node-end", animated: false, style: { stroke: "#52525b", strokeWidth: 2 } },
];

interface DagTopologyVisualizerProps {
  steps?: StepRunView[];
  onNodeClick?: (nodeId: string) => void;
  onRunWorkflow?: () => void;
  isRunning?: boolean;
  className?: string;
}

export function DagTopologyVisualizer({
  steps = [],
  onNodeClick,
  onRunWorkflow,
  isRunning = false,
  className = "",
}: DagTopologyVisualizerProps) {
  const [nodes, setNodes] = useState<Node<WorkflowNodeData>[]>(INITIAL_NODES);
  const [edges, setEdges] = useState<Edge[]>(INITIAL_EDGES);
  const [selectedNodeData, setSelectedNodeData] = useState<WorkflowNodeData | null>(null);

  const nodeTypes = useMemo(() => ({ customWorkflow: CustomWorkflowNode }), []);

  const onNodesChange: OnNodesChange<Node<WorkflowNodeData>> = useCallback(
    (changes) => setNodes((nds) => applyNodeChanges(changes, nds)),
    []
  );

  const onEdgesChange: OnEdgesChange = useCallback(
    (changes) => setEdges((eds) => applyEdgeChanges(changes, eds)),
    []
  );

  const handleNodeClick = useCallback(
    (_: React.MouseEvent, node: Node) => {
      const data = node.data as WorkflowNodeData;
      setSelectedNodeData(data);
      onNodeClick?.(node.id);
    },
    [onNodeClick]
  );

  return (
    <BentoCard
      title="工作流 DAG 依赖拓扑 (Topology Visualizer)"
      subtitle="阶段 4 DSL 规范：START ➔ 并行 AGENT ➔ JOIN ➔ QA ➔ APPROVAL ➔ 沙箱 ➔ END"
      icon={<Layers className="w-4 h-4 text-indigo-400" />}
      badge={
        <div className="flex items-center space-x-1.5">
          <span className="text-[10px] font-mono px-2 py-0.5 rounded-full bg-zinc-800 text-zinc-300 border border-zinc-700/60">
            DAG Nodes: {nodes.length}
          </span>
          {steps.some((s) => s.status === "WAITING_APPROVAL") && (
            <span className="text-[10px] font-mono px-2 py-0.5 rounded-full bg-amber-500/20 text-amber-300 border border-amber-500/40 animate-pulse">
              1 Gate Suspended
            </span>
          )}
        </div>
      }
      actions={
        onRunWorkflow && (
          <button
            onClick={onRunWorkflow}
            disabled={isRunning}
            className="flex items-center space-x-1.5 px-3 py-1 rounded-lg bg-indigo-600 hover:bg-indigo-500 text-white font-medium text-xs transition shadow-md shadow-indigo-600/20 disabled:opacity-50"
          >
            <Sparkles className="w-3.5 h-3.5" />
            <span>{isRunning ? "执行中..." : "重新编排"}</span>
          </button>
        )
      }
      className={`h-full ${className}`}
      bodyClassName="flex flex-col p-0 overflow-hidden relative"
    >
      {/* Canvas Area */}
      <div className="flex-1 w-full bg-[#080c14] relative min-h-[320px]">
        <ReactFlow
          nodes={nodes}
          edges={edges}
          onNodesChange={onNodesChange}
          onEdgesChange={onEdgesChange}
          onNodeClick={handleNodeClick}
          nodeTypes={nodeTypes}
          fitView
          fitViewOptions={{ padding: 0.2 }}
          minZoom={0.4}
          maxZoom={1.5}
          proOptions={{ hideAttribution: true }}
        >
          <Background
            variant={BackgroundVariant.Dots}
            gap={18}
            size={1.2}
            color="#27272a"
          />
          <Controls
            className="!bg-zinc-900/90 !border-zinc-800 !rounded-xl !overflow-hidden [&>button]:!bg-zinc-900 [&>button]:!border-zinc-800 [&>button]:!fill-zinc-400 hover:[&>button]:!bg-zinc-800"
          />
        </ReactFlow>

        {/* Selected Node Inspector Drawer (Floats on bottom right) */}
        {selectedNodeData && (
          <div className="absolute right-3 bottom-3 max-w-sm rounded-xl border border-zinc-800/90 bg-zinc-950/95 p-3.5 shadow-2xl backdrop-blur-md z-10 font-sans text-xs">
            <div className="flex items-center justify-between pb-2 border-b border-zinc-800/80 mb-2">
              <div className="flex items-center space-x-1.5">
                <Info className="w-3.5 h-3.5 text-indigo-400" />
                <span className="font-semibold text-zinc-100">
                  {selectedNodeData.label}
                </span>
              </div>
              <button
                onClick={() => setSelectedNodeData(null)}
                className="text-zinc-500 hover:text-zinc-300 text-xs px-1"
              >
                ✕
              </button>
            </div>

            <div className="space-y-1.5 font-mono text-[11px]">
              <div className="flex justify-between text-zinc-400">
                <span>节点 ID:</span>
                <span className="text-zinc-200">{selectedNodeData.id}</span>
              </div>
              <div className="flex justify-between text-zinc-400">
                <span>类型:</span>
                <span className="text-zinc-200">{selectedNodeData.nodeType}</span>
              </div>
              <div className="flex justify-between text-zinc-400">
                <span>状态:</span>
                <StatusBadge status={selectedNodeData.status} size="sm" />
              </div>
              {selectedNodeData.agentPlatform && (
                <div className="flex justify-between text-zinc-400">
                  <span>运行时:</span>
                  <span className="text-emerald-400">{selectedNodeData.agentPlatform}</span>
                </div>
              )}
              {selectedNodeData.durationMs !== undefined && (
                <div className="flex justify-between text-zinc-400">
                  <span>耗时:</span>
                  <span className="text-zinc-200">{selectedNodeData.durationMs} ms</span>
                </div>
              )}
              {selectedNodeData.outputRef && (
                <div className="flex justify-between text-zinc-400">
                  <span>交付产物:</span>
                  <span className="text-sky-400">{selectedNodeData.outputRef}</span>
                </div>
              )}
              {selectedNodeData.promptTemplate && (
                <div className="mt-2 pt-2 border-t border-zinc-800/60 font-sans text-[11px] text-zinc-300">
                  <span className="text-zinc-500 font-mono text-[10px] block mb-0.5">
                    Prompt 模板:
                  </span>
                  {selectedNodeData.promptTemplate}
                </div>
              )}
            </div>
          </div>
        )}
      </div>
    </BentoCard>
  );
}
