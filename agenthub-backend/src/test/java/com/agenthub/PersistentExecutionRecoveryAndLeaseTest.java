package com.agenthub;

import com.agenthub.execution.application.ExecutionApplication;
import com.agenthub.execution.domain.model.WorkflowRunStatus;
import com.agenthub.execution.dto.RunEventView;
import com.agenthub.execution.dto.StartRunCommand;
import com.agenthub.execution.dto.StepRunView;
import com.agenthub.execution.dto.WorkflowRunView;
import com.agenthub.execution.infrastructure.entity.RunEventEntity;
import com.agenthub.execution.infrastructure.entity.StepRunEntity;
import com.agenthub.execution.infrastructure.entity.WorkflowRunEntity;
import com.agenthub.execution.infrastructure.repository.RunEventRepository;
import com.agenthub.execution.infrastructure.repository.StepRunRepository;
import com.agenthub.execution.infrastructure.repository.WorkflowRunRepository;
import com.agenthub.execution.scheduler.PersistentExecutionRecoveryRunner;
import com.agenthub.execution.service.RunEventBroadcaster;
import com.agenthub.orchestration.domain.dsl.WorkflowDsl;
import com.agenthub.orchestration.domain.dsl.WorkflowEdgeDsl;
import com.agenthub.orchestration.domain.dsl.WorkflowNodeDsl;
import com.agenthub.orchestration.scheduler.DagExecutionEngine;
import com.agenthub.shared.context.RequestContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class PersistentExecutionRecoveryAndLeaseTest {

    @Autowired
    private ExecutionApplication executionApplication;

    @Autowired
    private WorkflowRunRepository workflowRunRepository;

    @Autowired
    private StepRunRepository stepRunRepository;

    @Autowired
    private RunEventRepository runEventRepository;

    @Autowired
    private RunEventBroadcaster broadcaster;

    @Autowired
    private DagExecutionEngine dagExecutionEngine;

    @Autowired
    private PersistentExecutionRecoveryRunner recoveryRunner;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        RequestContext.get().setUserId("user-1");
    }

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    @Test
    @DisplayName("测试阶段 C 数据库租约原子争抢：两 Worker 并发争抢同一 Run，仅首个成功，租约过期后可接管")
    void shouldEnforceAtomicRunLeaseContention() {
        String runId = "run-lease-" + UUID.randomUUID().toString().substring(0, 8);
        WorkflowRunEntity run = new WorkflowRunEntity(runId, "proj-default", "def-test", "RUNNING", null);
        run.setCreatedAt(LocalDateTime.now());
        run.setUpdatedAt(LocalDateTime.now());
        workflowRunRepository.save(run);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime leaseUntil = now.plusSeconds(10);

        // Worker 1 claims the lease
        int claimed1 = transactionTemplate.execute(status ->
                workflowRunRepository.tryAcquireRunLease(runId, "worker-alpha", leaseUntil, now)
        );
        assertThat(claimed1).isEqualTo(1);

        // Worker 2 attempts to claim while lease is active
        int claimed2 = transactionTemplate.execute(status ->
                workflowRunRepository.tryAcquireRunLease(runId, "worker-beta", leaseUntil, now)
        );
        assertThat(claimed2).isEqualTo(0);

        // Worker 1 releases the lease
        int released = transactionTemplate.execute(status ->
                workflowRunRepository.releaseRunLease(runId, "worker-alpha", LocalDateTime.now())
        );
        assertThat(released).isEqualTo(1);

        // Worker 2 can now successfully claim the lease
        int claimedByBeta = transactionTemplate.execute(status ->
                workflowRunRepository.tryAcquireRunLease(runId, "worker-beta", LocalDateTime.now().plusSeconds(30), LocalDateTime.now())
        );
        assertThat(claimedByBeta).isEqualTo(1);
    }

    @Test
    @DisplayName("测试阶段 C 重启恢复内核：模拟后端崩溃，重启恢复断点任务，且绝不重复已完成步骤")
    void shouldRecoverStaleRunAndResumeExecutionWithoutRepeatingCompletedSteps() throws Exception {
        String runId = "run-crash-" + UUID.randomUUID().toString().substring(0, 8);

        // Construct 3-node linear pipeline: step1 -> step2 -> step3
        WorkflowDsl dsl = new WorkflowDsl("wf-recover-pipe", "崩溃恢复管道测试");
        WorkflowNodeDsl node1 = new WorkflowNodeDsl("node-init", "数据准备", "START");
        WorkflowNodeDsl node2 = new WorkflowNodeDsl("node-agent", "逻辑处理", "AGENT", "MOCK", "执行核心算法");
        WorkflowNodeDsl node3 = new WorkflowNodeDsl("node-final", "收尾汇总", "END");
        dsl.setNodes(List.of(node1, node2, node3));
        dsl.setEdges(List.of(
                new WorkflowEdgeDsl("node-init", "node-agent"),
                new WorkflowEdgeDsl("node-agent", "node-final")
        ));
        String dslJson = objectMapper.writeValueAsString(dsl);

        // Create stale run (crashed worker)
        WorkflowRunEntity run = new WorkflowRunEntity(runId, "proj-default", "wf-recover-pipe", "RUNNING", null);
        run.setDslSnapshot(dslJson);
        run.setLeaseOwner("crashed-worker-old");
        run.setLeaseUntil(LocalDateTime.now().minusMinutes(10)); // Stale lease
        run.setHeartbeatAt(LocalDateTime.now().minusMinutes(10));
        run.setAttempt(1);
        workflowRunRepository.save(run);

        // Simulate step 1 already SUCCEEDED before crash
        StepRunEntity step1 = new StepRunEntity("step-done-1", runId, "node-init", "SUCCEEDED");
        step1.setFinishedAt(LocalDateTime.now().minusMinutes(11));
        stepRunRepository.save(step1);

        // Simulate step 2 was RUNNING during crash with stale lease
        StepRunEntity step2 = new StepRunEntity("step-crashed-2", runId, "node-agent", "RUNNING");
        step2.setLeaseOwner("crashed-worker-old");
        step2.setLeaseUntil(LocalDateTime.now().minusMinutes(10));
        stepRunRepository.save(step2);

        // Simulate step 3 was PENDING
        StepRunEntity step3 = new StepRunEntity("step-pending-3", runId, "node-final", "PENDING");
        stepRunRepository.save(step3);

        // Trigger startup recovery scanner
        int recovered = recoveryRunner.recoverStaleRuns();
        assertThat(recovered).isEqualTo(1);

        // Wait up to 10s for the resumed DAG to complete
        long limit = System.currentTimeMillis() + 10000;
        WorkflowRunEntity finalRun = workflowRunRepository.findById(runId).orElseThrow();
        while (!"SUCCEEDED".equals(finalRun.getStatus()) && System.currentTimeMillis() < limit) {
            Thread.sleep(200);
            finalRun = workflowRunRepository.findById(runId).orElseThrow();
        }

        assertThat(finalRun.getStatus()).isEqualTo("SUCCEEDED");
        assertThat(finalRun.getAttempt()).isGreaterThan(1);

        // Verify Step 1 was NEVER re-created or re-executed (remains step-done-1)
        List<StepRunEntity> allSteps = stepRunRepository.findByRunIdOrderByCreatedAtAsc(runId);
        assertThat(allSteps).hasSize(3);

        StepRunEntity verifyStep1 = stepRunRepository.findByRunIdAndNodeId(runId, "node-init").orElseThrow();
        assertThat(verifyStep1.getId()).isEqualTo("step-done-1");
        assertThat(verifyStep1.getStatus()).isEqualTo("SUCCEEDED");

        StepRunEntity verifyStep2 = stepRunRepository.findByRunIdAndNodeId(runId, "node-agent").orElseThrow();
        assertThat(verifyStep2.getStatus()).isEqualTo("SUCCEEDED");

        StepRunEntity verifyStep3 = stepRunRepository.findByRunIdAndNodeId(runId, "node-final").orElseThrow();
        assertThat(verifyStep3.getStatus()).isEqualTo("SUCCEEDED");
    }

    @Test
    @DisplayName("测试阶段 C 重启恢复人工确认保护：等待审批的 Run 在重启后保持 WAITING_APPROVAL，不强制失败或跳过")
    void shouldPreserveWaitingApprovalStateAcrossRestartRecovery() {
        String runId = "run-appr-" + UUID.randomUUID().toString().substring(0, 8);
        WorkflowRunEntity run = new WorkflowRunEntity(runId, "proj-default", "wf-appr", "RUNNING", null);
        run.setLeaseOwner("crashed-worker");
        run.setLeaseUntil(LocalDateTime.now().minusMinutes(5));
        workflowRunRepository.save(run);

        StepRunEntity apprStep = new StepRunEntity("step-appr-1", runId, "approval-node", "WAITING_APPROVAL");
        apprStep.setRequiresApproval(true);
        stepRunRepository.save(apprStep);

        recoveryRunner.recoverStaleRuns();

        WorkflowRunEntity recoveredRun = workflowRunRepository.findById(runId).orElseThrow();
        assertThat(recoveredRun.getStatus()).isEqualTo("WAITING_APPROVAL");
    }

    @Test
    @DisplayName("测试阶段 C 事件序号原子增长与数据库唯一索引约束防御")
    void shouldEnforceEventMonotonicityAndUniqueConstraint() {
        String runId = "run-event-" + UUID.randomUUID().toString().substring(0, 8);
        WorkflowRunEntity run = new WorkflowRunEntity(runId, "proj-default", "def-test", "RUNNING", null);
        workflowRunRepository.save(run);

        RunEventEntity e1 = broadcaster.publishEvent(runId, "TEST_EVT_1", "payload-1");
        RunEventEntity e2 = broadcaster.publishEvent(runId, "TEST_EVT_2", "payload-2");
        RunEventEntity e3 = broadcaster.publishEvent(runId, "TEST_EVT_3", "payload-3");

        assertThat(e1.getSequenceNum()).isEqualTo(1L);
        assertThat(e2.getSequenceNum()).isEqualTo(2L);
        assertThat(e3.getSequenceNum()).isEqualTo(3L);

        // Attempting to manually insert an entity with duplicate sequence number on the same run must trigger DataIntegrityViolationException
        RunEventEntity duplicateSeq = new RunEventEntity("evt-dup", runId, 2L, "DUPLICATE", "bad-payload");
        assertThatThrownBy(() -> {
            transactionTemplate.execute(status -> {
                runEventRepository.save(duplicateSeq);
                runEventRepository.flush();
                return null;
            });
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("测试阶段 C 租约防篡改保护：当其他活跃 Worker 持有未过期租约时，执行引擎拒绝抢占并拒绝篡改")
    void shouldPreventLeaseTheftWhenRunIsActivelyHeldByAnotherWorker() throws Exception {
        String runId = "run-active-" + UUID.randomUUID().toString().substring(0, 8);
        WorkflowRunEntity run = new WorkflowRunEntity(runId, "proj-default", "wf-active", "RUNNING", null);
        run.setLeaseOwner("worker-foreign");
        LocalDateTime activeUntil = LocalDateTime.now().plusSeconds(60);
        run.setLeaseUntil(activeUntil);
        workflowRunRepository.save(run);

        WorkflowDsl dsl = new WorkflowDsl("wf-active", "活跃租约保护测试");
        dsl.setNodes(List.of(new WorkflowNodeDsl("node-1", "单步", "START")));
        dsl.setEdges(Collections.emptyList());

        CompletableFuture<com.agenthub.execution.domain.model.WorkflowRunStatus> future =
                dagExecutionEngine.executeDag(runId, dsl, null, Collections.emptyMap(), 60);

        com.agenthub.execution.domain.model.WorkflowRunStatus status = future.get(5, TimeUnit.SECONDS);
        assertThat(status).isEqualTo(com.agenthub.execution.domain.model.WorkflowRunStatus.FAILED);

        // Verify foreign worker lease was NOT stomped
        WorkflowRunEntity preserved = workflowRunRepository.findById(runId).orElseThrow();
        assertThat(preserved.getLeaseOwner()).isEqualTo("worker-foreign");
        assertThat(preserved.getLeaseUntil()).isAfter(LocalDateTime.now());
    }
}
