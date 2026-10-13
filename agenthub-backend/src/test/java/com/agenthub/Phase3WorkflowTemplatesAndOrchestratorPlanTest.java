package com.agenthub;

import com.agenthub.agent.application.AgentApplication;
import com.agenthub.agent.application.OfflineEvaluationApplication;
import com.agenthub.agent.domain.context.AssembledContext;
import com.agenthub.agent.domain.context.ContextAssemblyEngine;
import com.agenthub.agent.domain.context.ContextLayerType;
import com.agenthub.agent.domain.context.ContextTruncationStrategy;
import com.agenthub.agent.dto.AgentDefinitionView;
import com.agenthub.agent.domain.definition.AgentToolCategory;
import com.agenthub.agent.domain.definition.AgentToolDefinition;
import com.agenthub.agent.domain.definition.ToolRegistry;
import com.agenthub.agent.domain.evaluation.EvaluationReportView;
import com.agenthub.agent.domain.evaluation.EvaluationTaskCategory;
import com.agenthub.agent.domain.provider.exception.NoAvailableProviderException;
import com.agenthub.agent.domain.provider.model.ChatRequest;
import com.agenthub.agent.domain.provider.router.DynamicProviderRouter;
import com.agenthub.agent.domain.provider.spi.LlmProvider;
import com.agenthub.identity.infrastructure.entity.UserEntity;
import com.agenthub.identity.infrastructure.repository.UserRepository;
import com.agenthub.orchestration.application.OrchestratorPlanApplication;
import com.agenthub.orchestration.application.WorkflowOrchestrationApplication;
import com.agenthub.orchestration.domain.dsl.*;
import com.agenthub.orchestration.domain.plan.OrchestrationPlanStatus;
import com.agenthub.orchestration.domain.template.WorkflowTemplateRegistry;
import com.agenthub.orchestration.domain.template.WorkflowTemplateType;
import com.agenthub.orchestration.domain.template.WorkflowTemplateView;
import com.agenthub.orchestration.dto.*;
import com.agenthub.project.application.ProjectApplication;
import com.agenthub.project.application.WorkspaceApplication;
import com.agenthub.project.domain.context.SecurityLevel;
import com.agenthub.project.dto.CreateProjectCommand;
import com.agenthub.project.dto.ProjectView;
import com.agenthub.project.dto.WorkspaceView;
import com.agenthub.project.infrastructure.repository.ProjectRepository;
import com.agenthub.project.infrastructure.repository.WorkspaceRepository;
import com.agenthub.shared.context.RequestContext;
import com.agenthub.shared.exception.BusinessException;
import com.agenthub.shared.exception.ErrorCode;
import com.agenthub.execution.application.ExecutionApplication;
import com.agenthub.execution.domain.model.CancelTokenRegistry;
import com.agenthub.execution.domain.model.WorkflowRunStatus;
import com.agenthub.execution.dto.StartRunCommand;
import com.agenthub.execution.dto.WorkflowRunView;
import com.agenthub.execution.infrastructure.repository.RunEventRepository;
import com.agenthub.execution.infrastructure.repository.StepRunRepository;
import com.agenthub.execution.infrastructure.repository.WorkflowRunRepository;
import com.agenthub.orchestration.application.ApprovalApplication;
import com.agenthub.orchestration.dto.ApprovalDecisionCommand;
import com.agenthub.orchestration.dto.ApprovalView;
import com.agenthub.orchestration.infrastructure.repository.ApprovalRepository;
import com.agenthub.orchestration.scheduler.DagExecutionEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 3（Agent 工作单元、模板与 Orchestrator 计划产品化）五维防御测试套件。
 * 覆盖：
 * 1. 极值边界输入：Kahn DAG 环路检测、自环检测、孤岛节点检测与非法入参防御；
 * 2. 状态机与模板实例化：版本化 WorkflowTemplate（BUG_FIX_WITH_TESTS 与 NEW_FEATURE_ENDPOINT）7 阶段 Kahn 拓扑与 Agent 角色绑定；
 * 3. 错误恢复与 Provider 路由守卫：requireLive 拒绝 Mock 兜底，工具输出超出配额安全截断；
 * 4. 并发与阶段编排：Kahn 拓扑排序计算并行执行批次（executionStages）与分层 Context Assembly 预算治理；
 * 5. 安全与计划门禁：Orchestrator 结构化 Plan 生成、未确认前阻断高影响工具（FORBIDDEN）、用户确认后放行，离线评测集 5 类任务全绿；
 * 6. 模板实例化端到端执行：BUG_FIX_WITH_TESTS 拓扑在计划确权后完整运行，审批流转与工具门禁防御；
 * 7. 工具输出配额、UTF-8 多字节边界防截断与 ContextAssembly 极值防御。
 */
@SpringBootTest
@ActiveProfiles("test")
class Phase3WorkflowTemplatesAndOrchestratorPlanTest {

    @Autowired
    private WorkflowOrchestrationApplication workflowApplication;

    @Autowired
    private OrchestratorPlanApplication planApplication;

    @Autowired
    private AgentApplication agentApplication;

    @Autowired
    private OfflineEvaluationApplication evaluationApplication;

    @Autowired
    private ToolRegistry toolRegistry;

    @Autowired
    private WorkflowTemplateRegistry templateRegistry;

    @Autowired
    private DynamicProviderRouter providerRouter;

    @Autowired
    private ProjectApplication projectApplication;

    @Autowired
    private WorkspaceApplication workspaceApplication;

    @Autowired
    private ExecutionApplication executionApplication;

    @Autowired
    private DagExecutionEngine dagExecutionEngine;

    @Autowired
    private ApprovalApplication approvalApplication;

    @Autowired
    private WorkflowRunRepository workflowRunRepository;

    @Autowired
    private StepRunRepository stepRunRepository;

    @Autowired
    private RunEventRepository runEventRepository;

    @Autowired
    private ApprovalRepository approvalRepository;

    @Autowired
    private CancelTokenRegistry cancelTokenRegistry;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private UserRepository userRepository;

    private String testUserId;
    private String testProjectId;
    private String testWorkspaceId;
    private final List<String> testRunIds = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() {
        testUserId = "usr-p3-" + UUID.randomUUID().toString().substring(0, 8);
        userRepository.save(new UserEntity(
                testUserId,
                testUserId + "@agenthub.test",
                "HashPassword123!",
                "Alice",
                "ACTIVE"
        ));

        RequestContext.get().setUserId(testUserId);
        RequestContext.get().setUsername(testUserId + "@agenthub.test");

        ProjectView proj = projectApplication.createProject(new CreateProjectCommand(
                "p3-project-" + UUID.randomUUID().toString().substring(0, 6),
                "Phase 3 Test Project",
                "."
        ));
        testProjectId = proj.getId();

        testWorkspaceId = workspaceRepository.findByProjectId(testProjectId).orElseThrow().getId();
    }

    @AfterEach
    void tearDown() {
        RequestContext.clear();
        for (String rId : testRunIds) {
            try {
                cancelTokenRegistry.get(rId).ifPresent(token -> token.cancel("Test teardown cleanup"));
            } catch (Exception ignored) {}
            try {
                runEventRepository.deleteAll(runEventRepository.findByRunIdOrderBySequenceNumAsc(rId));
                stepRunRepository.deleteAll(stepRunRepository.findByRunIdOrderByCreatedAtAsc(rId));
                approvalRepository.deleteAll(approvalRepository.findByRunId(rId));
                workflowRunRepository.deleteById(rId);
            } catch (Exception ignored) {}
        }
        testRunIds.clear();
        try {
            if (testWorkspaceId != null) {
                workspaceRepository.deleteById(testWorkspaceId);
            }
            if (testProjectId != null) {
                projectRepository.deleteById(testProjectId);
            }
            if (testUserId != null) {
                userRepository.deleteById(testUserId);
            }
        } catch (Exception ignored) {}
    }

    @Test
    @DisplayName("1. 极值边界输入：Kahn DAG 环路检测、自环检测、孤岛孤立节点拦截")
    void shouldDetectCyclesSelfLoopsAndIslandsViaKahnDslValidator() {
        // 1) Cycle detection (A -> B -> C -> A)
        WorkflowDsl cycleDsl = new WorkflowDsl("wf-cycle", "Cycle Test");
        cycleDsl.setNodes(List.of(
                new WorkflowNodeDsl("node-a", "Node A", "AGENT"),
                new WorkflowNodeDsl("node-b", "Node B", "AGENT"),
                new WorkflowNodeDsl("node-c", "Node C", "AGENT")
        ));
        cycleDsl.setEdges(List.of(
                new WorkflowEdgeDsl("node-a", "node-b"),
                new WorkflowEdgeDsl("node-b", "node-c"),
                new WorkflowEdgeDsl("node-c", "node-a")
        ));

        assertThatThrownBy(() -> WorkflowDslValidator.validate(cycleDsl))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Cycle detected");

        // 2) Self loop detection (A -> A)
        WorkflowDsl loopDsl = new WorkflowDsl("wf-loop", "Self Loop Test");
        loopDsl.setNodes(List.of(new WorkflowNodeDsl("node-x", "Node X", "AGENT")));
        loopDsl.setEdges(List.of(new WorkflowEdgeDsl("node-x", "node-x")));

        assertThatThrownBy(() -> WorkflowDslValidator.validate(loopDsl))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Self-loop edge detected");

        // 3) Island node detection (A -> B, but C is isolated)
        WorkflowDsl islandDsl = new WorkflowDsl("wf-island", "Island Test");
        islandDsl.setNodes(List.of(
                new WorkflowNodeDsl("node-1", "Node 1", "AGENT"),
                new WorkflowNodeDsl("node-2", "Node 2", "AGENT"),
                new WorkflowNodeDsl("node-isolated", "Node Isolated", "AGENT")
        ));
        islandDsl.setEdges(List.of(new WorkflowEdgeDsl("node-1", "node-2")));

        assertThatThrownBy(() -> WorkflowDslValidator.validate(islandDsl))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Isolated island node detected");
    }

    @Test
    @DisplayName("2. 状态机与模板实例化：版本化 WorkflowTemplate 完整 7 阶段 Kahn 拓扑与 Agent 角色绑定")
    void shouldVerifyVersionedWorkflowTemplatesAndInstantiateDefinitions() {
        List<WorkflowTemplateView> templates = workflowApplication.listTemplates();
        assertThat(templates).hasSizeGreaterThanOrEqualTo(2);

        // 1) 验证缺陷修复模板 (BUG_FIX_WITH_TESTS)
        WorkflowTemplateView bugFixTpl = workflowApplication.getTemplate("tpl-bug-fix-with-tests");
        assertThat(bugFixTpl.getTemplateType()).isEqualTo(WorkflowTemplateType.BUG_FIX_WITH_TESTS);
        assertThat(bugFixTpl.getStages()).containsExactly(
                "REQUIREMENT_CLARIFICATION", "PLAN", "IMPLEMENTATION", "TEST", "REVIEW", "APPROVAL", "DELIVERY"
        );
        assertThat(bugFixTpl.getRequiredAgentRoles()).contains(
                "backend-architect", "backend-developer", "qa-tester", "security-reviewer"
        );

        // 验证审批节点严格标注 requiresApproval=true
        WorkflowDsl bugFixDsl = bugFixTpl.getDsl();
        WorkflowNodeDsl approvalNode = bugFixDsl.getNodes().stream()
                .filter(n -> "node-approval".equals(n.getId()))
                .findFirst().orElseThrow();
        assertThat(approvalNode.getType()).isEqualTo("APPROVAL");
        assertThat(approvalNode.isRequiresApproval()).isTrue();

        // 2) 实例化模板为正式 WorkflowDefinition
        WorkflowDefinitionView instantiated = workflowApplication.instantiateTemplate(
                "tpl-bug-fix-with-tests",
                "Project Alpha Defect Fix",
                "Customized for ticket #1024"
        );
        assertThat(instantiated.getId()).startsWith("wf-inst-");
        assertThat(instantiated.getName()).isEqualTo("Project Alpha Defect Fix");
        assertThat(instantiated.getDsl().getNodes()).hasSize(7);

        // 3) 验证特性端点模板 (NEW_FEATURE_ENDPOINT)
        WorkflowTemplateView endpointTpl = workflowApplication.getTemplate("tpl-new-feature-endpoint");
        assertThat(endpointTpl.getTemplateType()).isEqualTo(WorkflowTemplateType.NEW_FEATURE_ENDPOINT);
        ValidateDslResultView validateResult = workflowApplication.validateDsl(endpointTpl.getDsl());
        assertThat(validateResult.isValid()).isTrue();
        assertThat(validateResult.getTopologicalOrder()).hasSize(7);
        assertThat(validateResult.getExecutionStages()).hasSize(7);
    }

    @Test
    @DisplayName("3. 异常容错与 Provider 路由守卫：requireLive 拒绝 Mock 兜底，工具输出超出配额安全截断")
    void shouldEnforceRequireLiveRouteAndGuardToolOutputSize() {
        // 1) 当 requireLive=true 时，如果要求非 Mock 的特定能力且无活体 Provider，必须明确抛出 NoAvailableProviderException
        ChatRequest liveReq = new ChatRequest();
        liveReq.setRequireLive(true);
        liveReq.setPreferredProvider("UNCONFIGURED_ENTERPRISE_PROVIDER");
        liveReq.setRequiredCapabilities(Set.of("non_existent_capability_xyz"));

        assertThatThrownBy(() -> providerRouter.selectCandidates(liveReq))
                .isInstanceOf(NoAvailableProviderException.class)
                .hasMessageContaining("No live provider available");

        // 2) 当 requireLive=false（默认/测试模式）时，允许回退到 MockLlmProvider
        ChatRequest mockReq = new ChatRequest();
        mockReq.setRequireLive(false);
        mockReq.setRequiredCapabilities(Set.of("non_existent_capability_xyz"));
        List<LlmProvider> candidates = providerRouter.selectCandidates(mockReq);
        assertThat(candidates).isNotEmpty();
        assertThat(candidates.get(0).getId()).isEqualTo("prov-mock");

        // 3) 工具输出配额保护：超出 maxOutputSizeBytes 触发安全截断与来源追溯
        AgentToolDefinition fileReadTool = toolRegistry.findByName("file_read").orElseThrow();
        assertThat(fileReadTool.getMaxOutputSizeBytes()).isEqualTo(2 * 1024 * 1024);

        String smallOutput = "class UserService { public void test() {} }";
        assertThat(toolRegistry.guardOutputSize("file_read", smallOutput)).isEqualTo(smallOutput);

        // 测试一个配额极小的测试场景（512KB 工具注入超大字符串）
        byte[] oversized = new byte[600 * 1024];
        Arrays.fill(oversized, (byte) 'A');
        String rawOversized = new String(oversized);
        String guarded = toolRegistry.guardOutputSize("code_analyze", rawOversized);
        assertThat(guarded).contains("[TRUNCATED: Output exceeded max allowed size");
    }

    @Test
    @DisplayName("4. 并发与编排：Kahn 拓扑排序计算并行执行批次（executionStages）与分层 Context Assembly 预算治理")
    void shouldComputeParallelStagesAndAssembleLayeredContextWithBudgets() {
        // 1) 并行 DAG 结构：root -> [branchA, branchB] -> join -> terminal
        WorkflowDsl parallelDsl = new WorkflowDsl("wf-parallel", "Parallel Stages Test");
        parallelDsl.setNodes(List.of(
                new WorkflowNodeDsl("root", "Root Spec", "AGENT"),
                new WorkflowNodeDsl("branchA", "Frontend Impl", "AGENT"),
                new WorkflowNodeDsl("branchB", "Backend Impl", "AGENT"),
                new WorkflowNodeDsl("join", "Integration Test", "AGENT"),
                new WorkflowNodeDsl("terminal", "Delivery", "AGENT")
        ));
        parallelDsl.setEdges(List.of(
                new WorkflowEdgeDsl("root", "branchA"),
                new WorkflowEdgeDsl("root", "branchB"),
                new WorkflowEdgeDsl("branchA", "join"),
                new WorkflowEdgeDsl("branchB", "join"),
                new WorkflowEdgeDsl("join", "terminal")
        ));

        TopologicalSortResult sortResult = WorkflowDslValidator.validate(parallelDsl);
        List<List<String>> stages = sortResult.getExecutionStages();
        assertThat(stages).hasSize(4);
        assertThat(stages.get(0)).containsExactly("root");
        assertThat(stages.get(1)).containsExactlyInAnyOrder("branchA", "branchB"); // 并行批次
        assertThat(stages.get(2)).containsExactly("join");
        assertThat(stages.get(3)).containsExactly("terminal");

        // 2) 分层 Context Assembly：6 层装配、预算配额与截断策略
        List<ContextAssemblyEngine.LayerInput> layers = List.of(
                new ContextAssemblyEngine.LayerInput(
                        ContextLayerType.SYSTEM_AND_PROJECT_RULES,
                        "# PROJECT RULES\n1. DDD 4-layer architecture\n2. Defensive programming",
                        50,
                        ContextTruncationStrategy.TRUNCATE_TAIL,
                        SecurityLevel.INTERNAL,
                        "PROJECT_RULES.md"
                ),
                new ContextAssemblyEngine.LayerInput(
                        ContextLayerType.AOCI_COGNITION,
                        "AOCI baseline: commit 586f2d5, 22 tables, 18 rest controllers",
                        50,
                        ContextTruncationStrategy.SUMMARIZE,
                        SecurityLevel.RESTRICTED,
                        "aoci:scope:core"
                ),
                new ContextAssemblyEngine.LayerInput(
                        ContextLayerType.AFFECTED_FILES,
                        "class OrderService {\n" + "    // 500 lines of code\n".repeat(20) + "}",
                        20, // 预算较紧，触发截断
                        ContextTruncationStrategy.TRUNCATE_TAIL,
                        SecurityLevel.INTERNAL,
                        "OrderService.java"
                ),
                new ContextAssemblyEngine.LayerInput(
                        ContextLayerType.USER_INPUT,
                        "修复状态机并发扣减时的空指针异常",
                        50,
                        ContextTruncationStrategy.NONE,
                        SecurityLevel.PUBLIC,
                        "user-prompt"
                )
        );

        AssembledContext assembled = agentApplication.assembleContext(layers);
        assertThat(assembled.getLayers()).hasSize(4);
        assertThat(assembled.getAssembledPrompt()).contains("=== [CONTEXT: SYSTEM_AND_PROJECT_RULES");
        assertThat(assembled.getAssembledPrompt()).contains("=== [CONTEXT: USER_INPUT");
        // 验证紧预算层标记为 truncated
        assertThat(assembled.getLayers().get(2).isTruncated()).isTrue();
    }

    @Test
    @DisplayName("5. 安全与计划门禁：Orchestrator 计划生成、未确认前阻断高影响工具（FORBIDDEN）、用户确认后放行，离线评测集全绿")
    void shouldEnforceOrchestratorPlanConfirmationAndPassOfflineEvaluation() {
        // 1) 生成 Orchestrator Plan
        GeneratePlanCommand genCmd = new GeneratePlanCommand(
                testProjectId,
                testWorkspaceId,
                "BUG_FIX_WITH_TESTS",
                "修复订单取消状态转换时的空指针异常并提供测试"
        );
        OrchestrationPlanView plan = planApplication.generatePlan(genCmd);
        assertThat(plan.getPlanId()).startsWith("plan-");
        assertThat(plan.getStatus()).isEqualTo(OrchestrationPlanStatus.GENERATED);
        assertThat(plan.isUserConfirmed()).isFalse();
        assertThat(plan.getQualityGates()).contains("MAVEN_TEST_PASS", "ARCHUNIT_GUARD_PASS");
        assertThat(plan.getToolPermissions()).isNotEmpty();

        // 验证高影响工具定义
        assertThat(toolRegistry.isHighImpact("file_write")).isTrue();
        assertThat(toolRegistry.isHighImpact("command_exec")).isTrue();
        assertThat(toolRegistry.isHighImpact("file_read")).isFalse();

        // 2) 门禁测试：低影响工具（file_read）在未确认时允许执行
        planApplication.enforceHighImpactToolAuthorization(plan.getPlanId(), "file_read");

        // 3) 门禁测试：高影响工具（file_write / command_exec）在未确认时严格拦截（FORBIDDEN）
        assertThatThrownBy(() -> planApplication.enforceHighImpactToolAuthorization(plan.getPlanId(), "file_write"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);

        assertThatThrownBy(() -> planApplication.enforceHighImpactToolAuthorization(plan.getPlanId(), "command_exec"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);

        // 4) 用户确认计划（确认范围、文件、工具与预算）
        ConfirmPlanCommand confirmCmd = new ConfirmPlanCommand(
                true,
                List.of("file_write", "command_exec"),
                "Approved after reviewing affected files and token budget"
        );
        OrchestrationPlanView confirmedPlan = planApplication.confirmPlan(plan.getPlanId(), confirmCmd);
        assertThat(confirmedPlan.getStatus()).isEqualTo(OrchestrationPlanStatus.CONFIRMED);
        assertThat(confirmedPlan.isUserConfirmed()).isTrue();
        assertThat(confirmedPlan.getConfirmedBy()).isEqualTo(testUserId);

        // 5) 确认后：已授权高影响工具顺利放行！
        planApplication.enforceHighImpactToolAuthorization(confirmedPlan.getPlanId(), "file_write");
        planApplication.enforceHighImpactToolAuthorization(confirmedPlan.getPlanId(), "command_exec");

        // 5.1) 细粒度未授权高影响工具拦截：用户仅授权了 file_write 和 command_exec，未授权 git_operation，严格阻断（FORBIDDEN）
        assertThatThrownBy(() -> planApplication.enforceHighImpactToolAuthorization(confirmedPlan.getPlanId(), "git_operation"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);

        // 6) 离线评测集验证：5 大任务类型（新增接口、修复缺陷、重构、测试补齐、恶意 Prompt 注入防御）
        EvaluationReportView evalReport = evaluationApplication.runEvaluationSuite("gpt-4o-baseline");
        assertThat(evalReport.getPassRate()).isEqualTo(1.0);
        assertThat(evalReport.getToolMisuseRate()).isEqualTo(0.0);
        assertThat(evalReport.getDiffAcceptanceRate()).isEqualTo(1.0);
        assertThat(evalReport.getTaskResults()).hasSize(5);

        // 验证恶意 Prompt 注入任务未发生工具误用
        var promptDefenseTask = evalReport.getTaskResults().stream()
                .filter(t -> t.getCategory() == EvaluationTaskCategory.PROMPT_INJECTION_DEFENSE)
                .findFirst().orElseThrow();
        assertThat(promptDefenseTask.isPassed()).isTrue();
        assertThat(promptDefenseTask.isToolMisuse()).isFalse();
    }

    @Test
    @DisplayName("6. 模板实例化端到端执行：BUG_FIX_WITH_TESTS 拓扑在计划确权后完整运行，审批流转与工具门禁防御")
    void shouldExecuteInstantiatedWorkflowTemplateEndToEndWithConfirmedPlan(@TempDir File baseDir) throws Exception {
        // 1) 为缺陷修复模板生成计划并实例化
        GeneratePlanCommand genCmd = new GeneratePlanCommand(
                testProjectId,
                testWorkspaceId,
                "BUG_FIX_WITH_TESTS",
                "修复状态机并发扣减时的空指针异常并提供测试"
        );
        OrchestrationPlanView plan = planApplication.generatePlan(genCmd);
        WorkflowDefinitionView def = workflowApplication.instantiateTemplate(
                "tpl-bug-fix-with-tests",
                "E2E Defect Fix Run",
                "Testing end-to-end execution of instantiated workflow template"
        );

        File wsDir = new File(baseDir, "ws-phase3-e2e");
        wsDir.mkdirs();

        // 2) 未确权场景测试：未确认计划前直接启动执行，高影响工具（file_write）拦截导致 Run 失败
        WorkflowRunView unconfirmedRun = executionApplication.startRun(new StartRunCommand(
                testProjectId, null, "idem-unconf-" + UUID.randomUUID()
        ));
        testRunIds.add(unconfirmedRun.getId());

        CompletableFuture<WorkflowRunStatus> unconfFuture = dagExecutionEngine.executeDag(
                unconfirmedRun.getId(), def.getDsl(), wsDir.getAbsolutePath(),
                Map.of("planId", plan.getPlanId()), 30, testUserId
        );
        WorkflowRunStatus unconfStatus = unconfFuture.get(10, TimeUnit.SECONDS);
        assertThat(unconfStatus).isEqualTo(WorkflowRunStatus.FAILED);

        // 3) 确权放行场景测试：用户在计划中完整授权全部所需工具
        ConfirmPlanCommand confirmCmd = new ConfirmPlanCommand(
                true,
                List.of("file_read", "file_tree", "file_write", "command_exec", "git_operation", "code_analyze"),
                "Fully authorized by user for bug fix workflow"
        );
        OrchestrationPlanView confirmedPlan = planApplication.confirmPlan(plan.getPlanId(), confirmCmd);
        assertThat(confirmedPlan.isUserConfirmed()).isTrue();

        WorkflowRunView confirmedRun = executionApplication.startRun(new StartRunCommand(
                testProjectId, null, "idem-conf-" + UUID.randomUUID()
        ));
        testRunIds.add(confirmedRun.getId());

        CompletableFuture<WorkflowRunStatus> succFuture = dagExecutionEngine.executeDag(
                confirmedRun.getId(), def.getDsl(), wsDir.getAbsolutePath(),
                Map.of("planId", confirmedPlan.getPlanId(), "requirement", "Fix NPE"), 60, testUserId
        );

        // 4) 流程自动执行至 node-approval（APPROVAL 节点）进入挂起等待
        long deadline = System.currentTimeMillis() + 15_000;
        ApprovalView pending = null;
        while (System.currentTimeMillis() < deadline) {
            List<ApprovalView> approvals = approvalApplication.listApprovalsByRunId(confirmedRun.getId());
            if (!approvals.isEmpty() && "PENDING".equalsIgnoreCase(approvals.get(0).getStatus())) {
                pending = approvals.get(0);
                break;
            }
            Thread.sleep(100);
        }
        assertThat(pending).isNotNull();

        // 5) 人工审批批准，工作流继续执行后续 DELIVERY 节点并成功完结
        approvalApplication.decide(pending.getId(), new ApprovalDecisionCommand("APPROVED", "Gate approved"));
        WorkflowRunStatus finalStatus = succFuture.get(15, TimeUnit.SECONDS);
        assertThat(finalStatus).isEqualTo(WorkflowRunStatus.SUCCEEDED);
    }

    @Test
    @DisplayName("7. 工具输出配额、UTF-8 多字节边界防截断与 ContextAssembly 极值防御")
    void shouldVerifyToolOutputLimitsUtf8BoundariesAndDefensiveContextAssembly() {
        // 1) network_request 注册与高影响工具分类验证
        AgentToolDefinition netTool = toolRegistry.findByName("network_request").orElseThrow();
        assertThat(netTool.isHighImpact()).isTrue();
        assertThat(netTool.getCategory()).isEqualTo(AgentToolCategory.NETWORK_REQUEST);
        assertThat(netTool.getMaxOutputSizeBytes()).isEqualTo(1024 * 1024);

        // 2) UTF-8 中文多字节截断边界防御：确保超长输出截断时不会将 3 字节中文字符截断成损坏序列
        String sampleChinese = "AgentHub 智能软件变更交付平台研发防御测试用例验证".repeat(20000);
        String guardedOutput = toolRegistry.guardOutputSize("code_analyze", sampleChinese);
        assertThat(guardedOutput).contains("[TRUNCATED: Output exceeded max allowed size");
        int markerIdx = guardedOutput.indexOf("\n[TRUNCATED");
        String prefix = guardedOutput.substring(0, markerIdx);
        assertThat(prefix).isNotEmpty();
        byte[] prefixBytes = prefix.getBytes(StandardCharsets.UTF_8);
        assertThat(prefixBytes.length).isLessThanOrEqualTo(512 * 1024);
        // 验证前缀反向解码为合法的 Unicode 字符串且无乱码替代字符 \uFFFD
        assertThat(prefix).doesNotContain("\uFFFD");

        // 3) ContextAssemblyEngine 防御性入参测试：null type、null strategy、null securityLevel 均有安全缺省降级
        List<ContextAssemblyEngine.LayerInput> nullDefensiveInputs = List.of(
                new ContextAssemblyEngine.LayerInput(null, "Safe content text", 100, null, null, null)
        );
        AssembledContext ctx = agentApplication.assembleContext(nullDefensiveInputs);
        assertThat(ctx.getLayers()).hasSize(1);
        assertThat(ctx.getLayers().get(0).getLayerType()).isEqualTo(ContextLayerType.USER_INPUT);
        assertThat(ctx.getLayers().get(0).getSecurityLevel()).isEqualTo(SecurityLevel.INTERNAL);
        assertThat(ctx.getLayers().get(0).getTruncationStrategy()).isEqualTo(ContextTruncationStrategy.TRUNCATE_TAIL);
        assertThat(ctx.isWithinBudget()).isTrue();
    }
}
