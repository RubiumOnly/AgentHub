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
}
