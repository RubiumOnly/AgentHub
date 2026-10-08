package com.agenthub.infrastructure.concurrency;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

@Component
public class WorkspaceLockManager {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceLockManager.class);
    private final ConcurrentHashMap<String, ReentrantLock> lockMap = new ConcurrentHashMap<>();

    private String normalizeKey(String workspacePath) {
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

    public boolean tryLock(String workspacePath, long timeoutMs) {
        String key = normalizeKey(workspacePath);
        ReentrantLock lock = lockMap.computeIfAbsent(key, k -> new ReentrantLock());
        try {
            return lock.tryLock(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Thread interrupted while acquiring lock for workspace: {}", workspacePath);
            return false;
        }
    }

    public void unlock(String workspacePath) {
        String key = normalizeKey(workspacePath);
        ReentrantLock lock = lockMap.get(key);
        if (lock != null && lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }
}
