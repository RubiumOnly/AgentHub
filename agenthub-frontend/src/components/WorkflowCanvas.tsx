"use client";

import React, { useState, useCallback } from "react";
import {
  ReactFlow,
  Controls,
  Background,
  applyNodeChanges,
  applyEdgeChanges,
  addEdge,
  Node,
  Edge,
  OnNodesChange,
  OnEdgesChange,
  OnConnect,
} from "@xyflow/react";
import "@xyflow/react/dist/style.css";
import { Play, Sparkles, CheckCircle, Clock, AlertCircle } from "lucide-react";
import { apiClient, isDemoMode, MOCK_RUN } from "@/services/api";

const initialNodes: Node[] = [
  {
    id: "start",
    type: "input",
    position: { x: 50, y: 150 },
    data: { label: "🚀 任务启动 (Start)" },
    style: { background: "#1e293b", color: "#f8fafc", borderColor: "#475569", borderRadius: "12px", padding: "10px", fontSize: "12px" },
  },
  {
    id: "backend-node",
    position: { x: 280, y: 80 },
    data: { label: "📐 后端架构师 (DeepSeek / Spring Boot)" },
    style: { background: "#0f172a", color: "#34d399", borderColor: "#059669", borderRadius: "12px", padding: "10px", fontSize: "12px" },
  },
  {
    id: "frontend-node",
    position: { x: 280, y: 220 },
    data: { label: "🎨 前端工程师 (Next.js 14 / UI)" },
    style: { background: "#0f172a", color: "#38bdf8", borderColor: "#0284c7", borderRadius: "12px", padding: "10px", fontSize: "12px" },
  },
  {
    id: "qa-node",
    position: { x: 540, y: 150 },
    data: { label: "🛡️ QA 审计员 (JGit Diff 审查)" },
    style: { background: "#0f172a", color: "#c084fc", borderColor: "#9333ea", borderRadius: "12px", padding: "10px", fontSize: "12px" },
  },
  {
    id: "end",
    type: "output",
    position: { x: 780, y: 150 },
    data: { label: "🏁 产物交付 (End & Deliver)" },
    style: { background: "#1e293b", color: "#f8fafc", borderColor: "#475569", borderRadius: "12px", padding: "10px", fontSize: "12px" },
  },
];

const initialEdges: Edge[] = [
  { id: "e1-2", source: "start", target: "backend-node", animated: true },
  { id: "e1-3", source: "start", target: "frontend-node", animated: true },
  { id: "e2-4", source: "backend-node", target: "qa-node", animated: true },
  { id: "e3-4", source: "frontend-node", target: "qa-node", animated: true },
  { id: "e4-5", source: "qa-node", target: "end", animated: true },
];

export default function WorkflowCanvas({
  projectId,
  workspaceId,
  runId,
  definitionId,
  onWorkflowCompleted,
}: {
  projectId?: string;
  workspaceId?: string;
  runId?: string;
  definitionId?: string;
  onWorkflowCompleted?: () => void;
}) {
  const [nodes, setNodes] = useState<Node[]>(initialNodes);
  const [edges, setEdges] = useState<Edge[]>(initialEdges);
  const [taskPrompt, setTaskPrompt] = useState("开发高内聚用户认证模块与登录表单");
  const [isRunning, setIsRunning] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [executionResult, setExecutionResult] = useState<{
    executionId: string;
    status: string;
    simulated: boolean;
    finalOutput?: string;
  } | null>(null);

  const onNodesChange: OnNodesChange = useCallback(
    (changes) => setNodes((nds) => applyNodeChanges(changes, nds)),
    []
  );

  const onEdgesChange: OnEdgesChange = useCallback(
    (changes) => setEdges((eds) => applyEdgeChanges(changes, eds)),
    []
  );

  const onConnect: OnConnect = useCallback(
    (params) => setEdges((eds) => addEdge({ ...params, animated: true }, eds)),
    []
  );

  const handleRunWorkflow = async () => {
    setIsRunning(true);
    setErrorMessage(null);
    setExecutionResult(null);

    try {
      if (isDemoMode()) {
        setExecutionResult({
          executionId: runId || MOCK_RUN.id,
          status: "DEMO_SIMULATED",
          simulated: true,
          finalOutput: `[DEMO_SIMULATED] 离线演示模式：已按目标 "${taskPrompt}" 生成模拟运行视图（未调用真实 Provider）。`,
        });
        if (onWorkflowCompleted) onWorkflowCompleted();
        return;
      }

      if (!projectId || !definitionId) {
        setErrorMessage("当前处于真实运行模式，请通过当前 Project/Workspace/Run 上下文注入 projectId 与 definitionId 后再触发执行。");
        return;
      }

      const run = await apiClient.startRun({
        projectId,
        definitionId,
        idempotencyKey: `canvas-${workspaceId || projectId}-${Date.now()}`,
      });
      setExecutionResult({
        executionId: run.id,
        status: run.status,
        simulated: false,
        finalOutput: `Run ${run.id} 已通过受控接口 /api/executions/runs 创建（当前后端状态：${run.status}，目标：${taskPrompt}）。`,
      });
      if (onWorkflowCompleted) onWorkflowCompleted();
    } catch (err) {
      const msg = err instanceof Error ? err.message : "触发工作流失败，请检查登录状态与后端服务";
      setErrorMessage(msg);
    } finally {
      setIsRunning(false);
    }
  };

  return (
    <div className="flex flex-col h-full bg-[#070b14] rounded-2xl overflow-hidden border border-slate-800">
      {/* 顶部控制面板 */}
      <div className="p-3 bg-slate-900/60 border-b border-slate-800 flex items-center justify-between text-xs">
        <div className="flex items-center space-x-3 flex-1 mr-4">
          <span className="font-semibold text-slate-200 flex items-center space-x-1.5 shrink-0">
            <Sparkles className="w-4 h-4 text-indigo-400" />
            <span>DAG 团队编排画布</span>
          </span>
          <input
            type="text"
            value={taskPrompt}
            onChange={(e) => setTaskPrompt(e.target.value)}
            placeholder="输入此工作流的全局研发目标..."
            className="flex-1 bg-slate-950 border border-slate-800 rounded-lg px-3 py-1.5 text-slate-200 focus:outline-none focus:border-indigo-500"
          />
        </div>

        <button
          onClick={handleRunWorkflow}
          disabled={isRunning}
          className="px-4 py-1.5 bg-indigo-600 hover:bg-indigo-500 text-white rounded-lg font-medium transition shadow-md shadow-indigo-500/20 flex items-center space-x-1.5 shrink-0"
        >
          {isRunning ? (
            <>
              <Clock className="w-3.5 h-3.5 animate-spin" />
              <span>提交执行请求中...</span>
            </>
          ) : (
            <>
              <Play className="w-3.5 h-3.5" />
              <span>一键触发工作流</span>
            </>
          )}
        </button>
      </div>

      {/* React Flow 画布主体 */}
      <div className="flex-1 w-full h-[400px] relative">
        <ReactFlow
          nodes={nodes}
          edges={edges}
          onNodesChange={onNodesChange}
          onEdgesChange={onEdgesChange}
          onConnect={onConnect}
          fitView
        >
          <Background color="#1e293b" gap={16} />
          <Controls />
        </ReactFlow>
      </div>

      {/* 错误提示卡片 */}
      {errorMessage && (
        <div className="p-3 bg-rose-950/80 border-t border-rose-800/70 text-xs text-rose-200 flex items-center space-x-2">
          <AlertCircle className="w-4 h-4 text-rose-400 shrink-0" />
          <span>{errorMessage}</span>
        </div>
      )}

      {/* 执行状态投影卡片 */}
      {executionResult && (
        <div className="p-3 bg-slate-900/90 border-t border-slate-800 text-xs">
          <div className="flex items-center justify-between mb-1.5">
            <div className={`flex items-center space-x-1.5 font-semibold ${executionResult.simulated ? "text-amber-400" : "text-emerald-400"}`}>
              <CheckCircle className="w-4 h-4" />
              <span>
                {executionResult.simulated ? "离线模拟运行已生成" : "受控工作流运行已提交"}（ID: {executionResult.executionId} · 状态: {executionResult.status}）
              </span>
            </div>
            <span className="text-[10px] text-slate-400">
              {executionResult.simulated ? "DEMO_SIMULATED（未写入真实工作区）" : "事实状态以 /api/executions/runs 与 SSE 事件流为准"}
            </span>
          </div>
          <div className="bg-slate-950 p-2.5 rounded-lg border border-slate-800 text-[11px] font-mono text-slate-300 max-h-24 overflow-y-auto">
            {executionResult.finalOutput}
          </div>
        </div>
      )}
    </div>
  );
}
