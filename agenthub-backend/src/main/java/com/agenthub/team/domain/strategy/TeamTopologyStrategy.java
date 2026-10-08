package com.agenthub.team.domain.strategy;

import com.agenthub.team.domain.model.NextSpeakerDecision;
import com.agenthub.team.domain.model.TeamCoordinationContext;
import com.agenthub.team.domain.model.TeamTopology;
import com.agenthub.team.infrastructure.entity.TeamEntity;
import com.agenthub.team.infrastructure.entity.TeamMemberEntity;

import java.util.List;

/**
 * Strategy interface defining how a team's topology determines the next actor/speaker.
 */
public interface TeamTopologyStrategy {

    TeamTopology getTopology();

    NextSpeakerDecision decideNextSpeaker(TeamEntity team, List<TeamMemberEntity> members, TeamCoordinationContext context);
}
