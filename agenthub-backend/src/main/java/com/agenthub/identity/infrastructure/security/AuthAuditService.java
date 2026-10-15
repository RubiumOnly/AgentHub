package com.agenthub.identity.infrastructure.security;

import com.agenthub.identity.infrastructure.entity.AuthAuditLogEntity;
import com.agenthub.identity.infrastructure.repository.AuthAuditLogRepository;
import com.agenthub.identity.dto.AuthAuditLogView;
import com.agenthub.infrastructure.metrics.AgentHubMetricsCollector;
import com.agenthub.shared.contract.PageResultView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AuthAuditService {

    private static final Logger log = LoggerFactory.getLogger(AuthAuditService.class);

    private final AuthAuditLogRepository auditLogRepository;

    @Autowired(required = false)
    private AgentHubMetricsCollector metricsCollector;

    public AuthAuditService(AuthAuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String eventType, String userId, String email, String ipAddress, String details) {
        try {
            String id = "audit-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            AuthAuditLogEntity entity = new AuthAuditLogEntity(id, eventType, userId, email, ipAddress, details);
            auditLogRepository.save(entity);
            if (metricsCollector != null) {
                metricsCollector.recordAuditEvent(eventType);
            }
            log.info("AUTH_AUDIT: type=[{}] user=[{}] email=[{}] ip=[{}] details=[{}]",
                    eventType, userId, email, ipAddress, details);
        } catch (Exception e) {
            log.error("Failed to write auth audit log: {}", e.getMessage(), e);
        }
    }

    @Transactional(readOnly = true)
    public PageResultView<AuthAuditLogView> queryAuditLogs(String eventType, String userId, int page, int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, size));

        List<AuthAuditLogEntity> entities;
        if (eventType != null && !eventType.isBlank() && userId != null && !userId.isBlank()) {
            entities = auditLogRepository.findByUserIdAndEventTypeOrderByCreatedAtDesc(userId.trim(), eventType.trim());
        } else if (eventType != null && !eventType.isBlank()) {
            entities = auditLogRepository.findByEventTypeOrderByCreatedAtDesc(eventType.trim());
        } else if (userId != null && !userId.isBlank()) {
            entities = auditLogRepository.findByUserIdOrderByCreatedAtDesc(userId.trim());
        } else {
            entities = auditLogRepository.findAllByOrderByCreatedAtDesc();
        }

        List<AuthAuditLogView> views = entities.stream()
                .map(e -> new AuthAuditLogView(
                        e.getId(), e.getEventType(), e.getUserId(), e.getEmail(),
                        e.getIpAddress(), e.getDetails(), e.getCreatedAt()))
                .collect(Collectors.toList());
        return com.agenthub.shared.contract.PageRequestQuery.of(safePage, safeSize).slice(views);
    }
}
