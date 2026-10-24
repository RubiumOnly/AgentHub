package com.agenthub.execution.application;

import com.agenthub.audit.dto.ArtifactLineageView;
import com.agenthub.audit.infrastructure.entity.ArtifactEntity;
import com.agenthub.execution.domain.retry.ExecutionErrorClassifier;
import com.agenthub.execution.domain.retry.RetryCategory;
import com.agenthub.execution.domain.state.UnifiedExecutionStateGraph;
import com.agenthub.execution.dto.*;
import com.agenthub.execution.infrastructure.entity.RunEventEntity;
import com.agenthub.execution.infrastructure.entity.StepRunEntity;
import com.agenthub.execution.infrastructure.entity.WorkflowRunEntity;
import com.agenthub.execution.infrastructure.repository.RunEventRepository;
import com.agenthub.execution.infrastructure.repository.StepRunRepository;
import com.agenthub.execution.infrastructure.repository.WorkflowRunRepository;
import com.agenthub.identity.infrastructure.security.ResourceAccessGuard;
import com.agenthub.orchestration.infrastructure.entity.ApprovalEntity;
import com.agenthub.project.infrastructure.entity.ProjectEntity;
import com.agenthub.project.infrastructure.repository.ProjectRepository;
import com.agenthub.shared.context.RequestContext;
import com.agenthub.shared.exception.BusinessException;
import com.agenthub.shared.exception.ErrorCode;
import com.agenthub.execution.domain.memory.ContextMemoryCompressor;
import com.agenthub.execution.domain.memory.HierarchicalContextMemory;
import com.agenthub.project.infrastructure.entity.WorkspaceEntity;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Internal deep module responsible for read-only WorkflowRun, StepRun, and RunEvent queries
 * with strict project ownership verification.
 */
@Component
public class RunQueryModule {

    private final WorkflowRunRepository workflowRunRepository;
    private final StepRunRepository stepRunRepository;
    private final RunEventRepository runEventRepository;
    private final ProjectRepository projectRepository;
    private final ResourceAccessGuard accessGuard;
    private final com.agenthub.identity.infrastructure.security.ResourceOwnershipVerifier ownershipVerifier;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.agenthub.audit.application.ArtifactApplication artifactApplication;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.agenthub.orchestration.infrastructure.repository.ApprovalRepository approvalRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.agenthub.audit.infrastructure.repository.ArtifactRepository artifactRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.agenthub.project.infrastructure.repository.WorkspaceRepository workspaceRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.agenthub.domain.workspace.service.WorkspaceResolver workspaceResolver;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    public RunQueryModule(WorkflowRunRepository workflowRunRepository,
                          StepRunRepository stepRunRepository,
                          RunEventRepository runEventRepository,
                          ProjectRepository projectRepository,
                          ResourceAccessGuard accessGuard) {
        this(workflowRunRepository, stepRunRepository, runEventRepository, projectRepository, accessGuard, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public RunQueryModule(WorkflowRunRepository workflowRunRepository,
                          StepRunRepository stepRunRepository,
                          RunEventRepository runEventRepository,
                          ProjectRepository projectRepository,
                          ResourceAccessGuard accessGuard,
                          @org.springframework.beans.factory.annotation.Autowired(required = false) com.agenthub.identity.infrastructure.security.ResourceOwnershipVerifier ownershipVerifier) {
        this.workflowRunRepository = workflowRunRepository;
        this.stepRunRepository = stepRunRepository;
        this.runEventRepository = runEventRepository;
        this.projectRepository = projectRepository;
        this.accessGuard = accessGuard;
        this.ownershipVerifier = ownershipVerifier != null ? ownershipVerifier
                : (accessGuard != null ? new com.agenthub.identity.infrastructure.security.ResourceOwnershipVerifier(accessGuard, projectRepository, null, null, workflowRunRepository, null, null) : null);
    }

    @Transactional(readOnly = true)
    public WorkflowRunView getRunById(String runId) {
        WorkflowRunEntity run = ownershipVerifier != null ? ownershipVerifier.verifyRunAccess(runId) : verifyRunAccessFallback(runId);
        return toRunView(run);
    }

    @Transactional(readOnly = true)
    public List<WorkflowRunView> listRunsByProjectId(String projectId) {
        if (ownershipVerifier != null) {
            ownershipVerifier.verifyProjectAccess(projectId);
        } else {
            ProjectEntity project = projectRepository.findById(projectId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND, "Project not found: " + projectId));
            accessGuard.checkOwnership(project.getOwnerId(), RequestContext.get().getUserId());
        }
        return workflowRunRepository.findByProjectIdOrderByCreatedAtDesc(projectId).stream()
                .map(RunQueryModule::toRunView)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<StepRunView> listStepRuns(String runId) {
        if (ownershipVerifier != null) {
            ownershipVerifier.verifyRunAccess(runId);
        } else {
            verifyRunAccessFallback(runId);
        }
        return stepRunRepository.findByRunIdOrderByCreatedAtAsc(runId).stream()
                .map(RunQueryModule::toStepView)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<RunEventView> listEvents(String runId, Long afterSeq) {
        if (ownershipVerifier != null) {
            ownershipVerifier.verifyRunAccess(runId);
        } else {
            verifyRunAccessFallback(runId);
        }

        List<RunEventEntity> events;
        if (afterSeq != null && afterSeq > 0) {
            events = runEventRepository.findByRunIdAndSequenceNumGreaterThanOrderBySequenceNumAsc(runId, afterSeq);
        } else {
            events = runEventRepository.findByRunIdOrderBySequenceNumAsc(runId);
        }
        return events.stream().map(RunQueryModule::toEventView).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public RunOperationsView getRunOperations(String runId) {
        WorkflowRunEntity run = ownershipVerifier != null ? ownershipVerifier.verifyRunAccess(runId) : verifyRunAccessFallback(runId);
        List<StepRunEntity> stepEntities = stepRunRepository.findByRunIdOrderByCreatedAtAsc(runId);
        List<ApprovalEntity> approvals = approvalRepository != null ? approvalRepository.findByRunId(runId) : List.of();
        List<ArtifactEntity> artifacts = artifactRepository != null ? artifactRepository.findByRunIdOrderByCreatedAtDesc(runId) : List.of();

        ArtifactLineageView lineage = artifactApplication != null ? artifactApplication.getArtifactLineageByRunId(runId) : null;
        UnifiedExecutionStateGraph.NextActionDetermination nextAction =
                UnifiedExecutionStateGraph.determineNextAction(run, stepEntities, approvals, artifacts);

        Map<String, ArtifactEntity> snapshotMap = new HashMap<>();
        for (ArtifactEntity a : artifacts) {
            if (a.getStepRunId() != null) {
                snapshotMap.put(a.getStepRunId(), a);
            }
        }

        List<ToolSideEffectView> sideEffects = new ArrayList<>();
        int totalRetries = 0;
        String failureCategory = "NONE";
        String failureReason = null;

        for (StepRunEntity s : stepEntities) {
            if (s.getAttempt() != null && s.getAttempt() > 1) {
                totalRetries += (s.getAttempt() - 1);
            }
            if ("FAILED".equalsIgnoreCase(s.getStatus()) || "CANCELLED".equalsIgnoreCase(s.getStatus())) {
                String err = s.getErrorMessage() != null ? s.getErrorMessage() : (run.getCancelReason() != null ? run.getCancelReason() : "Step failed");
                failureReason = err;
                ExecutionErrorClassifier.ClassificationResult cl = ExecutionErrorClassifier.classify(new RuntimeException(err));
                failureCategory = cl.getCategory().name();
            }
            ArtifactEntity art = snapshotMap.get(s.getId());
            if (art != null) {
                List<String> f = parseFiles(art.getMetadataJson());
                Map<String, String> c = parseChecksums(art.getMetadataJson());
                sideEffects.add(new ToolSideEffectView(
                        s.getNodeId(),
                        f.isEmpty() ? List.of(s.getInputRef() != null ? s.getInputRef() : "workspace") : f,
                        "GIT_SNAPSHOT",
                        s.getStatus(),
                        c
                ));
            } else if (s.getOutputRef() != null && !s.getOutputRef().isBlank()) {
                sideEffects.add(new ToolSideEffectView(
                        s.getNodeId(),
                        List.of(s.getInputRef() != null ? s.getInputRef() : "workspace"),
                        "EXECUTE_NODE",
                        s.getStatus(),
                        java.util.Map.of()
                ));
            }
        }

        if (run.getCancelReason() != null && "NONE".equals(failureCategory)) {
            ExecutionErrorClassifier.ClassificationResult cl = ExecutionErrorClassifier.classify(new RuntimeException(run.getCancelReason()));
            failureCategory = cl.getCategory().name();
            failureReason = run.getCancelReason();
        }

        String gitBaseline = null;
        WorkspaceEntity ws = null;
        if (workspaceRepository != null) {
            ws = workspaceRepository.findByProjectId(run.getProjectId()).orElse(null);
            if (ws != null && ws.getGitBaselineCommit() != null) {
                gitBaseline = ws.getGitBaselineCommit();
            }
        }
        if (gitBaseline == null && lineage != null) {
            gitBaseline = lineage.getBaselineCommitHash();
        }

        String aociStatus = "NOT_CONFIGURED";
        if (ws != null && workspaceResolver != null) {
            try {
                java.nio.file.Path wsRoot = workspaceResolver.getWorkspaceRoot(ws.getId());
                File rootDir = wsRoot != null ? wsRoot.toFile() : null;
                aociStatus = ContextMemoryCompressor.determineAociCognitionStatus(rootDir, gitBaseline);
            } catch (Exception ignored) {}
        }

        HierarchicalContextMemory.ProjectMemory projMem = new HierarchicalContextMemory.ProjectMemory(
                run.getProjectId(),
                "RULE-1: Strict DDD layers; RULE-2: No long RPC in @Transactional; RULE-3: Single state graph",
                gitBaseline,
                "aoci-fp-" + run.getProjectId(),
                aociStatus
        );

        HierarchicalContextMemory.RunMemory runMem = new HierarchicalContextMemory.RunMemory(
                run.getId(),
                run.getDefinitionId() != null ? run.getDefinitionId() : "WorkflowRun",
                run.getDefinitionId(),
                null,
                50000,
                0
        );

        HierarchicalContextMemory memory = new HierarchicalContextMemory(projMem, runMem);
        for (StepRunEntity s : stepEntities) {
            HierarchicalContextMemory.StepMemoryItem item = new HierarchicalContextMemory.StepMemoryItem(
                    s.getId(),
                    s.getNodeId(),
                    s.getStatus(),
                    1,
                    10
            );
            item.setRawOutput(s.getOutputRef());
            item.setErrorDetails(s.getErrorMessage());
            ArtifactEntity art = snapshotMap.get(s.getId());
            if (art != null) {
                item.setModifiedFiles(parseFiles(art.getMetadataJson()));
            }
            memory.addStepMemory(item);
        }

        ContextMemoryCompressor.CompressionReport report = ContextMemoryCompressor.compressIfNeeded(memory, 20000);

        HierarchicalMemorySummary memorySummary = new HierarchicalMemorySummary(
                run.getProjectId(),
                run.getId(),
                3,
                stepEntities.size(),
                report.getStepsCompressed(),
                (long) report.getCompressedTokens(),
                !report.isBudgetMet(),
                aociStatus,
                List.of("PROJECT_RULES", "AOCI_COGNITION", "GIT_BASELINE")
        );

        boolean leaseExpired = run.getLeaseUntil() != null && run.getLeaseUntil().isBefore(LocalDateTime.now());
        LeaseStatusView leaseStatus = new LeaseStatusView(
                run.getLeaseOwner(),
                run.getLeaseUntil(),
                run.getHeartbeatAt(),
                leaseExpired
        );

        List<RunEventEntity> events = runEventRepository.findByRunIdOrderBySequenceNumAsc(runId);
        List<RunEventView> recentEvents = events.stream()
                .skip(Math.max(0, events.size() - 10))
                .map(RunQueryModule::toEventView)
                .toList();

        return new RunOperationsView(
                toRunView(run),
                stepEntities.stream().map(RunQueryModule::toStepView).toList(),
                nextAction.getUnifiedStatus(),
                nextAction.getNextAction(),
                nextAction.isRequiresHumanIntervention(),
                nextAction.getPendingDecisions(),
                sideEffects,
                memorySummary,
                lineage,
                recentEvents,
                leaseStatus,
                totalRetries,
                failureCategory,
                failureReason
        );
    }

    private WorkflowRunEntity verifyRunAccessFallback(String runId) {
        WorkflowRunEntity run = workflowRunRepository.findById(runId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RUN_NOT_FOUND, "Run not found: " + runId));
        ProjectEntity project = projectRepository.findById(run.getProjectId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND, "Project not found: " + run.getProjectId()));
        accessGuard.checkOwnership(project.getOwnerId(), RequestContext.get().getUserId());
        return run;
    }

    public static WorkflowRunView toRunView(WorkflowRunEntity entity) {
        return new WorkflowRunView(
                entity.getId(),
                entity.getProjectId(),
                entity.getDefinitionId(),
                entity.getStatus(),
                entity.getIdempotencyKey(),
                entity.getStartedAt(),
                entity.getFinishedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getCancelReason(),
                entity.getCancelledAt(),
                entity.getCorrelationId()
        );
    }

    public static StepRunView toStepView(StepRunEntity entity) {
        return new StepRunView(
                entity.getId(),
                entity.getRunId(),
                entity.getNodeId(),
                entity.getStatus(),
                entity.getAttempt(),
                entity.getInputRef(),
                entity.getOutputRef(),
                entity.getErrorMessage(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getStartedAt(),
                entity.getFinishedAt(),
                entity.getDurationMs(),
                entity.getCorrelationId()
        );
    }

    public static RunEventView toEventView(RunEventEntity entity) {
        return new RunEventView(
                entity.getId(),
                entity.getRunId(),
                entity.getSequenceNum(),
                entity.getEventType(),
                entity.getPayload(),
                entity.getCreatedAt()
        );
    }

    private List<String> parseFiles(String metadataJson) {
        List<String> files = new ArrayList<>();
        if (metadataJson == null || metadataJson.isBlank()) return files;
        try {
            com.fasterxml.jackson.databind.ObjectMapper om = objectMapper != null ? objectMapper : new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root = om.readTree(metadataJson);
            com.fasterxml.jackson.databind.JsonNode fNode = root.get("files");
            if (fNode == null || !fNode.isArray()) fNode = root.get("changedFiles");
            if (fNode != null && fNode.isArray()) {
                for (com.fasterxml.jackson.databind.JsonNode n : fNode) {
                    if (n.isTextual() && !n.asText().isBlank()) files.add(n.asText().trim());
                }
            }
        } catch (Exception ignored) {}
        return files;
    }

    private Map<String, String> parseChecksums(String metadataJson) {
        Map<String, String> sums = new HashMap<>();
        if (metadataJson == null || metadataJson.isBlank()) return sums;
        try {
            com.fasterxml.jackson.databind.ObjectMapper om = objectMapper != null ? objectMapper : new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root = om.readTree(metadataJson);
            com.fasterxml.jackson.databind.JsonNode sNode = root.get("fileChecksums");
            if (sNode == null || !sNode.isObject()) sNode = root.get("checksums");
            if (sNode != null && sNode.isObject()) {
                sNode.fields().forEachRemaining(entry -> {
                    if (entry.getValue().isTextual()) sums.put(entry.getKey(), entry.getValue().asText());
                });
            }
        } catch (Exception ignored) {}
        return sums;
    }
}
