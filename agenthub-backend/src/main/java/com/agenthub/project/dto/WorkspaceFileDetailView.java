package com.agenthub.project.dto;

public class WorkspaceFileDetailView {
    private String name;
    private String relativePath;
    private String content;
    private boolean binary;
    private long size;
    private boolean truncated;
    private long lastModified;

    public WorkspaceFileDetailView() {}

    public WorkspaceFileDetailView(String name, String relativePath, String content, boolean binary,
                                   long size, boolean truncated, long lastModified) {
        this.name = name;
        this.relativePath = relativePath;
        this.content = content;
        this.binary = binary;
        this.size = size;
        this.truncated = truncated;
        this.lastModified = lastModified;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getRelativePath() { return relativePath; }
    public void setRelativePath(String relativePath) { this.relativePath = relativePath; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public boolean isBinary() { return binary; }
    public void setBinary(boolean binary) { this.binary = binary; }
    public long getSize() { return size; }
    public void setSize(long size) { this.size = size; }
    public boolean isTruncated() { return truncated; }
    public void setTruncated(boolean truncated) { this.truncated = truncated; }
    public long getLastModified() { return lastModified; }
    public void setLastModified(long lastModified) { this.lastModified = lastModified; }
}
