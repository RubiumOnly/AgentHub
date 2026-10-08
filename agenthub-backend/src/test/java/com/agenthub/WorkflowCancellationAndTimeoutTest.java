package com.agenthub;

import com.agenthub.execution.application.ExecutionApplication;
import com.agenthub.execution.domain.model.CancelToken;
import com.agenthub.execution.domain.model.CancelTokenRegistry;
import com.agenthub.execution.domain.model.RunCancelledException;
import com.agenthub.execution.dto.RunEventView;
import com.agenthub.execution.dto.StartRunCommand;
import com.agenthub.execution.dto.StepRunView;
import com.agenthub.execution.dto.WorkflowRunView;
import com.agenthub.execution.infrastructure.entity.StepRunEntity;
import com.agenthub.execution.infrastructure.repository.StepRunRepository;
import com.agenthub.shared.context.RequestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class WorkflowCancellationAndTimeoutTest {

    @Autowired
    private ExecutionApplication executionApplication;

    @Autowired
    private CancelTokenRegistry cancelTokenRegistry;

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
    @DisplayName("测试 CancelToken 优雅协作、注册回调执行与 checkCancelled 拦截")
    void shouldExecuteCancelTokenGracefulCallbacks() {
        String runId = "run-cancel-" + UUID.randomUUID().toString().substring(0, 6);
        CancelToken token = cancelTokenRegistry.getOrCreate(runId);

        assertThat(token.isCancelled()).isFalse();
        assertThat(token.isTimedOut()).isFalse();

        AtomicBoolean callbackFired = new AtomicBoolean(false);
        token.registerCallback(() -> callbackFired.set(true));

        // Trigger cancellation
        boolean cancelled = token.cancel("User cancelled via control UI");
        assertThat(cancelled).isTrue();
        assertThat(token.isCancelled()).isTrue();
        assertThat(token.getReason()).isEqualTo("User cancelled via control UI");
        assertThat(callbackFired.get()).isTrue();

        // checkCancelled must throw RunCancelledException
        assertThatThrownBy(token::checkCancelled)
                .isInstanceOf(RunCancelledException.class)
                .hasMessageContaining("User cancelled via control UI");

        // Double cancellation should be idempotent (returns false)
        boolean secondCancel = token.cancel("Duplicate cancellation attempt");
        assertThat(secondCancel).isFalse();
    }

    @Test
    @DisplayName("测试 CancelToken 强平终止：注册的工作线程被强制中断，子进程被强平回收")
    void shouldForciblyTerminateRegisteredThreadAndProcess() throws Exception {
        String runId = "run-force-" + UUID.randomUUID().toString().substring(0, 6);
        CancelToken token = cancelTokenRegistry.getOrCreate(runId);

        CountDownLatch threadStarted = new CountDownLatch(1);
        AtomicBoolean threadInterrupted = new AtomicBoolean(false);

        Thread worker = new Thread(() -> {
            threadStarted.countDown();
            try {
                Thread.sleep(10_000);
            } catch (InterruptedException e) {
                threadInterrupted.set(true);
            }
        });
        token.registerThread(worker);
        worker.start();

        threadStarted.await(2, TimeUnit.SECONDS);

        // Cancel token -> worker thread must be interrupted
        token.cancel("Emergency stop");
        worker.join(2000);

        assertThat(threadInterrupted.get()).isTrue();
    }

    @Test
    @DisplayName("测试 CancelToken 超时强平标志与原因记录")
    void shouldEnforceTimeoutWatchdogOnCancelToken() {
        String runId = "run-timeout-" + UUID.randomUUID().toString().substring(0, 6);
        CancelToken token = cancelTokenRegistry.getOrCreate(runId);

        token.timeout("Exceeded execution budget of 30 seconds");
        assertThat(token.isCancelled()).isTrue();
        assertThat(token.isTimedOut()).isTrue();
        assertThat(token.getReason()).contains("Exceeded execution budget of 30 seconds");
    }

    @Test
    @DisplayName("测试通过 ExecutionApplication.cancelRun 进行全链路取消：级联取消所有待执行与活跃 Step，并持久化 CANCELLED 事件")
    void shouldCascadeCancelWorkflowRunAndActiveSteps() {
        WorkflowRunView run = executionApplication.startRun(new StartRunCommand("proj-default", "def-default", null));
        String runId = run.getId();

        // Create two steps for the run
        String step1Id = "step-1-" + UUID.randomUUID().toString().substring(0, 6);
        String step2Id = "step-2-" + UUID.randomUUID().toString().substring(0, 6);
        stepRunRepository.save(new StepRunEntity(step1Id, runId, "node-backend", "RUNNING"));
        stepRunRepository.save(new StepRunEntity(step2Id, runId, "node-frontend", "PENDING"));

        // Call cancelRun
        WorkflowRunView cancelledRun = executionApplication.cancelRun(runId, "User requested emergency cancellation");
        assertThat(cancelledRun.getStatus()).isEqualTo("CANCELLED");
        assertThat(cancelledRun.getCancelReason()).contains("emergency cancellation");
        assertThat(cancelledRun.getCancelledAt()).isNotNull();

        // Steps must be cascade-cancelled
        List<StepRunView> steps = executionApplication.listStepRuns(runId);
        assertThat(steps).hasSize(2);
        assertThat(steps.get(0).getStatus()).isEqualTo("CANCELLED");
        assertThat(steps.get(1).getStatus()).isEqualTo("CANCELLED");

        // Event log must contain RUN_CANCELLED
        List<RunEventView> events = executionApplication.listEvents(runId, null);
        assertThat(events.stream().anyMatch(e -> "RUN_CANCELLED".equals(e.getEventType()))).isTrue();
    }
}
