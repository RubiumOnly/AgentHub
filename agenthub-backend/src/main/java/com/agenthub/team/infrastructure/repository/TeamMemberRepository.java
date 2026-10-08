package com.agenthub.team.infrastructure.repository;

import com.agenthub.team.infrastructure.entity.TeamMemberEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TeamMemberRepository extends JpaRepository<TeamMemberEntity, String> {
    List<TeamMemberEntity> findByTeamIdOrderBySortOrderAsc(String teamId);
    void deleteByTeamId(String teamId);
}
