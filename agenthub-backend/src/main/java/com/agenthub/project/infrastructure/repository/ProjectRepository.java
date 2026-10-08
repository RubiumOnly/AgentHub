package com.agenthub.project.infrastructure.repository;

import com.agenthub.project.infrastructure.entity.ProjectEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProjectRepository extends JpaRepository<ProjectEntity, String> {
    List<ProjectEntity> findByOwnerIdOrderByUpdatedAtDesc(String ownerId);
}
