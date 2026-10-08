package com.agenthub.orchestration.api;

import com.agenthub.adapter.common.Result;
import com.agenthub.orchestration.application.WorkflowOrchestrationApplication;
import com.agenthub.orchestration.domain.dsl.WorkflowDsl;
import com.agenthub.orchestration.dto.CreateWorkflowDefinitionCommand;
import com.agenthub.orchestration.dto.ValidateDslResultView;
import com.agenthub.orchestration.dto.WorkflowDefinitionView;
import com.agenthub.shared.context.RequestContext;
import com.agenthub.shared.exception.BusinessException;
import com.agenthub.shared.exception.ErrorCode;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/workflows")
public class WorkflowDefinitionController {

    private final WorkflowOrchestrationApplication orchestrationApplication;

    public WorkflowDefinitionController(WorkflowOrchestrationApplication orchestrationApplication) {
        this.orchestrationApplication = orchestrationApplication;
    }

    @PostMapping("/definitions")
    public Result<WorkflowDefinitionView> createDefinition(@RequestBody CreateWorkflowDefinitionCommand cmd) {
        String currentUserId = RequestContext.get().getUserId();
        if (currentUserId == null || currentUserId.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Authentication required to create workflow definition");
        }
        WorkflowDefinitionView view = orchestrationApplication.createDefinition(cmd);
        return Result.ok(view);
    }

    @GetMapping("/definitions/{id}")
    public Result<WorkflowDefinitionView> getDefinition(@PathVariable("id") String id) {
        WorkflowDefinitionView view = orchestrationApplication.getDefinitionById(id);
        return Result.ok(view);
    }

    @GetMapping("/definitions")
    public Result<List<WorkflowDefinitionView>> listDefinitions() {
        List<WorkflowDefinitionView> views = orchestrationApplication.listDefinitions();
        return Result.ok(views);
    }

    @PostMapping("/definitions/validate")
    public Result<ValidateDslResultView> validateDsl(@RequestBody WorkflowDsl dsl) {
        ValidateDslResultView result = orchestrationApplication.validateDsl(dsl);
        return Result.ok(result);
    }
}
