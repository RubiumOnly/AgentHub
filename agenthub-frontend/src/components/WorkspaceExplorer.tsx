"use client";

import React, { useState, useEffect } from "react";
import { Folder, FileText, RefreshCw, Save, Check, FileCode } from "lucide-react";

interface FileNode {
  name: string;
  relativePath: string;
  isDirectory: boolean;
  size: number;
}

export default function WorkspaceExplorer({ workspacePath }: { workspacePath: string }) {
  const [files, setFiles] = useState<FileNode[]>([]);
  const [selectedFile, setSelectedFile] = useState<string | null>(null);
  const [fileContent, setFileContent] = useState<string>("");
  const [isSaving, setIsSaving] = useState(false);
  const [saveSuccess, setSaveSuccess] = useState(false);

  const fetchFiles = async () => {
    try {
      const res = await fetch(`http://localhost:8080/api/workspace/files?path=${encodeURIComponent(workspacePath)}`);
      const data = await res.json();
      if (data.data) {
        setFiles(data.data);
        if (data.data.length > 0 && !selectedFile) {
          const firstNonDir = data.data.find((f: FileNode) => !f.isDirectory);
          if (firstNonDir) {
            loadFileContent(firstNonDir.relativePath);
          }
        }
      }
    } catch {
      // Fallback workspace file seeds
      setFiles([
        { name: "pom.xml", relativePath: "pom.xml", isDirectory: false, size: 4210 },
        { name: "application.yml", relativePath: "src/main/resources/application.yml", isDirectory: false, size: 840 },
        { name: "AuthController.java", relativePath: "src/main/java/com/agenthub/identity/AuthController.java", isDirectory: false, size: 2890 },
      ]);
      if (!selectedFile) {
        loadFileContent("src/main/java/com/agenthub/identity/AuthController.java");
      }
    }
  };

  const loadFileContent = async (relPath: string) => {
    setSelectedFile(relPath);
    try {
      const fullPath = workspacePath.replace(/\\/g, "/") + "/" + relPath;
      const res = await fetch(`http://localhost:8080/api/workspace/file/content?filePath=${encodeURIComponent(fullPath)}`);
      const data = await res.json();
      setFileContent(data.data || "");
    } catch {
      setFileContent(`package com.agenthub.identity;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    // Generated safe code in sandboxed workspace
    @PostMapping("/login")
    public String login() {
        return "authenticated";
    }
}`);
    }
  };

  const handleSave = async () => {
    if (!selectedFile) return;
    setIsSaving(true);
    try {
      const fullPath = workspacePath.replace(/\\/g, "/") + "/" + selectedFile;
      await fetch("http://localhost:8080/api/workspace/file/save", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ filePath: fullPath, content: fileContent }),
      });
      setSaveSuccess(true);
      setTimeout(() => setSaveSuccess(false), 2000);
    } catch (err) {
      console.error("Save failed:", err);
    } finally {
      setIsSaving(false);
    }
  };

  useEffect(() => {
    fetchFiles();
  }, [workspacePath]);

  return (
    <div className="flex h-full rounded-2xl border border-zinc-800/80 bg-zinc-950/70 backdrop-blur-md overflow-hidden text-xs">
      {/* 左侧文件列表 */}
      <div className="w-60 border-r border-zinc-800/80 bg-zinc-950/60 flex flex-col shrink-0">
        <div className="p-3 border-b border-zinc-800/60 flex items-center justify-between">
          <span className="font-semibold text-zinc-300 flex items-center space-x-1.5">
            <Folder className="w-3.5 h-3.5 text-amber-400" />
            <span>受控工作区文件树</span>
          </span>
          <button
            onClick={fetchFiles}
            title="刷新文件"
            className="text-zinc-400 hover:text-zinc-200 transition p-1 rounded-md hover:bg-zinc-800"
          >
            <RefreshCw className="w-3.5 h-3.5" />
          </button>
        </div>

        <div className="flex-1 p-2 space-y-1 overflow-y-auto">
          {files.length === 0 ? (
            <div className="text-[11px] text-zinc-500 p-2 font-mono">工作区尚无文件</div>
          ) : (
            files.map((file) => (
              <button
                key={file.relativePath}
                onClick={() => !file.isDirectory && loadFileContent(file.relativePath)}
                className={`w-full flex items-center space-x-2 px-2.5 py-1.5 rounded-xl text-left transition ${
                  selectedFile === file.relativePath
                    ? "bg-indigo-600/20 text-indigo-300 border border-indigo-500/30 font-medium"
                    : "text-zinc-400 hover:bg-zinc-900 hover:text-zinc-200"
                }`}
              >
                {file.isDirectory ? (
                  <Folder className="w-3.5 h-3.5 text-amber-400 shrink-0" />
                ) : (
                  <FileCode className="w-3.5 h-3.5 text-emerald-400 shrink-0" />
                )}
                <span className="truncate font-mono text-[11px]">{file.name}</span>
              </button>
            ))
          )}
        </div>
      </div>

      {/* 右侧文件内容编辑/预览区 */}
      <div className="flex-1 flex flex-col bg-zinc-950/90 overflow-hidden">
        <div className="h-10 border-b border-zinc-800/80 px-4 flex items-center justify-between bg-zinc-900/60">
          <span className="font-mono text-zinc-300 text-[11px] truncate">
            {selectedFile || "未选择文件"}
          </span>
          {selectedFile && (
            <button
              onClick={handleSave}
              disabled={isSaving}
              className="flex items-center space-x-1 px-3 py-1 bg-indigo-600 hover:bg-indigo-500 text-white rounded-lg text-[11px] font-medium transition shadow-sm"
            >
              {saveSuccess ? (
                <>
                  <Check className="w-3 h-3 text-emerald-300" />
                  <span>已保存</span>
                </>
              ) : (
                <>
                  <Save className="w-3 h-3" />
                  <span>保存代码</span>
                </>
              )}
            </button>
          )}
        </div>

        <div className="flex-1 p-3">
          <textarea
            value={fileContent}
            onChange={(e) => setFileContent(e.target.value)}
            className="w-full h-full bg-zinc-950 font-mono text-[11px] text-zinc-200 resize-none focus:outline-none border-0 leading-relaxed p-1"
            placeholder="请在左侧选择文件浏览或编辑..."
          />
        </div>
      </div>
    </div>
  );
}
