package com.agenthub.project.dto;

public class WorkspaceLockStatusView {

    private final String workspaceId;
    private final String workspaceKey;
    private final String ownerId;
    private final boolean locked;
    private final long acquiredAt;
    private final long leaseExpiresAt;
    private final long remainingTtlMs;

    public WorkspaceLockStatusView(String workspaceId, String ownerId, boolean locked, long acquiredAt, long leaseExpiresAt) {
        this(workspaceId, workspaceId, ownerId, locked, acquiredAt, leaseExpiresAt,
                locked && leaseExpiresAt > 0 ? Math.max(0L, leaseExpiresAt - System.currentTimeMillis()) : 0L);
    }

    public WorkspaceLockStatusView(String workspaceId,
                                   String workspaceKey,
                                   String ownerId,
                                   boolean locked,
                                   long acquiredAt,
                                   long leaseExpiresAt,
                                   long remainingTtlMs) {
        this.workspaceId = workspaceId;
        this.workspaceKey = workspaceKey;
        this.ownerId = ownerId;
        this.locked = locked;
        this.acquiredAt = acquiredAt;
        this.leaseExpiresAt = leaseExpiresAt;
        this.remainingTtlMs = remainingTtlMs;
    }

    public String getWorkspaceId() {
        return workspaceId;
    }

    public String getWorkspaceKey() {
        return workspaceKey;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public boolean isLocked() {
        return locked;
    }

    public long getAcquiredAt() {
        return acquiredAt;
    }

    public long getLeaseExpiresAt() {
        return leaseExpiresAt;
    }

    public long getRemainingTtlMs() {
        return remainingTtlMs;
    }
}
