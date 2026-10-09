package com.agenthub.identity.infrastructure.repository;

import com.agenthub.identity.infrastructure.entity.AuthAuditLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuthAuditLogRepository extends JpaRepository<AuthAuditLogEntity, String> {

    List<AuthAuditLogEntity> findByUserIdOrderByCreatedAtDesc(String userId);

    List<AuthAuditLogEntity> findByEventTypeOrderByCreatedAtDesc(String eventType);
}
