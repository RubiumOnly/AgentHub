package com.agenthub.audit.infrastructure.repository;

import com.agenthub.audit.infrastructure.entity.ArtifactEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ArtifactRepository extends JpaRepository<ArtifactEntity, String> {
    List<ArtifactEntity> findByRunId(String runId);
}
