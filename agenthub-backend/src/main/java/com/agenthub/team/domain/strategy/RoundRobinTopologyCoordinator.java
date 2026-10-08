package com.agenthub.team.domain.strategy;

import com.agenthub.team.domain.model.NextSpeakerDecision;
import com.agenthub.team.domain.model.TeamCoordinationContext;
import com.agenthub.team.domain.model.TeamTopology;
import com.agenthub.team.infrastructure.entity.TeamEntity;
import com.agenthub.team.infrastructure.entity.TeamMemberEntity;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class RoundRobinTopologyCoordinator implements TeamTopologyStrategy {

    @Override
    public TeamTopology getTopology() {
        return TeamTopology.ROUND_ROBIN;
    }

    @Override
    public NextSpeakerDecision decideNextSpeaker(TeamEntity team, List<TeamMemberEntity> members, TeamCoordinationContext context) {
        if (members == null || members.isEmpty()) {
            return NextSpeakerDecision.terminate("No members registered in team");
        }

        List<TeamMemberEntity> sorted = members.stream()
                .sorted(Comparator.comparingInt(TeamMemberEntity::getSortOrder))
                .toList();

        // Check turn limit
        if (context.getCurrentTurn() >= context.getMaxTurns() - 1) {
            TeamMemberEntity last = sorted.get(sorted.size() - 1);
            return NextSpeakerDecision.summarize(
                    last.getAgentInstanceId(),
                    last.getRole(),
                    "Round-Robin topology: Final rotation reached; summarizing team results"
            );
        }

        String currentSpeaker = context.getCurrentSpeakerId();
        if (currentSpeaker == null || currentSpeaker.isBlank()) {
            TeamMemberEntity first = sorted.get(0);
            return NextSpeakerDecision.continueWith(
                    first.getAgentInstanceId(),
                    first.getRole(),
                    "Round-Robin topology: Starting round rotation at slot 0: " + first.getRole(),
                    first.getSystemPromptOverride()
            );
        }

        int currentIndex = -1;
        for (int i = 0; i < sorted.size(); i++) {
            if (sorted.get(i).getAgentInstanceId().equalsIgnoreCase(currentSpeaker) ||
                sorted.get(i).getRole().equalsIgnoreCase(currentSpeaker)) {
                currentIndex = i;
                break;
            }
        }

        int nextIndex = (currentIndex + 1) % sorted.size();
        TeamMemberEntity nextMember = sorted.get(nextIndex);

        return NextSpeakerDecision.continueWith(
                nextMember.getAgentInstanceId(),
                nextMember.getRole(),
                "Round-Robin topology: Sequential rotation to slot " + nextIndex + ": " + nextMember.getRole(),
                nextMember.getSystemPromptOverride()
        );
    }
}
