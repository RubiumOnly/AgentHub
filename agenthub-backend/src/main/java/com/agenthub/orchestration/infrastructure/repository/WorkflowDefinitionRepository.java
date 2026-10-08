package com.agenthub.orchestration.infrastructure.repository;

import com.agenthub.orchestration.infrastructure.entity.WorkflowDefinitionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WorkflowDefinitionRepository extends JpaRepository<WorkflowDefinitionEntity, String> {
    Optional<WorkflowDefinitionEntity> findTopByOrderByCreatedAtDesc();
}
