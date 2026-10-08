package com.agenthub.domain.workspace.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class StructuredDiff implements Serializable {
    private static final long serialVersionUID = 1L;

    private String workspaceId;
    private String baselineCommit;
    private String currentCommit;
    private int totalFilesChanged;
    private int totalLinesAdded;
    private int totalLinesDeleted;
    private List<FileDiffEntry> entries = new ArrayList<>();

    public StructuredDiff() {}

    public StructuredDiff(String workspaceId, String baselineCommit, String currentCommit,
                          int totalFilesChanged, int totalLinesAdded, int totalLinesDeleted,
                          List<FileDiffEntry> entries) {
        this.workspaceId = workspaceId;
        this.baselineCommit = baselineCommit;
        this.currentCommit = currentCommit;
        this.totalFilesChanged = totalFilesChanged;
        this.totalLinesAdded = totalLinesAdded;
        this.totalLinesDeleted = totalLinesDeleted;
        this.entries = entries != null ? entries : new ArrayList<>();
    }

    public String getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(String workspaceId) { this.workspaceId = workspaceId; }
    public String getBaselineCommit() { return baselineCommit; }
    public void setBaselineCommit(String baselineCommit) { this.baselineCommit = baselineCommit; }
    public String getCurrentCommit() { return currentCommit; }
    public void setCurrentCommit(String currentCommit) { this.currentCommit = currentCommit; }
    public int getTotalFilesChanged() { return totalFilesChanged; }
    public void setTotalFilesChanged(int totalFilesChanged) { this.totalFilesChanged = totalFilesChanged; }
    public int getTotalLinesAdded() { return totalLinesAdded; }
    public void setTotalLinesAdded(int totalLinesAdded) { this.totalLinesAdded = totalLinesAdded; }
    public int getTotalLinesDeleted() { return totalLinesDeleted; }
    public void setTotalLinesDeleted(int totalLinesDeleted) { this.totalLinesDeleted = totalLinesDeleted; }
    public List<FileDiffEntry> getEntries() { return entries; }
    public void setEntries(List<FileDiffEntry> entries) { this.entries = entries != null ? entries : new ArrayList<>(); }
}
