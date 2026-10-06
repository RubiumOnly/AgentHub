package com.agenthub.adapter.web;

import com.agenthub.adapter.common.Result;
import com.agenthub.domain.agent.model.AgentExecutionRequest;
import com.agenthub.domain.agent.model.AgentExecutionResult;
import com.agenthub.domain.agent.model.AgentPlatformType;
import com.agenthub.domain.agent.service.AgentAdapterFactory;
import com.agenthub.domain.agent.spi.UnifiedAgentAdapter;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/agents")
public class AgentRegistryController {

    private final AgentAdapterFactory adapterFactory;

    public AgentRegistryController(AgentAdapterFactory adapterFactory) {
        this.adapterFactory = adapterFactory;
    }

    @GetMapping("/platforms")
    public Result<List<Map<String, Object>>> getSupportedPlatforms() {
        Map<AgentPlatformType, Boolean> availability = adapterFactory.checkAllPlatformAvailability();
        List<Map<String, Object>> result = availability.entrySet().stream()
                .map(entry -> {
                    UnifiedAgentAdapter adapter = adapterFactory.getAdapter(entry.getKey());
                    return Map.<String, Object>of(
                            "code", entry.getKey().getCode(),
                            "name", entry.getKey().getDescription(),
                            "available", entry.getValue(),
                            "version", adapter != null ? adapter.checkVersion() : "N/A"
                    );
                })
                .collect(Collectors.toList());
        return Result.ok(result);
    }

    @PostMapping("/execute")
    public Result<AgentExecutionResult> execute(@RequestBody AgentExecutionRequest request) {
        UnifiedAgentAdapter adapter = adapterFactory.getAdapter(request.getPlatformType());
        AgentExecutionResult executionResult = adapter.execute(request);
        return Result.ok(executionResult);
    }
}
