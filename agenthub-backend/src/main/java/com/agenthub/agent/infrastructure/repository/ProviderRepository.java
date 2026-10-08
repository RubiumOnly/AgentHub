package com.agenthub.agent.infrastructure.repository;

import com.agenthub.agent.infrastructure.entity.ProviderEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProviderRepository extends JpaRepository<ProviderEntity, String> {
    List<ProviderEntity> findByOwnerId(String ownerId);
}
