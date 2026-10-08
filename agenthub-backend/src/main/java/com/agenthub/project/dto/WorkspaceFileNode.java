package com.agenthub.project.dto;

public class WorkspaceFileNode {
    private String name;
    private String relativePath;
    private boolean isDirectory;
    private long size;
    private boolean binary;
    private long lastModified;

    public WorkspaceFileNode() {}

    public WorkspaceFileNode(String name, String relativePath, boolean isDirectory, long size) {
        this.name = name;
        this.relativePath = relativePath;
        this.isDirectory = isDirectory;
        this.size = size;
        this.binary = false;
        this.lastModified = System.currentTimeMillis();
    }

    public WorkspaceFileNode(String name, String relativePath, boolean isDirectory, long size, boolean binary, long lastModified) {
        this.name = name;
        this.relativePath = relativePath;
        this.isDirectory = isDirectory;
        this.size = size;
        this.binary = binary;
        this.lastModified = lastModified;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getRelativePath() { return relativePath; }
    public void setRelativePath(String relativePath) { this.relativePath = relativePath; }
    public boolean isDirectory() { return isDirectory; }
    public void setDirectory(boolean directory) { isDirectory = directory; }
    public long getSize() { return size; }
    public void setSize(long size) { this.size = size; }
    public boolean isBinary() { return binary; }
    public void setBinary(boolean binary) { this.binary = binary; }
    public long getLastModified() { return lastModified; }
    public void setLastModified(long lastModified) { this.lastModified = lastModified; }
}
