package com.agenthub.infrastructure.concurrency;

import com.agenthub.domain.workspace.service.WorkspaceResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Timestamp;
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

    @Autowired(required = false)
    private WorkspaceResolver workspaceResolver;

    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    public WorkspaceLockManager() {}

    @Autowired
    public WorkspaceLockManager(@Autowired(required = false) WorkspaceResolver workspaceResolver,
                                @Autowired(required = false) JdbcTemplate jdbcTemplate) {
        this.workspaceResolver = workspaceResolver;
        this.jdbcTemplate = jdbcTemplate;
    }

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
        if (workspaceResolver != null) {
            try {
                Path root = workspaceResolver.getWorkspaceRoot(workspacePath);
                return root.toAbsolutePath().normalize().toString()
                        .replace('\\', '/')
                        .toLowerCase(Locale.ROOT);
            } catch (Exception ignored) {}
        }
        try {
            return Path.of(workspacePath).toAbsolutePath().normalize().toString()
                    .replace('\\', '/')
                    .toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            return workspacePath.replace('\\', '/').toLowerCase(Locale.ROOT);
        }
    }

    private String getDbKey(String key) {
        if (key == null) return "";
        if (key.length() <= 128) return key;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(key.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder("sha256:");
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return key.substring(key.length() - 128);
        }
    }

    private boolean syncAcquireDbLock(String key, String ownerId, long leaseTtlMs) {
        if (jdbcTemplate == null) return true;
        String dbKey = getDbKey(key);
        long now = System.currentTimeMillis();
        Timestamp nowTs = new Timestamp(now);
        Timestamp expiresTs = new Timestamp(now + leaseTtlMs);

        try {
            var rows = jdbcTemplate.query(
                    "SELECT owner_id, expires_at FROM workspace_locks WHERE workspace_key = ?",
                    (rs, rowNum) -> new Object[]{rs.getString("owner_id"), rs.getTimestamp("expires_at")},
                    dbKey
            );

            if (rows.isEmpty()) {
                jdbcTemplate.update(
                        "INSERT INTO workspace_locks (workspace_key, owner_id, acquired_at, expires_at, lease_ttl_ms) VALUES (?, ?, ?, ?, ?)",
                        dbKey, ownerId, nowTs, expiresTs, leaseTtlMs
                );
                return true;
            } else {
                Object[] row = rows.get(0);
                String currentOwner = (String) row[0];
                Timestamp currentExpires = (Timestamp) row[1];

                if (ownerId.equals(currentOwner)) {
                    jdbcTemplate.update(
                            "UPDATE workspace_locks SET expires_at = ?, lease_ttl_ms = ? WHERE workspace_key = ? AND owner_id = ?",
                            expiresTs, leaseTtlMs, dbKey, ownerId
                    );
                    return true;
                } else if (currentExpires != null && currentExpires.getTime() < now) {
                    int updated = jdbcTemplate.update(
                            "UPDATE workspace_locks SET owner_id = ?, acquired_at = ?, expires_at = ?, lease_ttl_ms = ? WHERE workspace_key = ? AND expires_at < ?",
                            ownerId, nowTs, expiresTs, leaseTtlMs, dbKey, nowTs
                    );
                    return updated > 0;
                } else {
                    return false;
                }
            }
        } catch (Exception e) {
            log.debug("Database workspace lock sync non-fatal error: {}", e.getMessage());
            return true;
        }
    }

    private boolean syncRenewDbLock(String key, String ownerId, long additionalTtlMs) {
        if (jdbcTemplate == null) return true;
        String dbKey = getDbKey(key);
        long now = System.currentTimeMillis();
        Timestamp newExpiresTs = new Timestamp(now + additionalTtlMs);
        try {
            int updated = jdbcTemplate.update(
                    "UPDATE workspace_locks SET expires_at = ?, lease_ttl_ms = ? WHERE workspace_key = ? AND owner_id = ? AND expires_at >= ?",
                    newExpiresTs, additionalTtlMs, dbKey, ownerId, new Timestamp(now)
            );
            return updated > 0;
        } catch (Exception e) {
            log.debug("Database workspace lock renew non-fatal error: {}", e.getMessage());
            return true;
        }
    }

    private void syncReleaseDbLock(String key, String ownerId) {
        if (jdbcTemplate == null) return;
        String dbKey = getDbKey(key);
        try {
            jdbcTemplate.update("DELETE FROM workspace_locks WHERE workspace_key = ? AND owner_id = ?", dbKey, ownerId);
        } catch (Exception e) {
            log.debug("Database workspace lock release non-fatal error: {}", e.getMessage());
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
                    if (!syncAcquireDbLock(key, ownerId, leaseTtlMs)) {
                        long remainingWait = deadline - now;
                        if (remainingWait <= 0) return false;
                        long waitTime = Math.min(remainingWait, 50);
                        try {
                            entry.condition.await(waitTime, TimeUnit.MILLISECONDS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return false;
                        }
                        continue;
                    }
                    entry.ownerId = ownerId;
                    entry.acquiredAt = now;
                    entry.leaseTtlMs = leaseTtlMs;
                    entry.expiresAt = now + leaseTtlMs;
                    entry.holdCount = 1;
                    return true;
                }

                // 2. Lock is reentrant for the same owner
                if (ownerId.equals(entry.ownerId)) {
                    syncAcquireDbLock(key, ownerId, leaseTtlMs);
                    entry.holdCount++;
                    entry.expiresAt = Math.max(entry.expiresAt, now + leaseTtlMs);
                    return true;
                }

                // 3. Held by another owner, but lease has expired (Deadlock recovery)
                if (now > entry.expiresAt) {
                    if (!syncAcquireDbLock(key, ownerId, leaseTtlMs)) {
                        long remainingWait = deadline - now;
                        if (remainingWait <= 0) return false;
                        long waitTime = Math.min(remainingWait, 50);
                        try {
                            entry.condition.await(waitTime, TimeUnit.MILLISECONDS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return false;
                        }
                        continue;
                    }
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
                syncRenewDbLock(key, ownerId, additionalTtlMs);
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
                syncReleaseDbLock(key, ownerId);
                entry.ownerId = null;
                entry.expiresAt = 0;
                entry.holdCount = 0;
                entry.condition.signalAll();
                if (!entry.internalLock.hasWaiters(entry.condition)) {
                    lockMap.remove(key, entry);
                }
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
