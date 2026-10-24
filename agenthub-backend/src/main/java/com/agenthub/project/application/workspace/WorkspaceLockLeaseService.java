package com.agenthub.project.application.workspace;

import com.agenthub.infrastructure.concurrency.WorkspaceLockManager;
import com.agenthub.project.dto.WorkspaceLockStatusView;
import com.agenthub.shared.context.RequestContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 工作区锁租约缝（Phase 10 R8 自 WorkspaceApplicationService 抽出）：
 * 获取/续租/释放/状态查询与锁属主解析的唯一实现；属主优先取认证主体。
 */
@Component
public class WorkspaceLockLeaseService {

    private final WorkspaceLockManager lockManager;

    public WorkspaceLockLeaseService(@Autowired(required = false) WorkspaceLockManager lockManager) {
        this.lockManager = lockManager;
    }

    public boolean acquireLock(String workspaceId, String requestedOwnerId, long waitTimeoutMs, long leaseTtlMs) {
        if (lockManager == null) {
            return false;
        }
        String lockOwner = resolveLockOwner(requestedOwnerId);
        long waitTimeout = waitTimeoutMs > 0 ? waitTimeoutMs : 3000;
        long leaseTtl = leaseTtlMs > 0 ? leaseTtlMs : 60000;
        return lockManager.tryAcquireLock(workspaceId, lockOwner, waitTimeout, leaseTtl);
    }

    public boolean renewLock(String workspaceId, String requestedOwnerId, long additionalTtlMs) {
        if (lockManager == null) {
            return false;
        }
        String lockOwner = resolveLockOwner(requestedOwnerId);
        long additionalTtl = additionalTtlMs > 0 ? additionalTtlMs : 60000;
        return lockManager.renewLease(workspaceId, lockOwner, additionalTtl);
    }

    public boolean releaseLock(String workspaceId, String requestedOwnerId) {
        if (lockManager == null) {
            return false;
        }
        String lockOwner = resolveLockOwner(requestedOwnerId);
        return lockManager.releaseLock(workspaceId, lockOwner);
    }

    public WorkspaceLockStatusView getLockStatus(String workspaceId) {
        if (lockManager == null) {
            return new WorkspaceLockStatusView(workspaceId, workspaceId, null, false, 0, 0, 0);
        }
        return lockManager.getLockInfo(workspaceId)
                .map(info -> new WorkspaceLockStatusView(
                        workspaceId,
                        info.getWorkspaceKey(),
                        info.getOwnerId(),
                        info.isLocked(),
                        info.getAcquiredAt(),
                        info.getExpiresAt(),
                        info.getRemainingTtlMs()
                ))
                .orElse(new WorkspaceLockStatusView(workspaceId, workspaceId, null, false, 0, 0, 0));
    }

    public int concurrentLockCount(String workspaceId) {
        if (lockManager == null) {
            return 0;
        }
        return lockManager.getLockInfo(workspaceId).map(info -> info.isLocked() ? 1 : 0).orElse(0);
    }

    private String resolveLockOwner(String requestedOwnerId) {
        String currentUserId = RequestContext.get().getUserId();
        if (currentUserId != null && !currentUserId.isBlank()) {
            return currentUserId;
        }
        return (requestedOwnerId != null && !requestedOwnerId.isBlank()) ? requestedOwnerId : "anonymous";
    }
}
