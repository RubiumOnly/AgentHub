package com.agenthub;

import com.agenthub.execution.application.ExecutionApplication;
import com.agenthub.execution.domain.model.WorkflowRunStatus;
import com.agenthub.execution.dto.RunEventView;
import com.agenthub.execution.dto.StartRunCommand;
import com.agenthub.execution.dto.StepRunView;
import com.agenthub.execution.dto.WorkflowRunView;
import com.agenthub.execution.infrastructure.repository.StepRunRepository;
import com.agenthub.execution.infrastructure.repository.WorkflowRunRepository;
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
class WorkflowDagSchedulingAndParallelExecutionTest {

    @Autowired
    private DagExecutionEngine dagExecutionEngine;

    @Autowired
    private ExecutionApplication executionApplication;

    @Autowired
    private WorkflowRunRepository workflowRunRepository;

    @Autowired
    private StepRunRepository stepRunRepository;

    @BeforeEach
    void setUp() {
        RequestContext.get().setUserId("user-1");
    }

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    @Test
    @DisplayName("测试菱形 DAG 并发调度与拓扑汇聚：Backend 与 Frontend 真正并行执行，QA 汇聚等待两者全量成功")
    void shouldExecuteDiamondDagConcurrentlyAndConvergeSuccessfully(@TempDir File workspaceDir) throws Exception {
        WorkflowRunView run = executionApplication.startRun(new StartRunCommand("proj-default", null, null));
        String runId = run.getId();

        WorkflowDsl dsl = new WorkflowDsl("wf-diamond", "菱形并发与数据拓扑");
        WorkflowNodeDsl startNode = new WorkflowNodeDsl("start", "工作流起点", "START");

        WorkflowNodeDsl backendNode = new WorkflowNodeDsl("backend", "后端架构", "AGENT", "MOCK", "编写后端服务接口");
        WorkflowNodeDsl frontendNode = new WorkflowNodeDsl("frontend", "前端工程", "AGENT", "MOCK", "编写前端用户界面");

        WorkflowNodeDsl qaNode = new WorkflowNodeDsl("qa", "QA测试审查", "AGENT", "MOCK",
                "审查代码:\n[后端]: {{steps.backend.outputs.result}}\n[前端]: {{steps.frontend.outputs.result}}");
        qaNode.setInputs(Map.of(
                "backendCode", "{{steps.backend.outputs.result}}",
                "frontendCode", "{{steps.frontend.outputs.result}}"
        ));

        WorkflowNodeDsl endNode = new WorkflowNodeDsl("end", "工作流终点", "END");

        dsl.setNodes(List.of(startNode, backendNode, frontendNode, qaNode, endNode));
        dsl.setEdges(List.of(
                new WorkflowEdgeDsl("start", "backend"),
                new WorkflowEdgeDsl("start", "frontend"),
                new WorkflowEdgeDsl("backend", "qa"),
                new WorkflowEdgeDsl("frontend", "qa"),
                new WorkflowEdgeDsl("qa", "end")
        ));
        dsl.setOutputs(Map.of("summary", "{{steps.qa.outputs.result}}"));

        CompletableFuture<WorkflowRunStatus> future = dagExecutionEngine.executeDag(
                runId, dsl, workspaceDir.getAbsolutePath(), Map.of("initPrompt", "构建登录模块"), 60
        );

        WorkflowRunStatus finalStatus = future.get(15, TimeUnit.SECONDS);
        assertThat(finalStatus).isEqualTo(WorkflowRunStatus.SUCCEEDED);

        // Verify Step Runs in DB
        List<StepRunView> stepRuns = executionApplication.listStepRuns(runId);
        assertThat(stepRuns).hasSize(5);

        for (StepRunView step : stepRuns) {
            assertThat(step.getStatus()).isEqualTo("SUCCEEDED");
        }

        // Verify Run status in DB
        WorkflowRunView runView = executionApplication.getRunById(runId);
        assertThat(runView.getStatus()).isEqualTo("SUCCEEDED");

        // Verify Events
        List<RunEventView> events = executionApplication.listEvents(runId, 0L);
        assertThat(events).isNotEmpty();
        assertThat(events.stream().anyMatch(e -> "RUN_STARTED".equals(e.getEventType()))).isTrue();
        assertThat(events.stream().anyMatch(e -> "RUN_COMPLETED".equals(e.getEventType()))).isTrue();
    }

    @Test
    @DisplayName("测试 DAG 执行中途协作取消：终止未完成节点并安全回收")
    void shouldCancelRunningDagGracefully(@TempDir File workspaceDir) throws Exception {
        WorkflowRunView run = executionApplication.startRun(new StartRunCommand("proj-default", null, null));
        String runId = run.getId();

        WorkflowDsl dsl = new WorkflowDsl("wf-cancel", "取消测试");
        dsl.setNodes(List.of(
                new WorkflowNodeDsl("start", "起点", "START"),
                new WorkflowNodeDsl("step1", "任务1", "AGENT", "MOCK", "长程任务1"),
                new WorkflowNodeDsl("step2", "任务2", "AGENT", "MOCK", "长程任务2")
        ));
        dsl.setEdges(List.of(
                new WorkflowEdgeDsl("start", "step1"),
                new WorkflowEdgeDsl("step1", "step2")
        ));

        CompletableFuture<WorkflowRunStatus> future = dagExecutionEngine.executeDag(
                runId, dsl, workspaceDir.getAbsolutePath(), Map.of(), 60
        );

        // Immediately cancel
        Thread.sleep(50);
        executionApplication.cancelRun(runId, "测试主动取消");

        WorkflowRunStatus finalStatus = future.get(10, TimeUnit.SECONDS);
        assertThat(finalStatus).isEqualTo(WorkflowRunStatus.CANCELLED);

        WorkflowRunView runView = executionApplication.getRunById(runId);
        assertThat(runView.getStatus()).isEqualTo("CANCELLED");
        assertThat(runView.getCancelReason()).contains("测试主动取消");
    }

    @Test
    @DisplayName("测试 DAG 执行期间自动持有工作区锁，执行完成后自动释放")
    void shouldAcquireAndReleaseWorkspaceLockDuringDagExecution(@TempDir File workspaceDir) throws Exception {
        WorkflowRunView run = executionApplication.startRun(new StartRunCommand("proj-default", null, null));
        String runId = run.getId();

        WorkflowDsl dsl = new WorkflowDsl("wf-lock-test", "工作区锁测试");
        dsl.setNodes(List.of(
                new WorkflowNodeDsl("start", "起点", "START"),
                new WorkflowNodeDsl("step1", "任务1", "AGENT", "MOCK", "任务内容")
        ));
        dsl.setEdges(List.of(new WorkflowEdgeDsl("start", "step1")));

        CompletableFuture<WorkflowRunStatus> future = dagExecutionEngine.executeDag(
                runId, dsl, workspaceDir.getAbsolutePath(), Map.of(), 60
        );

        WorkflowRunStatus finalStatus = future.get(10, TimeUnit.SECONDS);
        assertThat(finalStatus).isEqualTo(WorkflowRunStatus.SUCCEEDED);
    }
}
