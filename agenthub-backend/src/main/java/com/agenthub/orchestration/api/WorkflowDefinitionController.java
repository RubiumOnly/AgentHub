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
import com.agenthub.orchestration.application.OrchestratorPlanApplication;
import com.agenthub.orchestration.domain.template.WorkflowTemplateView;
import com.agenthub.orchestration.dto.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/workflows")
public class WorkflowDefinitionController {

    private final WorkflowOrchestrationApplication orchestrationApplication;
    private final OrchestratorPlanApplication planApplication;

    public WorkflowDefinitionController(WorkflowOrchestrationApplication orchestrationApplication,
                                        OrchestratorPlanApplication planApplication) {
        this.orchestrationApplication = orchestrationApplication;
        this.planApplication = planApplication;
    }

    public static class InstantiateTemplateRequest {
        public String name;
        public String description;
    }

    public static class RejectPlanRequest {
        public String reason;
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

    @GetMapping("/templates")
    public Result<List<WorkflowTemplateView>> listTemplates() {
        return Result.ok(orchestrationApplication.listTemplates());
    }

    @GetMapping("/templates/{id}")
    public Result<WorkflowTemplateView> getTemplate(@PathVariable("id") String id) {
        return Result.ok(orchestrationApplication.getTemplate(id));
    }

    @PostMapping("/templates/{id}/instantiate")
    public Result<WorkflowDefinitionView> instantiateTemplate(
            @PathVariable("id") String id,
            @RequestBody(required = false) InstantiateTemplateRequest req) {
        String name = req != null ? req.name : null;
        String desc = req != null ? req.description : null;
        return Result.ok(orchestrationApplication.instantiateTemplate(id, name, desc));
    }

    @PostMapping("/orchestrator/plans")
    public Result<OrchestrationPlanView> generatePlan(@RequestBody GeneratePlanCommand cmd) {
        String currentUserId = RequestContext.get().getUserId();
        if (currentUserId == null || currentUserId.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Authentication required to generate orchestrator plan");
        }
        return Result.ok(planApplication.generatePlan(cmd));
    }

    @GetMapping("/orchestrator/plans/{planId}")
    public Result<OrchestrationPlanView> getPlan(@PathVariable("planId") String planId) {
        return Result.ok(planApplication.getPlan(planId));
    }

    @PostMapping("/orchestrator/plans/{planId}/confirm")
    public Result<OrchestrationPlanView> confirmPlan(
            @PathVariable("planId") String planId,
            @RequestBody(required = false) ConfirmPlanCommand cmd) {
        String currentUserId = RequestContext.get().getUserId();
        if (currentUserId == null || currentUserId.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Authentication required to confirm orchestrator plan");
        }
        ConfirmPlanCommand effectiveCmd = cmd != null ? cmd : new ConfirmPlanCommand(true, null, "Confirmed by user");
        return Result.ok(planApplication.confirmPlan(planId, effectiveCmd));
    }

    @PostMapping("/orchestrator/plans/{planId}/reject")
    public Result<OrchestrationPlanView> rejectPlan(
            @PathVariable("planId") String planId,
            @RequestBody(required = false) RejectPlanRequest req) {
        String currentUserId = RequestContext.get().getUserId();
        if (currentUserId == null || currentUserId.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Authentication required to reject orchestrator plan");
        }
        String reason = req != null ? req.reason : "Rejected by user";
        return Result.ok(planApplication.rejectPlan(planId, reason));
    }

    @GetMapping("/projects/{projectId}/plans")
    public Result<List<OrchestrationPlanView>> listPlansByProject(@PathVariable("projectId") String projectId) {
        return Result.ok(planApplication.listPlansByProject(projectId));
    }
}
