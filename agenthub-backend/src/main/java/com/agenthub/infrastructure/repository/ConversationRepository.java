package com.agenthub.infrastructure.repository;

import com.agenthub.infrastructure.repository.entity.ConversationEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationRepository extends JpaRepository<ConversationEntity, String> {
    List<ConversationEntity> findAllByOrderByUpdatedAtDesc();
    List<ConversationEntity> findByOwnerIdOrderByUpdatedAtDesc(String ownerId);
    List<ConversationEntity> findByProjectIdOrderByUpdatedAtDesc(String projectId);
    List<ConversationEntity> findByTeamIdOrderByUpdatedAtDesc(String teamId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM ConversationEntity c WHERE c.id = :id")
    Optional<ConversationEntity> findByIdForUpdate(@Param("id") String id);
}
