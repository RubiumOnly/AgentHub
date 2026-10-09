package com.agenthub.sandbox.domain.model;

import java.io.Serializable;

/**
 * Resource quotas and watchdog timeout constraints for sandbox execution.
 */
public class SandboxResourceQuota implements Serializable {
    private static final long serialVersionUID = 1L;

    public static final long DEFAULT_TIMEOUT_MS = 30_000L;
    public static final int DEFAULT_MAX_OUTPUT_BYTES = 1024 * 1024; // 1 MB
    public static final int DEFAULT_MAX_MEMORY_MB = 512;
    public static final String DEFAULT_MAX_CPU = "1.0";
    public static final int DEFAULT_MAX_DISK_MB = 500;

    private long maxTimeoutMs;
    private int maxOutputBytes;
    private int maxMemoryMb;
    private String maxCpu;
    private int maxDiskMb;

    public SandboxResourceQuota() {
        this.maxTimeoutMs = DEFAULT_TIMEOUT_MS;
        this.maxOutputBytes = DEFAULT_MAX_OUTPUT_BYTES;
        this.maxMemoryMb = DEFAULT_MAX_MEMORY_MB;
        this.maxCpu = DEFAULT_MAX_CPU;
        this.maxDiskMb = DEFAULT_MAX_DISK_MB;
    }

    public SandboxResourceQuota(long maxTimeoutMs, int maxOutputBytes, int maxMemoryMb, String maxCpu, int maxDiskMb) {
        this.maxTimeoutMs = maxTimeoutMs > 0 ? maxTimeoutMs : DEFAULT_TIMEOUT_MS;
        this.maxOutputBytes = maxOutputBytes > 0 ? maxOutputBytes : DEFAULT_MAX_OUTPUT_BYTES;
        this.maxMemoryMb = maxMemoryMb > 0 ? maxMemoryMb : DEFAULT_MAX_MEMORY_MB;
        this.maxCpu = (maxCpu != null && !maxCpu.isBlank()) ? maxCpu : DEFAULT_MAX_CPU;
        this.maxDiskMb = maxDiskMb > 0 ? maxDiskMb : DEFAULT_MAX_DISK_MB;
    }

    public static SandboxResourceQuota defaults() {
        return new SandboxResourceQuota();
    }

    public static SandboxResourceQuota of(long timeoutMs, int maxOutputBytes) {
        return new SandboxResourceQuota(timeoutMs, maxOutputBytes, DEFAULT_MAX_MEMORY_MB, DEFAULT_MAX_CPU, DEFAULT_MAX_DISK_MB);
    }

    public long getMaxTimeoutMs() { return maxTimeoutMs; }
    public void setMaxTimeoutMs(long maxTimeoutMs) { this.maxTimeoutMs = maxTimeoutMs; }

    public int getMaxOutputBytes() { return maxOutputBytes; }
    public void setMaxOutputBytes(int maxOutputBytes) { this.maxOutputBytes = maxOutputBytes; }

    public int getMaxMemoryMb() { return maxMemoryMb; }
    public void setMaxMemoryMb(int maxMemoryMb) { this.maxMemoryMb = maxMemoryMb; }

    public String getMaxCpu() { return maxCpu; }
    public void setMaxCpu(String maxCpu) { this.maxCpu = maxCpu; }

    public int getMaxDiskMb() { return maxDiskMb; }
    public void setMaxDiskMb(int maxDiskMb) { this.maxDiskMb = maxDiskMb; }
}
