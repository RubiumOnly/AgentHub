"use client";

import React, { useState } from "react";
import { apiClient } from "@/services/api";
import { ProjectView } from "@/types";
import {
  FolderPlus,
  X,
  Loader2,
  FolderGit2,
  FileText,
  AlertCircle,
  Sparkles,
} from "lucide-react";

interface CreateProjectModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSuccess: (project: ProjectView) => void;
}

export function CreateProjectModal({ isOpen, onClose, onSuccess }: CreateProjectModalProps) {
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [isLoading, setIsLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  if (!isOpen) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!name.trim()) {
      setErrorMessage("请填写项目名称");
      return;
    }
    setIsLoading(true);
    setErrorMessage(null);
    try {
      const project = await apiClient.createProject({
        name: name.trim(),
        description: description.trim() || undefined,
      });
      setName("");
      setDescription("");
      onSuccess(project);
      onClose();
    } catch (err: any) {
      setErrorMessage(err.message || "创建项目失败，请检查网络或后端服务");
    } finally {
      setIsLoading(false);
    }
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
            <FolderPlus className="w-5 h-5" />
          </div>
          <div>
            <h3 className="text-base font-bold text-zinc-100 tracking-tight">
              新建研发项目 (New Project)
            </h3>
            <p className="text-xs text-zinc-400 font-mono mt-0.5">
              自动分配独立受控工作区与 JGit 基线保护
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

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-xs font-medium text-zinc-300 mb-1.5">
              项目名称 (Project Name) <span className="text-rose-400">*</span>
            </label>
            <div className="relative">
              <div className="pointer-events-none absolute inset-y-0 left-0 pl-3 flex items-center text-zinc-500">
                <FolderGit2 className="w-4 h-4" />
              </div>
              <input
                type="text"
                required
                value={name}
                onChange={(e) => setName(e.target.value)}
                placeholder="例如: agenthub-auth-service"
                className="w-full pl-9 pr-3 py-2 rounded-xl border border-zinc-800 bg-zinc-900/90 text-sm text-zinc-100 placeholder-zinc-500 focus:outline-none focus:border-indigo-500 focus:ring-1 focus:ring-indigo-500 transition-colors font-mono"
              />
            </div>
          </div>

          <div>
            <label className="block text-xs font-medium text-zinc-300 mb-1.5">
              项目描述 (Description)
            </label>
            <div className="relative">
              <div className="pointer-events-none absolute top-2.5 left-3 text-zinc-500">
                <FileText className="w-4 h-4" />
              </div>
              <textarea
                rows={3}
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                placeholder="简述项目目标、技术栈或交付范围..."
                className="w-full pl-9 pr-3 py-2 rounded-xl border border-zinc-800 bg-zinc-900/90 text-sm text-zinc-100 placeholder-zinc-500 focus:outline-none focus:border-indigo-500 focus:ring-1 focus:ring-indigo-500 transition-colors resize-none"
              />
            </div>
          </div>

          <div className="pt-2 flex items-center justify-end space-x-2">
            <button
              type="button"
              onClick={onClose}
              className="px-3.5 py-2 rounded-xl text-xs font-medium text-zinc-400 hover:text-zinc-200 hover:bg-zinc-800/60 transition-colors"
            >
              取消
            </button>
            <button
              type="submit"
              disabled={isLoading}
              className="flex items-center space-x-1.5 px-4 py-2 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-white text-xs font-medium shadow-md shadow-indigo-600/30 transition disabled:opacity-50"
            >
              {isLoading ? (
                <>
                  <Loader2 className="w-3.5 h-3.5 animate-spin" />
                  <span>创建中...</span>
                </>
              ) : (
                <>
                  <Sparkles className="w-3.5 h-3.5" />
                  <span>立即创建</span>
                </>
              )}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
