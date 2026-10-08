package com.agenthub.audit.infrastructure.repository;

import com.agenthub.audit.infrastructure.entity.ArtifactEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ArtifactRepository extends JpaRepository<ArtifactEntity, String> {
    List<ArtifactEntity> findByRunId(String runId);
    List<ArtifactEntity> findByRunIdOrderByCreatedAtDesc(String runId);
    List<ArtifactEntity> findByStepRunId(String stepRunId);
    Optional<ArtifactEntity> findByRunIdAndStepRunId(String runId, String stepRunId);
    List<ArtifactEntity> findByRunIdAndArtifactType(String runId, String artifactType);
}
