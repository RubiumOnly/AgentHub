package com.agenthub.agent.infrastructure.repository;

import com.agenthub.agent.infrastructure.entity.AgentInstanceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AgentInstanceRepository extends JpaRepository<AgentInstanceEntity, String> {
    List<AgentInstanceEntity> findByOwnerId(String ownerId);
    List<AgentInstanceEntity> findByProjectId(String projectId);
}
