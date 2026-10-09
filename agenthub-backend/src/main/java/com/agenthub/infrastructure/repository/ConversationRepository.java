package com.agenthub.infrastructure.repository;

import com.agenthub.infrastructure.repository.entity.ConversationEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE ConversationEntity c SET c.tokenCount = COALESCE(c.tokenCount, 0) + :tokenDelta, c.updatedAt = :now WHERE c.id = :id")
    int recordMessageActivity(@Param("id") String id, @Param("tokenDelta") int tokenDelta, @Param("now") LocalDateTime now);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE ConversationEntity c SET c.status = :status, c.updatedAt = :now WHERE c.id = :id")
    int updateStatus(@Param("id") String id, @Param("status") String status, @Param("now") LocalDateTime now);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE ConversationEntity c SET c.summary = :summary, c.updatedAt = :now WHERE c.id = :id")
    int updateSummary(@Param("id") String id, @Param("summary") String summary, @Param("now") LocalDateTime now);
}
