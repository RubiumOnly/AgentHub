"use client";

import React, { useState, useEffect } from "react";
import {
  Folder,
  FileText,
  RefreshCw,
  Save,
  Check,
  FileCode,
  AlertCircle,
  GitPullRequest,
  RotateCcw,
  History,
  HardDrive,
  ShieldCheck,
  Loader2,
  Bookmark,
} from "lucide-react";
import { apiClient, isDemoMode } from "@/services/api";
import { WorkspaceQuotaUsage } from "@/types";

interface FileNode {
  name: string;
  relativePath: string;
  isDirectory: boolean;
  size: number;
}

const DEMO_FILES: FileNode[] = [
  { name: "pom.xml", relativePath: "pom.xml", isDirectory: false, size: 4210 },
  { name: "application.yml", relativePath: "src/main/resources/application.yml", isDirectory: false, size: 840 },
  { name: "AuthController.java", relativePath: "src/main/java/com/agenthub/identity/AuthController.java", isDirectory: false, size: 2890 },
];

export default function WorkspaceExplorer({
  workspaceId = "ws-default",
  workspacePath,
}: {
  workspaceId?: string;
  workspacePath?: string;
}) {
  const [files, setFiles] = useState<FileNode[]>([]);
  const [selectedFile, setSelectedFile] = useState<string | null>(null);
  const [fileContent, setFileContent] = useState<string>("");
  const [isSaving, setIsSaving] = useState(false);
  const [saveSuccess, setSaveSuccess] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [infoMessage, setInfoMessage] = useState<string | null>(null);

  const [quota, setQuota] = useState<WorkspaceQuotaUsage | null>(null);
  const [isOperating, setIsOperating] = useState(false);

  const fetchFiles = async () => {
    setErrorMessage(null);
    if (isDemoMode()) {
      setFiles(DEMO_FILES);
      if (!selectedFile) {
        loadFileContent("src/main/java/com/agenthub/identity/AuthController.java");
      }
      return;
    }

    try {
      const data = await apiClient.listFiles(workspaceId);
      if (data && data.length > 0) {
        setFiles(data);
        if (!selectedFile) {
          const firstNonDir = data.find((f: FileNode) => !f.isDirectory);
          if (firstNonDir) {
            loadFileContent(firstNonDir.relativePath);
          }
        }
      } else {
        setFiles([]);
        setSelectedFile(null);
        setFileContent("");
      }
    } catch (err: any) {
      setErrorMessage(err.message || "无法加载工作区文件列表");
      setFiles([]);
    }
  };

  const fetchQuota = async () => {
    if (isDemoMode() || !workspaceId || workspaceId.startsWith("ws-default")) return;
    try {
      const q = await apiClient.getWorkspaceQuota(workspaceId);
      setQuota(q);
    } catch {
      // quota fetch optional
    }
  };

  const loadFileContent = async (relPath: string) => {
    setSelectedFile(relPath);
    setErrorMessage(null);

    if (isDemoMode()) {
      setFileContent(`package com.agenthub.identity;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    // 演示模式：沙箱受控代码预览
    @PostMapping("/login")
    public String login() {
        return "authenticated";
    }
}`);
      return;
    }

    try {
      const content = await apiClient.getFileContent(workspaceId, relPath);
      setFileContent(content || "");
    } catch (err: any) {
      setErrorMessage(`无法读取文件内容: ${err.message || "网络或权限异常"}`);
      setFileContent("");
    }
  };

  const handleSave = async () => {
    if (!selectedFile) return;
    setIsSaving(true);
    setErrorMessage(null);
    try {
      const success = await apiClient.saveFile(workspaceId, selectedFile, fileContent);
      if (success) {
        setSaveSuccess(true);
        setTimeout(() => setSaveSuccess(false), 2000);
        fetchQuota();
      }
    } catch (err: any) {
      setErrorMessage(`保存失败: ${err.message || "网络或权限异常"}`);
    } finally {
      setIsSaving(false);
    }
  };

  // 重新同步 (Resync)
  const handleResync = async () => {
    if (isDemoMode()) {
      setInfoMessage("演示模式：已模拟重新同步工作区");
      setTimeout(() => setInfoMessage(null), 3000);
      return;
    }
    setIsOperating(true);
    setErrorMessage(null);
    try {
      await apiClient.resyncWorkspace(workspaceId);
      setInfoMessage("工作区已成功重新同步至基线 Commit");
      setTimeout(() => setInfoMessage(null), 3000);
      await fetchFiles();
      await fetchQuota();
    } catch (err: any) {
      setErrorMessage(`重新同步失败: ${err.message || "网络异常"}`);
    } finally {
      setIsOperating(false);
    }
  };

  // 放弃变更 (Discard)
  const handleDiscard = async () => {
    const confirmed = window.confirm("确定要放弃工作区中所有未提交的本地修改吗？此操作不可逆。");
    if (!confirmed) return;

    if (isDemoMode()) {
      setInfoMessage("演示模式：已重置工作区变更");
      setTimeout(() => setInfoMessage(null), 3000);
      return;
    }
    setIsOperating(true);
    setErrorMessage(null);
    try {
      await apiClient.discardWorkspace(workspaceId, true, "用户通过控制台主动放弃");
      setInfoMessage("工作区变更已全部放弃并重置至基线");
      setTimeout(() => setInfoMessage(null), 3000);
      await fetchFiles();
      if (selectedFile) {
        await loadFileContent(selectedFile);
      }
      await fetchQuota();
    } catch (err: any) {
      setErrorMessage(`放弃变更失败: ${err.message || "网络异常"}`);
    } finally {
      setIsOperating(false);
    }
  };

  // 恢复快照 (Restore Snapshot)
  const handleRestoreSnapshot = async () => {
    const commitOrTag = window.prompt("请输入要恢复的目标 Git 快照 Commit Hash 或 Tag：");
    if (!commitOrTag || !commitOrTag.trim()) return;

    if (isDemoMode()) {
      setInfoMessage(`演示模式：已恢复快照 [${commitOrTag.trim()}]`);
      setTimeout(() => setInfoMessage(null), 3000);
      return;
    }
    setIsOperating(true);
    setErrorMessage(null);
    try {
      await apiClient.restoreSnapshot(workspaceId, commitOrTag.trim(), true);
      setInfoMessage(`已成功将工作区恢复至快照 [${commitOrTag.trim()}]`);
      setTimeout(() => setInfoMessage(null), 3000);
      await fetchFiles();
      if (selectedFile) {
        await loadFileContent(selectedFile);
      }
      await fetchQuota();
    } catch (err: any) {
      setErrorMessage(`恢复快照失败: ${err.message || "网络异常"}`);
    } finally {
      setIsOperating(false);
    }
  };

  // 变更基线 (Change Baseline)
  const handleChangeBaseline = async () => {
    const targetCommit = window.prompt("请输入新的 Git 基线 Commit Hash：");
    if (!targetCommit || !targetCommit.trim()) return;

    if (isDemoMode()) {
      setInfoMessage(`演示模式：已变更基线至 [${targetCommit.trim()}]`);
      setTimeout(() => setInfoMessage(null), 3000);
      return;
    }
    setIsOperating(true);
    setErrorMessage(null);
    try {
      await apiClient.changeBaseline(workspaceId, targetCommit.trim(), true);
      setInfoMessage(`基线 Commit 已变更为 [${targetCommit.trim()}]`);
      setTimeout(() => setInfoMessage(null), 3000);
      await fetchFiles();
      await fetchQuota();
    } catch (err: any) {
      setErrorMessage(`变更基线失败: ${err.message || "网络异常"}`);
    } finally {
      setIsOperating(false);
    }
  };

  useEffect(() => {
    fetchFiles();
    fetchQuota();
  }, [workspaceId]);

  return (
    <div className="flex h-full rounded-2xl border border-zinc-800/80 bg-zinc-950/70 backdrop-blur-md overflow-hidden text-xs flex-col">
      {/* 顶部生命周期与配额工具栏 */}
      <div className="h-11 border-b border-zinc-800/80 bg-zinc-950/90 px-3 flex items-center justify-between shrink-0 font-mono text-[11px]">
        {/* 左侧：工作区标识与配额状态 */}
        <div className="flex items-center space-x-3">
          <div className="flex items-center space-x-1.5 text-zinc-300 font-semibold">
            <Folder className="w-3.5 h-3.5 text-indigo-400" />
            <span className="truncate">{workspaceId}</span>
          </div>

          {quota && (
            <div className="hidden md:flex items-center space-x-2 text-[10px] text-zinc-400 bg-zinc-900/80 px-2 py-0.5 rounded-lg border border-zinc-800">
              <span className="flex items-center space-x-1">
                <HardDrive className="w-3 h-3 text-zinc-500" />
                <span>
                  {Math.round(quota.currentCapacityBytes / 1024)}KB / {Math.round(quota.maxCapacityBytes / (1024 * 1024))}MB
                </span>
              </span>
              <span>·</span>
              <span>
                {quota.currentFilesCount} / {quota.maxFilesCount} 文件
              </span>
              <span>·</span>
              <span className={quota.allowNetworkAccess ? "text-emerald-400" : "text-amber-400"}>
                {quota.allowNetworkAccess ? "网络开放" : "网络离线受控"}
              </span>
            </div>
          )}
        </div>

        {/* 右侧：受控工作区生命周期动作 */}
        <div className="flex items-center space-x-1.5">
          <button
            onClick={handleResync}
            disabled={isOperating}
            title="将工作区重新同步至当前基线"
            className="flex items-center space-x-1 px-2.5 py-1 rounded-lg text-zinc-300 hover:text-zinc-100 hover:bg-zinc-800/80 transition disabled:opacity-50"
          >
            {isOperating ? <Loader2 className="w-3 h-3 animate-spin" /> : <GitPullRequest className="w-3 h-3 text-indigo-400" />}
            <span className="hidden sm:inline">重新同步</span>
          </button>

          <button
            onClick={handleRestoreSnapshot}
            disabled={isOperating}
            title="恢复指定历史快照 Commit 或 Tag"
            className="flex items-center space-x-1 px-2.5 py-1 rounded-lg text-zinc-300 hover:text-zinc-100 hover:bg-zinc-800/80 transition disabled:opacity-50"
          >
            <History className="w-3 h-3 text-amber-400" />
            <span className="hidden sm:inline">恢复快照</span>
          </button>

          <button
            onClick={handleChangeBaseline}
            disabled={isOperating}
            title="推进或变更受控工作区初始基线"
            className="flex items-center space-x-1 px-2.5 py-1 rounded-lg text-zinc-300 hover:text-zinc-100 hover:bg-zinc-800/80 transition disabled:opacity-50"
          >
            <Bookmark className="w-3 h-3 text-emerald-400" />
            <span className="hidden sm:inline">基线变更</span>
          </button>

          <button
            onClick={handleDiscard}
            disabled={isOperating}
            title="丢弃当前工作区所有未提交本地修改"
            className="flex items-center space-x-1 px-2.5 py-1 rounded-lg text-rose-300 hover:text-rose-200 hover:bg-rose-950/40 border border-rose-900/30 transition disabled:opacity-50"
          >
            <RotateCcw className="w-3 h-3 text-rose-400" />
            <span className="hidden sm:inline">放弃修改</span>
          </button>

          <button
            onClick={() => {
              fetchFiles();
              fetchQuota();
            }}
            title="刷新文件树与配额"
            className="p-1 rounded hover:bg-zinc-800 text-zinc-400 hover:text-zinc-200 transition"
          >
            <RefreshCw className="w-3.5 h-3.5" />
          </button>
        </div>
      </div>

      {/* 提示 Banner */}
      {infoMessage && (
        <div className="px-3 py-1.5 bg-emerald-950/40 border-b border-emerald-500/20 text-emerald-300 text-[11px] flex items-center space-x-1.5 shrink-0">
          <ShieldCheck className="w-3.5 h-3.5 text-emerald-400 shrink-0" />
          <span>{infoMessage}</span>
        </div>
      )}

      {errorMessage && (
        <div className="px-3 py-1.5 bg-rose-950/40 border-b border-rose-500/20 text-rose-300 text-[11px] flex items-center space-x-1.5 shrink-0 font-mono">
          <AlertCircle className="w-3.5 h-3.5 text-rose-400 shrink-0" />
          <span>{errorMessage}</span>
        </div>
      )}

      {/* 主体部分：文件树与编辑器 */}
      <div className="flex flex-1 overflow-hidden">
        {/* 左侧文件列表 */}
        <div className="w-60 border-r border-zinc-800/80 bg-zinc-950/60 flex flex-col shrink-0">
          <div className="flex-1 overflow-y-auto p-2 space-y-1">
            {files.length === 0 ? (
              <div className="text-center p-4 text-zinc-500 text-[11px]">
                工作区暂无文件
              </div>
            ) : (
              files.map((file) => (
                <button
                  key={file.relativePath}
                  onClick={() => !file.isDirectory && loadFileContent(file.relativePath)}
                  className={`w-full flex items-center space-x-2 px-2.5 py-1.5 rounded-lg text-left transition font-mono text-[11px] ${
                    selectedFile === file.relativePath
                      ? "bg-indigo-600/20 text-indigo-300 border border-indigo-500/30"
                      : "text-zinc-400 hover:bg-zinc-900/60 hover:text-zinc-200"
                  }`}
                >
                  {file.isDirectory ? (
                    <Folder className="w-3.5 h-3.5 text-amber-400/80 shrink-0" />
                  ) : (
                    <FileCode className="w-3.5 h-3.5 text-zinc-400 shrink-0" />
                  )}
                  <span className="truncate">{file.name}</span>
                </button>
              ))
            )}
          </div>
        </div>

        {/* 右侧文件内容编辑器 */}
        <div className="flex-1 flex flex-col bg-zinc-950/40 overflow-hidden">
          <div className="h-9 border-b border-zinc-800/80 px-4 flex items-center justify-between shrink-0 bg-zinc-950/60">
            <div className="flex items-center space-x-2 text-zinc-400 font-mono text-[11px] truncate">
              <FileText className="w-3.5 h-3.5 text-indigo-400" />
              <span>{selectedFile || "未选择文件"}</span>
            </div>

            {selectedFile && (
              <button
                onClick={handleSave}
                disabled={isSaving}
                className={`flex items-center space-x-1 px-3 py-1 rounded-lg text-xs font-medium transition ${
                  saveSuccess
                    ? "bg-emerald-600/20 text-emerald-300 border border-emerald-500/30"
                    : "bg-indigo-600 hover:bg-indigo-500 text-white shadow-sm"
                }`}
              >
                {saveSuccess ? (
                  <>
                    <Check className="w-3 h-3" />
                    <span>已保存</span>
                  </>
                ) : (
                  <>
                    <Save className="w-3 h-3" />
                    <span>{isSaving ? "保存中..." : "保存变更"}</span>
                  </>
                )}
              </button>
            )}
          </div>

          <div className="flex-1 p-3 overflow-hidden">
            <textarea
              value={fileContent}
              onChange={(e) => setFileContent(e.target.value)}
              disabled={!selectedFile}
              placeholder={selectedFile ? "文件内容为空" : "请从左侧选择一个文件进行查看或编辑..."}
              className="w-full h-full bg-transparent resize-none font-mono text-xs text-zinc-200 focus:outline-none leading-relaxed"
              spellCheck={false}
            />
          </div>
        </div>
      </div>
    </div>
  );
}
