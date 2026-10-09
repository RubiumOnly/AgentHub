"use client";

import React, { useState, useEffect } from "react";
import { Folder, FileText, RefreshCw, Save, Check, FileCode, AlertCircle } from "lucide-react";
import { apiClient, isDemoMode } from "@/services/api";

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
      }
    } catch (err: any) {
      setErrorMessage(`保存失败: ${err.message || "网络或权限异常"}`);
    } finally {
      setIsSaving(false);
    }
  };

  useEffect(() => {
    fetchFiles();
  }, [workspaceId]);

  return (
    <div className="flex h-full rounded-2xl border border-zinc-800/80 bg-zinc-950/70 backdrop-blur-md overflow-hidden text-xs">
      {/* 左侧文件列表 */}
      <div className="w-60 border-r border-zinc-800/80 bg-zinc-950/60 flex flex-col shrink-0">
        <div className="p-3 border-b border-zinc-800/80 flex items-center justify-between font-semibold text-zinc-300">
          <div className="flex items-center space-x-1.5">
            <Folder className="w-4 h-4 text-indigo-400" />
            <span className="truncate">工作区: {workspaceId}</span>
          </div>
          <button
            onClick={fetchFiles}
            title="刷新文件树"
            className="p-1 rounded hover:bg-zinc-800 text-zinc-400 hover:text-zinc-200 transition"
          >
            <RefreshCw className="w-3.5 h-3.5" />
          </button>
        </div>

        <div className="flex-1 overflow-y-auto p-2 space-y-1">
          {files.length === 0 ? (
            <div className="text-center p-4 text-zinc-500 text-[11px]">
              {errorMessage ? (
                <span className="text-rose-400">{errorMessage}</span>
              ) : (
                "工作区暂无文件"
              )}
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
      <div className="flex-1 flex flex-col bg-zinc-950/40">
        <div className="h-10 border-b border-zinc-800/80 px-4 flex items-center justify-between shrink-0 bg-zinc-950/60">
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

        {errorMessage && (
          <div className="px-4 py-2 bg-rose-950/30 border-b border-rose-500/20 text-rose-300 text-[11px] flex items-center space-x-2">
            <AlertCircle className="w-3.5 h-3.5 text-rose-400 shrink-0" />
            <span>{errorMessage}</span>
          </div>
        )}

        <div className="flex-1 p-3">
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
  );
}
