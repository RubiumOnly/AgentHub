package com.agenthub.adapter.web;

import com.agenthub.adapter.common.Result;
import com.agenthub.agent.application.AgentApplication;
import com.agenthub.agent.dto.AgentDefinitionView;
import com.agenthub.domain.agent.model.AgentExecutionRequest;
import com.agenthub.domain.agent.model.AgentExecutionResult;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/agents")
public class AgentRegistryController {

    private final AgentApplication agentApplication;

    public AgentRegistryController(AgentApplication agentApplication) {
        this.agentApplication = agentApplication;
    }

    @GetMapping
    public Result<List<AgentDefinitionView>> listDefinitions() {
        return Result.ok(agentApplication.listDefinitions());
    }

    @GetMapping("/platforms")
    public Result<List<Map<String, Object>>> getSupportedPlatforms() {
        return Result.ok(agentApplication.getSupportedPlatforms());
    }

    @PostMapping("/execute")
    public Result<AgentExecutionResult> execute(@RequestBody AgentExecutionRequest request) {
        return Result.ok(agentApplication.execute(request));
    }
}
