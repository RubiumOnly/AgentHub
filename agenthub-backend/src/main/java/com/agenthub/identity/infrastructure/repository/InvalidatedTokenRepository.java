package com.agenthub.identity.infrastructure.repository;

import com.agenthub.identity.infrastructure.entity.InvalidatedTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface InvalidatedTokenRepository extends JpaRepository<InvalidatedTokenEntity, String> {

    List<InvalidatedTokenEntity> findByExpiresAtAfter(LocalDateTime now);

    @Modifying
    @Query("DELETE FROM InvalidatedTokenEntity e WHERE e.expiresAt <= :now")
    int deleteExpiredTokens(@Param("now") LocalDateTime now);
}
