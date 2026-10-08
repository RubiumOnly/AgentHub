package com.agenthub.infrastructure.repository;

import com.agenthub.infrastructure.repository.entity.ConversationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ConversationRepository extends JpaRepository<ConversationEntity, String> {
    List<ConversationEntity> findAllByOrderByUpdatedAtDesc();
    List<ConversationEntity> findByOwnerIdOrderByUpdatedAtDesc(String ownerId);
    List<ConversationEntity> findByProjectIdOrderByUpdatedAtDesc(String projectId);
}
