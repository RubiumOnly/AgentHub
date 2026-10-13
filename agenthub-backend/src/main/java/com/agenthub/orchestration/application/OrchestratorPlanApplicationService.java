package com.agenthub.orchestration.application;

import com.agenthub.agent.domain.definition.AgentToolDefinition;
import com.agenthub.agent.domain.definition.ToolRegistry;
import com.agenthub.identity.infrastructure.security.AuthAuditService;
import com.agenthub.orchestration.domain.dsl.WorkflowDsl;
import com.agenthub.orchestration.domain.dsl.WorkflowNodeDsl;
import com.agenthub.orchestration.domain.plan.*;
import com.agenthub.orchestration.domain.template.WorkflowTemplateRegistry;
import com.agenthub.orchestration.domain.template.WorkflowTemplateView;
import com.agenthub.orchestration.dto.ConfirmPlanCommand;
import com.agenthub.orchestration.dto.GeneratePlanCommand;
import com.agenthub.orchestration.dto.OrchestrationPlanView;
import com.agenthub.shared.context.RequestContext;
import com.agenthub.shared.exception.BusinessException;
import com.agenthub.shared.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OrchestratorPlanApplicationService implements OrchestratorPlanApplication {

    private static final Logger log = LoggerFactory.getLogger(OrchestratorPlanApplicationService.class);

    private final Map<String, OrchestrationPlan> planStore = new ConcurrentHashMap<>();
    private final ToolRegistry toolRegistry;
    private final WorkflowTemplateRegistry templateRegistry;
    private final AuthAuditService auditService;

    public OrchestratorPlanApplicationService(ToolRegistry toolRegistry,
                                              WorkflowTemplateRegistry templateRegistry,
                                              AuthAuditService auditService) {
        this.toolRegistry = toolRegistry;
        this.templateRegistry = templateRegistry;
        this.auditService = auditService;
    }

    @Override
    public OrchestrationPlanView generatePlan(GeneratePlanCommand cmd) {
        if (cmd == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "GeneratePlanCommand must not be null");
        }
        if (cmd.getProjectId() == null || cmd.getProjectId().isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "projectId is required for Orchestrator plan");
        }
        if (cmd.getWorkspaceId() == null || cmd.getWorkspaceId().isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "workspaceId is required for Orchestrator plan");
        }
        if (cmd.getRequirement() == null || cmd.getRequirement().isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Requirement prompt must not be blank");
        }

        String templateKey = cmd.getTemplateType() != null && !cmd.getTemplateType().isBlank()
                ? cmd.getTemplateType().trim()
                : "BUG_FIX_WITH_TESTS";

        String templateId = templateKey.toLowerCase(Locale.ROOT).startsWith("tpl-")
                ? templateKey
                : "tpl-" + templateKey.toLowerCase(Locale.ROOT).replace('_', '-');

        WorkflowTemplateView templateView = templateRegistry.getTemplate(templateId)
                .orElseGet(() -> templateRegistry.getTemplate("tpl-bug-fix-with-tests")
                        .orElseThrow(() -> new BusinessException(ErrorCode.WORKFLOW_INVALID, "Template not found: " + templateKey)));

        String planId = "plan-" + UUID.randomUUID().toString().substring(0, 8);
        OrchestrationPlan plan = new OrchestrationPlan();
        plan.setPlanId(planId);
        plan.setProjectId(cmd.getProjectId());
        plan.setWorkspaceId(cmd.getWorkspaceId());
        plan.setTemplateType(templateView.getTemplateType().name());
        plan.setRequirement(cmd.getRequirement());
        plan.setScope("Bounded Context: " + templateView.getName());
        plan.setStatus(OrchestrationPlanStatus.GENERATED);
        plan.setUserConfirmed(false);

        // Affected files
        List<String> affectedFiles = new ArrayList<>();
        if (cmd.getTargetFiles() != null && !cmd.getTargetFiles().isEmpty()) {
            affectedFiles.addAll(cmd.getTargetFiles());
        } else {
            affectedFiles.add("src/main/java/com/agenthub/sample/TargetService.java");
            affectedFiles.add("src/test/java/com/agenthub/sample/TargetServiceTest.java");
        }
        plan.setAffectedFiles(affectedFiles);

        // Tool permissions gathered from template nodes
        Set<String> requiredToolNames = new LinkedHashSet<>();
        WorkflowDsl dsl = templateView.getDsl();
        if (dsl != null && dsl.getNodes() != null) {
            for (WorkflowNodeDsl node : dsl.getNodes()) {
                if (node.getRequiredTools() != null) {
                    requiredToolNames.addAll(node.getRequiredTools());
                }
            }
        }
        if (requiredToolNames.isEmpty()) {
            requiredToolNames.addAll(List.of("file_read", "file_write", "command_exec", "git_operation"));
        }

        List<ToolPermissionRequest> permissions = new ArrayList<>();
        for (String toolName : requiredToolNames) {
            AgentToolDefinition toolDef = toolRegistry.findByName(toolName).orElse(null);
            boolean highImpact = toolDef != null ? toolDef.isHighImpact() : toolRegistry.isHighImpact(toolName);
            permissions.add(new ToolPermissionRequest(
                    toolName,
                    toolDef != null ? toolDef.getCategory() : null,
                    highImpact,
                    "Required by workflow template stage",
                    false // Unconfirmed initially
            ));
        }
        plan.setToolPermissions(permissions);

        // Estimated budget & quality gates
        plan.setEstimatedBudget(new EstimatedBudget(25000, 0.05, 300L));
        plan.setQualityGates(List.of(
                "MAVEN_TEST_PASS",
                "ZERO_NEW_WARNINGS",
                "DIFF_REVIEW_PASS",
                "ARCHUNIT_GUARD_PASS"
        ));
        plan.setRollbackPlan("JGit 工作区快照还原与隔离分支无损重置 (JGitWorkspaceManager revert/resync)");
        plan.setWorkflowDsl(dsl);

        planStore.put(planId, plan);
        log.info("Generated Orchestrator plan [{}] for project [{}] workspace [{}]", planId, cmd.getProjectId(), cmd.getWorkspaceId());
        return toView(plan);
    }

    @Override
    public OrchestrationPlanView confirmPlan(String planId, ConfirmPlanCommand cmd) {
        if (planId == null || !planStore.containsKey(planId)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Orchestration plan not found: " + planId);
        }
        OrchestrationPlan plan = planStore.get(planId);
        if (cmd == null || !cmd.isConfirmed()) {
            plan.setStatus(OrchestrationPlanStatus.REJECTED);
            plan.setUserConfirmed(false);
            plan.setRejectionReason(cmd != null ? cmd.getComments() : "User rejected confirmation");
            log.info("Plan [{}] rejected by user", planId);
            return toView(plan);
        }

        String userId = RequestContext.get().getUserId();
        plan.setUserConfirmed(true);
        plan.setStatus(OrchestrationPlanStatus.CONFIRMED);
        plan.setConfirmedBy(userId != null ? userId : "user-confirmed");
        plan.setConfirmedAt(LocalDateTime.now());
        plan.setUpdatedAt(LocalDateTime.now());

        // Mark tool permissions authorized
        List<String> explicitlyAuthorized = cmd != null ? cmd.getAuthorizedTools() : null;
        for (ToolPermissionRequest req : plan.getToolPermissions()) {
            if (explicitlyAuthorized == null) {
                req.setAuthorized(true);
            } else {
                boolean auth = !req.isHighImpact() || explicitlyAuthorized.contains(req.getToolName());
                req.setAuthorized(auth);
            }
        }

        auditService.record("PLAN_CONFIRM", userId, null, null,
                "Confirmed Orchestrator plan [" + planId + "] with scope [" + plan.getScope() + "]");
        log.info("Plan [{}] successfully confirmed by user [{}]", planId, userId);
        return toView(plan);
    }

    @Override
    public OrchestrationPlanView rejectPlan(String planId, String reason) {
        if (planId == null || !planStore.containsKey(planId)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Orchestration plan not found: " + planId);
        }
        OrchestrationPlan plan = planStore.get(planId);
        plan.setUserConfirmed(false);
        plan.setStatus(OrchestrationPlanStatus.REJECTED);
        plan.setRejectionReason(reason != null ? reason : "Rejected by user");
        plan.setUpdatedAt(LocalDateTime.now());
        log.info("Plan [{}] rejected: {}", planId, reason);
        return toView(plan);
    }

    @Override
    public OrchestrationPlanView getPlan(String planId) {
        if (planId == null || !planStore.containsKey(planId)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Orchestration plan not found: " + planId);
        }
        return toView(planStore.get(planId));
    }

    @Override
    public List<OrchestrationPlanView> listPlansByProject(String projectId) {
        if (projectId == null || projectId.isBlank()) return Collections.emptyList();
        return planStore.values().stream()
                .filter(p -> projectId.equals(p.getProjectId()))
                .sorted(Comparator.comparing(OrchestrationPlan::getCreatedAt).reversed())
                .map(this::toView)
                .toList();
    }

    @Override
    public void enforceHighImpactToolAuthorization(String planId, String toolName) {
        if (toolName == null) return;
        boolean isHighImpact = toolRegistry.isHighImpact(toolName);
        if (!isHighImpact) {
            return; // Read-only / low-impact tools are always permitted
        }

        if (planId == null || planId.isBlank() || !planStore.containsKey(planId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "High-impact tool execution [" + toolName + "] blocked: Valid Orchestrator plan required before execution");
        }

        OrchestrationPlan plan = planStore.get(planId);
        if (!plan.isUserConfirmed()) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "High-impact tool execution [" + toolName + "] blocked: Orchestrator plan [" + planId + "] must be confirmed by user before execution");
        }

        ToolPermissionRequest perm = plan.getToolPermissions().stream()
                .filter(p -> toolName.equals(p.getToolName()))
                .findFirst()
                .orElse(null);
        if (perm == null || !perm.isAuthorized()) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "High-impact tool execution [" + toolName + "] blocked: Tool is not authorized in Orchestrator plan [" + planId + "]");
        }
    }

    private OrchestrationPlanView toView(OrchestrationPlan p) {
        return new OrchestrationPlanView(
                p.getPlanId(),
                p.getProjectId(),
                p.getWorkspaceId(),
                p.getTemplateType(),
                p.getRequirement(),
                p.getScope(),
                p.getStatus(),
                p.getAffectedFiles(),
                p.getToolPermissions(),
                p.getEstimatedBudget(),
                p.getQualityGates(),
                p.getRollbackPlan(),
                p.isUserConfirmed(),
                p.getConfirmedBy(),
                p.getConfirmedAt(),
                p.getRejectionReason(),
                p.getWorkflowDsl(),
                p.getCreatedAt()
        );
    }
}
