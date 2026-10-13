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

    @Value("${agenthub.auth.superuser-ids:}")
    private String superuserIdsConfig = "";

    @Value("${agenthub.demo.default-user-id:}")
    private String demoDefaultUserId = "";

    @Value("${agenthub.demo.default-project-id:}")
    private String demoDefaultProjectId = "";

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
        if (allowSuperuserBypass && isConfiguredSuperuser(currentUserId)) {
            return;
        }
        if (!resourceOwnerId.equals(currentUserId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Access denied: you do not have permission for this resource");
        }
    }

    public boolean isConfiguredSuperuser(String userId) {
        if (RequestContext.get().isSystem()) {
            return true;
        }
        if (!allowSuperuserBypass || userId == null || userId.isBlank()) {
            return false;
        }
        if (superuserIdsConfig != null && !superuserIdsConfig.isBlank()) {
            for (String token : superuserIdsConfig.split(",")) {
                if (userId.equals(token.trim())) {
                    return true;
                }
            }
        }
        return demoDefaultUserId != null && !demoDefaultUserId.isBlank() && userId.equals(demoDefaultUserId.trim());
    }

    public boolean isAllowSuperuserBypass() {
        return allowSuperuserBypass;
    }

    public void setAllowSuperuserBypass(boolean allowSuperuserBypass) {
        this.allowSuperuserBypass = allowSuperuserBypass;
    }

    public String getSuperuserIdsConfig() {
        return superuserIdsConfig;
    }

    public void setSuperuserIdsConfig(String superuserIdsConfig) {
        this.superuserIdsConfig = superuserIdsConfig;
    }

    public String getDemoDefaultUserId() {
        return demoDefaultUserId;
    }

    public void setDemoDefaultUserId(String demoDefaultUserId) {
        this.demoDefaultUserId = demoDefaultUserId;
    }

    public String getDemoDefaultProjectId() {
        return demoDefaultProjectId;
    }

    public void setDemoDefaultProjectId(String demoDefaultProjectId) {
        this.demoDefaultProjectId = demoDefaultProjectId;
    }
}
