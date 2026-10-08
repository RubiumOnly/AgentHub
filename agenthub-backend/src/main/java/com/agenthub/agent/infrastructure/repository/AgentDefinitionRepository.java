package com.agenthub.agent.infrastructure.repository;

import com.agenthub.agent.infrastructure.entity.AgentDefinitionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AgentDefinitionRepository extends JpaRepository<AgentDefinitionEntity, String> {
    Optional<AgentDefinitionEntity> findByDefKey(String defKey);
}
