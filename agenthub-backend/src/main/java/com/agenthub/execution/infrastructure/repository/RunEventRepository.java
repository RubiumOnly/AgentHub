package com.agenthub.execution.infrastructure.repository;

import com.agenthub.execution.infrastructure.entity.RunEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RunEventRepository extends JpaRepository<RunEventEntity, String> {
    List<RunEventEntity> findByRunIdOrderBySequenceNumAsc(String runId);
    List<RunEventEntity> findByRunIdOrderBySequenceNumAsc(String runId, org.springframework.data.domain.Pageable pageable);
    List<RunEventEntity> findByRunIdAndSequenceNumGreaterThanOrderBySequenceNumAsc(String runId, Long sequenceNum);
    List<RunEventEntity> findByRunIdAndSequenceNumGreaterThanOrderBySequenceNumAsc(String runId, Long sequenceNum, org.springframework.data.domain.Pageable pageable);
    long countByRunId(String runId);

    @org.springframework.data.jpa.repository.Query("SELECT MAX(e.sequenceNum) FROM RunEventEntity e WHERE e.runId = :runId")
    Long findMaxSequenceNum(@org.springframework.data.repository.query.Param("runId") String runId);
}
