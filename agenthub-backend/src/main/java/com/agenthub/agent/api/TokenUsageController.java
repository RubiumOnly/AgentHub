package com.agenthub.agent.api;

import com.agenthub.adapter.common.Result;
import com.agenthub.agent.application.TokenUsageApplication;
import com.agenthub.agent.dto.TokenSummaryView;
import com.agenthub.agent.dto.TokenUsageAuditView;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/token-usages")
public class TokenUsageController {

    private final TokenUsageApplication tokenUsageApplication;

    public TokenUsageController(TokenUsageApplication tokenUsageApplication) {
        this.tokenUsageApplication = tokenUsageApplication;
    }

    @GetMapping("/runs/{runId}")
    public Result<List<TokenUsageAuditView>> getRunTokenUsages(@PathVariable String runId) {
        return Result.ok(tokenUsageApplication.getRunTokenUsages(runId));
    }

    @GetMapping("/steps/{stepRunId}")
    public Result<List<TokenUsageAuditView>> getStepTokenUsages(@PathVariable String stepRunId) {
        return Result.ok(tokenUsageApplication.getStepTokenUsages(stepRunId));
    }

    @GetMapping("/summary")
    public Result<TokenSummaryView> getSummary() {
        return Result.ok(tokenUsageApplication.getSummary());
    }

    @GetMapping("/governance")
    public Result<com.agenthub.agent.dto.TokenGovernanceView> getGovernance() {
        return Result.ok(tokenUsageApplication.getGovernance());
    }

    @PostMapping("/budget/check")
    public Result<com.agenthub.agent.dto.BudgetCheckResultView> checkBudget(@RequestBody(required = false) com.agenthub.agent.dto.BudgetCheckCommand cmd) {
        return Result.ok(tokenUsageApplication.checkBudget(cmd));
    }
}
