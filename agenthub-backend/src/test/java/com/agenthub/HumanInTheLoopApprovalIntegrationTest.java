package com.agenthub;

import com.agenthub.execution.application.ExecutionApplication;
import com.agenthub.execution.domain.model.WorkflowRunStatus;
import com.agenthub.execution.dto.RunEventView;
import com.agenthub.execution.dto.StartRunCommand;
import com.agenthub.execution.dto.StepRunView;
import com.agenthub.execution.dto.WorkflowRunView;
import com.agenthub.orchestration.application.ApprovalApplication;
import com.agenthub.orchestration.domain.dsl.WorkflowDsl;
import com.agenthub.orchestration.domain.dsl.WorkflowEdgeDsl;
import com.agenthub.orchestration.domain.dsl.WorkflowNodeDsl;
import com.agenthub.orchestration.dto.ApprovalDecisionCommand;
import com.agenthub.orchestration.dto.ApprovalView;
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
class HumanInTheLoopApprovalIntegrationTest {

    @Autowired
    private DagExecutionEngine dagExecutionEngine;

    @Autowired
    private ExecutionApplication executionApplication;

    @Autowired
    private ApprovalApplication approvalApplication;

    @BeforeEach
    void setUp() {
        RequestContext.get().setUserId("user-1");
    }

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    @Test
    @DisplayName("测试人工审批通过场景：执行至审批节点自动挂起 WAITING_APPROVAL，审批通过后恢复 DAG 调度")
    void shouldSuspendAndResumeOnApprovalApproved(@TempDir File workspaceDir) throws Exception {
        WorkflowRunView run = executionApplication.startRun(new StartRunCommand("proj-default", null, null));
        String runId = run.getId();

        WorkflowDsl dsl = new WorkflowDsl("wf-approval-pass", "审批恢复测试");

        WorkflowNodeDsl devNode = new WorkflowNodeDsl("dev_step", "开发编码", "AGENT", "MOCK", "开发功能代码");

        WorkflowNodeDsl approvalNode = new WorkflowNodeDsl("human_gate", "上线人工门禁", "APPROVAL");
        approvalNode.setRequiresApproval(true);

        WorkflowNodeDsl deployNode = new WorkflowNodeDsl("deploy_step", "发布生产", "AGENT", "MOCK", "执行生产发布");

        dsl.setNodes(List.of(devNode, approvalNode, deployNode));
        dsl.setEdges(List.of(
                new WorkflowEdgeDsl("dev_step", "human_gate"),
                new WorkflowEdgeDsl("human_gate", "deploy_step")
        ));

        CompletableFuture<WorkflowRunStatus> future = dagExecutionEngine.executeDag(
                runId, dsl, workspaceDir.getAbsolutePath(), Map.of(), 60
        );

        // Wait for workflow to reach WAITING_APPROVAL
        ApprovalView pendingApproval = null;
        for (int i = 0; i < 50; i++) {
            List<ApprovalView> approvals = approvalApplication.listApprovalsByRunId(runId);
            if (!approvals.isEmpty() && "PENDING".equalsIgnoreCase(approvals.get(0).getStatus())) {
                pendingApproval = approvals.get(0);
                break;
            }
            Thread.sleep(100);
        }

        assertThat(pendingApproval).isNotNull();
        assertThat(pendingApproval.getStatus()).isEqualTo("PENDING");

        // Verify Run & Step states in database
        WorkflowRunView currentRun = executionApplication.getRunById(runId);
        assertThat(currentRun.getStatus()).isEqualTo("WAITING_APPROVAL");

        List<StepRunView> stepRuns = executionApplication.listStepRuns(runId);
        StepRunView approvalStep = stepRuns.stream()
                .filter(s -> "human_gate".equals(s.getNodeId()))
                .findFirst().orElseThrow();
        assertThat(approvalStep.getStatus()).isEqualTo("WAITING_APPROVAL");

        // Approve the request
        ApprovalView approved = approvalApplication.approve(pendingApproval.getId(), "通过架构委员会审核");
        assertThat(approved.getStatus()).isEqualTo("APPROVED");

        // DAG should resume and finish with SUCCEEDED
        WorkflowRunStatus finalStatus = future.get(15, TimeUnit.SECONDS);
        assertThat(finalStatus).isEqualTo(WorkflowRunStatus.SUCCEEDED);

        WorkflowRunView finalRun = executionApplication.getRunById(runId);
        assertThat(finalRun.getStatus()).isEqualTo("SUCCEEDED");

        List<StepRunView> finalSteps = executionApplication.listStepRuns(runId);
        for (StepRunView step : finalSteps) {
            assertThat(step.getStatus()).isEqualTo("SUCCEEDED");
        }

        // Verify events
        List<RunEventView> events = executionApplication.listEvents(runId, 0L);
        assertThat(events.stream().anyMatch(e -> "APPROVAL_REQUESTED".equals(e.getEventType()))).isTrue();
        assertThat(events.stream().anyMatch(e -> "APPROVAL_DECIDED".equals(e.getEventType()))).isTrue();
    }

    @Test
    @DisplayName("测试人工审批拒绝场景：拒绝后标记 Step 与 Run 失败，后续节点被短路拦截")
    void shouldAbortWorkflowOnApprovalRejected(@TempDir File workspaceDir) throws Exception {
        WorkflowRunView run = executionApplication.startRun(new StartRunCommand("proj-default", null, null));
        String runId = run.getId();

        WorkflowDsl dsl = new WorkflowDsl("wf-approval-reject", "审批拒绝测试");

        WorkflowNodeDsl devNode = new WorkflowNodeDsl("dev_step", "开发编码", "AGENT", "MOCK", "开发功能代码");

        WorkflowNodeDsl approvalNode = new WorkflowNodeDsl("human_gate", "上线人工门禁", "APPROVAL");
        approvalNode.setRequiresApproval(true);

        WorkflowNodeDsl deployNode = new WorkflowNodeDsl("deploy_step", "发布生产", "AGENT", "MOCK", "执行生产发布");

        dsl.setNodes(List.of(devNode, approvalNode, deployNode));
        dsl.setEdges(List.of(
                new WorkflowEdgeDsl("dev_step", "human_gate"),
                new WorkflowEdgeDsl("human_gate", "deploy_step")
        ));

        CompletableFuture<WorkflowRunStatus> future = dagExecutionEngine.executeDag(
                runId, dsl, workspaceDir.getAbsolutePath(), Map.of(), 60
        );

        // Wait for workflow to reach WAITING_APPROVAL
        ApprovalView pendingApproval = null;
        for (int i = 0; i < 50; i++) {
            List<ApprovalView> approvals = approvalApplication.listApprovalsByRunId(runId);
            if (!approvals.isEmpty() && "PENDING".equalsIgnoreCase(approvals.get(0).getStatus())) {
                pendingApproval = approvals.get(0);
                break;
            }
            Thread.sleep(100);
        }

        assertThat(pendingApproval).isNotNull();

        // Reject the request
        ApprovalView rejected = approvalApplication.reject(pendingApproval.getId(), "安全扫描存在高危漏洞，禁止上线");
        assertThat(rejected.getStatus()).isEqualTo("REJECTED");

        // DAG should terminate with FAILED
        WorkflowRunStatus finalStatus = future.get(15, TimeUnit.SECONDS);
        assertThat(finalStatus).isEqualTo(WorkflowRunStatus.FAILED);

        WorkflowRunView finalRun = executionApplication.getRunById(runId);
        assertThat(finalRun.getStatus()).isEqualTo("FAILED");
    }

    @Test
    @DisplayName("测试审批挂起期间协作取消：Step 状态正确流转为 CANCELLED 而非伪报 FAILED")
    void shouldCancelWorkflowGracefullyWhileWaitingForApproval(@TempDir File workspaceDir) throws Exception {
        WorkflowRunView run = executionApplication.startRun(new StartRunCommand("proj-default", null, null));
        String runId = run.getId();

        WorkflowDsl dsl = new WorkflowDsl("wf-approval-cancel", "审批取消测试");
        WorkflowNodeDsl devNode = new WorkflowNodeDsl("dev_step", "开发编码", "AGENT", "MOCK", "开发功能代码");
        WorkflowNodeDsl approvalNode = new WorkflowNodeDsl("human_gate", "上线人工门禁", "APPROVAL");
        approvalNode.setRequiresApproval(true);

        dsl.setNodes(List.of(devNode, approvalNode));
        dsl.setEdges(List.of(new WorkflowEdgeDsl("dev_step", "human_gate")));

        CompletableFuture<WorkflowRunStatus> future = dagExecutionEngine.executeDag(
                runId, dsl, workspaceDir.getAbsolutePath(), Map.of(), 60
        );

        // Wait for workflow to reach WAITING_APPROVAL
        for (int i = 0; i < 50; i++) {
            List<ApprovalView> approvals = approvalApplication.listApprovalsByRunId(runId);
            if (!approvals.isEmpty() && "PENDING".equalsIgnoreCase(approvals.get(0).getStatus())) {
                break;
            }
            Thread.sleep(100);
        }

        // Cancel while waiting for approval
        executionApplication.cancelRun(runId, "用户主动放弃并取消上线审批");

        WorkflowRunStatus finalStatus = future.get(10, TimeUnit.SECONDS);
        assertThat(finalStatus).isEqualTo(WorkflowRunStatus.CANCELLED);

        WorkflowRunView finalRun = executionApplication.getRunById(runId);
        assertThat(finalRun.getStatus()).isEqualTo("CANCELLED");

        List<StepRunView> stepRuns = executionApplication.listStepRuns(runId);
        StepRunView approvalStep = stepRuns.stream()
                .filter(s -> "human_gate".equals(s.getNodeId()))
                .findFirst().orElseThrow();
        assertThat(approvalStep.getStatus()).isEqualTo("CANCELLED");
    }
}
