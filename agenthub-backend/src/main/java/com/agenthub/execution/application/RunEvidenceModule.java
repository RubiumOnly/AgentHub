package com.agenthub.execution.application;

import com.agenthub.agent.dto.TokenUsageAuditView;
import com.agenthub.agent.infrastructure.entity.TokenUsageAuditEntity;
import com.agenthub.agent.infrastructure.repository.TokenUsageAuditRepository;
import com.agenthub.agent.infrastructure.security.SecretMasker;
import com.agenthub.audit.dto.ArtifactView;
import com.agenthub.audit.infrastructure.entity.ArtifactEntity;
import com.agenthub.audit.infrastructure.repository.ArtifactRepository;
import com.agenthub.domain.workspace.model.FileDiffEntry;
import com.agenthub.domain.workspace.model.StructuredDiff;
import com.agenthub.domain.workspace.service.JGitWorkspaceManager;
import com.agenthub.domain.workspace.service.WorkspaceResolver;
import com.agenthub.execution.dto.RunEventView;
import com.agenthub.execution.application.evidence.EvidenceChecksumCalculator;
import com.agenthub.execution.application.evidence.EvidenceUsageAggregator;
import com.agenthub.execution.dto.RunEvidenceBundleView;
import com.agenthub.execution.dto.StepRunView;
import com.agenthub.execution.dto.WorkflowRunView;
import com.agenthub.execution.infrastructure.entity.RunEventEntity;
import com.agenthub.execution.infrastructure.entity.StepRunEntity;
import com.agenthub.execution.infrastructure.entity.WorkflowRunEntity;
import com.agenthub.execution.infrastructure.repository.RunEventRepository;
import com.agenthub.execution.infrastructure.repository.StepRunRepository;
import com.agenthub.execution.infrastructure.repository.WorkflowRunRepository;
import com.agenthub.identity.infrastructure.security.ResourceAccessGuard;
import com.agenthub.orchestration.domain.dsl.WorkflowDsl;
import com.agenthub.orchestration.domain.dsl.WorkflowNodeDsl;
import com.agenthub.orchestration.dto.ApprovalView;
import com.agenthub.orchestration.infrastructure.entity.ApprovalEntity;
import com.agenthub.orchestration.infrastructure.entity.WorkflowDefinitionEntity;
import com.agenthub.orchestration.infrastructure.repository.ApprovalRepository;
import com.agenthub.orchestration.infrastructure.repository.WorkflowDefinitionRepository;
import com.agenthub.project.infrastructure.entity.ProjectEntity;
import com.agenthub.project.infrastructure.entity.WorkspaceEntity;
import com.agenthub.project.infrastructure.repository.ProjectRepository;
import com.agenthub.project.infrastructure.repository.WorkspaceRepository;
import com.agenthub.sandbox.infrastructure.entity.DeploymentEntity;
import com.agenthub.sandbox.infrastructure.repository.DeploymentRepository;
import com.agenthub.shared.context.RequestContext;
import com.agenthub.shared.exception.BusinessException;
import com.agenthub.shared.exception.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Internal deep module responsible for assembling, masking, and computing deterministic
 * SHA-256 checksums for RunEvidenceBundleView.
 */
@Component
public class RunEvidenceModule {

    @Autowired
    private EvidenceUsageAggregator usageAggregator;

    private static final Logger log = LoggerFactory.getLogger(RunEvidenceModule.class);

    private final WorkflowRunRepository workflowRunRepository;
    private final StepRunRepository stepRunRepository;
    private final RunEventRepository runEventRepository;
    private final ProjectRepository projectRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceResolver workspaceResolver;
    private final JGitWorkspaceManager gitManager;
    private final ArtifactRepository artifactRepository;
    private final ResourceAccessGuard accessGuard;
    private final com.agenthub.identity.infrastructure.security.ResourceOwnershipVerifier ownershipVerifier;

    @Autowired(required = false)
    private WorkflowDefinitionRepository workflowDefinitionRepository;

    @Autowired(required = false)
    private ObjectMapper objectMapper;

    @Autowired(required = false)
    private TokenUsageAuditRepository tokenUsageAuditRepository;

    @Autowired(required = false)
    private ApprovalRepository approvalRepository;

    @Autowired(required = false)
    private DeploymentRepository deploymentRepository;

    public RunEvidenceModule(WorkflowRunRepository workflowRunRepository,
                             StepRunRepository stepRunRepository,
                             RunEventRepository runEventRepository,
                             ProjectRepository projectRepository,
                             WorkspaceRepository workspaceRepository,
                             WorkspaceResolver workspaceResolver,
                             JGitWorkspaceManager gitManager,
                             ArtifactRepository artifactRepository,
                             ResourceAccessGuard accessGuard) {
        this(workflowRunRepository, stepRunRepository, runEventRepository, projectRepository,
                workspaceRepository, workspaceResolver, gitManager, artifactRepository, accessGuard, null);
    }

    @Autowired
    public RunEvidenceModule(WorkflowRunRepository workflowRunRepository,
                             StepRunRepository stepRunRepository,
                             RunEventRepository runEventRepository,
                             ProjectRepository projectRepository,
                             WorkspaceRepository workspaceRepository,
                             WorkspaceResolver workspaceResolver,
                             JGitWorkspaceManager gitManager,
                             ArtifactRepository artifactRepository,
                             ResourceAccessGuard accessGuard,
                             @Autowired(required = false) com.agenthub.identity.infrastructure.security.ResourceOwnershipVerifier ownershipVerifier) {
        this.workflowRunRepository = workflowRunRepository;
        this.stepRunRepository = stepRunRepository;
        this.runEventRepository = runEventRepository;
        this.projectRepository = projectRepository;
        this.workspaceRepository = workspaceRepository;
        this.workspaceResolver = workspaceResolver;
        this.gitManager = gitManager;
        this.artifactRepository = artifactRepository;
        this.accessGuard = accessGuard;
        this.ownershipVerifier = ownershipVerifier != null ? ownershipVerifier
                : (accessGuard != null ? new com.agenthub.identity.infrastructure.security.ResourceOwnershipVerifier(accessGuard, projectRepository, workspaceRepository, null, workflowRunRepository, artifactRepository, null) : null);
    }

    @Transactional(readOnly = true)
    public RunEvidenceBundleView exportRunEvidenceBundle(String runId) {
        if (runId == null || runId.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Run ID must not be empty");
        }
        String currentUserId = RequestContext.get().getUserId();
        if (!RequestContext.get().isSystem() && (currentUserId == null || currentUserId.isBlank())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Authentication required to export run evidence bundle");
        }

        WorkflowRunEntity run = ownershipVerifier != null ? ownershipVerifier.verifyRunAccess(runId, currentUserId) : verifyRunAccessFallback(runId, currentUserId);
        ProjectEntity project = projectRepository.findById(run.getProjectId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND, "Project not found: " + run.getProjectId()));

        Optional<WorkspaceEntity> wsOpt = workspaceRepository.findByProjectId(project.getId());
        String wsId = wsOpt.map(WorkspaceEntity::getId).orElse(null);

        List<ArtifactEntity> artifactEntities = new ArrayList<>(artifactRepository.findByRunId(runId));
        artifactEntities.sort(Comparator.comparing(ArtifactEntity::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(ArtifactEntity::getId));
        String runBaselineCommit = null;
        String runSnapshotCommit = null;
        Set<String> runArtifactIds = new LinkedHashSet<>();
        List<ArtifactView> sanitizedArtifacts = new ArrayList<>();
        for (ArtifactEntity ae : artifactEntities) {
            runArtifactIds.add(ae.getId());
            if ("BASELINE".equalsIgnoreCase(ae.getArtifactType())
                    && ae.getPathOrRef() != null && !ae.getPathOrRef().isBlank()
                    && runBaselineCommit == null) {
                runBaselineCommit = ae.getPathOrRef();
            }
            if ("SNAPSHOT".equalsIgnoreCase(ae.getArtifactType())
                    && ae.getPathOrRef() != null && !ae.getPathOrRef().isBlank()) {
                runSnapshotCommit = ae.getPathOrRef();
            }
            sanitizedArtifacts.add(new ArtifactView(
                    ae.getId(),
                    ae.getRunId(),
                    ae.getStepRunId(),
                    ae.getArtifactType(),
                    ae.getPathOrRef() != null ? SecretMasker.maskInText(ae.getPathOrRef()) : null,
                    ae.getChecksum(),
                    ae.getMetadataJson() != null ? SecretMasker.maskInText(ae.getMetadataJson()) : null,
                    ae.getReviewStatus(),
                    ae.getReviewedBy(),
                    ae.getReviewedAt(),
                    ae.getReviewComment() != null ? SecretMasker.maskInText(ae.getReviewComment()) : null,
                    ae.getCreatedAt()
            ));
        }

        String baselineCommit = (runBaselineCommit != null && !runBaselineCommit.isBlank())
                ? runBaselineCommit
                : wsOpt.map(WorkspaceEntity::getGitBaselineCommit).orElse(null);

        RunEvidenceBundleView bundle = new RunEvidenceBundleView();
        bundle.setSchemaVersion("agenthub.evidence.v1");
        bundle.setBundleId("evd-" + run.getId());
        bundle.setExportedAt(LocalDateTime.now());
        bundle.setProject(new RunEvidenceBundleView.ProjectEvidenceSummary(
                project.getId(),
                SecretMasker.maskInText(project.getName()),
                project.getOwnerId(),
                wsId,
                baselineCommit
        ));

        String rawDslSnapshot = run.getDslSnapshot();
        if ((rawDslSnapshot == null || rawDslSnapshot.isBlank()) && workflowDefinitionRepository != null && run.getDefinitionId() != null) {
            rawDslSnapshot = workflowDefinitionRepository.findById(run.getDefinitionId())
                    .map(WorkflowDefinitionEntity::getDslJson)
                    .orElse(null);
        }
        bundle.setWorkflowDslSnapshot(rawDslSnapshot != null ? SecretMasker.maskInText(rawDslSnapshot) : null);

        WorkflowRunView sanitizedRun = new WorkflowRunView(
                run.getId(),
                run.getProjectId(),
                run.getDefinitionId(),
                run.getStatus(),
                run.getIdempotencyKey(),
                run.getStartedAt(),
                run.getFinishedAt(),
                run.getCreatedAt(),
                run.getUpdatedAt(),
                run.getCancelReason() != null ? SecretMasker.maskInText(run.getCancelReason()) : null,
                run.getCancelledAt(),
                run.getCorrelationId()
        );
        bundle.setRun(sanitizedRun);

        List<StepRunEntity> stepEntities = stepRunRepository.findByRunIdOrderByCreatedAtAsc(runId);
        List<StepRunView> sanitizedSteps = stepEntities.stream()
                .map(se -> new StepRunView(
                        se.getId(),
                        se.getRunId(),
                        se.getNodeId(),
                        se.getStatus(),
                        se.getAttempt(),
                        se.getInputRef() != null ? SecretMasker.maskInText(se.getInputRef()) : null,
                        se.getOutputRef() != null ? SecretMasker.maskInText(se.getOutputRef()) : null,
                        se.getErrorMessage() != null ? SecretMasker.maskInText(se.getErrorMessage()) : null,
                        se.getCreatedAt(),
                        se.getUpdatedAt(),
                        se.getStartedAt(),
                        se.getFinishedAt(),
                        se.getDurationMs(),
                        se.getCorrelationId()
                ))
                .collect(Collectors.toList());
        bundle.setSteps(sanitizedSteps);

        List<RunEventEntity> eventEntities = runEventRepository.findByRunIdOrderBySequenceNumAsc(runId);
        List<RunEventView> sanitizedEvents = eventEntities.stream()
                .map(ev -> new RunEventView(
                        ev.getId(),
                        ev.getRunId(),
                        ev.getSequenceNum(),
                        ev.getEventType(),
                        ev.getPayload() != null ? SecretMasker.maskInText(ev.getPayload()) : null,
                        ev.getCreatedAt()
                ))
                .collect(Collectors.toList());
        bundle.setEvents(sanitizedEvents);

        Set<String> runtimeTypes = new LinkedHashSet<>();
        ObjectMapper mapper = objectMapper != null ? objectMapper : EvidenceChecksumCalculator.evidenceMapper();
        if (rawDslSnapshot != null && !rawDslSnapshot.isBlank()) {
            try {
                WorkflowDsl parsedDsl = mapper.readValue(rawDslSnapshot, WorkflowDsl.class);
                if (parsedDsl != null && parsedDsl.getNodes() != null) {
                    for (WorkflowNodeDsl n : parsedDsl.getNodes()) {
                        if ("AGENT".equalsIgnoreCase(n.getType()) || n.getAgentPlatform() != null) {
                            String rt = n.getAgentPlatform() != null && !n.getAgentPlatform().isBlank()
                                    ? n.getAgentPlatform().trim().toUpperCase()
                                    : "MOCK";
                            runtimeTypes.add(rt);
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        // Phase 10 R4 深模块拆分：用量聚合、simulated 判定与验证日志由 EvidenceUsageAggregator 承载
        EvidenceUsageAggregator.EvidenceUsageAggregate usageAggregate =
                usageAggregator.aggregate(runId, stepEntities, sanitizedEvents, runtimeTypes, mapper);
        bundle.setSimulated(usageAggregate.simulated());
        bundle.setExecutionMode(usageAggregate.simulated() ? "DEMO_SIMULATED" : "LIVE_PROVIDER");
        bundle.setProviderRuntime(usageAggregate.providerEvidence());
        List<String> verificationLogs = usageAggregate.verificationLogs();

        bundle.setArtifacts(sanitizedArtifacts);

        StructuredDiff sanitizedDiff = new StructuredDiff(wsId, baselineCommit, baselineCommit, 0, 0, 0, new ArrayList<>());
        if (wsOpt.isPresent()) {
            try {
                Path wsRoot = workspaceResolver.getWorkspaceRoot(wsOpt.get().getId());
                StructuredDiff rawDiff = gitManager.computeStructuredDiffBetweenCommits(
                        wsRoot.toFile(), baselineCommit, runSnapshotCommit);
                if (rawDiff != null) {
                    List<FileDiffEntry> cleanEntries = new ArrayList<>();
                    if (rawDiff.getEntries() != null) {
                        for (FileDiffEntry entry : rawDiff.getEntries()) {
                            cleanEntries.add(new FileDiffEntry(
                                    entry.getOldPath(),
                                    entry.getNewPath(),
                                    entry.getChangeType(),
                                    entry.getDiffContent() != null ? SecretMasker.maskInText(entry.getDiffContent()) : null,
                                    entry.getLinesAdded(),
                                    entry.getLinesDeleted(),
                                    entry.isBinary(),
                                    entry.isRename(),
                                    entry.isHasConflict(),
                                    entry.getReviewStatus(),
                                    entry.getChecksum()
                            ));
                        }
                    }
                    sanitizedDiff = new StructuredDiff(
                            wsOpt.get().getId(),
                            rawDiff.getBaselineCommit() != null ? rawDiff.getBaselineCommit() : baselineCommit,
                            rawDiff.getCurrentCommit(),
                            rawDiff.getTotalFilesChanged(),
                            rawDiff.getTotalLinesAdded(),
                            rawDiff.getTotalLinesDeleted(),
                            cleanEntries
                    );
                }
            } catch (Exception e) {
                log.debug("Workspace diff computation skipped or empty for run [{}]: {}", runId, e.getMessage());
            }
        }
        bundle.setGitDiff(sanitizedDiff);

        int succeededSteps = 0;
        int failedSteps = 0;
        int skippedSteps = 0;
        int cancelledSteps = 0;
        for (StepRunView s : sanitizedSteps) {
            if ("SUCCEEDED".equalsIgnoreCase(s.getStatus())) succeededSteps++;
            else if ("FAILED".equalsIgnoreCase(s.getStatus())) failedSteps++;
            else if ("SKIPPED".equalsIgnoreCase(s.getStatus())) skippedSteps++;
            else if ("CANCELLED".equalsIgnoreCase(s.getStatus())) cancelledSteps++;
        }
        String reportStatus;
        if ("SUCCEEDED".equalsIgnoreCase(run.getStatus()) && failedSteps == 0) {
            reportStatus = "PASSED";
        } else if (failedSteps > 0 || "FAILED".equalsIgnoreCase(run.getStatus()) || "TIMED_OUT".equalsIgnoreCase(run.getStatus())) {
            reportStatus = "FAILED";
        } else {
            reportStatus = run.getStatus();
        }
        String summaryText = String.format(
                "Run %s (%s) completed with status %s: %d/%d steps succeeded, %d failed, %d skipped, %d cancelled, %d files changed.",
                run.getId(),
                bundle.getExecutionMode(),
                run.getStatus(),
                succeededSteps,
                sanitizedSteps.size(),
                failedSteps,
                skippedSteps,
                cancelledSteps,
                sanitizedDiff.getTotalFilesChanged()
        );
        bundle.setTestReportSummary(new RunEvidenceBundleView.TestReportEvidence(
                reportStatus,
                sanitizedSteps.size(),
                succeededSteps,
                failedSteps,
                skippedSteps,
                cancelledSteps,
                summaryText,
                verificationLogs
        ));

        List<ApprovalView> approvalViews = new ArrayList<>();
        if (approvalRepository != null) {
            List<ApprovalEntity> approvals = new ArrayList<>(approvalRepository.findByRunId(runId));
            approvals.sort(Comparator.comparing(ApprovalEntity::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(ApprovalEntity::getId));
            for (ApprovalEntity ap : approvals) {
                approvalViews.add(new ApprovalView(
                        ap.getId(),
                        ap.getRunId(),
                        ap.getStepRunId(),
                        ap.getStatus(),
                        ap.getRequestedBy(),
                        ap.getReviewedBy(),
                        ap.getDecisionReason() != null ? SecretMasker.maskInText(ap.getDecisionReason()) : null,
                        ap.getDecision(),
                        ap.getComments() != null ? SecretMasker.maskInText(ap.getComments()) : null,
                        ap.getReviewedAt(),
                        ap.getCreatedAt(),
                        ap.getUpdatedAt()
                ));
            }
        }
        bundle.setApprovals(approvalViews);

        List<RunEvidenceBundleView.DeploymentEvidence> deploymentViews = new ArrayList<>();
        if (deploymentRepository != null) {
            List<DeploymentEntity> projectDeployments = deploymentRepository.findByProjectIdOrderByStartedAtDesc(project.getId());
            List<DeploymentEntity> matched = projectDeployments.stream()
                    .filter(d -> {
                        String artId = d.getArtifactId();
                        if (artId != null && !artId.isBlank() && !artId.startsWith("art-dep-")) {
                            return runArtifactIds.contains(artId);
                        }
                        if (run.getCreatedAt() == null || d.getCreatedAt() == null) {
                            return false;
                        }
                        boolean afterRunStart = !d.getCreatedAt().isBefore(run.getCreatedAt().minusSeconds(2));
                        boolean beforeRunEnd = run.getFinishedAt() == null
                                || !d.getCreatedAt().isAfter(run.getFinishedAt().plusSeconds(5));
                        return afterRunStart && beforeRunEnd;
                    })
                    .sorted(Comparator.comparing(DeploymentEntity::getStartedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                            .thenComparing(DeploymentEntity::getId))
                    .collect(Collectors.toList());
            for (DeploymentEntity d : matched) {
                deploymentViews.add(new RunEvidenceBundleView.DeploymentEvidence(
                        d.getId(),
                        d.getProjectId(),
                        d.getArtifactId(),
                        d.getStatus(),
                        d.getTarget(),
                        d.getSandboxType(),
                        d.getTemplateType(),
                        d.getUrl() != null ? SecretMasker.maskUrl(d.getUrl()) : null,
                        d.getPort(),
                        d.getHealthCheckPath(),
                        d.getLifecycleTrace() != null ? SecretMasker.maskInText(d.getLifecycleTrace()) : null,
                        d.getTerminationReason() != null ? SecretMasker.maskInText(d.getTerminationReason()) : null,
                        d.getErrorMessage() != null ? SecretMasker.maskInText(d.getErrorMessage()) : null,
                        d.getStartedAt(),
                        d.getFinishedAt()
                ));
            }
        }
        bundle.setDeployments(deploymentViews);

        bundle.setChecksum(computeDeterministicChecksum(bundle));
        return bundle;
    }

    public boolean verifyEvidenceBundleChecksum(RunEvidenceBundleView bundle) {
        return EvidenceChecksumCalculator.verifyEvidenceBundleChecksum(bundle);
    }

    public static String computeDeterministicChecksum(RunEvidenceBundleView bundle) {
        return EvidenceChecksumCalculator.computeDeterministicChecksum(bundle);
    }

    private WorkflowRunEntity verifyRunAccessFallback(String runId, String currentUserId) {
        WorkflowRunEntity run = workflowRunRepository.findById(runId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RUN_NOT_FOUND, "Run not found: " + runId));
        ProjectEntity project = projectRepository.findById(run.getProjectId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND, "Project not found: " + run.getProjectId()));
        accessGuard.checkOwnership(project.getOwnerId(), currentUserId);
        return run;
    }
}
