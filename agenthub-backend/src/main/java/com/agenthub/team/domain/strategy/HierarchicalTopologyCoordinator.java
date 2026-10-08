package com.agenthub.team.domain.strategy;

import com.agenthub.team.domain.model.NextSpeakerDecision;
import com.agenthub.team.domain.model.TeamCoordinationContext;
import com.agenthub.team.domain.model.TeamRole;
import com.agenthub.team.domain.model.TeamTopology;
import com.agenthub.team.infrastructure.entity.TeamEntity;
import com.agenthub.team.infrastructure.entity.TeamMemberEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class HierarchicalTopologyCoordinator implements TeamTopologyStrategy {

    @Override
    public TeamTopology getTopology() {
        return TeamTopology.HIERARCHICAL;
    }

    @Override
    public NextSpeakerDecision decideNextSpeaker(TeamEntity team, List<TeamMemberEntity> members, TeamCoordinationContext context) {
        if (members == null || members.isEmpty()) {
            return NextSpeakerDecision.terminate("No members registered in team");
        }

        TeamMemberEntity leader = findLeader(team, members);
        String currentSpeaker = context.getCurrentSpeakerId();

        // 1. Initial turn: Leader initiates coordination
        if (currentSpeaker == null || currentSpeaker.isBlank()) {
            return NextSpeakerDecision.continueWith(
                    leader.getAgentInstanceId(),
                    leader.getRole(),
                    "Hierarchical topology: Leader initiates coordination and task decomposition",
                    leader.getSystemPromptOverride() != null ? leader.getSystemPromptOverride() : "Please decompose the task and assign responsibilities."
            );
        }

        boolean currentIsLeader = isSameAgent(leader, currentSpeaker);

        // 2. If current speaker is a worker or initiator, control strictly returns to Leader
        if (!currentIsLeader) {
            // Check if worker suggested a completion or final review
            if (context.getCurrentTurn() >= context.getMaxTurns() - 1) {
                return NextSpeakerDecision.summarize(
                        leader.getAgentInstanceId(),
                        leader.getRole(),
                        "Hierarchical topology: Max turn threshold approached; Leader synthesizes summary"
                );
            }
            boolean isRegisteredWorker = members.stream().anyMatch(m -> isSameAgent(m, currentSpeaker));
            String reason = isRegisteredWorker
                    ? "Hierarchical topology: Worker finished; control returns to Leader for evaluation"
                    : "Hierarchical topology: Leader received task from initiator and coordinates execution";

            return NextSpeakerDecision.continueWith(
                    leader.getAgentInstanceId(),
                    leader.getRole(),
                    reason,
                    "Evaluate the previous output and delegate to the next worker or conclude."
            );
        }

        // 3. Current speaker is Leader: Leader delegates to next worker
        // Check if handoff or mention specified
        if (context.getHandoffTarget() != null && !context.getHandoffTarget().isBlank()) {
            Optional<TeamMemberEntity> target = findMember(members, context.getHandoffTarget());
            if (target.isPresent() && !isSameAgent(leader, target.get().getAgentInstanceId())) {
                TeamMemberEntity worker = target.get();
                return NextSpeakerDecision.handoffTo(
                        worker.getAgentInstanceId(),
                        worker.getRole(),
                        "Hierarchical topology: Leader delegated directly to specified target: " + worker.getRole(),
                        worker.getSystemPromptOverride()
                );
            }
        }

        if (context.getLastMention() != null && !context.getLastMention().isBlank()) {
            Optional<TeamMemberEntity> mentioned = findMember(members, context.getLastMention());
            if (mentioned.isPresent() && !isSameAgent(leader, mentioned.get().getAgentInstanceId())) {
                TeamMemberEntity worker = mentioned.get();
                return NextSpeakerDecision.handoffTo(
                        worker.getAgentInstanceId(),
                        worker.getRole(),
                        "Hierarchical topology: Leader dispatched to mentioned worker: " + worker.getRole(),
                        worker.getSystemPromptOverride()
                );
            }
        }

        // Fallback: Pick next worker by role sequence: ARCHITECT -> CODER -> REVIEWER / TESTER
        List<TeamMemberEntity> workers = members.stream()
                .filter(m -> !isSameAgent(leader, m.getAgentInstanceId()))
                .toList();

        if (workers.isEmpty()) {
            return NextSpeakerDecision.summarize(leader.getAgentInstanceId(), leader.getRole(), "No worker members available; Leader concludes");
        }

        // Cycle through workers based on turn
        int workerIndex = (context.getCurrentTurn() / 2) % workers.size();
        TeamMemberEntity selectedWorker = workers.get(workerIndex);

        return NextSpeakerDecision.handoffTo(
                selectedWorker.getAgentInstanceId(),
                selectedWorker.getRole(),
                "Hierarchical topology: Leader dispatched step to: " + selectedWorker.getRole(),
                selectedWorker.getSystemPromptOverride()
        );
    }

    private TeamMemberEntity findLeader(TeamEntity team, List<TeamMemberEntity> members) {
        if (team.getLeaderAgentId() != null) {
            for (TeamMemberEntity m : members) {
                if (m.getAgentInstanceId().equalsIgnoreCase(team.getLeaderAgentId()) ||
                    m.getRole().equalsIgnoreCase(team.getLeaderAgentId())) {
                    return m;
                }
            }
        }
        for (TeamMemberEntity m : members) {
            if (m.getRoleType() == TeamRole.ORCHESTRATOR) {
                return m;
            }
        }
        return members.get(0);
    }

    private Optional<TeamMemberEntity> findMember(List<TeamMemberEntity> members, String identifier) {
        for (TeamMemberEntity m : members) {
            if (m.getAgentInstanceId().equalsIgnoreCase(identifier) ||
                m.getRole().equalsIgnoreCase(identifier) ||
                (m.getRoleType() != null && m.getRoleType().name().equalsIgnoreCase(identifier))) {
                return Optional.of(m);
            }
        }
        return Optional.empty();
    }

    private boolean isSameAgent(TeamMemberEntity member, String identifier) {
        return member.getAgentInstanceId().equalsIgnoreCase(identifier) ||
               member.getRole().equalsIgnoreCase(identifier);
    }
}
