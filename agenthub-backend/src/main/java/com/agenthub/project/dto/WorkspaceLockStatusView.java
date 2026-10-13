package com.agenthub.project.dto;

public class WorkspaceLockStatusView {

    private final String workspaceId;
    private final String ownerId;
    private final boolean locked;
    private final long acquiredAt;
    private final long leaseExpiresAt;

    public WorkspaceLockStatusView(String workspaceId, String ownerId, boolean locked, long acquiredAt, long leaseExpiresAt) {
        this.workspaceId = workspaceId;
        this.ownerId = ownerId;
        this.locked = locked;
        this.acquiredAt = acquiredAt;
        this.leaseExpiresAt = leaseExpiresAt;
    }

    public String getWorkspaceId() {
        return workspaceId;
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
}
