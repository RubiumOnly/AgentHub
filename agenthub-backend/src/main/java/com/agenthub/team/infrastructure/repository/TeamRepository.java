package com.agenthub.team.infrastructure.repository;

import com.agenthub.team.infrastructure.entity.TeamEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TeamRepository extends JpaRepository<TeamEntity, String> {
    List<TeamEntity> findByProjectIdOrderByCreatedAtDesc(String projectId);
    List<TeamEntity> findAllByOrderByCreatedAtDesc();
}
