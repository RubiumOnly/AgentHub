package com.agenthub.execution.scheduler;

import com.agenthub.execution.domain.model.WorkflowRunStatus;
import com.agenthub.execution.infrastructure.entity.StepRunEntity;
import com.agenthub.execution.infrastructure.entity.WorkflowRunEntity;
import com.agenthub.execution.infrastructure.repository.StepRunRepository;
import com.agenthub.execution.infrastructure.repository.WorkflowRunRepository;
import com.agenthub.execution.service.ExecutionStateMachine;
import com.agenthub.execution.service.RunEventBroadcaster;
import com.agenthub.orchestration.scheduler.DagExecutionEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Phase C Persistent Execution Recovery Runner:
 * Automatically runs on application startup to scan:
 * 1. Stale RUNNING runs where lease has expired (e.g., previous JVM crashed / killed).
 * 2. QUEUED runs awaiting worker scheduling.
 * 3. Inspects step status:
 *    - Steps in WAITING_APPROVAL or requiring approval: retained for human confirmation.
 *    - Runs with valid dsl_snapshot: atomically claims lease and safely resumes execution without repeating completed steps.
 *    - Unrecoverable runs (no snapshot or exceeded max attempts): gracefully transitioned to FAILED.
 */
@Component
public class PersistentExecutionRecoveryRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PersistentExecutionRecoveryRunner.class);

    private final WorkflowRunRepository workflowRunRepository;
    private final StepRunRepository stepRunRepository;
    private final ExecutionStateMachine stateMachine;
    private final RunEventBroadcaster broadcaster;

    @Autowired(required = false)
    private DagExecutionEngine dagExecutionEngine;

    public PersistentExecutionRecoveryRunner(WorkflowRunRepository workflowRunRepository,
                                             StepRunRepository stepRunRepository,
                                             ExecutionStateMachine stateMachine,
                                             RunEventBroadcaster broadcaster) {
        this.workflowRunRepository = workflowRunRepository;
        this.stepRunRepository = stepRunRepository;
        this.stateMachine = stateMachine;
        this.broadcaster = broadcaster;
    }

    @Override
    public void run(ApplicationArguments args) {
        recoverStaleRuns();
    }

    @org.springframework.transaction.annotation.Transactional
    public synchronized int recoverStaleRuns() {
        LocalDateTime now = LocalDateTime.now();
        List<WorkflowRunEntity> staleRuns = workflowRunRepository.findStaleRunningRuns(now);
        log.info("Execution recovery watchdog initiated: found [{}] stale RUNNING runs with expired leases", staleRuns.size());

        int recoveredCount = 0;
        for (WorkflowRunEntity run : staleRuns) {
            String runId = run.getId();
            log.warn("Recovering stale run [{}] (previous leaseOwner: [{}], leaseUntil: [{}])",
                    runId, run.getLeaseOwner(), run.getLeaseUntil());

            List<StepRunEntity> steps = stepRunRepository.findByRunIdOrderByCreatedAtAsc(runId);
            boolean hasApprovalPending = steps.stream().anyMatch(s ->
                    "WAITING_APPROVAL".equalsIgnoreCase(s.getStatus()) ||
                    (Boolean.TRUE.equals(s.getRequiresApproval()) && "RUNNING".equalsIgnoreCase(s.getStatus()))
            );

            if (hasApprovalPending) {
                log.info("Run [{}] contains pending human approval requirement, keeping in WAITING_APPROVAL for manual confirmation", runId);
                stateMachine.transitionRun(runId, WorkflowRunStatus.WAITING_APPROVAL, "recovery-watchdog", "Preserved pending approval across restart", null);
                broadcaster.publishEvent(runId, "RUN_RECOVERY_PAUSED", "{\"reason\":\"Awaiting human approval after restart\"}");
                continue;
            }

            // Check if run has dslSnapshot for safe DAG resumption
            if (run.getDslSnapshot() != null && !run.getDslSnapshot().isBlank() && dagExecutionEngine != null) {
                int attempt = run.getAttempt() != null ? run.getAttempt() : 1;
                if (attempt >= 5) {
                    log.error("Run [{}] exceeded max recovery attempts ({}), transitioning to FAILED", runId, attempt);
                    stateMachine.transitionRun(runId, WorkflowRunStatus.FAILED, "recovery-watchdog", "Exceeded max recovery attempts", null);
                    broadcaster.publishEvent(runId, "RUN_FAILED", "{\"reason\":\"Exceeded max recovery attempts after restart\"}");
                    continue;
                }

                // Atomically claim lease
                int acquired = workflowRunRepository.tryAcquireRunLease(runId, DagExecutionEngine.INSTANCE_WORKER_ID, LocalDateTime.now().plusSeconds(180), now);
                if (acquired <= 0) {
                    log.info("Run [{}] lease was claimed by another worker during recovery, skipping", runId);
                    continue;
                }

                broadcaster.publishEvent(runId, "RUN_RECOVERED",
                        String.format("{\"resumedAttempt\":%d,\"newWorker\":\"%s\"}", attempt + 1, DagExecutionEngine.INSTANCE_WORKER_ID));

                log.info("Safely resuming DAG execution for run [{}] attempt [{}] with new lease owner [{}]",
                        runId, attempt + 1, DagExecutionEngine.INSTANCE_WORKER_ID);

                try {
                    dagExecutionEngine.resumeRun(runId);
                    recoveredCount++;
                } catch (Exception e) {
                    log.error("Failed to resume DAG execution for run [{}]: {}", runId, e.getMessage(), e);
                    stateMachine.transitionRun(runId, WorkflowRunStatus.FAILED, "recovery-watchdog", "Recovery failed: " + e.getMessage(), null);
                }
            } else {
                // Cannot resume without snapshot
                log.warn("Run [{}] lacks DSL snapshot for recovery, transitioning to FAILED", runId);
                stateMachine.transitionRun(runId, WorkflowRunStatus.FAILED, "recovery-watchdog", "Stale lease expired without persistent DSL snapshot", null);
                broadcaster.publishEvent(runId, "RUN_FAILED", "{\"reason\":\"Stale lease expired without persistent DSL snapshot\"}");
            }
        }

        return recoveredCount;
    }
}
