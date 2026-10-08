package com.agenthub.project.dto;

public class WorkspaceFileNode {
    private String name;
    private String relativePath;
    private boolean isDirectory;
    private long size;

    public WorkspaceFileNode() {}

    public WorkspaceFileNode(String name, String relativePath, boolean isDirectory, long size) {
        this.name = name;
        this.relativePath = relativePath;
        this.isDirectory = isDirectory;
        this.size = size;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getRelativePath() { return relativePath; }
    public void setRelativePath(String relativePath) { this.relativePath = relativePath; }
    public boolean isDirectory() { return isDirectory; }
    public void setDirectory(boolean directory) { isDirectory = directory; }
    public long getSize() { return size; }
    public void setSize(long size) { this.size = size; }
}
