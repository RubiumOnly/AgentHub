package com.agenthub.execution.domain.state;

import com.agenthub.audit.infrastructure.entity.ArtifactEntity;
import com.agenthub.execution.domain.model.StepRunStatus;
import com.agenthub.execution.domain.model.WorkflowRunStatus;
import com.agenthub.execution.dto.PendingDecisionItem;
import com.agenthub.execution.infrastructure.entity.StepRunEntity;
import com.agenthub.execution.infrastructure.entity.WorkflowRunEntity;
import com.agenthub.orchestration.infrastructure.entity.ApprovalEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Single Unified State Graph governing legal transitions, consistency invariants,
 * and next-action determination across WorkflowRun, StepRun, Approval, and Artifact entities.
 */
public class UnifiedExecutionStateGraph {

    // Legal transitions for WorkflowRunStatus
    private static final Map<WorkflowRunStatus, Set<WorkflowRunStatus>> LEGAL_RUN_TRANSITIONS = Map.of(
            WorkflowRunStatus.PENDING, Set.of(WorkflowRunStatus.RUNNING, WorkflowRunStatus.CANCELLED, WorkflowRunStatus.REJECTED),
            WorkflowRunStatus.RUNNING, Set.of(
                    WorkflowRunStatus.PAUSED,
                    WorkflowRunStatus.WAITING_APPROVAL,
                    WorkflowRunStatus.SUCCEEDED,
                    WorkflowRunStatus.FAILED,
                    WorkflowRunStatus.CANCELLED,
                    WorkflowRunStatus.TIMED_OUT,
                    WorkflowRunStatus.REJECTED
            ),
            WorkflowRunStatus.PAUSED, Set.of(WorkflowRunStatus.RUNNING, WorkflowRunStatus.CANCELLED, WorkflowRunStatus.REJECTED),
            WorkflowRunStatus.WAITING_APPROVAL, Set.of(WorkflowRunStatus.RUNNING, WorkflowRunStatus.FAILED, WorkflowRunStatus.CANCELLED, WorkflowRunStatus.REJECTED),
            WorkflowRunStatus.SUCCEEDED, Set.of(WorkflowRunStatus.REJECTED),
            WorkflowRunStatus.FAILED, Set.of(WorkflowRunStatus.RUNNING, WorkflowRunStatus.REJECTED),
            WorkflowRunStatus.CANCELLED, Set.of(),
            WorkflowRunStatus.TIMED_OUT, Set.of(WorkflowRunStatus.RUNNING, WorkflowRunStatus.REJECTED),
            WorkflowRunStatus.REJECTED, Set.of()
    );

    // Legal transitions for StepRunStatus
    private static final Map<StepRunStatus, Set<StepRunStatus>> LEGAL_STEP_TRANSITIONS = Map.of(
            StepRunStatus.PENDING, Set.of(StepRunStatus.RUNNING, StepRunStatus.SKIPPED, StepRunStatus.CANCELLED, StepRunStatus.WAITING_APPROVAL),
            StepRunStatus.RUNNING, Set.of(
                    StepRunStatus.PAUSED,
                    StepRunStatus.WAITING_APPROVAL,
                    StepRunStatus.SUCCEEDED,
                    StepRunStatus.FAILED,
                    StepRunStatus.CANCELLED,
                    StepRunStatus.TIMED_OUT,
                    StepRunStatus.SKIPPED
            ),
            StepRunStatus.PAUSED, Set.of(StepRunStatus.RUNNING, StepRunStatus.CANCELLED),
            StepRunStatus.WAITING_APPROVAL, Set.of(StepRunStatus.RUNNING, StepRunStatus.FAILED, StepRunStatus.CANCELLED, StepRunStatus.SUCCEEDED),
            StepRunStatus.SUCCEEDED, Set.of(),
            StepRunStatus.FAILED, Set.of(StepRunStatus.PENDING, StepRunStatus.RUNNING), // retry
            StepRunStatus.CANCELLED, Set.of(),
            StepRunStatus.TIMED_OUT, Set.of(StepRunStatus.PENDING, StepRunStatus.RUNNING), // retry after timeout
            StepRunStatus.SKIPPED, Set.of()
    );

    public static boolean canTransitionRun(WorkflowRunStatus from, WorkflowRunStatus to) {
        if (from == null || to == null) return false;
        if (from == to) return true;
        Set<WorkflowRunStatus> targets = LEGAL_RUN_TRANSITIONS.get(from);
        return targets != null && targets.contains(to);
    }

    public static boolean canTransitionStep(StepRunStatus from, StepRunStatus to) {
        if (from == null || to == null) return false;
        if (from == to) return true;
        Set<StepRunStatus> targets = LEGAL_STEP_TRANSITIONS.get(from);
        return targets != null && targets.contains(to);
    }

    public static void validateWorkflowRunTransition(WorkflowRunStatus from, WorkflowRunStatus to) {
        if (!canTransitionRun(from, to)) {
            throw new IllegalStateException("Illegal workflow run status transition from " + from + " to " + to);
        }
    }

    public static void validateStepRunTransition(StepRunStatus from, StepRunStatus to) {
        if (!canTransitionStep(from, to)) {
            throw new IllegalStateException("Illegal step run status transition from " + from + " to " + to);
        }
    }

    /**
     * Validates whether a run status is consistent with its collection of step statuses.
     */
    public static boolean isRunStepConsistent(WorkflowRunStatus runStatus, List<StepRunStatus> stepStatuses) {
        if (runStatus == null) return false;
        if (stepStatuses == null || stepStatuses.isEmpty()) return true;

        if (runStatus == WorkflowRunStatus.WAITING_APPROVAL) {
            boolean hasWaitingStep = stepStatuses.stream().anyMatch(s -> s == StepRunStatus.WAITING_APPROVAL);
            if (!hasWaitingStep) return false;
        }

        if (runStatus == WorkflowRunStatus.SUCCEEDED) {
            boolean anyNonTerminal = stepStatuses.stream().anyMatch(s -> !s.isTerminal());
            boolean anyFailed = stepStatuses.stream().anyMatch(s -> s == StepRunStatus.FAILED || s == StepRunStatus.CANCELLED);
            if (anyNonTerminal || anyFailed) return false;
        }

        if (runStatus == WorkflowRunStatus.CANCELLED) {
            boolean anyRunningOrPending = stepStatuses.stream().anyMatch(s -> s == StepRunStatus.RUNNING || s == StepRunStatus.PENDING);
            if (anyRunningOrPending) return false;
        }

        return true;
    }

    public static class NextActionDetermination {
        private final String unifiedStatus;
        private final String nextAction;
        private final boolean requiresHumanIntervention;
        private final List<PendingDecisionItem> pendingDecisions;

        public NextActionDetermination(String unifiedStatus, String nextAction, boolean requiresHumanIntervention, List<PendingDecisionItem> pendingDecisions) {
            this.unifiedStatus = unifiedStatus;
            this.nextAction = nextAction;
            this.requiresHumanIntervention = requiresHumanIntervention;
            this.pendingDecisions = pendingDecisions;
        }

        public String getUnifiedStatus() { return unifiedStatus; }
        public String getNextAction() { return nextAction; }
        public boolean isRequiresHumanIntervention() { return requiresHumanIntervention; }
        public List<PendingDecisionItem> getPendingDecisions() { return pendingDecisions; }
    }

    /**
     * Overloaded convenience method taking status directly.
     */
    public static NextActionDetermination determineNextAction(WorkflowRunStatus runStatus,
                                                              List<StepRunEntity> steps,
                                                              List<ApprovalEntity> approvals) {
        WorkflowRunEntity dummy = new WorkflowRunEntity("dummy", "proj-dummy", "def-dummy", runStatus != null ? runStatus.name() : "PENDING", "key-dummy");
        return determineNextAction(dummy, steps, approvals, List.of());
    }

    /**
     * Determines human-in-the-loop pending decisions and structured next action explanation.
     */
    public static NextActionDetermination determineNextAction(WorkflowRunEntity run,
                                                              List<StepRunEntity> steps,
                                                              List<ApprovalEntity> approvals,
                                                              List<ArtifactEntity> artifacts) {
        if (run == null) {
            return new NextActionDetermination("UNKNOWN", "Run not found", false, List.of());
        }

        WorkflowRunStatus runStatus = WorkflowRunStatus.fromString(run.getStatus());
        List<PendingDecisionItem> pendingDecisions = new ArrayList<>();

        if (runStatus == WorkflowRunStatus.WAITING_APPROVAL) {
            ApprovalEntity pendingAppr = null;
            if (approvals != null) {
                for (ApprovalEntity app : approvals) {
                    if ("PENDING".equalsIgnoreCase(app.getStatus())) {
                        pendingAppr = app;
                        break;
                    }
                }
            }

            String nodeName = "Approval Gate";
            String stepRunId = pendingAppr != null ? pendingAppr.getStepRunId() : null;
            if (stepRunId != null && steps != null) {
                for (StepRunEntity s : steps) {
                    if (stepRunId.equals(s.getId())) {
                        nodeName = s.getNodeId();
                        break;
                    }
                }
            }

            String apprId = pendingAppr != null ? pendingAppr.getId() : "unknown";
            String requestedBy = pendingAppr != null && pendingAppr.getRequestedBy() != null ? pendingAppr.getRequestedBy() : "system";

            PendingDecisionItem item = new PendingDecisionItem(
                    "APPROVAL_GATE",
                    apprId,
                    stepRunId,
                    nodeName,
                    "Approval required for node [" + nodeName + "], requested by [" + requestedBy + "]",
                    "REVIEWER",
                    pendingAppr != null ? pendingAppr.getCreatedAt() : run.getUpdatedAt()
            );
            pendingDecisions.add(item);

            String advice = String.format("Waiting for Reviewer to review and decide approval [%s] on step [%s] before execution can continue.",
                    apprId, nodeName);
            return new NextActionDetermination(runStatus.name(), advice, true, pendingDecisions);
        }

        if (runStatus == WorkflowRunStatus.PAUSED) {
            PendingDecisionItem item = new PendingDecisionItem(
                    "RUN_PAUSED",
                    run.getId(),
                    null,
                    "Workflow",
                    "Execution paused by operator. Resume or cancel.",
                    "OPERATOR",
                    run.getUpdatedAt()
            );
            pendingDecisions.add(item);
            return new NextActionDetermination(runStatus.name(),
                    "Execution paused by operator. Call resumeRun to continue or cancelRun to abort.",
                    true, pendingDecisions);
        }

        if (runStatus == WorkflowRunStatus.RUNNING) {
            String activeStepInfo = "Active steps executing";
            if (steps != null) {
                for (StepRunEntity s : steps) {
                    if ("RUNNING".equalsIgnoreCase(s.getStatus())) {
                        activeStepInfo = "Executing step [" + s.getNodeId() + "] (attempt " + (s.getLeaseOwner() != null ? "active" : "1") + ")";
                        break;
                    }
                }
            }
            return new NextActionDetermination(runStatus.name(), activeStepInfo, false, List.of());
        }

        if (runStatus == WorkflowRunStatus.FAILED) {
            String reason = run.getCancelReason() != null ? run.getCancelReason() : "Step execution failure";
            return new NextActionDetermination(runStatus.name(), "Run failed: " + reason + ". Inspect logs or retry individual steps.", false, List.of());
        }

        if (runStatus == WorkflowRunStatus.CANCELLED) {
            return new NextActionDetermination(runStatus.name(), "Run was cancelled by user.", false, List.of());
        }

        if (runStatus == WorkflowRunStatus.TIMED_OUT) {
            return new NextActionDetermination(runStatus.name(), "Run timed out exceeding watchdog limit.", false, List.of());
        }

        if (runStatus == WorkflowRunStatus.SUCCEEDED) {
            return new NextActionDetermination(runStatus.name(), "Run completed successfully. Artifacts ready for review and preview deployment.", false, List.of());
        }

        return new NextActionDetermination(runStatus.name(), "Run in state " + runStatus.name(), false, List.of());
    }
}
