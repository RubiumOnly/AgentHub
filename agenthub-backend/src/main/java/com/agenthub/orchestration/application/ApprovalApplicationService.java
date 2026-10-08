package com.agenthub.orchestration.application;

import com.agenthub.execution.infrastructure.entity.WorkflowRunEntity;
import com.agenthub.execution.infrastructure.repository.WorkflowRunRepository;
import com.agenthub.execution.service.RunEventBroadcaster;
import com.agenthub.identity.infrastructure.security.ResourceAccessGuard;
import com.agenthub.orchestration.domain.approval.ApprovalDecision;
import com.agenthub.orchestration.domain.approval.ApprovalDecisionResult;
import com.agenthub.orchestration.domain.approval.ApprovalSignalRegistry;
import com.agenthub.orchestration.dto.ApprovalDecisionCommand;
import com.agenthub.orchestration.dto.ApprovalView;
import com.agenthub.orchestration.infrastructure.entity.ApprovalEntity;
import com.agenthub.orchestration.infrastructure.repository.ApprovalRepository;
import com.agenthub.project.infrastructure.entity.ProjectEntity;
import com.agenthub.project.infrastructure.repository.ProjectRepository;
import com.agenthub.shared.context.RequestContext;
import com.agenthub.shared.exception.BusinessException;
import com.agenthub.shared.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ApprovalApplicationService implements ApprovalApplication {

    private static final Logger log = LoggerFactory.getLogger(ApprovalApplicationService.class);

    private final ApprovalRepository approvalRepository;
    private final ApprovalSignalRegistry approvalSignalRegistry;
    private final WorkflowRunRepository workflowRunRepository;
    private final ProjectRepository projectRepository;
    private final ResourceAccessGuard accessGuard;
    private final RunEventBroadcaster broadcaster;

    public ApprovalApplicationService(ApprovalRepository approvalRepository,
                                      ApprovalSignalRegistry approvalSignalRegistry,
                                      WorkflowRunRepository workflowRunRepository,
                                      ProjectRepository projectRepository,
                                      ResourceAccessGuard accessGuard,
                                      RunEventBroadcaster broadcaster) {
        this.approvalRepository = approvalRepository;
        this.approvalSignalRegistry = approvalSignalRegistry;
        this.workflowRunRepository = workflowRunRepository;
        this.projectRepository = projectRepository;
        this.accessGuard = accessGuard;
        this.broadcaster = broadcaster;
    }

    @Override
    @Transactional(readOnly = true)
    public ApprovalView getApprovalById(String id) {
        ApprovalEntity entity = approvalRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.APPROVAL_NOT_FOUND, "Approval not found: " + id));
        checkRunAccess(entity.getRunId());
        return toView(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ApprovalView> listApprovalsByRunId(String runId) {
        checkRunAccess(runId);
        return approvalRepository.findByRunId(runId).stream()
                .map(this::toView)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ApprovalView decide(String approvalId, ApprovalDecisionCommand cmd) {
        if (cmd == null || cmd.getDecision() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Decision must be provided");
        }

        ApprovalEntity entity = approvalRepository.findById(approvalId)
                .orElseThrow(() -> new BusinessException(ErrorCode.APPROVAL_NOT_FOUND, "Approval not found: " + approvalId));

        checkRunAccess(entity.getRunId());

        if (!"PENDING".equalsIgnoreCase(entity.getStatus())) {
            throw new BusinessException(ErrorCode.APPROVAL_ALREADY_DECIDED, "Approval has already been decided: " + entity.getStatus());
        }

        ApprovalDecision decision = ApprovalDecision.fromString(cmd.getDecision());
        String reviewer = RequestContext.get().getUserId();
        if (reviewer == null || reviewer.isBlank()) {
            reviewer = "system";
        }
        String reason = cmd.getReason() != null ? cmd.getReason() : (cmd.getComments() != null ? cmd.getComments() : "");

        LocalDateTime now = LocalDateTime.now();
        entity.setStatus(decision.name());
        entity.setDecision(decision.name());
        entity.setReviewedBy(reviewer);
        entity.setDecisionReason(reason);
        entity.setComments(reason);
        entity.setReviewedAt(now);
        entity.setUpdatedAt(now);

        ApprovalEntity saved = approvalRepository.save(entity);

        // Publish SSE event
        broadcaster.publishEvent(entity.getRunId(), "APPROVAL_DECIDED",
                "{\"approvalId\":\"" + approvalId + "\",\"decision\":\"" + decision.name() + "\",\"reviewedBy\":\"" + reviewer + "\",\"reason\":\"" + escapeJson(reason) + "\"}");

        // Signal running DAG thread if waiting
        approvalSignalRegistry.complete(approvalId, new ApprovalDecisionResult(decision, reason, reviewer));
        log.info("Approval [{}] decided as [{}] by reviewer [{}]", approvalId, decision.name(), reviewer);

        return toView(saved);
    }

    @Override
    @Transactional
    public ApprovalView approve(String approvalId, String reason) {
        return decide(approvalId, new ApprovalDecisionCommand("APPROVED", reason != null ? reason : "Approved by user"));
    }

    @Override
    @Transactional
    public ApprovalView reject(String approvalId, String reason) {
        return decide(approvalId, new ApprovalDecisionCommand("REJECTED", reason != null ? reason : "Rejected by user"));
    }

    private void checkRunAccess(String runId) {
        WorkflowRunEntity run = workflowRunRepository.findById(runId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RUN_NOT_FOUND, "Run not found: " + runId));
        ProjectEntity project = projectRepository.findById(run.getProjectId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND, "Project not found: " + run.getProjectId()));
        accessGuard.checkOwnership(project.getOwnerId(), RequestContext.get().getUserId());
    }

    private ApprovalView toView(ApprovalEntity e) {
        return new ApprovalView(
                e.getId(),
                e.getRunId(),
                e.getStepRunId(),
                e.getStatus(),
                e.getRequestedBy(),
                e.getReviewedBy(),
                e.getDecisionReason(),
                e.getDecision(),
                e.getComments(),
                e.getReviewedAt(),
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }

    private String escapeJson(String raw) {
        if (raw == null) return "";
        return raw.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
