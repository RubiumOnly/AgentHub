package com.agenthub.team.domain.strategy;

import com.agenthub.team.domain.model.NextSpeakerDecision;
import com.agenthub.team.domain.model.TeamCoordinationContext;
import com.agenthub.team.domain.model.TeamTopology;
import com.agenthub.team.infrastructure.entity.TeamEntity;
import com.agenthub.team.infrastructure.entity.TeamMemberEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class PeerToPeerTopologyCoordinator implements TeamTopologyStrategy {

    @Override
    public TeamTopology getTopology() {
        return TeamTopology.PEER_TO_PEER;
    }

    @Override
    public NextSpeakerDecision decideNextSpeaker(TeamEntity team, List<TeamMemberEntity> members, TeamCoordinationContext context) {
        if (members == null || members.isEmpty()) {
            return NextSpeakerDecision.terminate("No members registered in team");
        }

        String currentSpeaker = context.getCurrentSpeakerId();

        // 1. Initial turn: Pick first member or Architect
        if (currentSpeaker == null || currentSpeaker.isBlank()) {
            TeamMemberEntity first = members.get(0);
            return NextSpeakerDecision.continueWith(
                    first.getAgentInstanceId(),
                    first.getRole(),
                    "Peer-to-Peer topology: Initial peer starts negotiation",
                    first.getSystemPromptOverride() != null ? first.getSystemPromptOverride() : "Please introduce the proposal to your peers."
            );
        }

        // 2. Check if close to turn limit -> request consensus summary
        if (context.getCurrentTurn() >= context.getMaxTurns() - 1) {
            TeamMemberEntity summarizer = members.get(context.getCurrentTurn() % members.size());
            return NextSpeakerDecision.summarize(
                    summarizer.getAgentInstanceId(),
                    summarizer.getRole(),
                    "Peer-to-Peer topology: Near turn limit; consensus summary requested from peer: " + summarizer.getRole()
            );
        }

        // 3. Direct handoff target specified by previous peer
        if (context.getHandoffTarget() != null && !context.getHandoffTarget().isBlank()) {
            Optional<TeamMemberEntity> target = findMember(members, context.getHandoffTarget());
            if (target.isPresent() && !isSameAgent(target.get(), currentSpeaker)) {
                TeamMemberEntity peer = target.get();
                return NextSpeakerDecision.handoffTo(
                        peer.getAgentInstanceId(),
                        peer.getRole(),
                        "Peer-to-Peer topology: Direct peer handoff to: " + peer.getRole(),
                        peer.getSystemPromptOverride()
                );
            }
        }

        // 4. Mentioned peer
        if (context.getLastMention() != null && !context.getLastMention().isBlank()) {
            Optional<TeamMemberEntity> mentioned = findMember(members, context.getLastMention());
            if (mentioned.isPresent() && !isSameAgent(mentioned.get(), currentSpeaker)) {
                TeamMemberEntity peer = mentioned.get();
                return NextSpeakerDecision.handoffTo(
                        peer.getAgentInstanceId(),
                        peer.getRole(),
                        "Peer-to-Peer topology: Responding to peer mention of: " + peer.getRole(),
                        peer.getSystemPromptOverride()
                );
            }
        }

        // 5. Pick next peer different from current speaker
        int currentIndex = -1;
        for (int i = 0; i < members.size(); i++) {
            if (isSameAgent(members.get(i), currentSpeaker)) {
                currentIndex = i;
                break;
            }
        }

        int nextIndex = (currentIndex + 1) % members.size();
        TeamMemberEntity nextPeer = members.get(nextIndex);

        return NextSpeakerDecision.continueWith(
                nextPeer.getAgentInstanceId(),
                nextPeer.getRole(),
                "Peer-to-Peer topology: Negotiation passed to next peer: " + nextPeer.getRole(),
                nextPeer.getSystemPromptOverride()
        );
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
