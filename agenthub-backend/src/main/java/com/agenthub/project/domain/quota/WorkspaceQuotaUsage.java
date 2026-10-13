package com.agenthub.project.domain.quota;

/**
 * Current Workspace Quota Usage View.
 */
public class WorkspaceQuotaUsage {

    private String workspaceId;
    private long currentCapacityBytes;
    private long maxCapacityBytes;
    private int currentFilesCount;
    private int maxFilesCount;
    private long maxSingleFileSizeBytes;
    private long executionDurationSeconds;
    private long maxExecutionDurationSeconds;
    private boolean networkAccessAllowed;
    private int concurrentLocks;
    private int maxConcurrentLocks;

    public WorkspaceQuotaUsage() {}

    public WorkspaceQuotaUsage(String workspaceId, long currentCapacityBytes, long maxCapacityBytes,
                               int currentFilesCount, int maxFilesCount, long maxSingleFileSizeBytes,
                               long executionDurationSeconds, long maxExecutionDurationSeconds,
                               boolean networkAccessAllowed, int concurrentLocks, int maxConcurrentLocks) {
        this.workspaceId = workspaceId;
        this.currentCapacityBytes = currentCapacityBytes;
        this.maxCapacityBytes = maxCapacityBytes;
        this.currentFilesCount = currentFilesCount;
        this.maxFilesCount = maxFilesCount;
        this.maxSingleFileSizeBytes = maxSingleFileSizeBytes;
        this.executionDurationSeconds = executionDurationSeconds;
        this.maxExecutionDurationSeconds = maxExecutionDurationSeconds;
        this.networkAccessAllowed = networkAccessAllowed;
        this.concurrentLocks = concurrentLocks;
        this.maxConcurrentLocks = maxConcurrentLocks;
    }

    public String getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(String workspaceId) { this.workspaceId = workspaceId; }

    public long getCurrentCapacityBytes() { return currentCapacityBytes; }
    public void setCurrentCapacityBytes(long currentCapacityBytes) { this.currentCapacityBytes = currentCapacityBytes; }

    public long getMaxCapacityBytes() { return maxCapacityBytes; }
    public void setMaxCapacityBytes(long maxCapacityBytes) { this.maxCapacityBytes = maxCapacityBytes; }

    public int getCurrentFilesCount() { return currentFilesCount; }
    public void setCurrentFilesCount(int currentFilesCount) { this.currentFilesCount = currentFilesCount; }

    public int getMaxFilesCount() { return maxFilesCount; }
    public void setMaxFilesCount(int maxFilesCount) { this.maxFilesCount = maxFilesCount; }

    public long getMaxSingleFileSizeBytes() { return maxSingleFileSizeBytes; }
    public void setMaxSingleFileSizeBytes(long maxSingleFileSizeBytes) { this.maxSingleFileSizeBytes = maxSingleFileSizeBytes; }

    public long getExecutionDurationSeconds() { return executionDurationSeconds; }
    public void setExecutionDurationSeconds(long executionDurationSeconds) { this.executionDurationSeconds = executionDurationSeconds; }

    public long getMaxExecutionDurationSeconds() { return maxExecutionDurationSeconds; }
    public void setMaxExecutionDurationSeconds(long maxExecutionDurationSeconds) { this.maxExecutionDurationSeconds = maxExecutionDurationSeconds; }

    public boolean isNetworkAccessAllowed() { return networkAccessAllowed; }
    public void setNetworkAccessAllowed(boolean networkAccessAllowed) { this.networkAccessAllowed = networkAccessAllowed; }

    public int getConcurrentLocks() { return concurrentLocks; }
    public void setConcurrentLocks(int concurrentLocks) { this.concurrentLocks = concurrentLocks; }

    public int getMaxConcurrentLocks() { return maxConcurrentLocks; }
    public void setMaxConcurrentLocks(int maxConcurrentLocks) { this.maxConcurrentLocks = maxConcurrentLocks; }
}
