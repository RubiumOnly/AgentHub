package com.agenthub.infrastructure.concurrency;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

@Component
public class WorkspaceLockManager {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceLockManager.class);
    private static final long DEFAULT_LEASE_TTL_MS = 60_000L;

    public static class LockEntry {
        private final ReentrantLock internalLock = new ReentrantLock();
        private final Condition condition = internalLock.newCondition();
        private String ownerId;
        private long acquiredAt;
        private long leaseTtlMs;
        private long expiresAt;
        private int holdCount;

        public String getOwnerId() { return ownerId; }
        public long getAcquiredAt() { return acquiredAt; }
        public long getLeaseTtlMs() { return leaseTtlMs; }
        public long getExpiresAt() { return expiresAt; }
        public int getHoldCount() { return holdCount; }
    }

    public static class LockInfo {
        private final String workspaceKey;
        private final String ownerId;
        private final boolean locked;
        private final long remainingTtlMs;
        private final long acquiredAt;

        public LockInfo(String workspaceKey, String ownerId, boolean locked, long remainingTtlMs, long acquiredAt) {
            this.workspaceKey = workspaceKey;
            this.ownerId = ownerId;
            this.locked = locked;
            this.remainingTtlMs = remainingTtlMs;
            this.acquiredAt = acquiredAt;
        }

        public String getWorkspaceKey() { return workspaceKey; }
        public String getOwnerId() { return ownerId; }
        public boolean isLocked() { return locked; }
        public long getRemainingTtlMs() { return remainingTtlMs; }
        public long getAcquiredAt() { return acquiredAt; }
    }

    private final ConcurrentHashMap<String, LockEntry> lockMap = new ConcurrentHashMap<>();

    public String normalizeKey(String workspacePath) {
        if (workspacePath == null || workspacePath.isBlank()) {
            return "";
        }
        try {
            return Path.of(workspacePath).toAbsolutePath().normalize().toString()
                    .replace('\\', '/')
                    .toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            return workspacePath.replace('\\', '/').toLowerCase(Locale.ROOT);
        }
    }

    public boolean tryAcquireLock(String workspacePath, String ownerId, long waitTimeoutMs, long leaseTtlMs) {
        if (ownerId == null || ownerId.isBlank()) {
            ownerId = "thread-" + Thread.currentThread().getId();
        }
        if (leaseTtlMs <= 0) {
            leaseTtlMs = DEFAULT_LEASE_TTL_MS;
        }

        String key = normalizeKey(workspacePath);
        LockEntry entry = lockMap.computeIfAbsent(key, k -> new LockEntry());
        long deadline = System.currentTimeMillis() + waitTimeoutMs;

        try {
            if (!entry.internalLock.tryLock(waitTimeoutMs, TimeUnit.MILLISECONDS)) {
                return false;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while acquiring internal lock for workspace: {}", key);
            return false;
        }

        try {
            while (true) {
                long now = System.currentTimeMillis();

                // 1. Lock is unowned
                if (entry.ownerId == null) {
                    entry.ownerId = ownerId;
                    entry.acquiredAt = now;
                    entry.leaseTtlMs = leaseTtlMs;
                    entry.expiresAt = now + leaseTtlMs;
                    entry.holdCount = 1;
                    return true;
                }

                // 2. Lock is reentrant for the same owner
                if (ownerId.equals(entry.ownerId)) {
                    entry.holdCount++;
                    entry.expiresAt = Math.max(entry.expiresAt, now + leaseTtlMs);
                    return true;
                }

                // 3. Held by another owner, but lease has expired (Deadlock recovery)
                if (now > entry.expiresAt) {
                    log.warn("Lock for workspace [{}] expired (held by {}), auto-recovering for new owner [{}]",
                            key, entry.ownerId, ownerId);
                    entry.ownerId = ownerId;
                    entry.acquiredAt = now;
                    entry.leaseTtlMs = leaseTtlMs;
                    entry.expiresAt = now + leaseTtlMs;
                    entry.holdCount = 1;
                    return true;
                }

                // 4. Held by another owner and not yet expired
                long remainingWait = deadline - now;
                if (remainingWait <= 0) {
                    return false;
                }

                long waitTime = Math.min(remainingWait, Math.max(1, entry.expiresAt - now));
                try {
                    entry.condition.await(waitTime, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        } finally {
            entry.internalLock.unlock();
        }
    }

    public boolean renewLease(String workspacePath, String ownerId, long additionalTtlMs) {
        if (ownerId == null || ownerId.isBlank()) {
            ownerId = "thread-" + Thread.currentThread().getId();
        }
        String key = normalizeKey(workspacePath);
        LockEntry entry = lockMap.get(key);
        if (entry == null) return false;

        entry.internalLock.lock();
        try {
            long now = System.currentTimeMillis();
            if (ownerId.equals(entry.ownerId) && now <= entry.expiresAt) {
                entry.leaseTtlMs = additionalTtlMs;
                entry.expiresAt = now + additionalTtlMs;
                return true;
            }
            return false;
        } finally {
            entry.internalLock.unlock();
        }
    }

    public boolean releaseLock(String workspacePath, String ownerId) {
        if (ownerId == null || ownerId.isBlank()) {
            ownerId = "thread-" + Thread.currentThread().getId();
        }
        String key = normalizeKey(workspacePath);
        LockEntry entry = lockMap.get(key);
        if (entry == null) return false;

        entry.internalLock.lock();
        try {
            if (!ownerId.equals(entry.ownerId)) {
                log.warn("Unauthorized attempt to release lock for [{}] by [{}]. Held by [{}]",
                        key, ownerId, entry.ownerId);
                return false;
            }

            entry.holdCount--;
            if (entry.holdCount <= 0) {
                entry.ownerId = null;
                entry.expiresAt = 0;
                entry.holdCount = 0;
                entry.condition.signalAll();
            }
            return true;
        } finally {
            entry.internalLock.unlock();
        }
    }

    public Optional<LockInfo> getLockInfo(String workspacePath) {
        String key = normalizeKey(workspacePath);
        LockEntry entry = lockMap.get(key);
        if (entry == null) {
            return Optional.empty();
        }
        entry.internalLock.lock();
        try {
            long now = System.currentTimeMillis();
            boolean isLocked = entry.ownerId != null && now <= entry.expiresAt;
            long remaining = isLocked ? (entry.expiresAt - now) : 0;
            return Optional.of(new LockInfo(key, entry.ownerId, isLocked, remaining, entry.acquiredAt));
        } finally {
            entry.internalLock.unlock();
        }
    }

    public boolean isLocked(String workspacePath) {
        return getLockInfo(workspacePath).map(LockInfo::isLocked).orElse(false);
    }

    // Backward compatible API
    public boolean tryLock(String workspacePath, long timeoutMs) {
        return tryAcquireLock(workspacePath, "thread-" + Thread.currentThread().getId(), timeoutMs, DEFAULT_LEASE_TTL_MS);
    }

    public void unlock(String workspacePath) {
        releaseLock(workspacePath, "thread-" + Thread.currentThread().getId());
    }
}
