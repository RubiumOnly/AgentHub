package com.agenthub.execution.infrastructure.repository;

import com.agenthub.execution.infrastructure.entity.WorkflowRunEntity;
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
public interface WorkflowRunRepository extends JpaRepository<WorkflowRunEntity, String> {
    List<WorkflowRunEntity> findByProjectIdOrderByCreatedAtDesc(String projectId);
    Optional<WorkflowRunEntity> findByIdempotencyKey(String idempotencyKey);
    List<WorkflowRunEntity> findByStatusIn(List<String> statuses);

    @Query("SELECT r FROM WorkflowRunEntity r WHERE r.status = 'RUNNING' AND (r.leaseUntil IS NULL OR r.leaseUntil < :now)")
    List<WorkflowRunEntity> findStaleRunningRuns(@Param("now") LocalDateTime now);

    @Query("SELECT r FROM WorkflowRunEntity r WHERE r.status = 'QUEUED'")
    List<WorkflowRunEntity> findQueuedRuns();

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE WorkflowRunEntity r SET r.leaseOwner = :newOwner, r.leaseUntil = :leaseUntil, r.heartbeatAt = :now, r.attempt = COALESCE(r.attempt, 0) + 1 " +
           "WHERE r.id = :runId AND (r.leaseUntil IS NULL OR r.leaseUntil < :now OR r.leaseOwner = :newOwner)")
    int tryAcquireRunLease(@Param("runId") String runId,
                           @Param("newOwner") String newOwner,
                           @Param("leaseUntil") LocalDateTime leaseUntil,
                           @Param("now") LocalDateTime now);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE WorkflowRunEntity r SET r.leaseOwner = NULL, r.leaseUntil = NULL, r.heartbeatAt = :now " +
           "WHERE r.id = :runId AND r.leaseOwner = :owner")
    int releaseRunLease(@Param("runId") String runId,
                        @Param("owner") String owner,
                        @Param("now") LocalDateTime now);
}
