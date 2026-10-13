package com.agenthub.project.domain.quota;

/**
 * Workspace Resource Quota & Execution Policy.
 * Any violation throws BLOCKED_POLICY instead of silent truncation.
 */
public class WorkspaceQuotaPolicy {

    public static final long DEFAULT_MAX_CAPACITY_BYTES = 500 * 1024 * 1024L; // 500 MB
    public static final int DEFAULT_MAX_FILES_COUNT = 5000;
    public static final long DEFAULT_MAX_SINGLE_FILE_SIZE_BYTES = 10 * 1024 * 1024L; // 10 MB
    public static final long DEFAULT_MAX_EXECUTION_DURATION_SECONDS = 3600L; // 1 Hour
    public static final boolean DEFAULT_ALLOW_NETWORK_ACCESS = false;
    public static final int DEFAULT_MAX_CONCURRENT_LOCKS = 1;

    private long maxCapacityBytes = DEFAULT_MAX_CAPACITY_BYTES;
    private int maxFilesCount = DEFAULT_MAX_FILES_COUNT;
    private long maxSingleFileSizeBytes = DEFAULT_MAX_SINGLE_FILE_SIZE_BYTES;
    private long maxExecutionDurationSeconds = DEFAULT_MAX_EXECUTION_DURATION_SECONDS;
    private boolean allowNetworkAccess = DEFAULT_ALLOW_NETWORK_ACCESS;
    private int maxConcurrentLocks = DEFAULT_MAX_CONCURRENT_LOCKS;

    public WorkspaceQuotaPolicy() {}

    public WorkspaceQuotaPolicy(long maxCapacityBytes, int maxFilesCount, long maxSingleFileSizeBytes,
                                long maxExecutionDurationSeconds, boolean allowNetworkAccess, int maxConcurrentLocks) {
        this.maxCapacityBytes = maxCapacityBytes > 0 ? maxCapacityBytes : DEFAULT_MAX_CAPACITY_BYTES;
        this.maxFilesCount = maxFilesCount > 0 ? maxFilesCount : DEFAULT_MAX_FILES_COUNT;
        this.maxSingleFileSizeBytes = maxSingleFileSizeBytes > 0 ? maxSingleFileSizeBytes : DEFAULT_MAX_SINGLE_FILE_SIZE_BYTES;
        this.maxExecutionDurationSeconds = maxExecutionDurationSeconds > 0 ? maxExecutionDurationSeconds : DEFAULT_MAX_EXECUTION_DURATION_SECONDS;
        this.allowNetworkAccess = allowNetworkAccess;
        this.maxConcurrentLocks = maxConcurrentLocks > 0 ? maxConcurrentLocks : DEFAULT_MAX_CONCURRENT_LOCKS;
    }

    public static WorkspaceQuotaPolicy defaultPolicy() {
        return new WorkspaceQuotaPolicy();
    }

    public long getMaxCapacityBytes() { return maxCapacityBytes; }
    public void setMaxCapacityBytes(long maxCapacityBytes) { this.maxCapacityBytes = maxCapacityBytes; }

    public int getMaxFilesCount() { return maxFilesCount; }
    public void setMaxFilesCount(int maxFilesCount) { this.maxFilesCount = maxFilesCount; }

    public long getMaxSingleFileSizeBytes() { return maxSingleFileSizeBytes; }
    public void setMaxSingleFileSizeBytes(long maxSingleFileSizeBytes) { this.maxSingleFileSizeBytes = maxSingleFileSizeBytes; }

    public long getMaxExecutionDurationSeconds() { return maxExecutionDurationSeconds; }
    public void setMaxExecutionDurationSeconds(long maxExecutionDurationSeconds) { this.maxExecutionDurationSeconds = maxExecutionDurationSeconds; }

    public boolean isAllowNetworkAccess() { return allowNetworkAccess; }
    public void setAllowNetworkAccess(boolean allowNetworkAccess) { this.allowNetworkAccess = allowNetworkAccess; }

    public int getMaxConcurrentLocks() { return maxConcurrentLocks; }
    public void setMaxConcurrentLocks(int maxConcurrentLocks) { this.maxConcurrentLocks = maxConcurrentLocks; }
}
