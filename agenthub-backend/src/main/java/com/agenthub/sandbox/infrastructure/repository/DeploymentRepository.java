package com.agenthub.sandbox.infrastructure.repository;

import com.agenthub.sandbox.infrastructure.entity.DeploymentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DeploymentRepository extends JpaRepository<DeploymentEntity, String> {
    List<DeploymentEntity> findByProjectIdOrderByStartedAtDesc(String projectId);
}
