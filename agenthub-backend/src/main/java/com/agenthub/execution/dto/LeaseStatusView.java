package com.agenthub.execution.dto;

import java.time.LocalDateTime;

public class LeaseStatusView {
    private String leaseOwner;
    private LocalDateTime leaseUntil;
    private LocalDateTime heartbeatAt;
    private boolean isExpired;

    public LeaseStatusView() {}

    public LeaseStatusView(String leaseOwner, LocalDateTime leaseUntil, LocalDateTime heartbeatAt, boolean isExpired) {
        this.leaseOwner = leaseOwner;
        this.leaseUntil = leaseUntil;
        this.heartbeatAt = heartbeatAt;
        this.isExpired = isExpired;
    }

    public String getLeaseOwner() { return leaseOwner; }
    public void setLeaseOwner(String leaseOwner) { this.leaseOwner = leaseOwner; }

    public LocalDateTime getLeaseUntil() { return leaseUntil; }
    public void setLeaseUntil(LocalDateTime leaseUntil) { this.leaseUntil = leaseUntil; }

    public LocalDateTime getHeartbeatAt() { return heartbeatAt; }
    public void setHeartbeatAt(LocalDateTime heartbeatAt) { this.heartbeatAt = heartbeatAt; }

    public boolean isExpired() { return isExpired; }
    public void setExpired(boolean expired) { isExpired = expired; }
}
