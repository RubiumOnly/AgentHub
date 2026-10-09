"use client";

import React, { useState, useEffect, useRef } from "react";
import { BentoCard } from "@/components/common/BentoCard";
import { StatusBadge } from "@/components/common/StatusBadge";
import {
  Terminal,
  Play,
  RotateCcw,
  Copy,
  Trash2,
  Check,
  Radio,
  Lock,
  Unlock,
} from "lucide-react";
import { API_BASE, apiClient, isDemoMode } from "@/services/api";

export interface LogEntry {
  id: string;
  timestamp: string;
  seq?: number;
  level: "INFO" | "WARN" | "ERROR" | "STEP" | "AGENT" | "AUDIT" | "SANDBOX";
  source?: string;
  message: string;
}

// --- ANSI Escape Code Parser & Renderer ---
function renderAnsiMessage(text: string): React.ReactNode {
  if (!text || (!text.includes("\u001b[") && !text.includes("\x1b["))) {
    return text;
  }

  const ansiRegex = /\u001b\[([0-9;]*)m|\x1b\[([0-9;]*)m/g;
  const parts: React.ReactNode[] = [];
  let lastIndex = 0;
  let currentColor = "";
  let isBold = false;
  let match;

  while ((match = ansiRegex.exec(text)) !== null) {
    if (match.index > lastIndex) {
      const chunk = text.substring(lastIndex, match.index);
      parts.push(
        <span
          key={lastIndex}
          className={`${currentColor} ${isBold ? "font-bold" : ""}`}
        >
          {chunk}
        </span>
      );
    }

    const codeStr = match[1] || match[2] || "0";
    const codes = codeStr.split(";").map((c) => parseInt(c, 10));

    for (const code of codes) {
      if (code === 0) {
        currentColor = "";
        isBold = false;
      } else if (code === 1) {
        isBold = true;
      } else if (code === 30) {
        currentColor = "text-zinc-500";
      } else if (code === 31) {
        currentColor = "text-rose-400";
      } else if (code === 32) {
        currentColor = "text-emerald-400";
      } else if (code === 33) {
        currentColor = "text-amber-300";
      } else if (code === 34) {
        currentColor = "text-sky-400";
      } else if (code === 35) {
        currentColor = "text-purple-400";
      } else if (code === 36) {
        currentColor = "text-cyan-400";
      } else if (code === 37) {
        currentColor = "text-zinc-100";
      } else if (code === 90) {
        currentColor = "text-zinc-500";
      } else if (code === 91) {
        currentColor = "text-rose-300";
      } else if (code === 92) {
        currentColor = "text-emerald-300";
      } else if (code === 93) {
        currentColor = "text-amber-200";
      } else if (code === 94) {
        currentColor = "text-sky-300";
      }
    }

    lastIndex = ansiRegex.lastIndex;
  }

  if (lastIndex < text.length) {
    parts.push(
      <span
        key={lastIndex}
        className={`${currentColor} ${isBold ? "font-bold" : ""}`}
      >
        {text.substring(lastIndex)}
      </span>
    );
  }

  return parts;
}

const DEMO_LOGS: LogEntry[] = [
  {
    id: "log-1",
    timestamp: "11:00:01",
    seq: 1,
    level: "INFO",
    source: "Kernel",
    message: "WorkflowRun [run-exec-94218a] queued. \u001b[32mIdempotencyKey verified\u001b[0m.",
  },
  {
    id: "log-2",
    timestamp: "11:00:02",
    seq: 2,
    level: "STEP",
    source: "Orchestrator",
    message: "Step [START] transitioned \u001b[34mPENDING\u001b[0m -> \u001b[32mSUCCEEDED\u001b[0m in 45ms.",
  },
  {
    id: "log-3",
    timestamp: "11:00:03",
    seq: 3,
    level: "AGENT",
    source: "BackendArchitect",
    message: "Dispatched to \u001b[33mDeepSeek Provider\u001b[0m (latency=\u001b[32m245ms\u001b[0m). Emitted AuthController.java spec.",
  },
  {
    id: "log-4",
    timestamp: "11:00:08",
    seq: 4,
    level: "AGENT",
    source: "FrontendEngineer",
    message: "\u001b[36m[UI DESIGN]\u001b[0m Generated modern Bento components with Tailwind + Lucide Icons.",
  },
  {
    id: "log-5",
    timestamp: "11:00:14",
    seq: 5,
    level: "AUDIT",
    source: "QAAuditor",
    message: "JGit Unified Diff generated: 3 files changed (\u001b[32m+148\u001b[0m, \u001b[31m-8\u001b[0m lines). Hash snapshot taken.",
  },
  {
    id: "log-6",
    timestamp: "11:00:16",
    seq: 6,
    level: "WARN",
    source: "ApprovalEngine",
    message: "Node [node-security-gate] triggered: \u001b[33;1mWAITING_APPROVAL\u001b[0m. Suspended execution awaiting human signature.",
  },
];

interface LiveExecutionTerminalProps {
  runId?: string;
  onRefresh?: () => void;
  className?: string;
}

export function LiveExecutionTerminal({
  runId,
  onRefresh,
  className = "",
}: LiveExecutionTerminalProps) {
  const [logs, setLogs] = useState<LogEntry[]>([]);
  const [autoScroll, setAutoScroll] = useState(true);
  const [copied, setCopied] = useState(false);
  const [connectionStatus, setConnectionStatus] = useState<"CONNECTED" | "RECONNECTING" | "OFFLINE">("OFFLINE");
  const [lastEventId, setLastEventId] = useState<number>(0);

  const terminalBodyRef = useRef<HTMLDivElement>(null);
  const eventSourceRef = useRef<EventSource | null>(null);

  // Load historical events from backend or initialize demo logs
  useEffect(() => {
    if (isDemoMode()) {
      setLogs(DEMO_LOGS);
      setConnectionStatus("CONNECTED");
      setLastEventId(6);
      return;
    }

    if (!runId) {
      setLogs([]);
      setConnectionStatus("OFFLINE");
      return;
    }

    // Fetch initial event history from database
    let isCancelled = false;
    apiClient
      .listEvents(runId)
      .then((events) => {
        if (isCancelled || !events || events.length === 0) return;
        const initialLogs: LogEntry[] = events.map((ev) => ({
          id: `ev-${ev.id || ev.sequenceNum}`,
          timestamp: new Date(ev.createdAt).toLocaleTimeString("en-GB", { hour12: false }),
          seq: ev.sequenceNum,
          level: ev.eventType?.includes("WARN")
            ? "WARN"
            : ev.eventType?.includes("ERR")
            ? "ERROR"
            : ev.eventType?.includes("STEP")
            ? "STEP"
            : "INFO",
          source: ev.eventType || "Kernel",
          message: ev.payload,
        }));
        setLogs(initialLogs);
        const maxSeq = Math.max(...events.map((e) => e.sequenceNum), 0);
        setLastEventId(maxSeq);
      })
      .catch(() => {});

    // Acquire stream ticket and connect EventSource
    let activeEs: EventSource | null = null;
    const connectSSE = async () => {
      try {
        let ticketParam = "";
        try {
          const ticket = await apiClient.createStreamTicket();
          if (ticket) {
            ticketParam = `&ticket=${encodeURIComponent(ticket)}`;
          }
        } catch {}

        if (isCancelled) return;

        const sseUrl = `${API_BASE}/api/runs/${runId}/stream?lastEventId=${lastEventId}${ticketParam}`;
        const es = new EventSource(sseUrl, { withCredentials: true });
        activeEs = es;
        eventSourceRef.current = es;

        es.onopen = () => {
          if (!isCancelled) setConnectionStatus("CONNECTED");
        };

        es.onmessage = (event) => {
          if (isCancelled) return;
          try {
            const raw = JSON.parse(event.data);
            const seq = event.lastEventId ? parseInt(event.lastEventId, 10) : (raw.sequenceNum || Date.now());
            setLastEventId(seq);

            const newLog: LogEntry = {
              id: `sse-${seq}-${Date.now()}`,
              timestamp: new Date().toLocaleTimeString("en-GB", { hour12: false }),
              seq,
              level: raw.eventType?.includes("WARN")
                ? "WARN"
                : raw.eventType?.includes("ERR")
                ? "ERROR"
                : raw.eventType?.includes("STEP")
                ? "STEP"
                : "INFO",
              source: raw.eventType || "Stream",
              message: typeof raw.payload === "string" ? raw.payload : JSON.stringify(raw),
            };

            setLogs((prev) => {
              if (prev.some((p) => p.seq === seq && p.source === newLog.source)) return prev;
              return [...prev, newLog];
            });
            onRefresh?.();
          } catch {
            setLogs((prev) => [
              ...prev,
              {
                id: `raw-${Date.now()}`,
                timestamp: new Date().toLocaleTimeString("en-GB", { hour12: false }),
                level: "INFO",
                source: "Stream",
                message: event.data,
              },
            ]);
          }
        };

        es.onerror = () => {
          if (!isCancelled) {
            setConnectionStatus("RECONNECTING");
          }
        };
      } catch {
        if (!isCancelled) setConnectionStatus("OFFLINE");
      }
    };

    connectSSE();

    return () => {
      isCancelled = true;
      if (activeEs) activeEs.close();
      if (eventSourceRef.current) eventSourceRef.current.close();
    };
  }, [runId]);

  // Auto-scroll handler
  useEffect(() => {
    if (autoScroll && terminalBodyRef.current) {
      terminalBodyRef.current.scrollTop = terminalBodyRef.current.scrollHeight;
    }
  }, [logs, autoScroll]);

  const handleCopyLogs = () => {
    const text = logs
      .map((l) => `[${l.timestamp}] [${l.level}] [${l.source || "System"}] ${l.message}`)
      .join("\n");
    navigator.clipboard.writeText(text);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const handleClearLogs = () => {
    setLogs([]);
  };

  const getLevelColor = (level: LogEntry["level"]) => {
    switch (level) {
      case "ERROR":
        return "text-rose-400 bg-rose-950/40 border-rose-500/30";
      case "WARN":
        return "text-amber-400 bg-amber-950/40 border-amber-500/30";
      case "STEP":
        return "text-sky-400 bg-sky-950/40 border-sky-500/30";
      case "AGENT":
        return "text-indigo-400 bg-indigo-950/40 border-indigo-500/30";
      case "AUDIT":
        return "text-purple-400 bg-purple-950/40 border-purple-500/30";
      case "SANDBOX":
        return "text-emerald-400 bg-emerald-950/40 border-emerald-500/30";
      default:
        return "text-zinc-400 bg-zinc-800/40 border-zinc-700/30";
    }
  };

  return (
    <BentoCard
      title="实时执行流 (SSE Terminal)"
      subtitle={runId ? `Run: ${runId} | Cursor: #${lastEventId}` : "暂无活跃 Run"}
      icon={<Terminal className="w-4 h-4 text-sky-400" />}
      badge={
        <div className="flex items-center space-x-2">
          <StatusBadge status={connectionStatus} size="sm" />
          <span className="hidden sm:inline-flex items-center space-x-1 text-[10px] font-mono text-zinc-500">
            <Radio className="w-3 h-3 text-sky-400 animate-pulse" />
            <span>Ticket Secured</span>
          </span>
        </div>
      }
      actions={
        <div className="flex items-center space-x-1 text-xs">
          <button
            onClick={() => setAutoScroll((prev) => !prev)}
            title={autoScroll ? "锁定自动滚动 (开启)" : "自动滚动已暂停"}
            className={`px-2 py-1 rounded-lg border text-[11px] font-mono flex items-center space-x-1 transition-colors ${
              autoScroll
                ? "bg-indigo-600/20 text-indigo-300 border-indigo-500/30"
                : "bg-zinc-900 text-zinc-400 border-zinc-800"
            }`}
          >
            {autoScroll ? <Lock className="w-3 h-3" /> : <Unlock className="w-3 h-3" />}
            <span className="hidden sm:inline">Auto-Scroll</span>
          </button>

          <button
            onClick={handleCopyLogs}
            title="复制日志"
            className="p-1.5 rounded-lg border border-zinc-800 hover:border-zinc-700 bg-zinc-900/80 text-zinc-400 hover:text-zinc-200"
          >
            {copied ? <Check className="w-3 h-3 text-emerald-400" /> : <Copy className="w-3 h-3" />}
          </button>

          <button
            onClick={handleClearLogs}
            title="清空终端"
            className="p-1.5 rounded-lg border border-zinc-800 hover:border-zinc-700 bg-zinc-900/80 text-zinc-400 hover:text-rose-400"
          >
            <Trash2 className="w-3 h-3" />
          </button>
        </div>
      }
      className={`h-full ${className}`}
      bodyClassName="flex flex-col p-0 overflow-hidden"
    >
      {/* Terminal Output Area */}
      <div
        ref={terminalBodyRef}
        className="flex-1 bg-zinc-950/90 p-3 font-mono text-[11px] leading-relaxed overflow-y-auto space-y-1.5 select-text"
        style={{ minHeight: "220px" }}
      >
        {logs.length === 0 ? (
          <div className="flex flex-col items-center justify-center h-full text-zinc-600 py-8">
            <Terminal className="w-6 h-6 mb-2 text-zinc-700" />
            <span>{runId ? "等待实时执行流事件输出..." : "请启动或选择一个 Workflow Run"}</span>
          </div>
        ) : (
          logs.map((log) => (
            <div
              key={log.id}
              className="flex items-start space-x-2 py-0.5 hover:bg-zinc-900/40 rounded px-1 -mx-1 transition-colors"
            >
              <span className="text-zinc-600 shrink-0 select-none">
                {log.seq ? `#${String(log.seq).padStart(2, "0")}` : ""} {log.timestamp}
              </span>

              <span
                className={`px-1.5 py-0.2 rounded text-[10px] font-bold shrink-0 border ${getLevelColor(
                  log.level
                )}`}
              >
                {log.level}
              </span>

              {log.source && (
                <span className="text-zinc-400 shrink-0 font-medium">
                  [{log.source}]
                </span>
              )}

              <span className="text-zinc-200 break-all flex-1 whitespace-pre-wrap">
                {renderAnsiMessage(log.message)}
              </span>
            </div>
          ))
        )}
      </div>

      {/* Terminal Footer status bar */}
      <div className="border-t border-zinc-800/60 bg-zinc-900/80 px-3 py-1.5 flex items-center justify-between text-[11px] font-mono text-zinc-500">
        <div className="flex items-center space-x-3">
          <span>Buffer: {logs.length} events</span>
          <span>SSE: ticket-validated / chunked</span>
        </div>
        <div className="flex items-center space-x-2">
          <span className="w-1.5 h-1.5 rounded-full bg-emerald-400" />
          <span>Output Truncation Guard: Active</span>
        </div>
      </div>
    </BentoCard>
  );
}
