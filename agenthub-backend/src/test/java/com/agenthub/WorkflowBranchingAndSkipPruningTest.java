package com.agenthub;

import com.agenthub.execution.application.ExecutionApplication;
import com.agenthub.execution.domain.model.WorkflowRunStatus;
import com.agenthub.execution.dto.RunEventView;
import com.agenthub.execution.dto.StartRunCommand;
import com.agenthub.execution.dto.StepRunView;
import com.agenthub.execution.dto.WorkflowRunView;
import com.agenthub.orchestration.domain.dsl.WorkflowDsl;
import com.agenthub.orchestration.domain.dsl.WorkflowEdgeDsl;
import com.agenthub.orchestration.domain.dsl.WorkflowNodeDsl;
import com.agenthub.orchestration.scheduler.DagExecutionEngine;
import com.agenthub.shared.context.RequestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class WorkflowBranchingAndSkipPruningTest {

    @Autowired
    private DagExecutionEngine dagExecutionEngine;

    @Autowired
    private ExecutionApplication executionApplication;

    @BeforeEach
    void setUp() {
        RequestContext.get().setUserId("user-1");
    }

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    @Test
    @DisplayName("测试条件分支动态路径选择与级联短路剪枝：未命中分支标记 SKIPPED 并自动级联剪枝后续依赖")
    void shouldExecuteConditionalBranchingAndCascadeSkipPruning(@TempDir File workspaceDir) throws Exception {
        WorkflowRunView run = executionApplication.startRun(new StartRunCommand("proj-default", null, null));
        String runId = run.getId();

        WorkflowDsl dsl = new WorkflowDsl("wf-branch-skip", "条件分支与跳过机制测试");

        WorkflowNodeDsl startNode = new WorkflowNodeDsl("start", "起点", "START");

        // Fast-path branch (condition: inputs.environment == 'dev')
        WorkflowNodeDsl devBranch = new WorkflowNodeDsl("dev_deploy", "开发环境快速部署", "AGENT", "MOCK", "执行开发部署");
        devBranch.setCondition("inputs.environment == 'dev'");

        // Prod-path branch (condition: inputs.environment == 'prod')
        WorkflowNodeDsl prodBranch = new WorkflowNodeDsl("prod_deploy", "生产环境严苛审批", "AGENT", "MOCK", "执行生产部署");
        prodBranch.setCondition("inputs.environment == 'prod'");

        // Downstream of prod-path branch
        WorkflowNodeDsl prodAudit = new WorkflowNodeDsl("prod_audit", "生产环境双人复核", "AGENT", "MOCK", "生产复核");
        prodAudit.setJoinPolicy("all_succeeded");

        // Terminal Join Node (joinPolicy: any_succeeded)
        WorkflowNodeDsl finishNode = new WorkflowNodeDsl("finish", "部署归档收尾", "AGENT", "MOCK", "归档记录");
        finishNode.setJoinPolicy("any_succeeded");

        dsl.setNodes(List.of(startNode, devBranch, prodBranch, prodAudit, finishNode));
        dsl.setEdges(List.of(
                new WorkflowEdgeDsl("start", "dev_deploy"),
                new WorkflowEdgeDsl("start", "prod_deploy"),
                new WorkflowEdgeDsl("prod_deploy", "prod_audit"),
                new WorkflowEdgeDsl("dev_deploy", "finish"),
                new WorkflowEdgeDsl("prod_audit", "finish")
        ));

        // Execute with inputs.environment = 'dev'
        Map<String, Object> inputs = Map.of("environment", "dev");
        CompletableFuture<WorkflowRunStatus> future = dagExecutionEngine.executeDag(
                runId, dsl, workspaceDir.getAbsolutePath(), inputs, 60
        );

        WorkflowRunStatus finalStatus = future.get(15, TimeUnit.SECONDS);
        assertThat(finalStatus).isEqualTo(WorkflowRunStatus.SUCCEEDED);

        List<StepRunView> stepRuns = executionApplication.listStepRuns(runId);
        Map<String, String> statusMap = stepRuns.stream()
                .collect(java.util.stream.Collectors.toMap(StepRunView::getNodeId, StepRunView::getStatus));

        // dev_deploy should SUCCEED
        assertThat(statusMap.get("dev_deploy")).isEqualTo("SUCCEEDED");

        // prod_deploy should be SKIPPED because condition evaluated to false
        assertThat(statusMap.get("prod_deploy")).isEqualTo("SKIPPED");

        // prod_audit should be cascade SKIPPED because its upstream predecessor was SKIPPED
        assertThat(statusMap.get("prod_audit")).isEqualTo("SKIPPED");

        // finish should SUCCEED because its joinPolicy is any_succeeded and dev_deploy SUCCEEDED
        assertThat(statusMap.get("finish")).isEqualTo("SUCCEEDED");

        // Check STEP_SKIPPED events
        List<RunEventView> events = executionApplication.listEvents(runId, 0L);
        assertThat(events.stream().anyMatch(e -> "STEP_SKIPPED".equals(e.getEventType()))).isTrue();
    }

    @Test
    @DisplayName("测试基于边条件表达式 (WorkflowEdgeDsl.condition) 的动态分支路由与跳过机制")
    void shouldRouteBranchViaEdgeConditions(@TempDir File workspaceDir) throws Exception {
        WorkflowRunView run = executionApplication.startRun(new StartRunCommand("proj-default", null, null));
        String runId = run.getId();

        WorkflowDsl dsl = new WorkflowDsl("wf-edge-conditions", "边条件动态分支路由测试");

        WorkflowNodeDsl startNode = new WorkflowNodeDsl("start", "起点", "START");
        WorkflowNodeDsl entNode = new WorkflowNodeDsl("enterprise_branch", "企业版通道", "AGENT", "MOCK", "企业版流程");
        WorkflowNodeDsl commNode = new WorkflowNodeDsl("community_branch", "社区版通道", "AGENT", "MOCK", "社区版流程");
        WorkflowNodeDsl joinNode = new WorkflowNodeDsl("notify", "通知归档", "AGENT", "MOCK", "完成通知");
        joinNode.setJoinPolicy("any_succeeded");

        dsl.setNodes(List.of(startNode, entNode, commNode, joinNode));
        dsl.setEdges(List.of(
                new WorkflowEdgeDsl("e1", "start", "enterprise_branch", "inputs.tier == 'enterprise'"),
                new WorkflowEdgeDsl("e2", "start", "community_branch", "inputs.tier == 'community'"),
                new WorkflowEdgeDsl("e3", "enterprise_branch", "notify", null),
                new WorkflowEdgeDsl("e4", "community_branch", "notify", null)
        ));

        // Execute with inputs.tier = 'enterprise'
        CompletableFuture<WorkflowRunStatus> future = dagExecutionEngine.executeDag(
                runId, dsl, workspaceDir.getAbsolutePath(), Map.of("tier", "enterprise"), 60
        );

        WorkflowRunStatus finalStatus = future.get(15, TimeUnit.SECONDS);
        assertThat(finalStatus).isEqualTo(WorkflowRunStatus.SUCCEEDED);

        List<StepRunView> stepRuns = executionApplication.listStepRuns(runId);
        Map<String, String> statusMap = stepRuns.stream()
                .collect(java.util.stream.Collectors.toMap(StepRunView::getNodeId, StepRunView::getStatus));

        assertThat(statusMap.get("enterprise_branch")).isEqualTo("SUCCEEDED");
        assertThat(statusMap.get("community_branch")).isEqualTo("SKIPPED");
        assertThat(statusMap.get("notify")).isEqualTo("SUCCEEDED");
    }
}
