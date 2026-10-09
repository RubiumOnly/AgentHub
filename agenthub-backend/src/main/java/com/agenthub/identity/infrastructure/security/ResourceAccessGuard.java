package com.agenthub.identity.infrastructure.security;

import com.agenthub.shared.context.RequestContext;
import com.agenthub.shared.exception.BusinessException;
import com.agenthub.shared.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ResourceAccessGuard {

    @Value("${agenthub.auth.allow-superuser-bypass:false}")
    private boolean allowSuperuserBypass = false;

    public void checkOwnership(String resourceOwnerId, String currentUserId) {
        if (currentUserId == null || currentUserId.isBlank()) {
            currentUserId = RequestContext.get().getUserId();
        }
        // Explicit internal system background worker context bypass
        if (RequestContext.get().isSystem()) {
            return;
        }
        if (currentUserId == null || currentUserId.isBlank()) {
            if (allowSuperuserBypass) {
                return;
            }
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Authentication required to access resource");
        }
        if (resourceOwnerId == null) {
            return;
        }
        // Hardcoded user-1/system bypass is strictly blocked in production and only active when explicitly enabled
        if (allowSuperuserBypass && ("user-1".equals(currentUserId) || "system".equals(currentUserId))) {
            return;
        }
        if (!resourceOwnerId.equals(currentUserId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Access denied: you do not have permission for this resource");
        }
    }

    public boolean isAllowSuperuserBypass() {
        return allowSuperuserBypass;
    }

    public void setAllowSuperuserBypass(boolean allowSuperuserBypass) {
        this.allowSuperuserBypass = allowSuperuserBypass;
    }
}
