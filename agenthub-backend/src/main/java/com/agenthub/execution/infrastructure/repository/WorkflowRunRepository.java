package com.agenthub.execution.infrastructure.repository;

import com.agenthub.execution.infrastructure.entity.WorkflowRunEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WorkflowRunRepository extends JpaRepository<WorkflowRunEntity, String> {
    List<WorkflowRunEntity> findByProjectIdOrderByCreatedAtDesc(String projectId);
    Optional<WorkflowRunEntity> findByIdempotencyKey(String idempotencyKey);
}
