package com.agenthub.identity.infrastructure.security;

import com.agenthub.identity.infrastructure.entity.AuthAuditLogEntity;
import com.agenthub.identity.infrastructure.repository.AuthAuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AuthAuditService {

    private static final Logger log = LoggerFactory.getLogger(AuthAuditService.class);

    private final AuthAuditLogRepository auditLogRepository;

    public AuthAuditService(AuthAuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String eventType, String userId, String email, String ipAddress, String details) {
        try {
            String id = "audit-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            AuthAuditLogEntity entity = new AuthAuditLogEntity(id, eventType, userId, email, ipAddress, details);
            auditLogRepository.save(entity);
            log.info("AUTH_AUDIT: type=[{}] user=[{}] email=[{}] ip=[{}] details=[{}]",
                    eventType, userId, email, ipAddress, details);
        } catch (Exception e) {
            log.error("Failed to write auth audit log: {}", e.getMessage(), e);
        }
    }
}
