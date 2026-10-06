package com.agenthub.infrastructure.concurrency;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

@Component
public class WorkspaceLockManager {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceLockManager.class);
    private final ConcurrentHashMap<String, ReentrantLock> lockMap = new ConcurrentHashMap<>();

    public boolean tryLock(String workspacePath, long timeoutMs) {
        ReentrantLock lock = lockMap.computeIfAbsent(workspacePath, k -> new ReentrantLock());
        try {
            return lock.tryLock(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Thread interrupted while acquiring lock for workspace: {}", workspacePath);
            return false;
        }
    }

    public void unlock(String workspacePath) {
        ReentrantLock lock = lockMap.get(workspacePath);
        if (lock != null && lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }
}
