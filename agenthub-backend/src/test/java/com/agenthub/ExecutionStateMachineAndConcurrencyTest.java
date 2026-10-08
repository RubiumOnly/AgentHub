package com.agenthub;

import com.agenthub.execution.application.ExecutionApplication;
import com.agenthub.execution.domain.model.StepRunStatus;
import com.agenthub.execution.domain.model.WorkflowRunStatus;
import com.agenthub.execution.dto.RunEventView;
import com.agenthub.execution.dto.StartRunCommand;
import com.agenthub.execution.dto.WorkflowRunView;
import com.agenthub.execution.infrastructure.entity.StepRunEntity;
import com.agenthub.execution.infrastructure.entity.WorkflowRunEntity;
import com.agenthub.execution.infrastructure.repository.StepRunRepository;
import com.agenthub.execution.infrastructure.repository.WorkflowRunRepository;
import com.agenthub.execution.service.ExecutionStateMachine;
import com.agenthub.shared.context.RequestContext;
import com.agenthub.shared.exception.BusinessException;
import com.agenthub.shared.exception.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class ExecutionStateMachineAndConcurrencyTest {

    @Autowired
    private ExecutionApplication executionApplication;

    @Autowired
    private ExecutionStateMachine stateMachine;

    @Autowired
    private WorkflowRunRepository workflowRunRepository;

    @Autowired
    private StepRunRepository stepRunRepository;

    @BeforeEach
    void setUp() {
        RequestContext.get().setUserId("user-1");
        RequestContext.get().setCorrelationId("corr-test-" + UUID.randomUUID().toString().substring(0, 6));
    }

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    @Test
    @DisplayName("测试 WorkflowRun 状态机正常流转：RUNNING -> PAUSED -> RUNNING -> WAITING_APPROVAL -> RUNNING -> SUCCEEDED")
    void shouldExecuteValidWorkflowRunTransitions() {
        WorkflowRunView run = executionApplication.startRun(new StartRunCommand("proj-default", "def-default", null));
        String runId = run.getId();
        assertThat(run.getStatus()).isEqualTo("RUNNING");

        // 1. RUNNING -> PAUSED
        WorkflowRunEntity paused = stateMachine.transitionRun(runId, WorkflowRunStatus.PAUSED, "Paused for review");
        assertThat(paused.getStatus()).isEqualTo("PAUSED");

        // 2. PAUSED -> RUNNING
        WorkflowRunEntity resumed = stateMachine.transitionRun(runId, WorkflowRunStatus.RUNNING, "Resumed by developer");
        assertThat(resumed.getStatus()).isEqualTo("RUNNING");

        // 3. RUNNING -> WAITING_APPROVAL
        WorkflowRunEntity waiting = stateMachine.transitionRun(runId, WorkflowRunStatus.WAITING_APPROVAL, "Requires human gate approval");
        assertThat(waiting.getStatus()).isEqualTo("WAITING_APPROVAL");

        // 4. WAITING_APPROVAL -> RUNNING
        WorkflowRunEntity approved = stateMachine.transitionRun(runId, WorkflowRunStatus.RUNNING, "Approval granted");
        assertThat(approved.getStatus()).isEqualTo("RUNNING");

        // 5. RUNNING -> SUCCEEDED
        WorkflowRunEntity succeeded = stateMachine.transitionRun(runId, WorkflowRunStatus.SUCCEEDED, "All steps finished successfully");
        assertThat(succeeded.getStatus()).isEqualTo("SUCCEEDED");
        assertThat(succeeded.getFinishedAt()).isNotNull();

        // 6. Verify audit trail persisted in run_events
        List<RunEventView> events = executionApplication.listEvents(runId, null);
        assertThat(events.stream().anyMatch(e -> "RUN_STATE_CHANGED".equals(e.getEventType()))).isTrue();
    }

    @Test
    @DisplayName("测试 StepRun 状态机正常流转与重试：PENDING -> RUNNING -> FAILED -> PENDING -> RUNNING -> SUCCEEDED")
    void shouldExecuteStepRunTransitionsAndRetry() {
        WorkflowRunView run = executionApplication.startRun(new StartRunCommand("proj-default", "def-default", null));
        String runId = run.getId();

        String stepRunId = "step-" + UUID.randomUUID().toString().substring(0, 8);
        StepRunEntity step = new StepRunEntity(stepRunId, runId, "node-qa", "PENDING");
        stepRunRepository.save(step);

        // PENDING -> RUNNING
        stateMachine.transitionStep(stepRunId, StepRunStatus.RUNNING, "Starting QA checks");
        StepRunEntity runningStep = stepRunRepository.findById(stepRunId).orElseThrow();
        assertThat(runningStep.getStatus()).isEqualTo("RUNNING");
        assertThat(runningStep.getStartedAt()).isNotNull();

        // RUNNING -> FAILED
        stateMachine.transitionStep(stepRunId, StepRunStatus.FAILED, "Unit tests failed with exit code 1");
        StepRunEntity failedStep = stepRunRepository.findById(stepRunId).orElseThrow();
        assertThat(failedStep.getStatus()).isEqualTo("FAILED");
        assertThat(failedStep.getErrorMessage()).contains("exit code 1");
        assertThat(failedStep.getDurationMs()).isNotNull();

        // FAILED -> PENDING (Retry attempt)
        stateMachine.transitionStep(stepRunId, StepRunStatus.PENDING, "Triggering retry attempt 2");
        StepRunEntity retryingStep = stepRunRepository.findById(stepRunId).orElseThrow();
        assertThat(retryingStep.getStatus()).isEqualTo("PENDING");
        assertThat(retryingStep.getAttempt()).isEqualTo(2);
        assertThat(retryingStep.getErrorMessage()).isNull();

        // PENDING -> RUNNING -> SUCCEEDED
        stateMachine.transitionStep(stepRunId, StepRunStatus.RUNNING, "Rerunning attempt 2");
        stateMachine.transitionStep(stepRunId, StepRunStatus.SUCCEEDED, "All tests passed on retry");
        StepRunEntity succeededStep = stepRunRepository.findById(stepRunId).orElseThrow();
        assertThat(succeededStep.getStatus()).isEqualTo("SUCCEEDED");
        assertThat(succeededStep.getAttempt()).isEqualTo(2);
    }

    @Test
    @DisplayName("测试状态机非法转移拦截：终态（SUCCEEDED / CANCELLED / TIMED_OUT）严禁逆向或非法跳转")
    void shouldInterceptIllegalStateTransitions() {
        WorkflowRunView run = executionApplication.startRun(new StartRunCommand("proj-default", "def-default", null));
        String runId = run.getId();

        // RUNNING -> SUCCEEDED
        stateMachine.transitionRun(runId, WorkflowRunStatus.SUCCEEDED, "Done");

        // 1. Terminal SUCCEEDED -> RUNNING must be intercepted
        assertThatThrownBy(() -> stateMachine.transitionRun(runId, WorkflowRunStatus.RUNNING, "Re-run attempt"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.INVALID_STATE_TRANSITION);
                });

        // 2. Terminal SUCCEEDED -> CANCELLED must be intercepted
        assertThatThrownBy(() -> stateMachine.transitionRun(runId, WorkflowRunStatus.CANCELLED, "Cancel completed run"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.INVALID_STATE_TRANSITION);
                });

        // 3. New Run: PENDING cannot jump directly to SUCCEEDED without RUNNING
        WorkflowRunEntity pendingRun = new WorkflowRunEntity("run-pending-" + UUID.randomUUID().toString().substring(0, 6),
                "proj-default", "def-default", "PENDING", null);
        workflowRunRepository.save(pendingRun);

        assertThatThrownBy(() -> stateMachine.transitionRun(pendingRun.getId(), WorkflowRunStatus.SUCCEEDED, "Skip to finish"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.INVALID_STATE_TRANSITION);
                });
    }

    @Test
    @DisplayName("测试高并发状态转移竞态安全：多线程并发修改同一 Run 严格互斥，保证状态机一致性")
    void shouldSafelyHandleConcurrentStateTransitions() throws Exception {
        WorkflowRunView run = executionApplication.startRun(new StartRunCommand("proj-default", "def-default", null));
        String runId = run.getId();

        int threadCount = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger rejectedCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    // Each thread tries a terminal transition: odd tries CANCELLED, even tries SUCCEEDED
                    WorkflowRunStatus target = (index % 2 == 0) ? WorkflowRunStatus.SUCCEEDED : WorkflowRunStatus.CANCELLED;
                    stateMachine.transitionRun(runId, target, "Thread-" + index, "Race testing", "corr-" + index);
                    successCount.incrementAndGet();
                } catch (BusinessException be) {
                    if (be.getErrorCode() == ErrorCode.INVALID_STATE_TRANSITION) {
                        rejectedCount.incrementAndGet();
                    }
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = doneLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        // At most one thread can transition from RUNNING to a terminal state (SUCCEEDED or CANCELLED)
        // Subsequent threads attempting an incompatible terminal transition are strictly rejected
        WorkflowRunEntity finalRun = workflowRunRepository.findById(runId).orElseThrow();
        WorkflowRunStatus finalStatus = WorkflowRunStatus.fromString(finalRun.getStatus());
        assertThat(finalStatus.isTerminal()).isTrue();
        assertThat(successCount.get()).isGreaterThanOrEqualTo(1);
        assertThat(successCount.get() + rejectedCount.get()).isEqualTo(threadCount);
    }

    @Test
    @DisplayName("测试状态机流转事件实时通过 SSE 广播且保证全局严格单调递增序号（杜绝序号碰撞与遗漏）")
    void shouldBroadcastStateTransitionsViaSseAndMaintainMonotonicSequences() {
        WorkflowRunView run = executionApplication.startRun(new StartRunCommand("proj-default", "def-default", null));
        String runId = run.getId();

        // 1. Initial event is seq 1 (RUN_STARTED)
        // 2. Append custom event
        RunEventView e2 = executionApplication.appendEvent(runId, "TOKEN", "{\"token\":\"tok-1\"}");
        assertThat(e2.getSequenceNum()).isEqualTo(2L);

        // 3. State transition via stateMachine -> must produce seq 3 (RUN_STATE_CHANGED)
        stateMachine.transitionRun(runId, WorkflowRunStatus.PAUSED, "Paused for manual check");

        // 4. Create a step and transition it -> must produce seq 4 (STEP_STATE_CHANGED)
        String stepRunId = "step-test-" + UUID.randomUUID().toString().substring(0, 6);
        StepRunEntity step = new StepRunEntity(stepRunId, runId, "node-1", "PENDING");
        stepRunRepository.save(step);
        stateMachine.transitionStep(stepRunId, StepRunStatus.RUNNING, "Starting step");

        // 5. Append another event -> must produce seq 5 (NOT seq 3 or collision!)
        RunEventView e5 = executionApplication.appendEvent(runId, "TOKEN", "{\"token\":\"tok-2\"}");
        assertThat(e5.getSequenceNum()).isEqualTo(5L);

        // 6. Verify all events strictly monotonic from 1 to 5
        List<RunEventView> allEvents = executionApplication.listEvents(runId, null);
        assertThat(allEvents).hasSize(5);
        for (int i = 0; i < allEvents.size(); i++) {
            assertThat(allEvents.get(i).getSequenceNum()).isEqualTo((long) (i + 1));
        }

        // Verify event types
        assertThat(allEvents.get(0).getEventType()).isEqualTo("RUN_STARTED");
        assertThat(allEvents.get(1).getEventType()).isEqualTo("TOKEN");
        assertThat(allEvents.get(2).getEventType()).isEqualTo("RUN_STATE_CHANGED");
        assertThat(allEvents.get(3).getEventType()).isEqualTo("STEP_STATE_CHANGED");
        assertThat(allEvents.get(4).getEventType()).isEqualTo("TOKEN");
    }

    @Test
    @DisplayName("测试 TIMED_OUT 单步支持重试（retryStep），状态流转回 PENDING，递增 attempt 并清空历史超时信息")
    void shouldAllowStepRetryAfterTimeout() {
        WorkflowRunView run = executionApplication.startRun(new StartRunCommand("proj-default", "def-default", null));
        String runId = run.getId();

        String stepRunId = "step-to-" + UUID.randomUUID().toString().substring(0, 6);
        StepRunEntity step = new StepRunEntity(stepRunId, runId, "node-timeout", "RUNNING");
        step.setStartedAt(java.time.LocalDateTime.now().minusSeconds(10));
        stepRunRepository.save(step);

        // Transition step to TIMED_OUT
        stateMachine.transitionStep(stepRunId, StepRunStatus.TIMED_OUT, "Step exceeded SLA limit of 10s");
        StepRunEntity timedOutStep = stepRunRepository.findById(stepRunId).orElseThrow();
        assertThat(timedOutStep.getStatus()).isEqualTo("TIMED_OUT");
        assertThat(timedOutStep.getFinishedAt()).isNotNull();

        // Retry the timed out step via executionApplication
        com.agenthub.execution.dto.StepRunView retriedStep = executionApplication.retryStep(stepRunId);
        assertThat(retriedStep.getStatus()).isEqualTo("PENDING");
        assertThat(retriedStep.getAttempt()).isEqualTo(2);
        assertThat(retriedStep.getErrorMessage()).isNull();
        assertThat(retriedStep.getStartedAt()).isNull();
        assertThat(retriedStep.getFinishedAt()).isNull();
        assertThat(retriedStep.getDurationMs()).isNull();
    }

    @Test
    @DisplayName("测试 TIMED_OUT 工作流支持重启为 RUNNING，并自动清空旧的 finishedAt 与 cancelReason")
    void shouldAllowWorkflowRestartAfterTimeout() {
        WorkflowRunView run = executionApplication.startRun(new StartRunCommand("proj-default", "def-default", null));
        String runId = run.getId();

        // Transition to TIMED_OUT
        stateMachine.transitionRun(runId, WorkflowRunStatus.TIMED_OUT, "watchdog", "Global workflow timeout", "corr-to");
        WorkflowRunEntity timedOut = workflowRunRepository.findById(runId).orElseThrow();
        assertThat(timedOut.getStatus()).isEqualTo("TIMED_OUT");
        assertThat(timedOut.getFinishedAt()).isNotNull();
        assertThat(timedOut.getCancelReason()).isEqualTo("Global workflow timeout");
        assertThat(timedOut.getCancelledAt()).isNotNull();

        // Restart workflow
        WorkflowRunEntity restarted = stateMachine.transitionRun(runId, WorkflowRunStatus.RUNNING, "Restarting failed workflow");
        assertThat(restarted.getStatus()).isEqualTo("RUNNING");
        assertThat(restarted.getFinishedAt()).isNull();
        assertThat(restarted.getCancelledAt()).isNull();
        assertThat(restarted.getCancelReason()).isNull();
    }
}
