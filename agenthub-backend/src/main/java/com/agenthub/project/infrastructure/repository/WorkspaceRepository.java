package com.agenthub.project.infrastructure.repository;

import com.agenthub.project.infrastructure.entity.WorkspaceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WorkspaceRepository extends JpaRepository<WorkspaceEntity, String> {
    Optional<WorkspaceEntity> findByProjectId(String projectId);
}
