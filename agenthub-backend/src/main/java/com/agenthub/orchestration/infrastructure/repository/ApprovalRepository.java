package com.agenthub.orchestration.infrastructure.repository;

import com.agenthub.orchestration.infrastructure.entity.ApprovalEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ApprovalRepository extends JpaRepository<ApprovalEntity, String> {
    List<ApprovalEntity> findByRunId(String runId);
    Optional<ApprovalEntity> findByStepRunId(String stepRunId);
    Optional<ApprovalEntity> findByRunIdAndStepRunId(String runId, String stepRunId);
    List<ApprovalEntity> findByStatus(String status);
}
