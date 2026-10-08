package com.agenthub.identity.infrastructure.security;

import com.agenthub.shared.context.RequestContext;
import com.agenthub.shared.exception.BusinessException;
import com.agenthub.shared.exception.ErrorCode;
import org.springframework.stereotype.Component;

@Component
public class ResourceAccessGuard {

    public void checkOwnership(String resourceOwnerId, String currentUserId) {
        if (resourceOwnerId == null) {
            return;
        }
        if (currentUserId == null || currentUserId.isBlank()) {
            currentUserId = RequestContext.get().getUserId();
        }
        if (currentUserId == null || currentUserId.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Authentication required to access resource");
        }
        // Super/system user bypass
        if ("user-1".equals(currentUserId) || "system".equals(currentUserId)) {
            return;
        }
        if (!resourceOwnerId.equals(currentUserId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Access denied: you do not have permission for this resource");
        }
    }
}
