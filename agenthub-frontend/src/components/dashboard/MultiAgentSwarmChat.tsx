"use client";

import React, { useState, useEffect, useRef } from "react";
import { BentoCard } from "@/components/common/BentoCard";
import { StatusBadge } from "@/components/common/StatusBadge";
import {
  TeamView,
  MessageView,
  InteractiveCard,
} from "@/types";
import { apiClient } from "@/services/api";
import {
  Users,
  Bot,
  Send,
  Sparkles,
  ShieldAlert,
  Lock,
  Globe,
  RefreshCw,
  Clock,
  Layers,
  ArrowRight,
  BookmarkCheck,
  CheckCircle2,
} from "lucide-react";

const INITIAL_SWARM_MESSAGES: MessageView[] = [
  {
    id: "msg-seq-1",
    senderId: "Developer",
    senderType: "USER",
    messageType: "BROADCAST",
    protocolType: "NORMAL",
    sequenceNum: 1,
    content: "@Orchestrator 请启动用户中心认证模块的重构与沙箱预览上线。",
    createdAt: new Date(Date.now() - 180000).toISOString(),
    timestamp: "11:00:01",
  },
  {
    id: "msg-seq-2",
    senderId: "Orchestrator",
    senderType: "ORCHESTRATOR",
    messageType: "BROADCAST",
    protocolType: "REQUEST_REPLY",
    sequenceNum: 2,
    content: "已解析目标需求，已生成版本化 DAG 任务图 (wf-enterprise-auth-delivery)，并行调度 BackendArchitect 与 FrontendEngineer 开展协作。",
    createdAt: new Date(Date.now() - 170000).toISOString(),
    timestamp: "11:00:03",
    cardPayload: {
      cardId: "card-plan-01",
      headerTitle: "🚀 全栈协同执行计划",
      summary: "任务图并行展开，各 Agent 负责领域实现与审查闭环。",
      subtasks: [
        { id: "st-1", targetAgent: "BackendArchitect", title: "实现 AuthController 与 JWT 边界校验", status: "COMPLETED" },
        { id: "st-2", targetAgent: "FrontendEngineer", title: "构建 Bento Grid 响应式控制台组件", status: "COMPLETED" },
        { id: "st-3", targetAgent: "QAAuditor", title: "JGit Diff 行级审计与边界测试防御", status: "COMPLETED" },
        { id: "st-4", targetAgent: "SecOpsGovernor", title: "受控沙箱容器部署与端口校验", status: "WAITING_APPROVAL" },
      ],
      actions: ["查看 JGit Diff", "一键审批放行"],
    },
  },
  {
    id: "msg-seq-3",
    senderId: "BackendArchitect",
    senderType: "AGENT",
    messageType: "BROADCAST",
    protocolType: "HANDOFF",
    sequenceNum: 3,
    content: "后端领域模型与 AuthController 已完成，已对密码传输实施脱敏，并通过了 14 项安全防火墙单测。",
    createdAt: new Date(Date.now() - 140000).toISOString(),
    timestamp: "11:00:08",
  },
  {
    id: "msg-seq-4",
    senderId: "FrontendEngineer",
    senderType: "AGENT",
    messageType: "BROADCAST",
    protocolType: "HANDOFF",
    sequenceNum: 4,
    content: "前端 Next.js 14 Bento 模块已按照 modern-aesthetic-ui 落地，消除了所有 AI 塑料感元素，采用磨砂玻璃拟态与微交互动效。",
    createdAt: new Date(Date.now() - 110000).toISOString(),
    timestamp: "11:00:14",
  },
  {
    id: "msg-seq-5",
    senderId: "QAAuditor",
    senderType: "AGENT",
    recipientId: "SecOpsGovernor",
    messageType: "DIRECT",
    protocolType: "REQUEST_REPLY",
    sequenceNum: 5,
    content: "[私聊对等审查] JGit Unified Diff 生成完毕 (+148, -8 行)，基线快照无污染。移交安全门禁进行人工签名核准。",
    createdAt: new Date(Date.now() - 90000).toISOString(),
    timestamp: "11:00:16",
  },
];

interface MultiAgentSwarmChatProps {
  team?: TeamView | null;
  onCardAction?: (action: string) => void;
  className?: string;
}

export function MultiAgentSwarmChat({
  team,
  onCardAction,
  className = "",
}: MultiAgentSwarmChatProps) {
  const [messages, setMessages] = useState<MessageView[]>(INITIAL_SWARM_MESSAGES);
  const [inputText, setInputText] = useState("");
  const [isSending, setIsSending] = useState(false);
  const [showSummary, setShowSummary] = useState(true);

  const messagesEndRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages]);

  const handleSendMessage = async () => {
    if (!inputText.trim() || isSending) return;

    const nextSeq = messages.length + 1;
    const newMsg: MessageView = {
      id: `msg-${Date.now()}`,
      senderId: "Developer",
      senderType: "USER",
      messageType: "BROADCAST",
      protocolType: "NORMAL",
      sequenceNum: nextSeq,
      content: inputText,
      createdAt: new Date().toISOString(),
      timestamp: new Date().toLocaleTimeString("en-GB", { hour12: false }),
    };

    setMessages((prev) => [...prev, newMsg]);
    setInputText("");
    setIsSending(true);

    // Call backend API if active conversation exists
    try {
      await apiClient.sendMessage("default-conv", inputText, "Developer");
    } catch (e) {
      console.warn("Could not push to backend message bus:", e);
    } finally {
      setIsSending(false);
    }
  };

  const appendMention = (mentionTag: string) => {
    setInputText((prev) => (prev ? `${prev} ${mentionTag} ` : `${mentionTag} `));
  };

  return (
    <BentoCard
      title="多智能体协同网络 (Swarm Timeline)"
      subtitle={`协作拓扑: ${team?.topology || "HIERARCHICAL"} | 配额: ${team?.maxTurns || 12} 轮 | 单调保序锁: 128 分段重入锁`}
      icon={<Users className="w-4 h-4 text-emerald-400" />}
      badge={
        <div className="flex items-center space-x-1.5">
          <span className="flex items-center space-x-1 rounded-full bg-emerald-950/40 px-2 py-0.5 text-[10px] font-mono text-emerald-300 border border-emerald-500/30">
            <span className="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-pulse" />
            <span>LoopDetector: Normal</span>
          </span>
        </div>
      }
      className={`h-full ${className}`}
      bodyClassName="flex flex-col p-0 overflow-hidden"
    >
      {/* Registered Swarm Team Members Bar */}
      <div className="border-b border-zinc-800/60 bg-zinc-950/40 px-3 py-2 flex items-center space-x-2 overflow-x-auto text-[11px] shrink-0">
        <span className="text-zinc-500 font-mono text-[10px] shrink-0">ROLES:</span>
        {[
          { name: "Orchestrator", role: "LEADER", color: "text-amber-400" },
          { name: "BackendArchitect", role: "ARCHITECT", color: "text-emerald-400" },
          { name: "FrontendEngineer", role: "CODER", color: "text-sky-400" },
          { name: "QAAuditor", role: "REVIEWER", color: "text-purple-400" },
          { name: "SecOpsGovernor", role: "TESTER", color: "text-rose-400" },
        ].map((agent) => (
          <div
            key={agent.name}
            className="flex items-center space-x-1.5 rounded-lg border border-zinc-800/80 bg-zinc-900/60 px-2 py-1 shrink-0"
          >
            <Bot className={`w-3 h-3 ${agent.color}`} />
            <span className="font-medium text-zinc-200 text-[10px]">{agent.name}</span>
            <span className="rounded bg-zinc-800 px-1 text-[9px] font-mono text-zinc-400">
              {agent.role}
            </span>
          </div>
        ))}
      </div>

      {/* Rolling Summary Collapsible Banner (Phase 6 Context window governance) */}
      {showSummary && (
        <div className="border-b border-zinc-800/80 bg-indigo-950/20 px-3 py-1.5 flex items-center justify-between text-[11px] shrink-0">
          <div className="flex items-center space-x-2 text-indigo-300 truncate">
            <BookmarkCheck className="w-3.5 h-3.5 text-indigo-400 shrink-0" />
            <span className="truncate">
              [滚动历史摘要] 用户要求重构用户中心与沙箱发布，当前后端/前端/QA已全数交付，等待安全审批放行。
            </span>
          </div>
          <button
            onClick={() => setShowSummary(false)}
            className="text-[10px] text-zinc-500 hover:text-zinc-300 ml-2 shrink-0 font-mono"
          >
            收起
          </button>
        </div>
      )}

      {/* Messages Scroll Area */}
      <div className="flex-1 overflow-y-auto p-4 space-y-3.5 bg-zinc-950/60 font-sans">
        {messages.map((m, idx) => {
          const isUser = m.senderType === "USER";
          const isDirect = m.messageType === "DIRECT";

          return (
            <div
              key={m.id || idx}
              className={`flex flex-col ${isUser ? "items-end" : "items-start"}`}
            >
              {/* Message Meta Info */}
              <div className="flex items-center space-x-2 mb-1 text-[11px] text-zinc-400">
                <span className="font-mono text-[10px] text-zinc-500 font-semibold">
                  #{m.sequenceNum ?? idx + 1}
                </span>
                <span className="font-medium text-zinc-200">{m.senderId}</span>

                {/* Protocol Tag */}
                {m.protocolType && m.protocolType !== "NORMAL" && (
                  <span className="px-1.5 py-0.2 rounded bg-zinc-800 text-[9px] font-mono text-zinc-400 border border-zinc-700/40">
                    {m.protocolType}
                  </span>
                )}

                {/* Visibility Badge */}
                {isDirect ? (
                  <span className="flex items-center space-x-0.5 rounded bg-rose-950/40 px-1.5 py-0.2 text-[9px] font-mono text-rose-300 border border-rose-500/30">
                    <Lock className="w-2.5 h-2.5" />
                    <span>DIRECT P2P</span>
                  </span>
                ) : (
                  <span className="flex items-center space-x-0.5 rounded bg-zinc-800 px-1.5 py-0.2 text-[9px] font-mono text-zinc-400">
                    <Globe className="w-2.5 h-2.5 text-zinc-500" />
                    <span>BROADCAST</span>
                  </span>
                )}

                <span className="text-[10px] text-zinc-500">
                  {m.timestamp || "11:00"}
                </span>
              </div>

              {/* Message Bubble */}
              <div
                className={`max-w-[88%] rounded-2xl p-3.5 text-xs leading-relaxed transition-all shadow-sm ${
                  isUser
                    ? "bg-indigo-600 text-white rounded-tr-sm"
                    : isDirect
                    ? "bg-zinc-900/90 border border-rose-500/30 text-zinc-200 rounded-tl-sm ring-1 ring-rose-500/10"
                    : "bg-zinc-900/80 border border-zinc-800 text-zinc-200 rounded-tl-sm"
                }`}
              >
                <div className="whitespace-pre-wrap">{m.content}</div>

                {/* Interactive Card Rendering */}
                {m.cardPayload && (
                  <div className="mt-3 rounded-xl border border-zinc-800/90 bg-zinc-950/90 p-3 text-left">
                    <div className="flex items-center space-x-1.5 text-xs font-semibold text-amber-400 mb-1">
                      <Sparkles className="w-3.5 h-3.5" />
                      <span>{m.cardPayload.headerTitle}</span>
                    </div>
                    <p className="text-[11px] text-zinc-400 mb-2.5">
                      {m.cardPayload.summary}
                    </p>

                    {/* Subtasks */}
                    {m.cardPayload.subtasks && (
                      <div className="space-y-1.5 mb-3 font-mono text-[11px]">
                        {m.cardPayload.subtasks.map((task) => (
                          <div
                            key={task.id}
                            className="flex items-center justify-between rounded-lg border border-zinc-800/80 bg-zinc-900/60 p-2"
                          >
                            <div className="truncate mr-2 flex items-center space-x-1.5">
                              <span className="text-indigo-400 font-semibold">
                                @{task.targetAgent}
                              </span>
                              <span className="text-zinc-300 truncate">
                                {task.title}
                              </span>
                            </div>
                            <StatusBadge status={task.status} size="sm" />
                          </div>
                        ))}
                      </div>
                    )}

                    {/* Actions */}
                    {m.cardPayload.actions && (
                      <div className="flex items-center space-x-2 pt-2 border-t border-zinc-800/60">
                        {m.cardPayload.actions.map((act) => (
                          <button
                            key={act}
                            onClick={() => onCardAction?.(act)}
                            className="rounded-lg border border-indigo-500/30 bg-indigo-600/20 px-2.5 py-1 text-[11px] font-medium text-indigo-300 hover:bg-indigo-600/30 transition"
                          >
                            {act}
                          </button>
                        ))}
                      </div>
                    )}
                  </div>
                )}
              </div>
            </div>
          );
        })}
        <div ref={messagesEndRef} />
      </div>

      {/* Quick @Mention Bar */}
      <div className="border-t border-zinc-800/60 bg-zinc-950/70 px-3 py-1.5 flex items-center space-x-1.5 text-[11px] shrink-0">
        <span className="text-zinc-500 font-mono text-[10px] shrink-0">快捷呼叫:</span>
        {[
          "@Orchestrator",
          "@BackendArchitect",
          "@FrontendEngineer",
          "@QAAuditor",
          "@SecOpsGovernor",
        ].map((tag) => (
          <button
            key={tag}
            onClick={() => appendMention(tag)}
            className="rounded-md border border-zinc-800 bg-zinc-900 px-2 py-0.5 text-zinc-300 hover:border-zinc-700 hover:text-white transition font-mono text-[10px]"
          >
            {tag}
          </button>
        ))}
      </div>

      {/* Input Box Area */}
      <div className="border-t border-zinc-800/80 bg-zinc-950/90 p-3 shrink-0">
        <div className="flex items-center space-x-2 rounded-xl border border-zinc-800 bg-zinc-900/90 px-3 py-2 focus-within:border-indigo-500 transition">
          <input
            type="text"
            value={inputText}
            onChange={(e) => setInputText(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && handleSendMessage()}
            placeholder="发送指令或 @ 智能体发起协同交付..."
            className="flex-1 bg-transparent text-xs text-zinc-100 placeholder-zinc-500 focus:outline-none"
          />
          <button
            onClick={handleSendMessage}
            disabled={isSending || !inputText.trim()}
            className="flex h-7 w-7 items-center justify-center rounded-lg bg-indigo-600 text-white shadow-md shadow-indigo-600/20 hover:bg-indigo-500 transition disabled:opacity-40"
          >
            {isSending ? (
              <Clock className="w-3.5 h-3.5 animate-spin" />
            ) : (
              <Send className="w-3.5 h-3.5" />
            )}
          </button>
        </div>
      </div>
    </BentoCard>
  );
}
