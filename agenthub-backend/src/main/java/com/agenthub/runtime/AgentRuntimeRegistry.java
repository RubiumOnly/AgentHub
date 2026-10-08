package com.agenthub.runtime;

import com.agenthub.domain.agent.model.AgentPlatformType;
import com.agenthub.runtime.port.AgentRuntime;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry and resolver for AgentRuntime implementations.
 */
@Component
public class AgentRuntimeRegistry {

    private final Map<String, AgentRuntime> runtimeMap = new ConcurrentHashMap<>();
    private final AgentRuntime defaultRuntime;

    public AgentRuntimeRegistry(Map<String, AgentRuntime> runtimes,
                                @Qualifier("mockAgentRuntime") AgentRuntime mockRuntime) {
        if (runtimes != null) {
            this.runtimeMap.putAll(runtimes);
        }
        this.defaultRuntime = mockRuntime;
    }

    public AgentRuntime getRuntime(String runtimeType) {
        if (runtimeType == null || runtimeType.isBlank()) {
            return defaultRuntime;
        }
        String key = runtimeType.toLowerCase().trim();
        for (Map.Entry<String, AgentRuntime> entry : runtimeMap.entrySet()) {
            if (entry.getKey().toLowerCase().contains(key) ||
                entry.getValue().describe().getRuntimeType().equalsIgnoreCase(key)) {
                return entry.getValue();
            }
        }
        // If type mentions provider, llm, or router, route to providerDrivenAgentRuntime
        if (key.contains("provider") || key.contains("router") || key.contains("llm") || key.contains("openai") || key.contains("deepseek")) {
            AgentRuntime prov = runtimeMap.get("providerDrivenAgentRuntime");
            if (prov != null) return prov;
        }
        return defaultRuntime;
    }

    public AgentRuntime getRuntime(AgentPlatformType platformType) {
        if (platformType == null) {
            return defaultRuntime;
        }
        switch (platformType) {
            case SPRING_AI_API:
                AgentRuntime providerRuntime = runtimeMap.get("providerDrivenAgentRuntime");
                if (providerRuntime != null) return providerRuntime;
                AgentRuntime api = runtimeMap.get("openAiCompatibleRuntime");
                return api != null ? api : defaultRuntime;
            case CLAUDE_CODE:
            case CODEX:
            case OPENCLAW:
            case HERMES:
            case OPENCODE:
                AgentRuntime cli = runtimeMap.get("cliAgentRuntime");
                return cli != null ? cli : defaultRuntime;
            default:
                return defaultRuntime;
        }
    }

    public AgentRuntime getDefaultRuntime() {
        return defaultRuntime;
    }

    public Collection<AgentRuntime> listRuntimes() {
        return runtimeMap.values();
    }
}
