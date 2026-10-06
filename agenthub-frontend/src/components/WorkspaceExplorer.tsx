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
    } catch (err) {
      console.warn("Could not fetch workspace files:", err);
    }
  };

  const loadFileContent = async (relPath: string) => {
    setSelectedFile(relPath);
    try {
      const fullPath = workspacePath.replace(/\\/g, "/") + "/" + relPath;
      const res = await fetch(`http://localhost:8080/api/workspace/file/content?filePath=${encodeURIComponent(fullPath)}`);
      const data = await res.json();
      setFileContent(data.data || "");
    } catch (err) {
      setFileContent("// 暂无法加载文件内容");
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
    <div className="flex h-full rounded-2xl border border-slate-800 bg-[#070b14] overflow-hidden text-xs">
      {/* 左侧文件列表 */}
      <div className="w-56 border-r border-slate-800 bg-slate-950/60 flex flex-col">
        <div className="p-3 border-b border-slate-800 flex items-center justify-between">
          <span className="font-semibold text-slate-300 flex items-center space-x-1.5">
            <Folder className="w-3.5 h-3.5 text-amber-400" />
            <span>工作区文件树</span>
          </span>
          <button onClick={fetchFiles} className="text-slate-400 hover:text-slate-200 transition">
            <RefreshCw className="w-3.5 h-3.5" />
          </button>
        </div>

        <div className="flex-1 p-2 space-y-1 overflow-y-auto">
          {files.length === 0 ? (
            <div className="text-[11px] text-slate-500 p-2">工作区尚无文件</div>
          ) : (
            files.map((file) => (
              <button
                key={file.relativePath}
                onClick={() => !file.isDirectory && loadFileContent(file.relativePath)}
                className={`w-full flex items-center space-x-2 px-2.5 py-1.5 rounded-lg text-left transition ${
                  selectedFile === file.relativePath
                    ? "bg-indigo-600/20 text-indigo-300 border border-indigo-500/30 font-medium"
                    : "text-slate-400 hover:bg-slate-900"
                }`}
              >
                {file.isDirectory ? (
                  <Folder className="w-3.5 h-3.5 text-amber-400 shrink-0" />
                ) : (
                  <FileCode className="w-3.5 h-3.5 text-emerald-400 shrink-0" />
                )}
                <span className="truncate">{file.name}</span>
              </button>
            ))
          )}
        </div>
      </div>

      {/* 右侧文件内容编辑/预览区 */}
      <div className="flex-1 flex flex-col bg-slate-950">
        <div className="h-10 border-b border-slate-800 px-4 flex items-center justify-between bg-slate-900/40">
          <span className="font-mono text-slate-300 text-[11px] truncate">
            {selectedFile || "未选择文件"}
          </span>
          {selectedFile && (
            <button
              onClick={handleSave}
              disabled={isSaving}
              className="flex items-center space-x-1 px-3 py-1 bg-indigo-600 hover:bg-indigo-500 text-white rounded-md text-[11px] transition shadow-sm"
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
            className="w-full h-full bg-slate-950 font-mono text-[11px] text-slate-200 resize-none focus:outline-none border-0 leading-relaxed"
            placeholder="请在左侧选择文件浏览或编辑..."
          />
        </div>
      </div>
    </div>
  );
}
