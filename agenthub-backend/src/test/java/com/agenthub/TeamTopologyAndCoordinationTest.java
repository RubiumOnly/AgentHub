package com.agenthub;

import com.agenthub.team.application.TeamApplication;
import com.agenthub.team.domain.model.NextSpeakerDecision;
import com.agenthub.team.domain.model.TeamCoordinationContext;
import com.agenthub.team.domain.model.TeamRole;
import com.agenthub.team.domain.model.TeamTopology;
import com.agenthub.team.domain.strategy.HierarchicalTopologyCoordinator;
import com.agenthub.team.domain.strategy.PeerToPeerTopologyCoordinator;
import com.agenthub.team.domain.strategy.RoundRobinTopologyCoordinator;
import com.agenthub.team.dto.*;
import com.agenthub.team.infrastructure.entity.TeamEntity;
import com.agenthub.team.infrastructure.entity.TeamMemberEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class TeamTopologyAndCoordinationTest {

    @Autowired
    private TeamApplication teamApplication;

    @Autowired
    private HierarchicalTopologyCoordinator hierarchicalCoordinator;

    @Autowired
    private PeerToPeerTopologyCoordinator peerToPeerCoordinator;

    @Autowired
    private RoundRobinTopologyCoordinator roundRobinCoordinator;

    @Test
    @DisplayName("测试 Hierarchical 主从层级拓扑：Leader 主控拆解，Worker 执行后控制权严格回摆至 Leader")
    void shouldCoordinateHierarchicalTopologyCorrectly() {
        TeamEntity team = new TeamEntity("t-hier", "p-1", "Hierarchical Team", "Desc",
                TeamTopology.HIERARCHICAL, "agent-lead", 10, null);

        List<TeamMemberEntity> members = List.of(
                new TeamMemberEntity("m1", "t-hier", "agent-lead", "Orchestrator", TeamRole.ORCHESTRATOR, 0, "Lead", null, true),
                new TeamMemberEntity("m2", "t-hier", "agent-arch", "BackendArchitect", TeamRole.ARCHITECT, 1, "Arch", null, true),
                new TeamMemberEntity("m3", "t-hier", "agent-coder", "FrontendEngineer", TeamRole.CODER, 2, "Coder", null, true)
        );

        // Turn 0 (Initial turn): Leader must speak first
        TeamCoordinationContext ctx0 = new TeamCoordinationContext("c-1", null, 0, 10, null, null, "Build login", null);
        NextSpeakerDecision dec0 = hierarchicalCoordinator.decideNextSpeaker(team, members, ctx0);
        assertThat(dec0.getNextSpeakerId()).isEqualTo("agent-lead");
        assertThat(dec0.getAction()).isEqualTo(NextSpeakerDecision.Action.CONTINUE);

        // Turn 0 with user initiator: Leader receives task from initiator
        TeamCoordinationContext ctxUser = new TeamCoordinationContext("c-1", "user-1", 0, 10, null, null, "Build login", null);
        NextSpeakerDecision decUser = hierarchicalCoordinator.decideNextSpeaker(team, members, ctxUser);
        assertThat(decUser.getNextSpeakerId()).isEqualTo("agent-lead");
        assertThat(decUser.getReason()).contains("Leader received task from initiator");

        // Turn 1: Leader delegates to Architect
        TeamCoordinationContext ctx1 = new TeamCoordinationContext("c-1", "agent-lead", 1, 10, "@BackendArchitect", null, "Build login", null);
        NextSpeakerDecision dec1 = hierarchicalCoordinator.decideNextSpeaker(team, members, ctx1);
        assertThat(dec1.getNextSpeakerId()).isEqualTo("agent-arch");
        assertThat(dec1.getAction()).isEqualTo(NextSpeakerDecision.Action.HANDOFF);

        // Turn 2: Architect finished -> control strictly returns to Leader!
        TeamCoordinationContext ctx2 = new TeamCoordinationContext("c-1", "agent-arch", 2, 10, null, null, "Build login", null);
        NextSpeakerDecision dec2 = hierarchicalCoordinator.decideNextSpeaker(team, members, ctx2);
        assertThat(dec2.getNextSpeakerId()).isEqualTo("agent-lead");
        assertThat(dec2.getReason()).contains("Worker finished; control returns to Leader");

        // Turn 9: Near max turn threshold -> Leader concludes with summary
        TeamCoordinationContext ctxLast = new TeamCoordinationContext("c-1", "agent-coder", 9, 10, null, null, "Build login", null);
        NextSpeakerDecision decLast = hierarchicalCoordinator.decideNextSpeaker(team, members, ctxLast);
        assertThat(decLast.getNextSpeakerId()).isEqualTo("agent-lead");
        assertThat(decLast.getAction()).isEqualTo(NextSpeakerDecision.Action.SUMMARIZE);
    }

    @Test
    @DisplayName("测试 Peer-to-Peer 对等协商拓扑：去中心化平级协商，直接委托交接与轮转")
    void shouldCoordinatePeerToPeerTopologyCorrectly() {
        TeamEntity team = new TeamEntity("t-p2p", "p-1", "P2P Team", "Desc",
                TeamTopology.PEER_TO_PEER, null, 8, null);

        List<TeamMemberEntity> members = List.of(
                new TeamMemberEntity("m1", "t-p2p", "peer-a", "Architect", TeamRole.ARCHITECT, 0, "Arch", null, true),
                new TeamMemberEntity("m2", "t-p2p", "peer-b", "Coder", TeamRole.CODER, 1, "Code", null, true),
                new TeamMemberEntity("m3", "t-p2p", "peer-c", "Reviewer", TeamRole.REVIEWER, 2, "Review", null, true)
        );

        // Turn 0: First peer starts
        TeamCoordinationContext ctx0 = new TeamCoordinationContext("c-2", null, 0, 8, null, null, "Design cache", null);
        NextSpeakerDecision dec0 = peerToPeerCoordinator.decideNextSpeaker(team, members, ctx0);
        assertThat(dec0.getNextSpeakerId()).isEqualTo("peer-a");

        // Turn 1: Peer A directly hands off to Peer C
        TeamCoordinationContext ctx1 = new TeamCoordinationContext("c-2", "peer-a", 1, 8, null, "peer-c", "Design cache", null);
        NextSpeakerDecision dec1 = peerToPeerCoordinator.decideNextSpeaker(team, members, ctx1);
        assertThat(dec1.getNextSpeakerId()).isEqualTo("peer-c");
        assertThat(dec1.getAction()).isEqualTo(NextSpeakerDecision.Action.HANDOFF);

        // Turn 7: Near max turns -> consensus summary
        TeamCoordinationContext ctxLast = new TeamCoordinationContext("c-2", "peer-b", 7, 8, null, null, "Design cache", null);
        NextSpeakerDecision decLast = peerToPeerCoordinator.decideNextSpeaker(team, members, ctxLast);
        assertThat(decLast.getAction()).isEqualTo(NextSpeakerDecision.Action.SUMMARIZE);
    }

    @Test
    @DisplayName("测试 Round-Robin 轮询协作拓扑：严格按 sortOrder 顺时针轮转与环形闭环")
    void shouldCoordinateRoundRobinTopologyCorrectly() {
        TeamEntity team = new TeamEntity("t-rr", "p-1", "RoundRobin Team", "Desc",
                TeamTopology.ROUND_ROBIN, null, 10, null);

        List<TeamMemberEntity> members = List.of(
                new TeamMemberEntity("m1", "t-rr", "agent-0", "Dev1", TeamRole.CODER, 0, null, null, true),
                new TeamMemberEntity("m2", "t-rr", "agent-1", "Dev2", TeamRole.CODER, 1, null, null, true),
                new TeamMemberEntity("m3", "t-rr", "agent-2", "Dev3", TeamRole.CODER, 2, null, null, true)
        );

        // Turn 0: Starts at index 0
        NextSpeakerDecision dec0 = roundRobinCoordinator.decideNextSpeaker(team, members,
                new TeamCoordinationContext("c-3", null, 0, 10, null, null, "T", null));
        assertThat(dec0.getNextSpeakerId()).isEqualTo("agent-0");

        // Turn 1: Next is index 1
        NextSpeakerDecision dec1 = roundRobinCoordinator.decideNextSpeaker(team, members,
                new TeamCoordinationContext("c-3", "agent-0", 1, 10, null, null, "T", null));
        assertThat(dec1.getNextSpeakerId()).isEqualTo("agent-1");

        // Turn 2: Next is index 2
        NextSpeakerDecision dec2 = roundRobinCoordinator.decideNextSpeaker(team, members,
                new TeamCoordinationContext("c-3", "agent-1", 2, 10, null, null, "T", null));
        assertThat(dec2.getNextSpeakerId()).isEqualTo("agent-2");

        // Turn 3: Wraps around back to index 0!
        NextSpeakerDecision dec3 = roundRobinCoordinator.decideNextSpeaker(team, members,
                new TeamCoordinationContext("c-3", "agent-2", 3, 10, null, null, "T", null));
        assertThat(dec3.getNextSpeakerId()).isEqualTo("agent-0");
    }

    @Test
    @DisplayName("测试 TeamApplication 团队创建、成员管理与多态协同决策完整生命周期")
    void shouldManageTeamLifecycleViaApplicationService() {
        CreateTeamCommand createCmd = new CreateTeamCommand(
                "proj-default",
                "FullStackSquad",
                "Dedicated autonomous delivery squad",
                TeamTopology.HIERARCHICAL,
                "orch-inst-1",
                12,
                "{}",
                List.of(
                        new AddTeamMemberCommand("orch-inst-1", "Orchestrator", TeamRole.ORCHESTRATOR, 0, "Lead & decompose", null, true),
                        new AddTeamMemberCommand("back-inst-1", "BackendArchitect", TeamRole.ARCHITECT, 1, "Write Java & Spring", null, true)
                )
        );

        TeamView created = teamApplication.createTeam(createCmd);
        assertThat(created.getId()).startsWith("team-");
        assertThat(created.getName()).isEqualTo("FullStackSquad");
        assertThat(created.getTopology()).isEqualTo(TeamTopology.HIERARCHICAL);
        assertThat(created.getMembers()).hasSize(2);

        // Add a third member (Reviewer)
        TeamMemberView addedMember = teamApplication.addTeamMember(created.getId(), new AddTeamMemberCommand(
                "qa-inst-1", "QAAuditor", TeamRole.REVIEWER, 2, "Review & Audit", null, false
        ));
        assertThat(addedMember.getId()).startsWith("tm-");
        assertThat(addedMember.getRoleType()).isEqualTo(TeamRole.REVIEWER);

        TeamView refreshed = teamApplication.getTeamById(created.getId());
        assertThat(refreshed.getMembers()).hasSize(3);

        // Test coordinate next turn
        TeamCoordinateCommand coordCmd = new TeamCoordinateCommand(
                "conv-test-1",
                "orch-inst-1",
                1,
                null,
                "back-inst-1",
                "Implement auth",
                null
        );
        TeamCoordinateResultView coordRes = teamApplication.coordinateNextTurn(created.getId(), coordCmd);
        assertThat(coordRes.getNextSpeakerId()).isEqualTo("back-inst-1");
        assertThat(coordRes.getAction()).isEqualTo(NextSpeakerDecision.Action.HANDOFF);

        // Remove the member
        teamApplication.removeTeamMember(created.getId(), addedMember.getId());
        TeamView afterRemove = teamApplication.getTeamById(created.getId());
        assertThat(afterRemove.getMembers()).hasSize(2);
    }
}
