package com.agenthub.domain.agent.service;

import com.agenthub.domain.agent.model.AgentPlatformType;
import com.agenthub.domain.agent.spi.UnifiedAgentAdapter;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class AgentAdapterFactory {

    private final Map<AgentPlatformType, UnifiedAgentAdapter> adapterMap = new EnumMap<>(AgentPlatformType.class);

    public AgentAdapterFactory(List<UnifiedAgentAdapter> adapters) {
        for (UnifiedAgentAdapter adapter : adapters) {
            adapterMap.put(adapter.getSupportedPlatform(), adapter);
        }
    }

    public UnifiedAgentAdapter getAdapter(AgentPlatformType platformType) {
        return Optional.ofNullable(adapterMap.get(platformType))
                .orElseGet(() -> adapterMap.get(AgentPlatformType.SPRING_AI_API));
    }

    public Map<AgentPlatformType, Boolean> checkAllPlatformAvailability() {
        Map<AgentPlatformType, Boolean> status = new EnumMap<>(AgentPlatformType.class);
        for (AgentPlatformType type : AgentPlatformType.values()) {
            UnifiedAgentAdapter adapter = adapterMap.get(type);
            status.put(type, adapter != null && adapter.isAvailable());
        }
        return status;
    }
}
