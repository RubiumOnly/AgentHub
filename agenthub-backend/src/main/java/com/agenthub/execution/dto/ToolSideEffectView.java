package com.agenthub.execution.dto;

import java.util.List;
import java.util.Map;

public class ToolSideEffectView {
    private String toolName;
    private List<String> targetFiles;
    private String command;
    private String status;
    private Map<String, String> checksums;

    public ToolSideEffectView() {}

    public ToolSideEffectView(String toolName, List<String> targetFiles, String command, String status, Map<String, String> checksums) {
        this.toolName = toolName;
        this.targetFiles = targetFiles;
        this.command = command;
        this.status = status;
        this.checksums = checksums;
    }

    public String getToolName() { return toolName; }
    public void setToolName(String toolName) { this.toolName = toolName; }

    public List<String> getTargetFiles() { return targetFiles; }
    public void setTargetFiles(List<String> targetFiles) { this.targetFiles = targetFiles; }

    public String getCommand() { return command; }
    public void setCommand(String command) { this.command = command; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Map<String, String> getChecksums() { return checksums; }
    public void setChecksums(Map<String, String> checksums) { this.checksums = checksums; }
}
