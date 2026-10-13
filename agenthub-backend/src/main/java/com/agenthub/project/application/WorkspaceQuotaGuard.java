package com.agenthub.project.application;

import com.agenthub.project.domain.quota.WorkspaceQuotaPolicy;
import com.agenthub.project.domain.quota.WorkspaceQuotaUsage;
import com.agenthub.shared.exception.BusinessException;
import com.agenthub.shared.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

/**
 * Workspace Resource Quota & Security Policy Guard.
 * Enforces capacity, file count, single-file size, and network policies.
 * Never silently truncates; throws BLOCKED_POLICY with clear explanatory diagnostics.
 */
@Component
public class WorkspaceQuotaGuard {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceQuotaGuard.class);

    public void validateFileWrite(Path wsRoot, Path targetFile, byte[] contentBytes, WorkspaceQuotaPolicy policy) {
        if (policy == null) {
            policy = WorkspaceQuotaPolicy.defaultPolicy();
        }

        long writeSize = contentBytes != null ? contentBytes.length : 0L;

        // 1. Single file size quota
        if (writeSize > policy.getMaxSingleFileSizeBytes()) {
            log.warn("BLOCKED_POLICY: Single file size [{}] bytes exceeds quota [{}] bytes for target [{}]",
                    writeSize, policy.getMaxSingleFileSizeBytes(), targetFile);
            throw new BusinessException(ErrorCode.BLOCKED_POLICY,
                    String.format("File write blocked by policy: content size (%d bytes) exceeds single-file quota (%d bytes)",
                            writeSize, policy.getMaxSingleFileSizeBytes()));
        }

        if (!Files.exists(wsRoot)) {
            return;
        }

        // 2. Scan current workspace capacity and file count (ignoring .git)
        AtomicInteger fileCount = new AtomicInteger(0);
        AtomicLong totalBytes = new AtomicLong(0L);

        try (Stream<Path> stream = Files.walk(wsRoot)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> !isGitInternal(wsRoot, p))
                    .forEach(p -> {
                        fileCount.incrementAndGet();
                        try {
                            totalBytes.addAndGet(Files.size(p));
                        } catch (IOException ignored) {}
                    });
        } catch (IOException e) {
            log.warn("Failed to walk workspace directory for quota verification: {}", e.getMessage());
        }

        boolean fileAlreadyExists = Files.exists(targetFile);
        long existingSize = 0L;
        if (fileAlreadyExists) {
            try {
                existingSize = Files.size(targetFile);
            } catch (IOException ignored) {}
        } else {
            // New file addition check
            if (fileCount.get() + 1 > policy.getMaxFilesCount()) {
                log.warn("BLOCKED_POLICY: Workspace file count [{}] exceeds quota [{}]",
                        fileCount.get() + 1, policy.getMaxFilesCount());
                throw new BusinessException(ErrorCode.BLOCKED_POLICY,
                        String.format("File creation blocked by policy: workspace file count (%d) exceeds max file quota (%d)",
                                fileCount.get() + 1, policy.getMaxFilesCount()));
            }
        }

        long projectedTotalBytes = totalBytes.get() - existingSize + writeSize;
        if (projectedTotalBytes > policy.getMaxCapacityBytes()) {
            log.warn("BLOCKED_POLICY: Projected workspace capacity [{}] bytes exceeds quota [{}] bytes",
                    projectedTotalBytes, policy.getMaxCapacityBytes());
            throw new BusinessException(ErrorCode.BLOCKED_POLICY,
                    String.format("File write blocked by policy: projected workspace capacity (%d bytes) exceeds max quota (%d bytes)",
                            projectedTotalBytes, policy.getMaxCapacityBytes()));
        }
    }

    public void validateNetworkAccess(boolean requiresNetwork, WorkspaceQuotaPolicy policy) {
        if (policy == null) {
            policy = WorkspaceQuotaPolicy.defaultPolicy();
        }
        if (requiresNetwork && !policy.isAllowNetworkAccess()) {
            log.warn("BLOCKED_POLICY: Outbound network access requested but denied by workspace security policy");
            throw new BusinessException(ErrorCode.BLOCKED_POLICY,
                    "Outbound network access is strictly prohibited by workspace security policy");
        }
    }

    public WorkspaceQuotaUsage computeUsage(String workspaceId, Path wsRoot, WorkspaceQuotaPolicy policy, int concurrentLocks) {
        if (policy == null) {
            policy = WorkspaceQuotaPolicy.defaultPolicy();
        }

        AtomicInteger fileCount = new AtomicInteger(0);
        AtomicLong totalBytes = new AtomicLong(0L);

        if (Files.exists(wsRoot)) {
            try (Stream<Path> stream = Files.walk(wsRoot)) {
                stream.filter(Files::isRegularFile)
                        .filter(p -> !isGitInternal(wsRoot, p))
                        .forEach(p -> {
                            fileCount.incrementAndGet();
                            try {
                                totalBytes.addAndGet(Files.size(p));
                            } catch (IOException ignored) {}
                        });
            } catch (IOException e) {
                log.warn("Failed to walk workspace directory: {}", e.getMessage());
            }
        }

        return new WorkspaceQuotaUsage(
                workspaceId,
                totalBytes.get(),
                policy.getMaxCapacityBytes(),
                fileCount.get(),
                policy.getMaxFilesCount(),
                policy.getMaxSingleFileSizeBytes(),
                0L,
                policy.getMaxExecutionDurationSeconds(),
                policy.isAllowNetworkAccess(),
                concurrentLocks,
                policy.getMaxConcurrentLocks()
        );
    }

    private boolean isGitInternal(Path root, Path file) {
        try {
            Path rel = root.relativize(file);
            for (Path segment : rel) {
                if (segment.toString().equalsIgnoreCase(".git")) {
                    return true;
                }
            }
        } catch (Exception ignored) {}
        return false;
    }
}
