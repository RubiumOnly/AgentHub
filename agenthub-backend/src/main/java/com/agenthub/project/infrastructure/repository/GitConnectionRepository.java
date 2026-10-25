package com.agenthub.project.infrastructure.repository;

import com.agenthub.project.infrastructure.entity.GitConnectionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GitConnectionRepository extends JpaRepository<GitConnectionEntity, String> {

    List<GitConnectionEntity> findByProjectIdOrderByAuthorizedAtDesc(String projectId);

    Optional<GitConnectionEntity> findByProjectIdAndRepoUrlAndStatus(String projectId, String repoUrl, String status);

    Optional<GitConnectionEntity> findByIdAndProjectId(String id, String projectId);
}
