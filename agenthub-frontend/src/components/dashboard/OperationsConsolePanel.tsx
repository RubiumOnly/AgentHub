"use client";

import React, { useState, useEffect, useCallback } from "react";
import { BentoCard } from "@/components/common/BentoCard";
import { StatusBadge } from "@/components/common/StatusBadge";
import {
  OpsMetricsSummaryView,
  AuthAuditLogView,
  BackupManifest,
  RestoreVerificationReport,
} from "@/types";
import { opsApi } from "@/services/api";
import {
  Activity,
  HardDrive,
  ShieldAlert,
  RotateCcw,
  RefreshCw,
  CheckCircle2,
  Lock,
  Database,
  Cpu,
  Layers,
  FileText,
  AlertTriangle,
  Play,
  FileCheck,
} from "lucide-react";

interface OperationsConsolePanelProps {
  isDemo?: boolean;
}

export function OperationsConsolePanel({ isDemo = false }: OperationsConsolePanelProps) {
  const [metrics, setMetrics] = useState<OpsMetricsSummaryView | null>(null);
  const [auditLogs, setAuditLogs] = useState<AuthAuditLogView[]>([]);
  const [auditPage, setAuditPage] = useState(1);
  const [totalAuditLogs, setTotalAuditLogs] = useState(0);
  const [selectedEventType, setSelectedEventType] = useState<string>("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Backup & Restore states
  const [backupDir, setBackupDir] = useState("");
  const [passphrase, setPassphrase] = useState("");
  const [targetWorkspaceRoot, setTargetWorkspaceRoot] = useState("");
  const [isBackingUp, setIsBackingUp] = useState(false);
  const [isVerifying, setIsVerifying] = useState(false);
  const [isRestoring, setIsRestoring] = useState(false);
  const [lastManifest, setLastManifest] = useState<BackupManifest | null>(null);
  const [restoreReport, setRestoreReport] = useState<RestoreVerificationReport | null>(null);
  const [opsFeedback, setOpsFeedback] = useState<string | null>(null);

  const loadData = useCallback(async () => {
    setLoading(true);
    setError(null);

    if (isDemo) {
      setMetrics({
        systemHealth: "UP",
        activeRuns: 1,
        activeSseConnections: 2,
        workspaceDiskAvailableRatio: 0.88,
        circuitBreakerStates: { deepseek: "CLOSED", gemini: "CLOSED" },
        totalRuns: 14,
        totalSteps: 48,
        totalTokensConsumed: 124800,
        totalDeployments: 3,
        rbacEvaluations: { allowed: 156, denied: 4 },
        auditLogsRecorded: 28,
        uptimeSeconds: 86400 * 3 + 14200,
        jvmMemoryUsedMb: 342,
        jvmMemoryTotalMb: 1024,
        jvmMemoryMaxMb: 4096,
        sloStatus: "HEALTHY",
      });
      setAuditLogs([
        {
          id: "log-demo-1",
          eventType: "BACKUP_SNAPSHOT_TRIGGERED",
          userId: "usr-admin",
          ipAddress: "10.0.0.1",
          details: "Backup snapshot created [bkp-demo-2026] in 142ms",
          createdAt: new Date(Date.now() - 3600000).toISOString(),
        },
        {
          id: "log-demo-2",
          eventType: "RBAC_ROLE_ASSIGNED",
          userId: "usr-admin",
          ipAddress: "10.0.0.1",
          details: "Updated member [usr-dev-1] to role [MAINTAINER]",
          createdAt: new Date(Date.now() - 7200000).toISOString(),
        },
        {
          id: "log-demo-3",
          eventType: "LOGIN_SUCCESS",
          userId: "usr-admin",
          ipAddress: "10.0.0.1",
          details: "User successfully authenticated with JWT",
          createdAt: new Date(Date.now() - 86400000).toISOString(),
        },
      ]);
      setTotalAuditLogs(3);
      setLoading(false);
      return;
    }

    try {
      const [mSummary, logsRes] = await Promise.all([
        opsApi.getMetricsSummary().catch(() => null),
        opsApi.queryAuditLogs(selectedEventType || undefined, undefined, auditPage, 10).catch(() => null),
      ]);
      if (mSummary) setMetrics(mSummary);
      if (logsRes) {
        setAuditLogs(logsRes.items || []);
        setTotalAuditLogs(logsRes.totalElements || 0);
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : "获取运维监控数据失败";
      setError(msg);
    } finally {
      setLoading(false);
    }
  }, [isDemo, auditPage, selectedEventType]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const handleTriggerBackup = async () => {
    setIsBackingUp(true);
    setOpsFeedback(null);
    setError(null);
    try {
      if (isDemo) {
        const fakeManifest: BackupManifest = {
          backupId: `bkp-${Date.now()}`,
          consistentPointUtc: new Date().toISOString(),
          flywaySchemaVersion: "V12",
          encrypted: Boolean(passphrase),
          encryptionAlgorithm: passphrase ? "AES-256-GCM" : "NONE",
          dbSnapshotFileName: "db-snapshot.json",
          dbSnapshotSha256: "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
          tableCounts: { projects: 2, workspaces: 2, workflow_runs: 5 },
          workspaceFileChecksums: { "src/main/Service.java": "a1b2c3d4" },
          sampledRunFingerprints: {},
          backupDurationMs: 145,
        };
        setLastManifest(fakeManifest);
        setOpsFeedback("灾备快照创建成功 (Demo 仿真模式)。指纹校验匹配。");
        return;
      }
      const manifest = await opsApi.triggerBackup(backupDir.trim() || undefined, passphrase.trim() || undefined);
      setLastManifest(manifest);
      setOpsFeedback(`灾备快照成功创建: ${manifest.backupId} (SHA-256: ${manifest.dbSnapshotSha256.slice(0, 12)}...)`);
      await loadData();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : "创建灾备快照失败";
      setError(msg);
    } finally {
      setIsBackingUp(false);
    }
  };

  const handleVerifyBackup = async () => {
    if (!backupDir.trim() && !lastManifest) {
      setError("请指定待验证的备份目录路径");
      return;
    }
    setIsVerifying(true);
    setOpsFeedback(null);
    setError(null);
    try {
      if (isDemo) {
        setOpsFeedback("备份归档加密校验通过，SHA-256 签名无篡改，Schema 版本一致。");
        return;
      }
      const dir = backupDir.trim() || "data/backups/latest";
      const verified = await opsApi.verifyBackup(dir, passphrase.trim() || undefined);
      setLastManifest(verified);
      setOpsFeedback(`备份归档完整性校验通过: ${verified.backupId}`);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : "校验备份归档失败";
      setError(msg);
    } finally {
      setIsVerifying(false);
    }
  };

  const handleRestoreBackup = async () => {
    if (!backupDir.trim()) {
      setError("还原操作需要输入有效的备份快照目录路径");
      return;
    }
    if (!window.confirm("警告：灾备还原将校验快照并幂等同步至目标工作区和数据库，是否继续？")) return;

    setIsRestoring(true);
    setOpsFeedback(null);
    setError(null);
    try {
      if (isDemo) {
        setRestoreReport({
          backupId: "bkp-demo-restored",
          checksumVerified: true,
          schemaVersionVerified: true,
          sampledRunsVerified: true,
          restoredProjectsCount: 2,
          restoredRunsCount: 5,
          restoredStepsCount: 16,
          restoredEventsCount: 42,
          restoredArtifactsCount: 3,
          restoredDeploymentsCount: 1,
          restoredWorkspaceFilesCount: 8,
          restoreDurationMs: 230,
        });
        setOpsFeedback("灾备还原验证完成：数据库行数与工作区文件校验均绿灯通过。");
        return;
      }
      const report = await opsApi.restoreBackup(
        backupDir.trim(),
        targetWorkspaceRoot.trim() || undefined,
        passphrase.trim() || undefined
      );
      setRestoreReport(report);
      setOpsFeedback(`灾备还原成功：快照 [${report.backupId}] 已完成恢复 (校验用时 ${report.restoreDurationMs}ms)`);
      await loadData();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : "灾备还原执行失败";
      setError(msg);
    } finally {
      setIsRestoring(false);
    }
  };

  const formatUptime = (seconds?: number) => {
    if (!seconds) return "0s";
    const d = Math.floor(seconds / 86400);
    const h = Math.floor((seconds % 86400) / 3600);
    const m = Math.floor((seconds % 3600) / 60);
    return `${d > 0 ? `${d}d ` : ""}${h}h ${m}m`;
  };

  return (
    <div className="flex-1 flex flex-col space-y-4 overflow-y-auto pb-6">
      {/* Top Bento Row: System SLO & Telemetry Overview */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        {/* SLO Health Indicator */}
        <BentoCard
          title="系统健康与 SLO 评级"
          subtitle="Real-time Service Level Objective"
          icon={<Activity className="w-4 h-4 text-emerald-400" />}
          badge={
            <span
              className={`px-2 py-0.5 rounded text-[10px] font-mono font-bold flex items-center space-x-1 ${
                metrics?.sloStatus === "HEALTHY"
                  ? "bg-emerald-500/10 text-emerald-400 border border-emerald-500/30"
                  : metrics?.sloStatus === "WARNING"
                  ? "bg-amber-500/10 text-amber-400 border border-amber-500/30"
                  : "bg-rose-500/10 text-rose-400 border border-rose-500/30"
              }`}
            >
              <span className="w-1.5 h-1.5 rounded-full bg-current animate-pulse" />
              <span>{metrics?.sloStatus || "HEALTHY"}</span>
            </span>
          }
        >
          <div className="p-4 space-y-2">
            <div className="text-2xl font-mono font-bold text-zinc-100 flex items-baseline space-x-2">
              <span>{metrics?.systemHealth || "UP"}</span>
              <span className="text-xs text-zinc-400 font-normal">系统在线</span>
            </div>
            <div className="text-xs text-zinc-400">
              运行时间: <span className="text-zinc-200 font-mono">{formatUptime(metrics?.uptimeSeconds)}</span>
            </div>
          </div>
        </BentoCard>

        {/* Runtime Capacity */}
        <BentoCard
          title="并发执行负载"
          subtitle="Active DAG Runs & SSE streams"
          icon={<Cpu className="w-4 h-4 text-sky-400" />}
        >
          <div className="p-4 space-y-2">
            <div className="text-2xl font-mono font-bold text-zinc-100">
              {metrics?.activeRuns ?? 0}
              <span className="text-xs font-normal text-zinc-400 ml-1.5">活跃执行 / {metrics?.activeSseConnections ?? 0} SSE</span>
            </div>
            <div className="text-xs text-zinc-400">
              总计交付: <span className="text-zinc-200 font-mono">{metrics?.totalRuns ?? 0}</span> Runs / <span className="text-zinc-200 font-mono">{metrics?.totalSteps ?? 0}</span> Steps
            </div>
          </div>
        </BentoCard>

        {/* Memory & Storage */}
        <BentoCard
          title="JVM 与工作区磁盘"
          subtitle="Memory & Workspace Quota"
          icon={<HardDrive className="w-4 h-4 text-purple-400" />}
        >
          <div className="p-4 space-y-2">
            <div className="text-2xl font-mono font-bold text-zinc-100">
              {metrics?.jvmMemoryUsedMb ?? 0}
              <span className="text-xs font-normal text-zinc-400 ml-1">MB / {metrics?.jvmMemoryTotalMb ?? 0} MB</span>
            </div>
            <div className="text-xs text-zinc-400">
              磁盘可用比例: <span className="text-zinc-200 font-mono">{Math.round((metrics?.workspaceDiskAvailableRatio ?? 0.85) * 100)}%</span>
            </div>
          </div>
        </BentoCard>

        {/* RBAC Security Evaluations */}
        <BentoCard
          title="RBAC 鉴权与审计流"
          subtitle="Security Evaluations & Trails"
          icon={<ShieldAlert className="w-4 h-4 text-amber-400" />}
        >
          <div className="p-4 space-y-2">
            <div className="text-2xl font-mono font-bold text-emerald-400">
              {metrics?.rbacEvaluations?.allowed ?? 0}
              <span className="text-xs font-normal text-rose-400 ml-2">
                / {metrics?.rbacEvaluations?.denied ?? 0} 阻断
              </span>
            </div>
            <div className="text-xs text-zinc-400">
              审计事件库: <span className="text-zinc-200 font-mono">{metrics?.auditLogsRecorded ?? totalAuditLogs}</span> 条记录
            </div>
          </div>
        </BentoCard>
      </div>

      {/* Disaster Recovery Console BentoCard */}
      <BentoCard
        title="灾难恢复与数据快照管理"
        subtitle="Cryptographically verified point-in-time backups & idempotent restoration"
        icon={<Database className="w-4 h-4 text-indigo-400" />}
        badge={
          <span className="px-2 py-0.5 rounded text-[10px] font-mono bg-indigo-500/10 border border-indigo-500/30 text-indigo-300">
            PBKDF2-AES-256-GCM
          </span>
        }
      >
        <div className="p-5 space-y-4">
          <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
            <div className="space-y-1">
              <label className="text-xs font-medium text-zinc-300">快照归档路径 (可选，默认内置)</label>
              <input
                type="text"
                placeholder="例如: data/backups/manual-01"
                value={backupDir}
                onChange={(e) => setBackupDir(e.target.value)}
                className="w-full bg-zinc-950 border border-zinc-700/80 rounded-xl px-3 py-2 text-xs text-zinc-100 placeholder-zinc-400 outline-none focus:border-indigo-500 transition font-mono"
              />
            </div>
            <div className="space-y-1">
              <label className="text-xs font-medium text-zinc-300">加密口令 (Passphrase，留空则明文存储)</label>
              <input
                type="password"
                placeholder="AES-256-GCM 密钥口令"
                value={passphrase}
                onChange={(e) => setPassphrase(e.target.value)}
                className="w-full bg-zinc-950 border border-zinc-700/80 rounded-xl px-3 py-2 text-xs text-zinc-100 placeholder-zinc-400 outline-none focus:border-indigo-500 transition font-mono"
              />
            </div>
            <div className="space-y-1">
              <label className="text-xs font-medium text-zinc-300">目标恢复工作区根路径 (还原时生效)</label>
              <input
                type="text"
                placeholder="例如: data/workspaces-restore"
                value={targetWorkspaceRoot}
                onChange={(e) => setTargetWorkspaceRoot(e.target.value)}
                className="w-full bg-zinc-950 border border-zinc-700/80 rounded-xl px-3 py-2 text-xs text-zinc-100 placeholder-zinc-400 outline-none focus:border-indigo-500 transition font-mono"
              />
            </div>
          </div>

          <div className="flex flex-wrap items-center gap-2.5 pt-1">
            <button
              onClick={handleTriggerBackup}
              disabled={isBackingUp}
              className="flex items-center space-x-1.5 px-4 py-2 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-white text-xs font-medium transition shadow-md shadow-indigo-600/20 disabled:opacity-50"
            >
              <Database className="w-3.5 h-3.5" />
              <span>{isBackingUp ? "正在快照备份..." : "创建时间点一致性灾备快照"}</span>
            </button>

            <button
              onClick={handleVerifyBackup}
              disabled={isVerifying}
              className="flex items-center space-x-1.5 px-4 py-2 rounded-xl bg-zinc-800 hover:bg-zinc-700 text-zinc-200 text-xs font-medium transition border border-zinc-700/80 disabled:opacity-50"
            >
              <CheckCircle2 className="w-3.5 h-3.5 text-emerald-400" />
              <span>{isVerifying ? "正在校验指纹..." : "验证快照完整性与指纹"}</span>
            </button>

            <button
              onClick={handleRestoreBackup}
              disabled={isRestoring || !backupDir.trim()}
              className="flex items-center space-x-1.5 px-4 py-2 rounded-xl bg-rose-600/20 hover:bg-rose-600/30 text-rose-300 text-xs font-medium transition border border-rose-500/30 disabled:opacity-50"
            >
              <RotateCcw className="w-3.5 h-3.5" />
              <span>{isRestoring ? "正在还原现场..." : "还原备份至目标环境"}</span>
            </button>
          </div>

          {opsFeedback && (
            <div className="flex items-center space-x-2 rounded-xl border border-emerald-500/30 bg-emerald-950/20 p-3 text-xs text-emerald-300">
              <CheckCircle2 className="w-4 h-4 shrink-0 text-emerald-400" />
              <span>{opsFeedback}</span>
            </div>
          )}

          {lastManifest && (
            <div className="rounded-xl border border-zinc-800 bg-zinc-950/60 p-3.5 space-y-2 text-xs font-mono">
              <div className="flex items-center justify-between text-zinc-300">
                <span>快照 ID: <strong>{lastManifest.backupId}</strong></span>
                <span className="text-zinc-400">{lastManifest.consistentPointUtc}</span>
              </div>
              <div className="text-[11px] text-zinc-400 truncate">
                SHA-256 指纹: <span className="text-zinc-200">{lastManifest.dbSnapshotSha256}</span>
              </div>
              <div className="flex flex-wrap items-center gap-3 text-[11px] text-zinc-400">
                <span>耗时: {lastManifest.backupDurationMs}ms</span>
                <span>加密: {lastManifest.encrypted ? "AES-256-GCM" : "NONE"}</span>
                <span>Flyway 版本: {lastManifest.flywaySchemaVersion}</span>
              </div>
            </div>
          )}

          {restoreReport && (
            <div className="rounded-xl border border-emerald-500/30 bg-emerald-950/20 p-3.5 space-y-2 text-xs font-mono text-emerald-300">
              <div className="font-semibold text-emerald-200">
                灾备还原报告: 备份 ID [{restoreReport.backupId}] 已完成全量验证
              </div>
              <div className="grid grid-cols-2 md:grid-cols-4 gap-2 text-[11px]">
                <div>指纹校验: {restoreReport.checksumVerified ? "✓ 通过" : "✕ 失败"}</div>
                <div>Schema 一致: {restoreReport.schemaVersionVerified ? "✓ 通过" : "✕ 失败"}</div>
                <div>还原项目数: {restoreReport.restoredProjectsCount}</div>
                <div>还原工作区文件: {restoreReport.restoredWorkspaceFilesCount}</div>
              </div>
            </div>
          )}
        </div>
      </BentoCard>

      {/* Immutable Audit Logs Table */}
      <BentoCard
        title="不可篡改安全审计日志"
        subtitle="Security Trace Attribution & Compliance Trails"
        icon={<FileText className="w-4 h-4 text-teal-400" />}
        badge={
          <span className="px-2 py-0.5 rounded text-[10px] font-mono bg-zinc-800 text-zinc-400">
            总计 {totalAuditLogs} 条审计记录
          </span>
        }
        actions={
          <div className="flex items-center space-x-2">
            <select
              value={selectedEventType}
              onChange={(e) => {
                setSelectedEventType(e.target.value);
                setAuditPage(1);
              }}
              className="bg-zinc-900 border border-zinc-700 text-zinc-300 text-[11px] rounded-lg px-2 py-1 outline-none"
            >
              <option value="">全部事件类型</option>
              <option value="LOGIN_SUCCESS">LOGIN_SUCCESS</option>
              <option value="REGISTER_SUCCESS">REGISTER_SUCCESS</option>
              <option value="RBAC_ROLE_ASSIGNED">RBAC_ROLE_ASSIGNED</option>
              <option value="RBAC_ACCESS_DENIED">RBAC_ACCESS_DENIED</option>
              <option value="BACKUP_SNAPSHOT_TRIGGERED">BACKUP_SNAPSHOT_TRIGGERED</option>
              <option value="DISASTER_RESTORE_TRIGGERED">DISASTER_RESTORE_TRIGGERED</option>
            </select>
            <button
              onClick={loadData}
              disabled={loading}
              className="p-1 rounded-lg text-zinc-400 hover:text-zinc-200 hover:bg-zinc-800 transition"
              title="刷新审计记录"
            >
              <RefreshCw className={`w-3.5 h-3.5 ${loading ? "animate-spin" : ""}`} />
            </button>
          </div>
        }
      >
        <div className="overflow-x-auto">
          <table className="w-full text-left border-collapse">
            <thead>
              <tr className="border-b border-zinc-800/80 text-[11px] font-medium text-zinc-400 bg-zinc-950/40">
                <th className="py-2.5 px-4">事件类型</th>
                <th className="py-2.5 px-3">操作主体 (User ID)</th>
                <th className="py-2.5 px-3">IP 地址</th>
                <th className="py-2.5 px-3">记录时间 (UTC)</th>
                <th className="py-2.5 px-4">安全审计摘要</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-zinc-800/40 text-xs">
              {auditLogs.length === 0 ? (
                <tr>
                  <td colSpan={5} className="py-8 text-center text-zinc-400 text-xs">
                    暂无符合条件的审计日志。
                  </td>
                </tr>
              ) : (
                auditLogs.map((log) => {
                  const isDenied = log.eventType.includes("DENIED");
                  const isBackup = log.eventType.includes("BACKUP") || log.eventType.includes("RESTORE");
                  return (
                    <tr key={log.id} className="hover:bg-zinc-800/30 transition-colors">
                      <td className="py-2.5 px-4 font-mono text-[11px]">
                        <span
                          className={`px-2 py-0.5 rounded text-[10px] font-semibold border ${
                            isDenied
                              ? "bg-rose-500/10 text-rose-400 border-rose-500/30"
                              : isBackup
                              ? "bg-indigo-500/10 text-indigo-400 border-indigo-500/30"
                              : "bg-emerald-500/10 text-emerald-400 border-emerald-500/30"
                          }`}
                        >
                          {log.eventType}
                        </span>
                      </td>
                      <td className="py-2.5 px-3 font-mono text-[11px] text-zinc-300">
                        {log.userId || "anonymous"}
                      </td>
                      <td className="py-2.5 px-3 font-mono text-[11px] text-zinc-400">
                        {log.ipAddress || "-"}
                      </td>
                      <td className="py-2.5 px-3 font-mono text-[11px] text-zinc-400">
                        {log.createdAt ? log.createdAt.replace("T", " ").slice(0, 19) : "-"}
                      </td>
                      <td className="py-2.5 px-4 text-zinc-300 text-[11px] leading-relaxed">
                        {log.details || "-"}
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>

        {/* Audit Pagination */}
        {totalAuditLogs > 10 && (
          <div className="flex items-center justify-between border-t border-zinc-800/60 px-4 py-2.5 text-xs text-zinc-400">
            <span>
              第 <strong className="text-zinc-200">{auditPage}</strong> 页 / 共 {Math.ceil(totalAuditLogs / 10)} 页
            </span>
            <div className="flex items-center space-x-1.5">
              <button
                onClick={() => setAuditPage((p) => Math.max(1, p - 1))}
                disabled={auditPage <= 1}
                className="px-2.5 py-1 rounded bg-zinc-800 hover:bg-zinc-700 disabled:opacity-40 text-xs transition"
              >
                上一页
              </button>
              <button
                onClick={() => setAuditPage((p) => p + 1)}
                disabled={auditPage * 10 >= totalAuditLogs}
                className="px-2.5 py-1 rounded bg-zinc-800 hover:bg-zinc-700 disabled:opacity-40 text-xs transition"
              >
                下一页
              </button>
            </div>
          </div>
        )}
      </BentoCard>
    </div>
  );
}
