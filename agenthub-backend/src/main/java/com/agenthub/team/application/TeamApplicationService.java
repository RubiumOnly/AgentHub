package com.agenthub.team.application;

import com.agenthub.shared.exception.BusinessException;
import com.agenthub.shared.exception.ErrorCode;
import com.agenthub.team.domain.model.NextSpeakerDecision;
import com.agenthub.team.domain.model.TeamCoordinationContext;
import com.agenthub.team.domain.model.TeamRole;
import com.agenthub.team.domain.model.TeamTopology;
import com.agenthub.team.domain.strategy.TeamTopologyStrategy;
import com.agenthub.team.domain.strategy.TeamTopologyStrategyFactory;
import com.agenthub.team.dto.*;
import com.agenthub.team.infrastructure.entity.TeamEntity;
import com.agenthub.team.infrastructure.entity.TeamMemberEntity;
import com.agenthub.team.infrastructure.repository.TeamMemberRepository;
import com.agenthub.team.infrastructure.repository.TeamRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class TeamApplicationService implements TeamApplication {

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TeamTopologyStrategyFactory strategyFactory;

    public TeamApplicationService(TeamRepository teamRepository,
                                  TeamMemberRepository teamMemberRepository,
                                  TeamTopologyStrategyFactory strategyFactory) {
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.strategyFactory = strategyFactory;
    }

    @Override
    @Transactional
    public TeamView createTeam(CreateTeamCommand cmd) {
        if (cmd.getName() == null || cmd.getName().isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Team name cannot be empty");
        }

        String teamId = "team-" + UUID.randomUUID().toString().substring(0, 8);
        TeamTopology topology = cmd.getTopology() != null ? cmd.getTopology() : TeamTopology.HIERARCHICAL;

        TeamEntity entity = new TeamEntity(
                teamId,
                cmd.getProjectId() != null ? cmd.getProjectId() : "proj-default",
                cmd.getName(),
                cmd.getDescription(),
                topology,
                cmd.getLeaderAgentId(),
                cmd.getMaxTurns() != null ? cmd.getMaxTurns() : 10,
                cmd.getConfigJson()
        );
        teamRepository.save(entity);

        if (cmd.getInitialMembers() != null) {
            for (AddTeamMemberCommand memberCmd : cmd.getInitialMembers()) {
                String memberId = "tm-" + UUID.randomUUID().toString().substring(0, 8);
                TeamMemberEntity memberEntity = new TeamMemberEntity(
                        memberId,
                        teamId,
                        memberCmd.getAgentInstanceId(),
                        memberCmd.getRole() != null ? memberCmd.getRole() : memberCmd.getAgentInstanceId(),
                        memberCmd.getRoleType() != null ? memberCmd.getRoleType() : TeamRole.CUSTOM,
                        memberCmd.getSortOrder() != null ? memberCmd.getSortOrder() : 0,
                        memberCmd.getResponsibilities(),
                        memberCmd.getSystemPromptOverride(),
                        memberCmd.getCanDelegate() != null ? memberCmd.getCanDelegate() : true
                );
                teamMemberRepository.save(memberEntity);
            }
        }

        return getTeamById(teamId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TeamView> listTeams(String projectId) {
        List<TeamEntity> teams;
        if (projectId != null && !projectId.isBlank()) {
            teams = teamRepository.findByProjectIdOrderByCreatedAtDesc(projectId);
        } else {
            teams = teamRepository.findAllByOrderByCreatedAtDesc();
        }

        return teams.stream()
                .map(this::toTeamView)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public TeamView getTeamById(String teamId) {
        TeamEntity entity = teamRepository.findById(teamId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TEAM_NOT_FOUND, "Team not found: " + teamId));
        return toTeamView(entity);
    }

    @Override
    @Transactional
    public TeamMemberView addTeamMember(String teamId, AddTeamMemberCommand cmd) {
        if (!teamRepository.existsById(teamId)) {
            throw new BusinessException(ErrorCode.TEAM_NOT_FOUND, "Team not found: " + teamId);
        }
        if (cmd.getAgentInstanceId() == null || cmd.getAgentInstanceId().isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Agent instance ID cannot be empty");
        }

        String memberId = "tm-" + UUID.randomUUID().toString().substring(0, 8);
        TeamMemberEntity entity = new TeamMemberEntity(
                memberId,
                teamId,
                cmd.getAgentInstanceId(),
                cmd.getRole() != null ? cmd.getRole() : cmd.getAgentInstanceId(),
                cmd.getRoleType() != null ? cmd.getRoleType() : TeamRole.CUSTOM,
                cmd.getSortOrder() != null ? cmd.getSortOrder() : 0,
                cmd.getResponsibilities(),
                cmd.getSystemPromptOverride(),
                cmd.getCanDelegate() != null ? cmd.getCanDelegate() : true
        );
        teamMemberRepository.save(entity);
        return toMemberView(entity);
    }

    @Override
    @Transactional
    public void removeTeamMember(String teamId, String memberId) {
        if (!teamRepository.existsById(teamId)) {
            throw new BusinessException(ErrorCode.TEAM_NOT_FOUND, "Team not found: " + teamId);
        }
        TeamMemberEntity member = teamMemberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TEAM_MEMBER_NOT_FOUND, "Team member not found: " + memberId));
        if (!member.getTeamId().equals(teamId)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Member does not belong to team: " + teamId);
        }
        teamMemberRepository.delete(member);
    }

    @Override
    @Transactional(readOnly = true)
    public TeamCoordinateResultView coordinateNextTurn(String teamId, TeamCoordinateCommand cmd) {
        TeamEntity team = teamRepository.findById(teamId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TEAM_NOT_FOUND, "Team not found: " + teamId));
        List<TeamMemberEntity> members = teamMemberRepository.findByTeamIdOrderBySortOrderAsc(teamId);

        if (members.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Team has no members configured: " + teamId);
        }

        TeamTopologyStrategy strategy = strategyFactory.getStrategy(team.getTopology());

        TeamCoordinationContext context = new TeamCoordinationContext(
                cmd.getConversationId(),
                cmd.getCurrentSpeakerId(),
                cmd.getCurrentTurn() != null ? cmd.getCurrentTurn() : 0,
                team.getMaxTurns(),
                cmd.getLastMention(),
                cmd.getHandoffTarget(),
                cmd.getTaskGoal(),
                cmd.getRecentMessages() != null ? cmd.getRecentMessages() : Collections.emptyList()
        );

        NextSpeakerDecision decision = strategy.decideNextSpeaker(team, members, context);
        return TeamCoordinateResultView.fromDecision(decision);
    }

    private TeamView toTeamView(TeamEntity entity) {
        List<TeamMemberView> memberViews = teamMemberRepository.findByTeamIdOrderBySortOrderAsc(entity.getId()).stream()
                .map(this::toMemberView)
                .collect(Collectors.toList());

        return new TeamView(
                entity.getId(),
                entity.getProjectId(),
                entity.getName(),
                entity.getDescription(),
                entity.getTopology(),
                entity.getLeaderAgentId(),
                entity.getMaxTurns(),
                entity.getConfigJson(),
                entity.getStatus(),
                memberViews,
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private TeamMemberView toMemberView(TeamMemberEntity member) {
        return new TeamMemberView(
                member.getId(),
                member.getTeamId(),
                member.getAgentInstanceId(),
                member.getRole(),
                member.getRoleType(),
                member.getSortOrder(),
                member.getResponsibilities(),
                member.getSystemPromptOverride(),
                member.getCanDelegate()
        );
    }
}
