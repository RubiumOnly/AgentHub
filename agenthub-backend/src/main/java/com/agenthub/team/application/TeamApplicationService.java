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
import com.agenthub.identity.infrastructure.entity.UserEntity;
import com.agenthub.identity.infrastructure.repository.UserRepository;
import com.agenthub.identity.infrastructure.security.AuthAuditService;
import com.agenthub.identity.infrastructure.security.ResourceOwnershipVerifier;
import com.agenthub.project.infrastructure.entity.ProjectEntity;
import com.agenthub.project.infrastructure.repository.ProjectRepository;
import com.agenthub.shared.context.RequestContext;
import com.agenthub.team.domain.rbac.ProjectPermission;
import com.agenthub.team.domain.rbac.ProjectRbacRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TeamApplicationService implements TeamApplication {

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TeamTopologyStrategyFactory strategyFactory;

    @Autowired(required = false)
    private ProjectRepository projectRepository;

    @Autowired(required = false)
    private UserRepository userRepository;

    @Autowired(required = false)
    private ResourceOwnershipVerifier ownershipVerifier;

    @Autowired(required = false)
    private AuthAuditService authAuditService;

    @Value("${agenthub.demo.default-project-id:}")
    private String defaultProjectId = "";

    public TeamApplicationService(TeamRepository teamRepository,
                                  TeamMemberRepository teamMemberRepository,
                                  TeamTopologyStrategyFactory strategyFactory) {
        this(teamRepository, teamMemberRepository, strategyFactory, null, null, null, null);
    }

    @Autowired
    public TeamApplicationService(TeamRepository teamRepository,
                                  TeamMemberRepository teamMemberRepository,
                                  TeamTopologyStrategyFactory strategyFactory,
                                  @Autowired(required = false) ProjectRepository projectRepository,
                                  @Autowired(required = false) UserRepository userRepository,
                                  @Autowired(required = false) ResourceOwnershipVerifier ownershipVerifier,
                                  @Autowired(required = false) AuthAuditService authAuditService) {
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.strategyFactory = strategyFactory;
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.ownershipVerifier = ownershipVerifier;
        this.authAuditService = authAuditService;
    }

    @Override
    @Transactional
    public TeamView createTeam(CreateTeamCommand cmd) {
        if (cmd.getName() == null || cmd.getName().isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Team name cannot be empty");
        }

        String teamId = "team-" + UUID.randomUUID().toString().substring(0, 8);
        TeamTopology topology = cmd.getTopology() != null ? cmd.getTopology() : TeamTopology.HIERARCHICAL;
        String resolvedProjectId = (cmd.getProjectId() != null && !cmd.getProjectId().isBlank())
                ? cmd.getProjectId()
                : (defaultProjectId != null && !defaultProjectId.isBlank() ? defaultProjectId : null);

        TeamEntity entity = new TeamEntity(
                teamId,
                resolvedProjectId,
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

    @Override
    @Transactional(readOnly = true)
    public ProjectUserRoleView getCurrentUserRole(String projectId) {
        String currentUserId = RequestContext.get().getUserId();
        if (currentUserId == null || currentUserId.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Authentication required to check project role");
        }
        if (projectRepository == null) {
            return new ProjectUserRoleView(currentUserId, projectId, ProjectRbacRole.VIEWER, Set.of(), false);
        }
        ProjectEntity project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND, "Project not found: " + projectId));

        if (project.getOwnerId().equals(currentUserId)) {
            Set<String> perms = ProjectRbacRole.OWNER.getPermissions().stream()
                    .map(Enum::name)
                    .collect(Collectors.toSet());
            return new ProjectUserRoleView(currentUserId, projectId, ProjectRbacRole.OWNER, perms, true);
        }

        ProjectRbacRole role = null;
        if (ownershipVerifier != null) {
            role = ownershipVerifier.resolveUserRole(projectId, currentUserId);
        }
        if (role == null) {
            role = ProjectRbacRole.VIEWER;
        }

        Set<String> perms = role.getPermissions().stream()
                .map(Enum::name)
                .collect(Collectors.toSet());
        return new ProjectUserRoleView(currentUserId, projectId, role, perms, false);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProjectMemberView> listProjectMembers(String projectId) {
        if (ownershipVerifier != null) {
            ownershipVerifier.verifyProjectAccess(projectId);
        }
        List<ProjectMemberView> list = new ArrayList<>();
        if (projectRepository == null) {
            return list;
        }

        ProjectEntity project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND, "Project not found: " + projectId));

        // 1. Add Owner
        String ownerName = project.getOwnerId();
        String ownerEmail = "";
        if (userRepository != null) {
            Optional<UserEntity> userOpt = userRepository.findById(project.getOwnerId());
            if (userOpt.isPresent()) {
                ownerName = userOpt.get().getDisplayName() != null ? userOpt.get().getDisplayName() : userOpt.get().getEmail();
                ownerEmail = userOpt.get().getEmail();
            }
        }
        Set<String> ownerPerms = ProjectRbacRole.OWNER.getPermissions().stream().map(Enum::name).collect(Collectors.toSet());
        list.add(new ProjectMemberView(
                "owner-" + project.getOwnerId(),
                "team-" + projectId,
                projectId,
                project.getOwnerId(),
                ownerName,
                ownerEmail,
                "Project Owner",
                ProjectRbacRole.OWNER,
                ownerPerms,
                true,
                project.getCreatedAt()
        ));

        // 2. Add Team Members
        List<TeamEntity> teams = teamRepository.findByProjectIdOrderByCreatedAtDesc(projectId);
        for (TeamEntity team : teams) {
            List<TeamMemberEntity> members = teamMemberRepository.findByTeamIdOrderBySortOrderAsc(team.getId());
            for (TeamMemberEntity m : members) {
                if (m.getAgentInstanceId().equals(project.getOwnerId())) {
                    continue;
                }
                ProjectRbacRole rbacRole = m.getRoleType() != null
                        ? ProjectRbacRole.fromString(m.getRoleType().name())
                        : ProjectRbacRole.fromString(m.getRole());
                String dName = m.getAgentInstanceId();
                String dEmail = "";
                if (userRepository != null) {
                    Optional<UserEntity> uOpt = userRepository.findById(m.getAgentInstanceId());
                    if (uOpt.isPresent()) {
                        dName = uOpt.get().getDisplayName() != null ? uOpt.get().getDisplayName() : uOpt.get().getEmail();
                        dEmail = uOpt.get().getEmail();
                    }
                }
                Set<String> perms = rbacRole.getPermissions().stream().map(Enum::name).collect(Collectors.toSet());
                list.add(new ProjectMemberView(
                        m.getId(),
                        team.getId(),
                        projectId,
                        m.getAgentInstanceId(),
                        dName,
                        dEmail,
                        m.getRole() != null ? m.getRole() : rbacRole.name(),
                        rbacRole,
                        perms,
                        m.getCanDelegate(),
                        team.getCreatedAt()
                ));
            }
        }
        return list;
    }

    @Override
    @Transactional
    public ProjectMemberView addProjectMember(String projectId, AddProjectMemberCommand cmd) {
        if (ownershipVerifier != null) {
            ownershipVerifier.verifyProjectAccess(projectId, RequestContext.get().getUserId(), ProjectPermission.PROJECT_MANAGE);
        }
        if (cmd == null || cmd.getUserId() == null || cmd.getUserId().isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "User ID cannot be empty");
        }

        ProjectEntity project = projectRepository != null ? projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND, "Project not found: " + projectId)) : null;
        if (project != null && project.getOwnerId().equals(cmd.getUserId())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Cannot alter project owner: user is already permanent project OWNER");
        }

        List<TeamEntity> teams = teamRepository.findByProjectIdOrderByCreatedAtDesc(projectId);
        TeamEntity targetTeam;
        if (teams.isEmpty()) {
            String teamId = "team-" + UUID.randomUUID().toString().substring(0, 8);
            targetTeam = new TeamEntity(
                    teamId,
                    projectId,
                    (project != null ? project.getName() : projectId) + " Team",
                    "Default collaboration team",
                    TeamTopology.HIERARCHICAL,
                    null,
                    10,
                    null
            );
            teamRepository.save(targetTeam);
        } else {
            targetTeam = teams.get(0);
        }

        ProjectRbacRole rbacRole = cmd.getRbacRole() != null ? cmd.getRbacRole() : ProjectRbacRole.DEVELOPER;
        TeamRole teamRole;
        try {
            teamRole = TeamRole.valueOf(rbacRole.name());
        } catch (Exception e) {
            teamRole = TeamRole.CUSTOM;
        }

        List<TeamMemberEntity> existingMembers = teamMemberRepository.findByTeamIdOrderBySortOrderAsc(targetTeam.getId());
        for (TeamMemberEntity existing : existingMembers) {
            if (existing.getAgentInstanceId().equals(cmd.getUserId())) {
                existing.setRoleType(teamRole);
                existing.setRole(cmd.getRole() != null ? cmd.getRole() : rbacRole.name());
                if (cmd.getResponsibilities() != null) {
                    existing.setResponsibilities(cmd.getResponsibilities());
                }
                teamMemberRepository.save(existing);
                if (authAuditService != null) {
                    authAuditService.record("RBAC_ROLE_ASSIGNED", RequestContext.get().getUserId(), null, null,
                            "Updated project [" + projectId + "] member [" + cmd.getUserId() + "] role to [" + rbacRole.name() + "]");
                }
                Set<String> perms = rbacRole.getPermissions().stream().map(Enum::name).collect(Collectors.toSet());
                return new ProjectMemberView(
                        existing.getId(), targetTeam.getId(), projectId, cmd.getUserId(),
                        cmd.getUserId(), "", existing.getRole(), rbacRole, perms, existing.getCanDelegate(), LocalDateTime.now());
            }
        }

        String memberId = "tm-" + UUID.randomUUID().toString().substring(0, 8);
        TeamMemberEntity entity = new TeamMemberEntity(
                memberId,
                targetTeam.getId(),
                cmd.getUserId(),
                cmd.getRole() != null ? cmd.getRole() : rbacRole.name(),
                teamRole,
                existingMembers.size(),
                cmd.getResponsibilities(),
                null,
                cmd.getCanDelegate() != null ? cmd.getCanDelegate() : true
        );
        teamMemberRepository.save(entity);

        if (authAuditService != null) {
            authAuditService.record("RBAC_ROLE_ASSIGNED", RequestContext.get().getUserId(), null, null,
                    "Added project [" + projectId + "] member [" + cmd.getUserId() + "] with role [" + rbacRole.name() + "]");
        }

        Set<String> perms = rbacRole.getPermissions().stream().map(Enum::name).collect(Collectors.toSet());
        return new ProjectMemberView(
                memberId, targetTeam.getId(), projectId, cmd.getUserId(),
                cmd.getUserId(), "", entity.getRole(), rbacRole, perms, entity.getCanDelegate(), LocalDateTime.now());
    }

    @Override
    @Transactional
    public ProjectMemberView updateMemberRole(String projectId, String memberId, UpdateMemberRoleCommand cmd) {
        if (ownershipVerifier != null) {
            ownershipVerifier.verifyProjectAccess(projectId, RequestContext.get().getUserId(), ProjectPermission.PROJECT_MANAGE);
        }
        ProjectEntity project = projectRepository != null ? projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND, "Project not found: " + projectId)) : null;
        if (project != null && (memberId.equals("owner-" + project.getOwnerId()) || memberId.equals(project.getOwnerId()))) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Cannot modify project owner role: " + project.getOwnerId());
        }

        TeamMemberEntity member = teamMemberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TEAM_MEMBER_NOT_FOUND, "Team member not found: " + memberId));

        ProjectRbacRole rbacRole = cmd.getRbacRole() != null ? cmd.getRbacRole() : ProjectRbacRole.DEVELOPER;
        TeamRole teamRole;
        try {
            teamRole = TeamRole.valueOf(rbacRole.name());
        } catch (Exception e) {
            teamRole = TeamRole.CUSTOM;
        }

        member.setRoleType(teamRole);
        if (cmd.getRole() != null && !cmd.getRole().isBlank()) {
            member.setRole(cmd.getRole());
        }
        if (cmd.getResponsibilities() != null) {
            member.setResponsibilities(cmd.getResponsibilities());
        }
        teamMemberRepository.save(member);

        if (authAuditService != null) {
            authAuditService.record("RBAC_ROLE_ASSIGNED", RequestContext.get().getUserId(), null, null,
                    "Updated member [" + memberId + "] in project [" + projectId + "] to role [" + rbacRole.name() + "]");
        }

        Set<String> perms = rbacRole.getPermissions().stream().map(Enum::name).collect(Collectors.toSet());
        return new ProjectMemberView(
                member.getId(), member.getTeamId(), projectId, member.getAgentInstanceId(),
                member.getAgentInstanceId(), "", member.getRole(), rbacRole, perms, member.getCanDelegate(), LocalDateTime.now());
    }

    @Override
    @Transactional
    public void removeProjectMember(String projectId, String memberId) {
        if (ownershipVerifier != null) {
            ownershipVerifier.verifyProjectAccess(projectId, RequestContext.get().getUserId(), ProjectPermission.PROJECT_MANAGE);
        }
        ProjectEntity project = projectRepository != null ? projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND, "Project not found: " + projectId)) : null;
        if (project != null && (memberId.equals("owner-" + project.getOwnerId()) || memberId.equals(project.getOwnerId()))) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Cannot remove project owner: " + project.getOwnerId());
        }

        TeamMemberEntity member = teamMemberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TEAM_MEMBER_NOT_FOUND, "Team member not found: " + memberId));
        teamMemberRepository.delete(member);

        if (authAuditService != null) {
            authAuditService.record("RBAC_ROLE_ASSIGNED", RequestContext.get().getUserId(), null, null,
                    "Removed member [" + memberId + "] from project [" + projectId + "]");
        }
    }
}
