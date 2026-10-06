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

export default function WorkflowCanvas({ onWorkflowCompleted }: { onWorkflowCompleted?: () => void }) {
  const [nodes, setNodes] = useState<Node[]>(initialNodes);
  const [edges, setEdges] = useState<Edge[]>(initialEdges);
  const [taskPrompt, setTaskPrompt] = useState("开发高内聚用户认证模块与登录表单");
  const [isRunning, setIsRunning] = useState(false);
  const [executionResult, setExecutionResult] = useState<any>(null);

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
    setExecutionResult(null);

    // Call backend API
    const workflowPayload = {
      workflow: {
        id: "wf-visual-dag",
        name: "可视化协作工作流",
        description: "由用户在画布中编排的多智能体协同流程",
        nodes: [
          { id: "start", label: "任务启动", type: "START", nextNodeIds: ["backend-node", "frontend-node"] },
          { id: "backend-node", label: "后端设计", type: "AGENT", agentPlatform: "SPRING_AI_API", promptTemplate: "设计 RESTful 接口与领域模型: " + taskPrompt, nextNodeIds: ["qa-node"] },
          { id: "frontend-node", label: "前端构建", type: "AGENT", agentPlatform: "SPRING_AI_API", promptTemplate: "实现响应式界面组件: " + taskPrompt, nextNodeIds: ["qa-node"] },
          { id: "qa-node", label: "QA审查", type: "AGENT", agentPlatform: "SPRING_AI_API", promptTemplate: "执行代码审查与 Diff 比对: " + taskPrompt, nextNodeIds: ["end"] },
          { id: "end", label: "产物交付", type: "END", nextNodeIds: [] },
        ],
      },
      workspacePath: "d:/work/agenthub/data/workspaces/default",
      taskPrompt: taskPrompt,
    };

    try {
      const res = await fetch("http://localhost:8080/api/workspace/workflow/execute", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(workflowPayload),
      });
      const data = await res.json();
      setExecutionResult(data.data);
      if (onWorkflowCompleted) onWorkflowCompleted();
    } catch (err) {
      console.error("Workflow failed:", err);
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
              <span>多 Agent 协同执行中...</span>
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

      {/* 执行结果弹窗/底部卡片 */}
      {executionResult && (
        <div className="p-3 bg-slate-900/90 border-t border-slate-800 text-xs">
          <div className="flex items-center justify-between mb-1.5">
            <div className="flex items-center space-x-1.5 text-emerald-400 font-semibold">
              <CheckCircle className="w-4 h-4" />
              <span>工作流执行完毕（ID: {executionResult.executionId} · 状态: {executionResult.status}）</span>
            </div>
            <span className="text-[10px] text-slate-400">已自动触发 JGit 工作区版本快照</span>
          </div>
          <div className="bg-slate-950 p-2.5 rounded-lg border border-slate-800 text-[11px] font-mono text-slate-300 max-h-24 overflow-y-auto">
            {executionResult.finalOutput || "所有节点成功流转，代码已写入工作区。"}
          </div>
        </div>
      )}
    </div>
  );
}
