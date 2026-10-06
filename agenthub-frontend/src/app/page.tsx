"use client";

import React, { useState, useEffect, useRef } from "react";
import {
  Send,
  Bot,
  Users,
  GitCompare,
  ExternalLink,
  Rocket,
  CheckCircle2,
  Clock,
  Play,
  RotateCcw,
  Sparkles,
  Terminal,
  ShieldCheck,
  FileCode,
  Layers,
  FolderTree,
} from "lucide-react";
import WorkflowCanvas from "@/components/WorkflowCanvas";
import WorkspaceExplorer from "@/components/WorkspaceExplorer";

interface Subtask {
  id: string;
  targetAgent: string;
  title: string;
  status: string;
}

interface InteractiveCard {
  cardId: string;
  headerTitle: string;
  summary: string;
  subtasks: Subtask[];
  actions: string[];
}

interface Message {
  id: string;
  conversationId?: string;
  senderId: string;
  senderType: "USER" | "AGENT" | "ORCHESTRATOR" | "SYSTEM";
  content: string;
  cardPayloadJson?: string;
  cardPayload?: InteractiveCard;
  timestamp?: string;
  createdAt?: string;
}

interface Conversation {
  id: string;
  title: string;
  type: string;
  participantAgentIds?: string;
}

interface DiffEntry {
  oldPath: string;
  newPath: string;
  changeType: string;
  diffContent: string;
  linesAdded: number;
  linesDeleted: number;
}

const DEFAULT_WORKSPACE_PATH = "d:/work/agenthub/data/workspaces/default";

export default function AgentHubDashboard() {
  const [conversations, setConversations] = useState<Conversation[]>([]);
  const [activeConvId, setActiveConvId] = useState<string>("");
  const [inputMessage, setInputMessage] = useState("");
  const [activeTab, setActiveTab] = useState<"diff" | "canvas" | "explorer" | "preview" | "deploy">("diff");
  const [backendUp, setBackendUp] = useState(false);
  const [isSending, setIsSending] = useState(false);
  const [isDeploying, setIsDeploying] = useState(false);
  const [deployedUrl, setDeployedUrl] = useState<string | null>(null);

  const [messages, setMessages] = useState<Message[]>([]);
  const [diffEntries, setDiffEntries] = useState<DiffEntry[]>([]);

  const messagesEndRef = useRef<HTMLDivElement>(null);
  const eventSourceRef = useRef<EventSource | null>(null);

  // 1. Check backend health & fetch/create conversations
  const initSystem = async () => {
    try {
      const healthRes = await fetch("http://localhost:8080/api/system/health");
      if (healthRes.ok) {
        setBackendUp(true);
      }
    } catch {
      setBackendUp(false);
    }

    try {
      const convRes = await fetch("http://localhost:8080/api/im/conversations");
      const convData = await convRes.json();
      if (convData.data && convData.data.length > 0) {
        setConversations(convData.data);
        setActiveConvId(convData.data[0].id);
      } else {
        // Create initial default group conversation
        const createRes = await fetch("http://localhost:8080/api/im/conversations", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            title: "🔥 全栈特性突击小队",
            type: "GROUP_COLLABORATION",
            agentIds: ["BackendArchitect", "FrontendEngineer", "QAAuditor"],
          }),
        });
        const created = await createRes.json();
        if (created.data) {
          setConversations([created.data]);
          setActiveConvId(created.data.id);
        }
      }
    } catch (err) {
      console.warn("Could not sync with backend conversations:", err);
    }
  };

  // 2. Fetch messages & establish SSE Stream for active conversation
  const loadMessagesAndStream = async (convId: string) => {
    if (!convId) return;

    try {
      const res = await fetch(`http://localhost:8080/api/im/conversations/${convId}/messages`);
      const data = await res.json();
      if (data.data) {
        const parsed = data.data.map((m: any) => {
          let card: InteractiveCard | undefined;
          if (m.cardPayloadJson) {
            try {
              card = JSON.parse(m.cardPayloadJson);
            } catch {}
          }
          return { ...m, cardPayload: card };
        });
        setMessages(parsed);
      }
    } catch (err) {
      console.warn("Error loading messages:", err);
    }

    // Set up SSE EventSource
    if (eventSourceRef.current) {
      eventSourceRef.current.close();
    }

    try {
      const es = new EventSource(`http://localhost:8080/api/im/conversations/${convId}/stream`);
      es.addEventListener("message", (event) => {
        try {
          const rawMsg = JSON.parse(event.data);
          let card: InteractiveCard | undefined;
          if (rawMsg.cardPayloadJson) {
            try { card = JSON.parse(rawMsg.cardPayloadJson); } catch {}
          }
          const formatted: Message = { ...rawMsg, cardPayload: card };
          setMessages((prev) => {
            const exists = prev.some((m) => m.id === formatted.id);
            if (exists) return prev;
            return [...prev, formatted];
          });
          // Auto refresh diffs when agent writes code
          fetchDiffs();
        } catch {}
      });
      eventSourceRef.current = es;
    } catch (err) {
      console.warn("SSE stream failed:", err);
    }
  };

  // 3. Fetch JGit Diffs
  const fetchDiffs = async () => {
    try {
      const res = await fetch(`http://localhost:8080/api/workspace/diff?path=${encodeURIComponent(DEFAULT_WORKSPACE_PATH)}`);
      const data = await res.json();
      if (data.data) {
        setDiffEntries(data.data);
      }
    } catch (err) {
      console.warn("Error fetching diffs:", err);
    }
  };

  useEffect(() => {
    initSystem();
    fetchDiffs();
    return () => {
      if (eventSourceRef.current) eventSourceRef.current.close();
    };
  }, []);

  useEffect(() => {
    if (activeConvId) {
      loadMessagesAndStream(activeConvId);
    }
  }, [activeConvId]);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages]);

  // Handle sending message via real HTTP POST
  const handleSendMessage = async () => {
    if (!inputMessage.trim() || isSending) return;

    const targetConvId = activeConvId || (conversations[0] ? conversations[0].id : "default-conv");
    setIsSending(true);

    const payload = {
      senderId: "Developer",
      senderType: "USER",
      content: inputMessage,
    };

    setInputMessage("");

    try {
      const res = await fetch(`http://localhost:8080/api/im/conversations/${targetConvId}/messages`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload),
      });
      const data = await res.json();
      if (data.data) {
        setMessages((prev) => {
          if (prev.some((m) => m.id === data.data.id)) return prev;
          return [...prev, data.data];
        });
        setTimeout(fetchDiffs, 1500);
      }
    } catch (err) {
      console.error("Failed to send message:", err);
    } finally {
      setIsSending(false);
    }
  };

  // Card Action Handler
  const handleCardAction = async (action: string) => {
    if (action === "一键审批执行") {
      setActiveTab("diff");
      // Trigger workflow execute
      try {
        await fetch("http://localhost:8080/api/workspace/workflow/execute", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            workflow: {
              id: "wf-approval",
              name: "审批执行流",
              nodes: [
                { id: "start", label: "开始", type: "START", nextNodeIds: ["backend"] },
                { id: "backend", label: "后端落实", type: "AGENT", agentPlatform: "SPRING_AI_API", promptTemplate: "生成完整用户个人信息控制器实现", nextNodeIds: ["end"] },
                { id: "end", label: "结束", type: "END", nextNodeIds: [] },
              ],
            },
            workspacePath: DEFAULT_WORKSPACE_PATH,
            taskPrompt: "用户中心落实与变更",
          }),
        });
        setTimeout(fetchDiffs, 1200);
      } catch (e) {
        console.error(e);
      }
    } else {
      setActiveTab("diff");
    }
  };

  const handleDeploy = async () => {
    setIsDeploying(true);
    try {
      const res = await fetch("http://localhost:8080/api/sandbox/deploy/default", { method: "POST" });
      const data = await res.json();
      if (data.data?.previewUrl) {
        setDeployedUrl(data.data.previewUrl);
      }
    } catch (err) {
      console.error("Deploy failed:", err);
    } finally {
      setIsDeploying(false);
    }
  };

  return (
    <div className="flex flex-col h-screen overflow-hidden bg-[#090d16] text-slate-100 font-sans">
      {/* 顶部全局导航栏 */}
      <header className="h-14 border-b border-slate-800/80 bg-slate-900/60 backdrop-blur px-6 flex items-center justify-between z-20">
        <div className="flex items-center space-x-3">
          <div className="w-8 h-8 rounded-lg bg-indigo-600 flex items-center justify-center font-bold text-white shadow-lg shadow-indigo-500/20">
            AH
          </div>
          <span className="font-bold tracking-tight text-base text-slate-100">
            AgentHub <span className="text-xs px-2 py-0.5 ml-1.5 rounded-full bg-indigo-500/10 text-indigo-400 border border-indigo-500/20">Enterprise Core</span>
          </span>
        </div>

        <div className="flex items-center space-x-3 text-xs">
          <div className="flex items-center space-x-2 px-3 py-1 rounded-full bg-slate-800/60 border border-slate-700/50">
            <span className={`w-2 h-2 rounded-full ${backendUp ? "bg-emerald-400 animate-pulse" : "bg-amber-400"}`}></span>
            <span className="text-slate-300">Spring Boot 3.3 Backend : {backendUp ? "UP" : "CONNECTED"}</span>
          </div>
          <div className="flex items-center space-x-2 px-3 py-1 rounded-full bg-slate-800/60 border border-slate-700/50 text-slate-300">
            <ShieldCheck className="w-3.5 h-3.5 text-indigo-400" />
            <span>DeepSeek LLM Online</span>
          </div>
        </div>
      </header>

      {/* 主工作区 Bento Grid 3 栏架构 */}
      <div className="flex-1 grid grid-cols-12 overflow-hidden">
        {/* 左侧栏：会话与 Agent 成员列表 (3 栅格) */}
        <aside className="col-span-3 border-r border-slate-800/80 bg-slate-950/40 flex flex-col overflow-y-auto">
          <div className="p-4 border-b border-slate-800/60">
            <div className="text-xs font-semibold uppercase tracking-wider text-slate-400 mb-3 flex items-center justify-between">
              <span>协作会话 (Conversations)</span>
              <span className="text-[10px] bg-slate-800 px-1.5 py-0.5 rounded text-slate-400">{conversations.length}</span>
            </div>
            <div className="space-y-1.5">
              {conversations.map((conv) => (
                <button
                  key={conv.id}
                  onClick={() => setActiveConvId(conv.id)}
                  className={`w-full flex items-center space-x-3 px-3 py-2.5 rounded-xl text-left text-sm transition ${
                    activeConvId === conv.id
                      ? "bg-indigo-600/15 text-indigo-300 border border-indigo-500/30 font-medium"
                      : "text-slate-400 hover:bg-slate-900/60"
                  }`}
                >
                  <Users className="w-4 h-4 text-indigo-400 shrink-0" />
                  <div className="truncate">
                    <div className="truncate text-xs font-medium">{conv.title}</div>
                    <div className="text-[10px] text-slate-500 truncate">ID: {conv.id}</div>
                  </div>
                </button>
              ))}
            </div>
          </div>

          <div className="p-4 flex-1">
            <div className="text-xs font-semibold uppercase tracking-wider text-slate-400 mb-3 flex items-center justify-between">
              <span>可用智能体成员 (Agents)</span>
              <span className="text-[10px] bg-slate-800 px-1.5 py-0.5 rounded text-slate-400">5</span>
            </div>
            <div className="space-y-1.5">
              {[
                { name: "Orchestrator", role: "协调器 / 任务拆解", tag: "System", color: "text-amber-400" },
                { name: "BackendArchitect", role: "Spring Boot / DeepSeek", tag: "Live LLM", color: "text-emerald-400" },
                { name: "FrontendEngineer", role: "Next.js 14 / UI", tag: "Live LLM", color: "text-sky-400" },
                { name: "QAAuditor", role: "JGit Diff / 测试防御", tag: "Local", color: "text-purple-400" },
                { name: "Claude Code", role: "Anthropic CLI", tag: "CLI", color: "text-rose-400" },
              ].map((agent) => (
                <div
                  key={agent.name}
                  className="flex items-center justify-between px-3 py-2 rounded-xl bg-slate-900/40 border border-slate-800/40 hover:border-slate-700/60 transition"
                >
                  <div className="flex items-center space-x-2.5 truncate">
                    <Bot className={`w-4 h-4 ${agent.color}`} />
                    <div className="truncate">
                      <div className="text-xs font-medium text-slate-200">{agent.name}</div>
                      <div className="text-[10px] text-slate-500 truncate">{agent.role}</div>
                    </div>
                  </div>
                  <span className="text-[9px] px-1.5 py-0.5 rounded bg-slate-800 text-slate-400 shrink-0">
                    {agent.tag}
                  </span>
                </div>
              ))}
            </div>
          </div>
        </aside>

        {/* 中间栏：飞书级 IM 协同大厅 (5 栅格) */}
        <main className="col-span-5 border-r border-slate-800/80 flex flex-col bg-[#0b0f19] overflow-hidden">
          <div className="h-12 border-b border-slate-800/60 px-4 flex items-center justify-between bg-slate-900/20">
            <div className="flex items-center space-x-2">
              <span className="font-semibold text-sm text-slate-200">🔥 多 Agent 协同会话</span>
              <span className="text-[11px] text-slate-400 font-normal">（支持 @ 智能体指令协同）</span>
            </div>
            <span className="text-xs text-indigo-400 font-mono">SSE Stream : Live</span>
          </div>

          {/* 消息滚动区 */}
          <div className="flex-1 p-4 overflow-y-auto space-y-4">
            {messages.length === 0 ? (
              <div className="flex flex-col items-center justify-center h-full text-slate-500 text-xs">
                <Bot className="w-8 h-8 mb-2 text-slate-600 animate-bounce" />
                <span>暂无消息，请在下方发送指令或 @Orchestrator 开启协作</span>
              </div>
            ) : (
              messages.map((m, idx) => {
                const isUser = m.senderType === "USER";
                const isSystem = m.senderType === "SYSTEM";

                if (isSystem) {
                  return (
                    <div key={`sys-${m.id || ''}-${idx}`} className="flex justify-center">
                      <span className="text-[11px] bg-slate-800/50 text-slate-400 border border-slate-700/40 px-3 py-1 rounded-full">
                        {m.content}
                      </span>
                    </div>
                  );
                }

                return (
                  <div key={`msg-${m.id || ''}-${idx}`} className={`flex flex-col ${isUser ? "items-end" : "items-start"}`}>
                    <div className="flex items-center space-x-2 mb-1 text-[11px] text-slate-400">
                      <span className="font-medium text-slate-300">{m.senderId}</span>
                      <span>{m.timestamp || "实时"}</span>
                    </div>

                    <div
                      className={`max-w-[85%] rounded-2xl p-3.5 text-xs leading-relaxed shadow-sm ${
                        isUser
                          ? "bg-indigo-600 text-white rounded-tr-sm"
                          : "bg-slate-900/90 border border-slate-800 text-slate-200 rounded-tl-sm"
                      }`}
                    >
                      <div className="whitespace-pre-wrap">{m.content}</div>

                      {/* 飞书级富文本交互卡片渲染 */}
                      {m.cardPayload && (
                        <div className="mt-3 bg-slate-950/80 border border-slate-800 rounded-xl p-3 text-left">
                          <div className="font-semibold text-xs text-amber-400 mb-1 flex items-center space-x-1.5">
                            <Sparkles className="w-3.5 h-3.5" />
                            <span>{m.cardPayload.headerTitle}</span>
                          </div>
                          <p className="text-[11px] text-slate-400 mb-2.5">{m.cardPayload.summary}</p>

                          <div className="space-y-1.5 mb-3">
                            {m.cardPayload.subtasks?.map((task, tIdx) => (
                              <div
                                key={`subtask-${m.id || 'card'}-${task.id || ''}-${tIdx}`}
                                className="flex items-center justify-between p-2 rounded-lg bg-slate-900 border border-slate-800/70 text-[11px]"
                              >
                                <div className="truncate mr-2">
                                  <span className="font-mono text-indigo-400 mr-1.5">@{task.targetAgent}</span>
                                  <span className="text-slate-300">{task.title}</span>
                                </div>
                                <span
                                  className={`px-2 py-0.5 rounded text-[10px] shrink-0 font-medium ${
                                    task.status === "COMPLETED"
                                      ? "bg-emerald-500/10 text-emerald-400 border border-emerald-500/20"
                                      : task.status === "RUNNING"
                                      ? "bg-sky-500/10 text-sky-400 border border-sky-500/20 animate-pulse"
                                      : "bg-slate-800 text-slate-400"
                                  }`}
                                >
                                  {task.status}
                                </span>
                              </div>
                            ))}
                          </div>

                          <div className="flex space-x-2 pt-1 border-t border-slate-800/60">
                            {m.cardPayload.actions?.map((act) => (
                              <button
                                key={act}
                                onClick={() => handleCardAction(act)}
                                className="px-2.5 py-1 rounded bg-indigo-600/20 hover:bg-indigo-600/30 text-indigo-300 border border-indigo-500/30 text-[10px] font-medium transition"
                              >
                                {act}
                              </button>
                            ))}
                          </div>
                        </div>
                      )}
                    </div>
                  </div>
                );
              })
            )}
            <div ref={messagesEndRef} />
          </div>

          {/* 快捷 @Mention 栏 */}
          <div className="px-4 py-2 border-t border-slate-800/40 bg-slate-900/40 flex items-center space-x-2 text-[11px]">
            <span className="text-slate-400 shrink-0 font-medium">快捷指令:</span>
            {["@Orchestrator", "@BackendArchitect", "@FrontendEngineer", "@QAAuditor"].map((tag) => (
              <button
                key={tag}
                onClick={() => setInputMessage((prev) => prev + " " + tag + " ")}
                className="px-2 py-0.5 rounded-md bg-slate-800 hover:bg-slate-700 text-slate-300 transition border border-slate-700/50"
              >
                {tag}
              </button>
            ))}
          </div>

          {/* 输入框区域 */}
          <div className="p-3 border-t border-slate-800/80 bg-slate-950/60">
            <div className="flex items-center space-x-2 bg-slate-900/80 border border-slate-800 rounded-xl px-3 py-2 focus-within:border-indigo-500 transition">
              <input
                type="text"
                value={inputMessage}
                onChange={(e) => setInputMessage(e.target.value)}
                onKeyDown={(e) => e.key === "Enter" && handleSendMessage()}
                placeholder="发送消息或 @ 智能体发起协作..."
                className="flex-1 bg-transparent text-xs text-slate-100 placeholder-slate-500 focus:outline-none"
              />
              <button
                onClick={handleSendMessage}
                disabled={isSending}
                className="w-7 h-7 rounded-lg bg-indigo-600 hover:bg-indigo-500 text-white flex items-center justify-center transition shadow-md shadow-indigo-500/20"
              >
                {isSending ? <Clock className="w-3.5 h-3.5 animate-spin" /> : <Send className="w-3.5 h-3.5" />}
              </button>
            </div>
          </div>
        </main>

        {/* 右侧栏：开发者闭环工具箱 (4 栅格) */}
        <section className="col-span-4 flex flex-col bg-[#090d16] overflow-hidden">
          {/* 标签栏 */}
          <div className="h-12 border-b border-slate-800/80 px-2 flex items-center space-x-1 bg-slate-900/30 overflow-x-auto">
            <button
              onClick={() => setActiveTab("diff")}
              className={`flex items-center space-x-1.5 text-xs font-medium px-3 py-2.5 rounded-lg transition ${
                activeTab === "diff" ? "bg-indigo-600/20 text-indigo-400 border border-indigo-500/30" : "text-slate-400 hover:text-slate-300"
              }`}
            >
              <GitCompare className="w-3.5 h-3.5" />
              <span>JGit Diff</span>
            </button>
            <button
              onClick={() => setActiveTab("canvas")}
              className={`flex items-center space-x-1.5 text-xs font-medium px-3 py-2.5 rounded-lg transition ${
                activeTab === "canvas" ? "bg-indigo-600/20 text-indigo-400 border border-indigo-500/30" : "text-slate-400 hover:text-slate-300"
              }`}
            >
              <Layers className="w-3.5 h-3.5" />
              <span>DAG 画布</span>
            </button>
            <button
              onClick={() => setActiveTab("explorer")}
              className={`flex items-center space-x-1.5 text-xs font-medium px-3 py-2.5 rounded-lg transition ${
                activeTab === "explorer" ? "bg-indigo-600/20 text-indigo-400 border border-indigo-500/30" : "text-slate-400 hover:text-slate-300"
              }`}
            >
              <FolderTree className="w-3.5 h-3.5" />
              <span>文件树</span>
            </button>
            <button
              onClick={() => setActiveTab("preview")}
              className={`flex items-center space-x-1.5 text-xs font-medium px-3 py-2.5 rounded-lg transition ${
                activeTab === "preview" ? "bg-indigo-600/20 text-indigo-400 border border-indigo-500/30" : "text-slate-400 hover:text-slate-300"
              }`}
            >
              <Play className="w-3.5 h-3.5" />
              <span>沙箱预览</span>
            </button>
            <button
              onClick={() => setActiveTab("deploy")}
              className={`flex items-center space-x-1.5 text-xs font-medium px-3 py-2.5 rounded-lg transition ${
                activeTab === "deploy" ? "bg-indigo-600/20 text-indigo-400 border border-indigo-500/30" : "text-slate-400 hover:text-slate-300"
              }`}
            >
              <Rocket className="w-3.5 h-3.5" />
              <span>部署</span>
            </button>
          </div>

          {/* 选项卡内容区 */}
          <div className="flex-1 p-3 overflow-y-auto">
            {activeTab === "diff" && (
              <div className="space-y-3">
                <div className="flex items-center justify-between">
                  <div className="text-xs font-semibold text-slate-300 flex items-center space-x-1.5">
                    <FileCode className="w-4 h-4 text-emerald-400" />
                    <span>JGit 行级代码审查 (Working Tree vs Baseline)</span>
                  </div>
                  <button onClick={fetchDiffs} className="text-slate-400 hover:text-slate-200">
                    <RotateCcw className="w-3 h-3" />
                  </button>
                </div>

                {diffEntries.length === 0 ? (
                  <div className="p-6 bg-slate-950 border border-slate-800 rounded-xl text-center text-xs text-slate-500">
                    工作区当前与基线分支一致，触发智能体生成代码后将在此展示 Diff。
                  </div>
                ) : (
                  diffEntries.map((entry, idx) => (
                    <div key={idx} className="bg-slate-950 border border-slate-800 rounded-xl overflow-hidden font-mono text-[11px]">
                      <div className="bg-slate-900/80 px-3 py-1.5 border-b border-slate-800 text-slate-400 flex justify-between items-center">
                        <span className="truncate">{entry.newPath}</span>
                        <div className="flex items-center space-x-2 shrink-0">
                          <span className="text-emerald-400">+{entry.linesAdded}</span>
                          <span className="text-rose-400">-{entry.linesDeleted}</span>
                          <span className="px-1.5 py-0.5 rounded bg-slate-800 text-[10px] text-slate-300">{entry.changeType}</span>
                        </div>
                      </div>
                      <div className="p-2.5 text-slate-300 overflow-x-auto max-h-56">
                        <pre className="text-[10px] leading-tight text-emerald-300">{entry.diffContent}</pre>
                      </div>
                    </div>
                  ))
                )}
              </div>
            )}

            {activeTab === "canvas" && (
              <div className="h-full">
                <WorkflowCanvas onWorkflowCompleted={fetchDiffs} />
              </div>
            )}

            {activeTab === "explorer" && (
              <div className="h-full">
                <WorkspaceExplorer workspacePath={DEFAULT_WORKSPACE_PATH} />
              </div>
            )}

            {activeTab === "preview" && (
              <div className="h-full flex flex-col space-y-2">
                <div className="flex items-center justify-between text-xs text-slate-400">
                  <span className="flex items-center space-x-1.5">
                    <span className="w-2 h-2 rounded-full bg-emerald-400 animate-pulse"></span>
                    <span>沙箱端口: 8080/sandbox/preview</span>
                  </span>
                  <button
                    onClick={() => {
                      const iframe = document.getElementById("sandbox-frame") as HTMLIFrameElement;
                      if (iframe) iframe.src = iframe.src;
                    }}
                    className="flex items-center space-x-1 hover:text-slate-200 transition text-[11px]"
                  >
                    <RotateCcw className="w-3 h-3" />
                    <span>热重载刷新</span>
                  </button>
                </div>

                <div className="flex-1 border border-slate-800 rounded-2xl overflow-hidden bg-slate-950 shadow-inner min-h-[360px]">
                  <iframe
                    id="sandbox-frame"
                    src="http://localhost:8080/api/sandbox/preview/default"
                    className="w-full h-full border-0"
                    title="AgentHub Web Sandbox"
                  />
                </div>
              </div>
            )}

            {activeTab === "deploy" && (
              <div className="space-y-4">
                <div className="bg-slate-900/80 border border-slate-800 rounded-2xl p-4 text-xs">
                  <div className="flex items-center justify-between mb-3">
                    <span className="font-semibold text-slate-200">自动化生产部署清单</span>
                    <span className="px-2 py-0.5 rounded bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 text-[10px]">
                      READY
                    </span>
                  </div>
                  <p className="text-slate-400 text-[11px] mb-3">
                    一键提取项目最终交付物，生成生产级 Docker 镜像描述符并输出公网/局域网预览地址。
                  </p>

                  <div className="bg-slate-950 p-3 rounded-xl border border-slate-800/80 font-mono text-[10px] text-slate-300 mb-4">
                    <div className="text-slate-500 mb-1"># 生成的 Dockerfile</div>
                    <div>FROM nginx:alpine</div>
                    <div>COPY dist/ /usr/share/nginx/html/</div>
                    <div>EXPOSE 80</div>
                    <div>CMD [&quot;nginx&quot;, &quot;-g&quot;, &quot;daemon off;&quot;]</div>
                  </div>

                  <button
                    onClick={handleDeploy}
                    disabled={isDeploying}
                    className="w-full py-2.5 bg-gradient-to-r from-indigo-600 to-indigo-500 hover:from-indigo-500 hover:to-indigo-400 text-white rounded-xl font-medium text-xs transition shadow-lg shadow-indigo-500/25 flex items-center justify-center space-x-2"
                  >
                    {isDeploying ? (
                      <>
                        <Clock className="w-3.5 h-3.5 animate-spin" />
                        <span>正在构建镜像并发布...</span>
                      </>
                    ) : (
                      <>
                        <Rocket className="w-3.5 h-3.5" />
                        <span>立即一键部署</span>
                      </>
                    )}
                  </button>
                </div>

                {deployedUrl && (
                  <div className="p-3 bg-emerald-950/20 border border-emerald-500/30 rounded-xl text-xs space-y-1">
                    <div className="flex items-center space-x-1.5 text-emerald-400 font-semibold">
                      <CheckCircle2 className="w-4 h-4" />
                      <span>项目部署成功！</span>
                    </div>
                    <div className="text-[11px] text-slate-300">
                      访问链接:{" "}
                      <a href={deployedUrl} target="_blank" rel="noreferrer" className="text-indigo-400 underline ml-1">
                        {deployedUrl}
                      </a>
                    </div>
                  </div>
                )}
              </div>
            )}
          </div>
        </section>
      </div>
    </div>
  );
}
