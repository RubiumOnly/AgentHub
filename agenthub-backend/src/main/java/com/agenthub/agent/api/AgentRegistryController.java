package com.agenthub.agent.api;

import com.agenthub.adapter.common.Result;
import com.agenthub.agent.application.AgentApplication;
import com.agenthub.agent.dto.AgentDefinitionView;
import com.agenthub.domain.agent.model.AgentExecutionRequest;
import com.agenthub.domain.agent.model.AgentExecutionResult;
import com.agenthub.agent.application.OfflineEvaluationApplication;
import com.agenthub.agent.domain.context.AssembledContext;
import com.agenthub.agent.domain.context.ContextAssemblyEngine;
import com.agenthub.agent.domain.definition.AgentToolDefinition;
import com.agenthub.agent.domain.evaluation.EvaluationReportView;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/agents")
public class AgentRegistryController {

    private final AgentApplication agentApplication;
    private final OfflineEvaluationApplication evaluationApplication;

    public AgentRegistryController(AgentApplication agentApplication,
                                   OfflineEvaluationApplication evaluationApplication) {
        this.agentApplication = agentApplication;
        this.evaluationApplication = evaluationApplication;
    }

    public static class ContextAssemblyRequest {
        public List<ContextAssemblyEngine.LayerInput> layers;
    }

    public static class EvaluationRunRequest {
        public String model;
    }

    @GetMapping
    public Result<List<AgentDefinitionView>> listDefinitions() {
        return Result.ok(agentApplication.listDefinitions());
    }

    @GetMapping("/{defKey}")
    public Result<AgentDefinitionView> getDefinitionByKey(@PathVariable("defKey") String defKey) {
        return Result.ok(agentApplication.getDefinitionByKey(defKey));
    }

    @GetMapping("/tools")
    public Result<List<AgentToolDefinition>> listTools() {
        return Result.ok(agentApplication.listTools());
    }

    @PostMapping("/context-assembly")
    public Result<AssembledContext> assembleContext(@RequestBody ContextAssemblyRequest req) {
        List<ContextAssemblyEngine.LayerInput> layers = req != null && req.layers != null ? req.layers : List.of();
        return Result.ok(agentApplication.assembleContext(layers));
    }

    @PostMapping("/evaluations/run")
    public Result<EvaluationReportView> runEvaluation(@RequestBody(required = false) EvaluationRunRequest req) {
        String model = req != null ? req.model : null;
        return Result.ok(evaluationApplication.runEvaluationSuite(model));
    }

    @GetMapping("/evaluations/latest")
    public Result<EvaluationReportView> getLatestEvaluation() {
        return Result.ok(evaluationApplication.getLatestReport());
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
