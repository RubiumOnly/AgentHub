"use client";

import React, { useState } from "react";
import { apiClient } from "@/services/api";
import { UserView } from "@/types";
import {
  Lock,
  Mail,
  User,
  ShieldCheck,
  X,
  Loader2,
  Sparkles,
  ArrowRight,
  AlertCircle,
} from "lucide-react";

interface AuthModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSuccess: (user: UserView) => void;
}

export function AuthModal({ isOpen, onClose, onSuccess }: AuthModalProps) {
  const [isRegister, setIsRegister] = useState(false);
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [isLoading, setIsLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  if (!isOpen) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!email || !password) {
      setErrorMessage("请填写邮箱和密码");
      return;
    }
    setIsLoading(true);
    setErrorMessage(null);
    try {
      if (isRegister) {
        const res = await apiClient.register(email, password, displayName);
        onSuccess(res.user);
      } else {
        const res = await apiClient.login(email, password);
        onSuccess(res.user);
      }
      onClose();
    } catch (err: any) {
      setErrorMessage(err.message || "认证请求失败，请检查凭据或网络");
    } finally {
      setIsLoading(false);
    }
  };

  const handleQuickFillAdmin = () => {
    setEmail("admin@agenthub.local");
    setPassword("admin123");
    setIsRegister(false);
    setErrorMessage(null);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70 backdrop-blur-md animate-in fade-in duration-200">
      <div className="relative w-full max-w-md rounded-2xl border border-zinc-800 bg-zinc-950/95 p-6 shadow-2xl ring-1 ring-zinc-700/50 backdrop-blur-xl">
        {/* Glow accent */}
        <div className="pointer-events-none absolute inset-x-0 -top-px h-1 bg-gradient-to-r from-transparent via-indigo-500/60 to-transparent" />

        {/* Close button */}
        <button
          onClick={onClose}
          className="absolute top-4 right-4 p-1.5 rounded-lg text-zinc-400 hover:text-zinc-200 hover:bg-zinc-800/80 transition-colors"
        >
          <X className="w-4 h-4" />
        </button>

        {/* Header */}
        <div className="flex items-center space-x-3 mb-5">
          <div className="w-10 h-10 rounded-xl bg-indigo-600/20 border border-indigo-500/30 flex items-center justify-center text-indigo-400 shadow-inner">
            <ShieldCheck className="w-5 h-5" />
          </div>
          <div>
            <h3 className="text-base font-bold text-zinc-100 tracking-tight">
              {isRegister ? "注册 AgentHub 账户" : "登录 AgentHub 平台"}
            </h3>
            <p className="text-xs text-zinc-400 font-mono mt-0.5">
              可信身份闭环与多租户资源边界保护
            </p>
          </div>
        </div>

        {/* Error message */}
        {errorMessage && (
          <div className="mb-4 flex items-center space-x-2 rounded-xl border border-rose-500/40 bg-rose-950/30 p-2.5 text-xs text-rose-300">
            <AlertCircle className="w-4 h-4 shrink-0 text-rose-400" />
            <span className="flex-1">{errorMessage}</span>
          </div>
        )}

        {/* Form */}
        <form onSubmit={handleSubmit} className="space-y-3.5">
          <div>
            <label className="block text-xs font-medium text-zinc-300 mb-1">
              工作邮箱 (Email)
            </label>
            <div className="relative">
              <Mail className="absolute left-3 top-2.5 w-4 h-4 text-zinc-500" />
              <input
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="developer@agenthub.local"
                required
                className="w-full rounded-xl border border-zinc-800 bg-zinc-900/90 pl-9 pr-3 py-2 text-xs text-zinc-100 placeholder:text-zinc-600 focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500/40 font-mono"
              />
            </div>
          </div>

          <div>
            <label className="block text-xs font-medium text-zinc-300 mb-1">
              访问密码 (Password)
            </label>
            <div className="relative">
              <Lock className="absolute left-3 top-2.5 w-4 h-4 text-zinc-500" />
              <input
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="••••••••"
                required
                className="w-full rounded-xl border border-zinc-800 bg-zinc-900/90 pl-9 pr-3 py-2 text-xs text-zinc-100 placeholder:text-zinc-600 focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500/40 font-mono"
              />
            </div>
          </div>

          {isRegister && (
            <div>
              <label className="block text-xs font-medium text-zinc-300 mb-1">
                显示名称 (Display Name)
              </label>
              <div className="relative">
                <User className="absolute left-3 top-2.5 w-4 h-4 text-zinc-500" />
                <input
                  type="text"
                  value={displayName}
                  onChange={(e) => setDisplayName(e.target.value)}
                  placeholder="Lead Developer"
                  className="w-full rounded-xl border border-zinc-800 bg-zinc-900/90 pl-9 pr-3 py-2 text-xs text-zinc-100 placeholder:text-zinc-600 focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500/40"
                />
              </div>
            </div>
          )}

          {/* Submit button */}
          <button
            type="submit"
            disabled={isLoading}
            className="w-full mt-2 flex items-center justify-center space-x-2 rounded-xl bg-indigo-600 hover:bg-indigo-500 py-2.5 text-xs font-semibold text-white shadow-lg shadow-indigo-600/25 transition-all disabled:opacity-50"
          >
            {isLoading ? (
              <Loader2 className="w-4 h-4 animate-spin" />
            ) : (
              <>
                <span>{isRegister ? "完成注册并进入控制台" : "安全登录并加载项目"}</span>
                <ArrowRight className="w-3.5 h-3.5" />
              </>
            )}
          </button>
        </form>

        {/* Footer shortcuts & switch mode */}
        <div className="mt-5 pt-4 border-t border-zinc-800/80 flex flex-col space-y-2 text-xs">
          <button
            type="button"
            onClick={handleQuickFillAdmin}
            className="flex items-center justify-center space-x-1.5 py-1.5 rounded-lg border border-zinc-800 hover:border-zinc-700 bg-zinc-900/60 text-zinc-400 hover:text-zinc-200 transition-colors text-[11px] font-mono"
          >
            <Sparkles className="w-3 h-3 text-amber-400" />
            <span>一键填入默认系统管理员 (admin@agenthub.local)</span>
          </button>

          <div className="flex items-center justify-between text-zinc-400 text-xs pt-1">
            <span>{isRegister ? "已有账号？" : "没有账号？"}</span>
            <button
              type="button"
              onClick={() => {
                setIsRegister(!isRegister);
                setErrorMessage(null);
              }}
              className="text-indigo-400 hover:text-indigo-300 font-medium transition-colors"
            >
              {isRegister ? "直接登录" : "免费注册新用户"}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
