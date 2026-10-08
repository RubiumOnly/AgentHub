package com.agenthub.execution.infrastructure.repository;

import com.agenthub.execution.infrastructure.entity.StepRunEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StepRunRepository extends JpaRepository<StepRunEntity, String> {
    List<StepRunEntity> findByRunIdOrderByCreatedAtAsc(String runId);
    Optional<StepRunEntity> findByRunIdAndNodeId(String runId, String nodeId);
}
