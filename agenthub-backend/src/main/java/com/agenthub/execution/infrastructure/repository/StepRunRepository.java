package com.agenthub.execution.infrastructure.repository;

import com.agenthub.execution.infrastructure.entity.StepRunEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface StepRunRepository extends JpaRepository<StepRunEntity, String> {
    List<StepRunEntity> findByRunIdOrderByCreatedAtAsc(String runId);
    Optional<StepRunEntity> findByRunIdAndNodeId(String runId, String nodeId);
    List<StepRunEntity> findByRunIdAndStatus(String runId, String status);

    @Query("SELECT s FROM StepRunEntity s WHERE s.status = 'RUNNING' AND (s.leaseUntil IS NULL OR s.leaseUntil < :now)")
    List<StepRunEntity> findStaleRunningSteps(@Param("now") LocalDateTime now);

    @Transactional
    @Modifying
    @Query("UPDATE StepRunEntity s SET s.leaseOwner = :newOwner, s.leaseUntil = :leaseUntil, s.heartbeatAt = :now, s.attempt = COALESCE(s.attempt, 0) + 1 " +
           "WHERE s.id = :stepId AND (s.leaseUntil IS NULL OR s.leaseUntil < :now OR s.leaseOwner = :newOwner)")
    int tryAcquireStepLease(@Param("stepId") String stepId,
                            @Param("newOwner") String newOwner,
                            @Param("leaseUntil") LocalDateTime leaseUntil,
                            @Param("now") LocalDateTime now);

    @Transactional
    @Modifying
    @Query("UPDATE StepRunEntity s SET s.leaseOwner = NULL, s.leaseUntil = NULL, s.heartbeatAt = :now " +
           "WHERE s.id = :stepId AND s.leaseOwner = :owner")
    int releaseStepLease(@Param("stepId") String stepId,
                         @Param("owner") String owner,
                         @Param("now") LocalDateTime now);
}
