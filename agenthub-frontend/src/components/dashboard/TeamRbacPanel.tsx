"use client";

import React, { useState, useEffect, useCallback } from "react";
import { BentoCard } from "@/components/common/BentoCard";
import { StatusBadge } from "@/components/common/StatusBadge";
import {
  ProjectMemberView,
  ProjectUserRoleView,
  ProjectRbacRole,
} from "@/types";
import { projectsApi } from "@/services/api";
import {
  ShieldCheck,
  Users,
  UserPlus,
  Trash2,
  KeyRound,
  RefreshCw,
  AlertCircle,
  Crown,
  Shield,
  Code2,
  Eye,
  FileCheck,
} from "lucide-react";

interface TeamRbacPanelProps {
  projectId: string;
  isDemo?: boolean;
}

const RBAC_ROLES: Array<{
  role: ProjectRbacRole;
  label: string;
  desc: string;
  icon: React.ComponentType<{ className?: string }>;
  color: string;
}> = [
  { role: "OWNER", label: "所有者 (Owner)", desc: "最高管理权限，全功能控制", icon: Crown, color: "text-amber-400 bg-amber-500/10 border-amber-500/30" },
  { role: "ADMIN", label: "管理员 (Admin)", desc: "管理成员、配置与灾备操作", icon: Shield, color: "text-purple-400 bg-purple-500/10 border-purple-500/30" },
  { role: "MAINTAINER", label: "维护者 (Maintainer)", desc: "批准变更、执行工作流与沙箱部署", icon: KeyRound, color: "text-sky-400 bg-sky-500/10 border-sky-500/30" },
  { role: "DEVELOPER", label: "开发者 (Developer)", desc: "读写代码、运行任务与重做审查", icon: Code2, color: "text-emerald-400 bg-emerald-500/10 border-emerald-500/30" },
  { role: "VIEWER", label: "查看者 (Viewer)", desc: "只读查看代码树、执行过程与 Diff", icon: Eye, color: "text-zinc-400 bg-zinc-500/10 border-zinc-500/30" },
  { role: "AUDITOR", label: "审计员 (Auditor)", desc: "只读合规审查、证据链与审计日志", icon: FileCheck, color: "text-teal-400 bg-teal-500/10 border-teal-500/30" },
];

export function TeamRbacPanel({ projectId, isDemo = false }: TeamRbacPanelProps) {
  const [currentUserRole, setCurrentUserRole] = useState<ProjectUserRoleView | null>(null);
  const [members, setMembers] = useState<ProjectMemberView[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Invite modal state
  const [isInviteOpen, setIsInviteOpen] = useState(false);
  const [newUserId, setNewUserId] = useState("");
  const [newRole, setNewRole] = useState<ProjectRbacRole>("DEVELOPER");
  const [newRoleTitle, setNewRoleTitle] = useState("");
  const [submitting, setSubmitting] = useState(false);

  const loadData = useCallback(async () => {
    if (!projectId) return;
    setLoading(true);
    setError(null);

    if (isDemo) {
      setCurrentUserRole({
        userId: "demo-owner",
        projectId,
        rbacRole: "OWNER",
        permissions: ["PROJECT_VIEW", "PROJECT_MANAGE", "WORKSPACE_READ", "WORKSPACE_WRITE", "WORKFLOW_EXECUTE", "APPROVAL_DECIDE", "DEPLOYMENT_MANAGE", "AUDIT_VIEW", "DISASTER_RECOVERY"],
        isOwner: true,
      });
      setMembers([
        {
          memberId: "tm-demo-1",
          teamId: "team-demo",
          projectId,
          userId: "usr-arch-lead",
          displayName: "Alex Rivera",
          email: "alex@agenthub.dev",
          role: "Lead Architect",
          rbacRole: "OWNER",
          permissions: ["PROJECT_MANAGE", "WORKSPACE_WRITE", "APPROVAL_DECIDE"],
          canDelegate: true,
          createdAt: new Date().toISOString(),
        },
        {
          memberId: "tm-demo-2",
          teamId: "team-demo",
          projectId,
          userId: "usr-dev-core",
          displayName: "Elena Rostova",
          email: "elena@agenthub.dev",
          role: "Backend Engineer",
          rbacRole: "DEVELOPER",
          permissions: ["WORKSPACE_WRITE", "WORKFLOW_EXECUTE"],
          canDelegate: true,
          createdAt: new Date().toISOString(),
        },
        {
          memberId: "tm-demo-3",
          teamId: "team-demo",
          projectId,
          userId: "usr-sec-audit",
          displayName: "Marcus Vance",
          email: "marcus@compliance.io",
          role: "Security Auditor",
          rbacRole: "AUDITOR",
          permissions: ["AUDIT_VIEW"],
          canDelegate: false,
          createdAt: new Date().toISOString(),
        },
      ]);
      setLoading(false);
      return;
    }

    try {
      const [roleView, memberList] = await Promise.all([
        projectsApi.getCurrentUserRole(projectId).catch(() => null),
        projectsApi.listProjectMembers(projectId).catch(() => []),
      ]);
      if (roleView) setCurrentUserRole(roleView);
      if (memberList) setMembers(memberList);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : "获取团队数据失败";
      setError(msg);
    } finally {
      setLoading(false);
    }
  }, [projectId, isDemo]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const handleAddMember = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newUserId.trim()) return;

    setSubmitting(true);
    setError(null);
    try {
      if (isDemo) {
        const fakeMember: ProjectMemberView = {
          memberId: `tm-demo-${Date.now()}`,
          teamId: "team-demo",
          projectId,
          userId: newUserId.trim(),
          displayName: newUserId.trim(),
          email: `${newUserId.trim()}@company.local`,
          role: newRoleTitle || newRole,
          rbacRole: newRole,
          permissions: ["WORKSPACE_READ"],
          canDelegate: true,
          createdAt: new Date().toISOString(),
        };
        setMembers((prev) => [...prev, fakeMember]);
        setIsInviteOpen(false);
        setNewUserId("");
        setNewRoleTitle("");
        return;
      }

      await projectsApi.addProjectMember(projectId, {
        userId: newUserId.trim(),
        rbacRole: newRole,
        role: newRoleTitle.trim() || undefined,
      });

      setIsInviteOpen(false);
      setNewUserId("");
      setNewRoleTitle("");
      await loadData();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : "邀请成员失败";
      setError(msg);
    } finally {
      setSubmitting(false);
    }
  };

  const handleUpdateRole = async (memberId: string, role: ProjectRbacRole) => {
    setError(null);
    try {
      if (isDemo) {
        setMembers((prev) =>
          prev.map((m) => (m.memberId === memberId ? { ...m, rbacRole: role } : m))
        );
        return;
      }

      await projectsApi.updateMemberRole(projectId, memberId, { rbacRole: role });
      await loadData();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : "修改角色权限失败";
      setError(msg);
    }
  };

  const handleRemoveMember = async (memberId: string) => {
    if (!window.confirm("确认从当前项目中移除该成员？")) return;
    setError(null);
    try {
      if (isDemo) {
        setMembers((prev) => prev.filter((m) => m.memberId !== memberId));
        return;
      }
      await projectsApi.removeProjectMember(projectId, memberId);
      await loadData();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : "移除成员失败";
      setError(msg);
    }
  };

  const canManage = currentUserRole?.permissions.includes("PROJECT_MANAGE") || currentUserRole?.isOwner;

  return (
    <div className="flex-1 flex flex-col space-y-4 overflow-y-auto pb-6">
      {/* Top Banner: Current User Role & Permissions */}
      <div className="grid grid-cols-1 md:grid-cols-12 gap-4">
        <BentoCard
          title="当前登录主体与鉴权凭据"
          subtitle="Enterprise Multi-Role RBAC Model"
          icon={<ShieldCheck className="w-4 h-4 text-indigo-400" />}
          badge={
            currentUserRole ? (
              <span className="px-2.5 py-0.5 rounded-full text-[11px] font-mono font-semibold bg-indigo-500/10 border border-indigo-500/30 text-indigo-300">
                {currentUserRole.rbacRole} {currentUserRole.isOwner && "★ OWNER"}
              </span>
            ) : null
          }
          actions={
            <button
              onClick={loadData}
              disabled={loading}
              className="p-1.5 rounded-lg text-zinc-400 hover:text-zinc-200 hover:bg-zinc-800 transition"
              title="刷新权限状态"
            >
              <RefreshCw className={`w-3.5 h-3.5 ${loading ? "animate-spin" : ""}`} />
            </button>
          }
          className="md:col-span-8"
        >
          <div className="p-4 space-y-3">
            <div className="flex flex-wrap items-center gap-1.5">
              <span className="text-xs text-zinc-400 mr-1">生效权限集:</span>
              {currentUserRole?.permissions && currentUserRole.permissions.length > 0 ? (
                currentUserRole.permissions.map((p) => (
                  <span
                    key={p}
                    className="px-2 py-0.5 rounded-md text-[10px] font-mono bg-zinc-800/80 border border-zinc-700/60 text-zinc-300"
                  >
                    {p}
                  </span>
                ))
              ) : (
                <span className="text-xs text-zinc-400">读取默认只读权限 (WORKSPACE_READ)</span>
              )}
            </div>
            <p className="text-[11px] text-zinc-400 leading-relaxed">
              AgentHub 采用不可绕过的领域限界资源验证器 (ResourceOwnershipVerifier)，严格按照 OWNER &gt; ADMIN &gt; MAINTAINER &gt; DEVELOPER &gt; VIEWER / AUDITOR 执行权限裁决与审计归因。
            </p>
          </div>
        </BentoCard>

        {/* Quick Actions Card */}
        <BentoCard
          title="团队组织管控"
          subtitle="Project Team Management"
          icon={<Users className="w-4 h-4 text-purple-400" />}
          className="md:col-span-4"
        >
          <div className="p-4 flex flex-col justify-between h-full space-y-3">
            <div className="text-xs text-zinc-400">
              当前项目成员总数: <strong className="text-zinc-200 font-mono text-sm">{members.length}</strong> 人
            </div>
            {canManage ? (
              <button
                onClick={() => setIsInviteOpen(true)}
                className="w-full flex items-center justify-center space-x-2 px-3 py-2 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-white text-xs font-medium transition shadow-md shadow-indigo-600/20"
              >
                <UserPlus className="w-4 h-4" />
                <span>邀请/指派新成员</span>
              </button>
            ) : (
              <div className="text-[11px] text-amber-400/80 bg-amber-500/10 border border-amber-500/20 rounded-lg p-2.5">
                当前角色无成员管理权限 (需要 PROJECT_MANAGE)。
              </div>
            )}
          </div>
        </BentoCard>
      </div>

      {error && (
        <div className="flex items-center space-x-2 rounded-xl border border-rose-500/30 bg-rose-950/30 p-3 text-xs text-rose-300">
          <AlertCircle className="w-4 h-4 shrink-0 text-rose-400" />
          <span>{error}</span>
        </div>
      )}

      {/* Team Members List BentoCard */}
      <BentoCard
        title="项目成员与角色列表"
        subtitle="Immutable attribution & role bindings"
        icon={<Users className="w-4 h-4 text-indigo-400" />}
        badge={
          <span className="px-2 py-0.5 rounded text-[10px] font-mono bg-zinc-800 text-zinc-400">
            {members.length} 位协作成员
          </span>
        }
      >
        <div className="overflow-x-auto">
          <table className="w-full text-left border-collapse">
            <thead>
              <tr className="border-b border-zinc-800/80 text-[11px] font-medium text-zinc-400 bg-zinc-950/40">
                <th className="py-2.5 px-4">成员主体</th>
                <th className="py-2.5 px-3">用户 ID</th>
                <th className="py-2.5 px-3">业务头衔</th>
                <th className="py-2.5 px-3">RBAC 角色</th>
                <th className="py-2.5 px-3">加入时间</th>
                <th className="py-2.5 px-4 text-right">操作</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-zinc-800/40 text-xs">
              {members.length === 0 ? (
                <tr>
                  <td colSpan={6} className="py-8 text-center text-zinc-400 text-xs">
                    暂无项目成员记录。点击右上角“邀请新成员”添加项目协作者。
                  </td>
                </tr>
              ) : (
                members.map((m) => {
                  const roleConfig = RBAC_ROLES.find((r) => r.role === m.rbacRole) || RBAC_ROLES[3];
                  const RoleIcon = roleConfig.icon;
                  const isCurrent = m.userId === currentUserRole?.userId;

                  return (
                    <tr key={m.memberId} className="hover:bg-zinc-800/30 transition-colors">
                      <td className="py-3 px-4">
                        <div className="flex items-center space-x-2.5">
                          <div className="w-7 h-7 rounded-full bg-zinc-800 border border-zinc-700/80 flex items-center justify-center text-zinc-300 font-mono text-xs font-semibold shrink-0">
                            {(m.displayName || m.userId).charAt(0).toUpperCase()}
                          </div>
                          <div>
                            <div className="font-medium text-zinc-200 flex items-center space-x-1.5">
                              <span>{m.displayName || m.userId}</span>
                              {isCurrent && (
                                <span className="px-1.5 py-0.2 rounded text-[9px] font-mono bg-indigo-500/20 text-indigo-300">
                                  YOU
                                </span>
                              )}
                            </div>
                            <div className="text-[11px] text-zinc-400">{m.email || "-"}</div>
                          </div>
                        </div>
                      </td>
                      <td className="py-3 px-3 font-mono text-[11px] text-zinc-400">
                        {m.userId}
                      </td>
                      <td className="py-3 px-3 text-zinc-300 font-medium text-xs">
                        {m.role || "Team Member"}
                      </td>
                      <td className="py-3 px-3">
                        {canManage && m.rbacRole !== "OWNER" ? (
                          <select
                            value={m.rbacRole}
                            onChange={(e) => handleUpdateRole(m.memberId, e.target.value as ProjectRbacRole)}
                            className="bg-zinc-900 border border-zinc-700 text-zinc-200 text-xs rounded-lg px-2 py-1 outline-none focus:border-indigo-500 transition"
                          >
                            {RBAC_ROLES.map((r) => (
                              <option key={r.role} value={r.role}>
                                {r.label}
                              </option>
                            ))}
                          </select>
                        ) : (
                          <div className={`inline-flex items-center space-x-1.5 px-2.5 py-1 rounded-lg border text-[11px] font-medium ${roleConfig.color}`}>
                            <RoleIcon className="w-3.5 h-3.5" />
                            <span>{roleConfig.label.split(" ")[0]}</span>
                          </div>
                        )}
                      </td>
                      <td className="py-3 px-3 font-mono text-[11px] text-zinc-400">
                        {m.createdAt ? m.createdAt.slice(0, 10) : "-"}
                      </td>
                      <td className="py-3 px-4 text-right">
                        {canManage && m.rbacRole !== "OWNER" && !isCurrent ? (
                          <button
                            onClick={() => handleRemoveMember(m.memberId)}
                            className="p-1 rounded text-zinc-400 hover:text-rose-400 hover:bg-rose-500/10 transition"
                            title="移除成员"
                          >
                            <Trash2 className="w-3.5 h-3.5" />
                          </button>
                        ) : (
                          <span className="text-[10px] text-zinc-400">受保护</span>
                        )}
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>
      </BentoCard>

      {/* Role Definitions Guide */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-3">
        {RBAC_ROLES.map((item) => {
          const Icon = item.icon;
          return (
            <div
              key={item.role}
              className="p-3.5 rounded-xl border border-zinc-800/60 bg-zinc-900/30 flex items-start space-x-3"
            >
              <div className={`p-2 rounded-lg border shrink-0 ${item.color}`}>
                <Icon className="w-4 h-4" />
              </div>
              <div className="space-y-0.5">
                <div className="text-xs font-semibold text-zinc-200">{item.label}</div>
                <div className="text-[11px] text-zinc-400 leading-snug">{item.desc}</div>
              </div>
            </div>
          );
        })}
      </div>

      {/* Add Member Modal */}
      {isInviteOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 backdrop-blur-sm p-4">
          <div className="w-full max-w-md rounded-2xl border border-zinc-800 bg-zinc-900 p-6 shadow-2xl space-y-4 animate-in fade-in zoom-in-95 duration-200">
            <div className="flex items-center justify-between border-b border-zinc-800 pb-3">
              <div className="flex items-center space-x-2 text-zinc-100 font-semibold text-sm">
                <UserPlus className="w-4 h-4 text-indigo-400" />
                <span>邀请/指派新成员</span>
              </div>
              <button
                onClick={() => setIsInviteOpen(false)}
                className="text-zinc-400 hover:text-zinc-200 text-sm font-mono"
              >
                ✕
              </button>
            </div>

            <form onSubmit={handleAddMember} className="space-y-4">
              <div className="space-y-1.5">
                <label className="text-xs font-medium text-zinc-300">
                  用户 ID (User Identifier) <span className="text-rose-400">*</span>
                </label>
                <input
                  type="text"
                  required
                  placeholder="例如: usr-dev-bob 或邮箱"
                  value={newUserId}
                  onChange={(e) => setNewUserId(e.target.value)}
                  className="w-full bg-zinc-950 border border-zinc-700 rounded-xl px-3 py-2 text-xs text-zinc-100 placeholder-zinc-400 outline-none focus:border-indigo-500 transition"
                />
              </div>

              <div className="space-y-1.5">
                <label className="text-xs font-medium text-zinc-300">
                  分配 RBAC 权限角色 <span className="text-rose-400">*</span>
                </label>
                <select
                  value={newRole}
                  onChange={(e) => setNewRole(e.target.value as ProjectRbacRole)}
                  className="w-full bg-zinc-950 border border-zinc-700 rounded-xl px-3 py-2 text-xs text-zinc-100 outline-none focus:border-indigo-500 transition"
                >
                  {RBAC_ROLES.filter((r) => r.role !== "OWNER").map((r) => (
                    <option key={r.role} value={r.role}>
                      {r.label}
                    </option>
                  ))}
                </select>
              </div>

              <div className="space-y-1.5">
                <label className="text-xs font-medium text-zinc-300">业务头衔 / 岗位职责 (可选)</label>
                <input
                  type="text"
                  placeholder="例如: Frontend Architect / QA Reviewer"
                  value={newRoleTitle}
                  onChange={(e) => setNewRoleTitle(e.target.value)}
                  className="w-full bg-zinc-950 border border-zinc-700 rounded-xl px-3 py-2 text-xs text-zinc-100 placeholder-zinc-400 outline-none focus:border-indigo-500 transition"
                />
              </div>

              <div className="flex items-center justify-end space-x-2 pt-2">
                <button
                  type="button"
                  onClick={() => setIsInviteOpen(false)}
                  className="px-4 py-2 rounded-xl text-xs text-zinc-400 hover:text-zinc-200 hover:bg-zinc-800 transition"
                >
                  取消
                </button>
                <button
                  type="submit"
                  disabled={submitting || !newUserId.trim()}
                  className="px-4 py-2 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-white text-xs font-medium transition shadow-md shadow-indigo-600/30 disabled:opacity-50"
                >
                  {submitting ? "提交中..." : "确认添加成员"}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
