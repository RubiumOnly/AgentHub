package com.agenthub.team.domain.strategy;

import com.agenthub.team.domain.model.TeamTopology;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class TeamTopologyStrategyFactory {

    private final Map<TeamTopology, TeamTopologyStrategy> strategyMap = new EnumMap<>(TeamTopology.class);

    public TeamTopologyStrategyFactory(List<TeamTopologyStrategy> strategies) {
        for (TeamTopologyStrategy strategy : strategies) {
            strategyMap.put(strategy.getTopology(), strategy);
        }
    }

    public TeamTopologyStrategy getStrategy(TeamTopology topology) {
        TeamTopologyStrategy strategy = strategyMap.get(topology != null ? topology : TeamTopology.HIERARCHICAL);
        if (strategy == null) {
            throw new IllegalArgumentException("Unsupported team topology: " + topology);
        }
        return strategy;
    }
}
