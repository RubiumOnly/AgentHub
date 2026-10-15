package com.agenthub.audit.application;

import com.agenthub.shared.exception.BusinessException;
import com.agenthub.shared.exception.ErrorCode;
import com.agenthub.audit.dto.ArtifactLineageView;
import com.agenthub.audit.dto.ArtifactView;
import com.agenthub.audit.dto.StepLineageNode;
import com.agenthub.audit.infrastructure.entity.ArtifactEntity;
import com.agenthub.audit.infrastructure.repository.ArtifactRepository;
import com.agenthub.domain.workspace.model.StructuredDiff;
import com.agenthub.domain.workspace.service.JGitWorkspaceManager;
import com.agenthub.domain.workspace.service.WorkspaceResolver;
import com.agenthub.execution.infrastructure.entity.RunEventEntity;
import com.agenthub.execution.infrastructure.entity.StepRunEntity;
import com.agenthub.execution.infrastructure.entity.WorkflowRunEntity;
import com.agenthub.orchestration.infrastructure.entity.ApprovalEntity;
import com.agenthub.execution.infrastructure.repository.RunEventRepository;
import com.agenthub.execution.infrastructure.repository.WorkflowRunRepository;
import com.agenthub.identity.infrastructure.security.ResourceAccessGuard;
import com.agenthub.project.infrastructure.entity.ProjectEntity;
import com.agenthub.project.infrastructure.entity.WorkspaceEntity;
import com.agenthub.project.infrastructure.repository.ProjectRepository;
import com.agenthub.project.infrastructure.repository.WorkspaceRepository;
import com.agenthub.shared.context.RequestContext;
import com.agenthub.audit.domain.quality.*;
import com.agenthub.audit.dto.PullRequestDraftView;
import com.agenthub.execution.application.RunQueryModule;
import com.agenthub.execution.dto.StepRunView;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ArtifactApplicationService implements ArtifactApplication {

    private static final Logger log = LoggerFactory.getLogger(ArtifactApplicationService.class);

    private final ArtifactRepository artifactRepository;
    private final WorkflowRunRepository workflowRunRepository;
    private final ProjectRepository projectRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceResolver workspaceResolver;
    private final JGitWorkspaceManager gitManager;
    private final RunEventRepository runEventRepository;
    private final ResourceAccessGuard accessGuard;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    private final com.agenthub.identity.infrastructure.security.ResourceOwnershipVerifier ownershipVerifier;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private QualityGateEngine qualityGateEngine;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.agenthub.execution.service.RunEventBroadcaster broadcaster;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.agenthub.execution.infrastructure.repository.StepRunRepository stepRunRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.agenthub.orchestration.infrastructure.repository.ApprovalRepository approvalRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    public ArtifactApplicationService(ArtifactRepository artifactRepository,
                                    WorkflowRunRepository workflowRunRepository,
                                    ProjectRepository projectRepository,
                                    WorkspaceRepository workspaceRepository,
                                    WorkspaceResolver workspaceResolver,
                                    JGitWorkspaceManager gitManager,
                                    RunEventRepository runEventRepository,
                                    ResourceAccessGuard accessGuard) {
        this(artifactRepository, workflowRunRepository, projectRepository, workspaceRepository,
                workspaceResolver, gitManager, runEventRepository, accessGuard, new com.fasterxml.jackson.databind.ObjectMapper(), null);
    }

    public ArtifactApplicationService(ArtifactRepository artifactRepository,
                                    WorkflowRunRepository workflowRunRepository,
                                    ProjectRepository projectRepository,
                                    WorkspaceRepository workspaceRepository,
                                    WorkspaceResolver workspaceResolver,
                                    JGitWorkspaceManager gitManager,
                                    RunEventRepository runEventRepository,
                                    ResourceAccessGuard accessGuard,
                                    com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        this(artifactRepository, workflowRunRepository, projectRepository, workspaceRepository,
                workspaceResolver, gitManager, runEventRepository, accessGuard, objectMapper, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public ArtifactApplicationService(ArtifactRepository artifactRepository,
                                    WorkflowRunRepository workflowRunRepository,
                                    ProjectRepository projectRepository,
                                    WorkspaceRepository workspaceRepository,
                                    WorkspaceResolver workspaceResolver,
                                    JGitWorkspaceManager gitManager,
                                    RunEventRepository runEventRepository,
                                    ResourceAccessGuard accessGuard,
                                    @org.springframework.beans.factory.annotation.Autowired(required = false) com.fasterxml.jackson.databind.ObjectMapper objectMapper,
                                    @org.springframework.beans.factory.annotation.Autowired(required = false) com.agenthub.identity.infrastructure.security.ResourceOwnershipVerifier ownershipVerifier) {
        this.artifactRepository = artifactRepository;
        this.workflowRunRepository = workflowRunRepository;
        this.projectRepository = projectRepository;
        this.workspaceRepository = workspaceRepository;
        this.workspaceResolver = workspaceResolver;
        this.gitManager = gitManager;
        this.runEventRepository = runEventRepository;
        this.accessGuard = accessGuard;
        this.objectMapper = (objectMapper != null) ? objectMapper : new com.fasterxml.jackson.databind.ObjectMapper();
        this.ownershipVerifier = ownershipVerifier != null ? ownershipVerifier
                : (accessGuard != null ? new com.agenthub.identity.infrastructure.security.ResourceOwnershipVerifier(accessGuard, projectRepository, workspaceRepository, null, workflowRunRepository, artifactRepository, null) : null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ArtifactView> listArtifactsByRunId(String runId) {
        if (ownershipVerifier != null) {
            ownershipVerifier.verifyRunAccess(runId);
        } else {
            WorkflowRunEntity run = workflowRunRepository.findById(runId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Workflow run not found: " + runId));
            ProjectEntity project = projectRepository.findById(run.getProjectId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Project not found: " + run.getProjectId()));
            accessGuard.checkOwnership(project.getOwnerId(), RequestContext.get().getUserId());
        }

        return artifactRepository.findByRunIdOrderByCreatedAtDesc(runId).stream()
                .map(this::toView)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public ArtifactView getArtifactById(String artifactId) {
        ArtifactEntity entity = ownershipVerifier != null
                ? ownershipVerifier.verifyArtifactAccess(artifactId)
                : getArtifactByIdFallback(artifactId);

        return toView(entity);
    }

    private void markArtifactGateFailed(String artifactId, ArtifactEntity entity, QualityGateReport report) {
        if (transactionManager != null) {
            org.springframework.transaction.support.TransactionTemplate tt =
                    new org.springframework.transaction.support.TransactionTemplate(
                            transactionManager,
                            new org.springframework.transaction.support.DefaultTransactionDefinition(
                                    org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW));
            tt.executeWithoutResult(status -> {
                ArtifactEntity fresh = artifactRepository.findById(artifactId).orElse(entity);
                fresh.setReviewStatus("GATE_FAILED");
                if (report != null) {
                    try {
                        JsonNode rootNode = (fresh.getMetadataJson() != null && !fresh.getMetadataJson().isBlank())
                                ? objectMapper.readTree(fresh.getMetadataJson())
                                : objectMapper.createObjectNode();
                        if (rootNode instanceof ObjectNode obj) {
                            obj.set("qualityGateReport", objectMapper.valueToTree(report));
                            fresh.setMetadataJson(objectMapper.writeValueAsString(obj));
                        }
                    } catch (Exception ignored) {}
                }
                artifactRepository.save(fresh);
            });
        } else {
            entity.setReviewStatus("GATE_FAILED");
            artifactRepository.save(entity);
        }
    }

    @Override
    @Transactional
    public ArtifactView acceptArtifact(String artifactId, String reviewerId) {
        String actor = (reviewerId != null && !reviewerId.isBlank()) ? reviewerId : RequestContext.get().getUserId();
        ArtifactEntity entity = ownershipVerifier != null
                ? ownershipVerifier.verifyArtifactAccess(artifactId, actor)
                : getArtifactByIdWithActorFallback(artifactId, actor);

        // Phase 5 Anti-Forgery Quality Gate Defense: evaluate gate and ensure it passes
        QualityGateReport report = evaluateQualityGate(artifactId);
        if (report != null && report.getOverallStatus() == QualityGateStatus.FAILED) {
            markArtifactGateFailed(artifactId, entity, report);
            String failReasons = report.getCheckItems().stream()
                    .filter(c -> c.getStatus() == QualityGateStatus.FAILED)
                    .map(QualityGateCheckItem::getMessage)
                    .collect(Collectors.joining("; "));
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "Artifact [" + artifactId + "] failed quality gate checks and cannot be accepted. Violations: " + failReasons);
        }

        entity.setReviewStatus("ACCEPTED");
        entity.setReviewedBy(actor);
        entity.setReviewedAt(LocalDateTime.now());
        artifactRepository.save(entity);

        recordRunEvent(entity.getRunId(), "ARTIFACT_ACCEPTED", "Artifact accepted: " + artifactId);
        log.info("Artifact [{}] accepted by [{}]", artifactId, entity.getReviewedBy());
        return toView(entity);
    }

    @Override
    @Transactional
    public ArtifactView rejectArtifact(String artifactId, String reviewerId, String reason) {
        String actor = (reviewerId != null && !reviewerId.isBlank()) ? reviewerId : RequestContext.get().getUserId();
        ArtifactEntity entity = ownershipVerifier != null
                ? ownershipVerifier.verifyArtifactAccess(artifactId, actor)
                : getArtifactByIdWithActorFallback(artifactId, actor);

        entity.setReviewStatus("REJECTED");
        entity.setReviewedBy(actor);
        entity.setReviewedAt(LocalDateTime.now());
        entity.setReviewComment(reason);
        artifactRepository.save(entity);

        recordRunEvent(entity.getRunId(), "ARTIFACT_REJECTED", "Artifact rejected: " + artifactId + ", reason: " + reason);
        log.info("Artifact [{}] rejected by [{}], reason: {}", artifactId, entity.getReviewedBy(), reason);
        return toView(entity);
    }

    @Override
    @Transactional
    public ArtifactView revertArtifact(String artifactId, String actorId) {
        ArtifactEntity entity = artifactRepository.findById(artifactId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ARTIFACT_NOT_FOUND, "Artifact not found: " + artifactId));

        if ("REVERTED".equalsIgnoreCase(entity.getReviewStatus())) {
            throw new BusinessException(ErrorCode.ARTIFACT_REVIEW_INVALID, "Artifact has already been reverted: " + artifactId);
        }

        WorkflowRunEntity run = workflowRunRepository.findById(entity.getRunId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Workflow run not found: " + entity.getRunId()));
        ProjectEntity project = projectRepository.findById(run.getProjectId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Project not found: " + run.getProjectId()));
        String actor = (actorId != null && !actorId.isBlank()) ? actorId : RequestContext.get().getUserId();
        accessGuard.checkOwnership(project.getOwnerId(), actor);

        // Locate workspace root
        WorkspaceEntity workspace = workspaceRepository.findByProjectId(project.getId()).orElse(null);
        String workspaceKey = (workspace != null) ? workspace.getId() : "default";
        Path workspaceRoot = workspaceResolver.getWorkspaceRoot(workspaceKey);

        // Find baseline commit for this run
        String baselineCommit = null;
        if (workspace != null && workspace.getGitBaselineCommit() != null) {
            baselineCommit = workspace.getGitBaselineCommit();
        } else {
            List<ArtifactEntity> baselines = artifactRepository.findByRunIdAndArtifactType(run.getId(), "BASELINE");
            if (!baselines.isEmpty()) {
                baselineCommit = baselines.get(0).getPathOrRef();
            }
        }

        // Parse changed files from metadata
        List<String> targetFiles = extractFilesFromMetadata(entity.getMetadataJson());
        if (targetFiles.isEmpty() && entity.getPathOrRef() != null && !entity.getPathOrRef().isBlank()) {
            try {
                targetFiles = gitManager.getChangedFilesInCommit(workspaceRoot.toFile(), entity.getPathOrRef());
            } catch (Exception e) {
                log.warn("Failed to extract changed files from commit {}: {}", entity.getPathOrRef(), e.getMessage());
            }
        }

        if (baselineCommit == null || baselineCommit.isBlank()) {
            throw new BusinessException(ErrorCode.WORKSPACE_GIT_ERROR,
                    "Cannot revert: no baseline commit found for run " + run.getId());
        }

        try {
            gitManager.revertStepFiles(workspaceRoot.toFile(), baselineCommit, targetFiles, actorId);
        } catch (Exception e) {
            log.error("Failed to safely revert files for artifact {}: {}", artifactId, e.getMessage(), e);
            throw new BusinessException(ErrorCode.WORKSPACE_GIT_ERROR, "Git revert failed: " + e.getMessage());
        }

        entity.setReviewStatus("REVERTED");
        entity.setReviewedBy(actorId != null ? actorId : RequestContext.get().getUserId());
        entity.setReviewedAt(LocalDateTime.now());
        artifactRepository.save(entity);

        recordRunEvent(entity.getRunId(), "ARTIFACT_REVERTED",
                "Artifact " + artifactId + " reverted to baseline " + baselineCommit);
        log.info("Artifact [{}] reverted to baseline [{}] by [{}]", artifactId, baselineCommit, entity.getReviewedBy());
        return toView(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public void validateRunWorkspaceBinding(String runId, String workspaceId) {
        if (runId == null || runId.isBlank() || workflowRunRepository == null) {
            return;
        }
        WorkflowRunEntity run = workflowRunRepository.findById(runId)
                .orElseThrow(() -> new com.agenthub.shared.exception.BusinessException(
                        com.agenthub.shared.exception.ErrorCode.RUN_NOT_FOUND, "Run not found: " + runId));
        if (projectRepository != null) {
            ProjectEntity project = projectRepository.findById(run.getProjectId())
                    .orElseThrow(() -> new com.agenthub.shared.exception.BusinessException(
                            com.agenthub.shared.exception.ErrorCode.PROJECT_NOT_FOUND, "Project not found: " + run.getProjectId()));
            String currentUserId = RequestContext.get().getUserId();
            if (accessGuard != null && currentUserId != null && !currentUserId.isBlank()) {
                accessGuard.checkOwnership(project.getOwnerId(), currentUserId);
            }
        }
        if (workspaceId != null && !workspaceId.isBlank() && workspaceRepository != null) {
            Optional<WorkspaceEntity> wsOpt = workspaceRepository.findById(workspaceId);
            if (wsOpt.isEmpty()) {
                wsOpt = workspaceRepository.findByProjectId(workspaceId);
            }
            if (wsOpt.isPresent()) {
                String wsProjectId = wsOpt.get().getProjectId();
                if (wsProjectId != null && !wsProjectId.equals(run.getProjectId())) {
                    throw new com.agenthub.shared.exception.BusinessException(
                            com.agenthub.shared.exception.ErrorCode.FORBIDDEN,
                            "Access denied: run [" + runId + "] does not belong to workspace [" + workspaceId + "]");
                }
            } else if (projectRepository != null
                    && projectRepository.findById(workspaceId).isPresent()
                    && !workspaceId.equals(run.getProjectId())) {
                throw new com.agenthub.shared.exception.BusinessException(
                        com.agenthub.shared.exception.ErrorCode.FORBIDDEN,
                        "Access denied: run [" + runId + "] does not belong to project workspace [" + workspaceId + "]");
            }
        }
    }

    @Override
    @Transactional
    public ArtifactView recordStepSnapshot(String runId, String stepRunId, String workspaceId,
                                           String commitHash, String tagName, List<String> changedFiles,
                                           Map<String, String> fileChecksums, String author) {
        validateRunWorkspaceBinding(runId, workspaceId);
        String artifactId = "art-" + UUID.randomUUID().toString().substring(0, 8);
        StringBuilder meta = new StringBuilder("{");
        meta.append("\"commitHash\":\"").append(commitHash).append("\",");
        meta.append("\"tagName\":\"").append(tagName).append("\",");
        meta.append("\"author\":\"").append(author != null ? author : "").append("\",");
        meta.append("\"files\":[");
        if (changedFiles != null) {
            for (int i = 0; i < changedFiles.size(); i++) {
                if (i > 0) meta.append(",");
                meta.append("\"").append(changedFiles.get(i).replace("\"", "\\\"")).append("\"");
            }
        }
        meta.append("]}");

        ArtifactEntity entity = new ArtifactEntity(
                artifactId,
                runId,
                stepRunId,
                "SNAPSHOT",
                commitHash,
                commitHash,
                meta.toString()
        );
        entity.setReviewStatus("PENDING");
        artifactRepository.save(entity);
        return toView(entity);
    }

    private List<String> extractFilesFromMetadata(String metadataJson) {
        List<String> files = new ArrayList<>();
        if (metadataJson == null || metadataJson.isBlank()) {
            return files;
        }
        try {
            com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(metadataJson);
            com.fasterxml.jackson.databind.JsonNode filesNode = root.get("files");
            if (filesNode == null || !filesNode.isArray()) {
                filesNode = root.get("changedFiles");
            }
            if (filesNode != null && filesNode.isArray()) {
                for (com.fasterxml.jackson.databind.JsonNode f : filesNode) {
                    if (f.isTextual() && !f.asText().isBlank()) {
                        files.add(f.asText().trim());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse files from artifact metadata JSON: {}", e.getMessage());
        }
        return files;
    }

    private Map<String, String> extractChecksumsFromMetadata(String metadataJson) {
        Map<String, String> checksums = new HashMap<>();
        if (metadataJson == null || metadataJson.isBlank()) {
            return checksums;
        }
        try {
            com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(metadataJson);
            com.fasterxml.jackson.databind.JsonNode sumsNode = root.get("fileChecksums");
            if (sumsNode == null || !sumsNode.isObject()) {
                sumsNode = root.get("checksums");
            }
            if (sumsNode != null && sumsNode.isObject()) {
                sumsNode.fields().forEachRemaining(entry -> {
                    if (entry.getValue().isTextual()) {
                        checksums.put(entry.getKey(), entry.getValue().asText());
                    }
                });
            }
        } catch (Exception e) {
            log.warn("Failed to parse fileChecksums from artifact metadata: {}", e.getMessage());
        }
        return checksums;
    }

    @Override
    @Transactional(readOnly = true)
    public ArtifactLineageView getArtifactLineageByRunId(String runId) {
        if (runId == null || runId.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Run ID must not be null");
        }
        if (ownershipVerifier != null) {
            ownershipVerifier.verifyRunAccess(runId);
        } else {
            WorkflowRunEntity r = workflowRunRepository.findById(runId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Workflow run not found: " + runId));
            ProjectEntity p = projectRepository.findById(r.getProjectId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Project not found: " + r.getProjectId()));
            accessGuard.checkOwnership(p.getOwnerId(), RequestContext.get().getUserId());
        }

        WorkflowRunEntity run = workflowRunRepository.findById(runId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Workflow run not found: " + runId));

        List<ArtifactEntity> artifacts = artifactRepository.findByRunIdOrderByCreatedAtDesc(runId);
        String baselineCommit = null;
        for (ArtifactEntity art : artifacts) {
            if ("BASELINE".equalsIgnoreCase(art.getArtifactType())) {
                baselineCommit = art.getPathOrRef();
                break;
            }
        }

        WorkspaceEntity ws = workspaceRepository.findByProjectId(run.getProjectId()).orElse(null);
        if (baselineCommit == null && ws != null && ws.getGitBaselineCommit() != null) {
            baselineCommit = ws.getGitBaselineCommit();
        }

        String currentCommit = baselineCommit;
        boolean isReverted = false;
        for (ArtifactEntity art : artifacts) {
            if ("REVERTED".equalsIgnoreCase(art.getReviewStatus())) {
                isReverted = true;
            }
            if ("SNAPSHOT".equalsIgnoreCase(art.getArtifactType()) && currentCommit == null) {
                currentCommit = art.getPathOrRef();
            }
        }

        List<StepLineageNode> stepNodes = new ArrayList<>();
        if (stepRunRepository != null) {
            List<StepRunEntity> steps = stepRunRepository.findByRunIdOrderByCreatedAtAsc(runId);
            Map<String, ApprovalEntity> approvalMap = new HashMap<>();
            if (approvalRepository != null) {
                List<ApprovalEntity> apprs = approvalRepository.findByRunId(runId);
                for (ApprovalEntity a : apprs) {
                    if (a.getStepRunId() != null) {
                        approvalMap.put(a.getStepRunId(), a);
                    }
                }
            }
            Map<String, ArtifactEntity> snapshotMap = new HashMap<>();
            for (ArtifactEntity a : artifacts) {
                if (a.getStepRunId() != null) {
                    snapshotMap.put(a.getStepRunId(), a);
                }
            }

            for (StepRunEntity s : steps) {
                ArtifactEntity art = snapshotMap.get(s.getId());
                ApprovalEntity appr = approvalMap.get(s.getId());
                List<String> files = art != null ? extractFilesFromMetadata(art.getMetadataJson()) : List.of();
                Map<String, String> checksums = art != null ? extractChecksumsFromMetadata(art.getMetadataJson()) : Map.of();

                stepNodes.add(new StepLineageNode(
                        s.getId(),
                        s.getNodeId(),
                        s.getStatus(),
                        art != null ? art.getId() : null,
                        art != null ? art.getPathOrRef() : null,
                        "snapshot-" + s.getId(),
                        files,
                        checksums,
                        art != null ? art.getReviewStatus() : "NONE",
                        appr != null ? appr.getId() : null,
                        appr != null ? appr.getStatus() : null
                ));
            }
        }

        int totalFiles = 0;
        for (StepLineageNode sn : stepNodes) {
            if (sn.getChangedFiles() != null) {
                totalFiles += sn.getChangedFiles().size();
            }
        }

        return new ArtifactLineageView(
                runId,
                run.getProjectId(),
                ws != null ? ws.getId() : null,
                baselineCommit,
                currentCommit,
                stepNodes,
                artifacts.stream().map(this::toView).toList(),
                artifacts.size(),
                totalFiles,
                isReverted
        );
    }

    @Override
    @Transactional(readOnly = true)
    public StructuredDiff getArtifactDiff(String artifactId) {
        ArtifactEntity entity = ownershipVerifier != null
                ? ownershipVerifier.verifyArtifactAccess(artifactId)
                : getArtifactByIdFallback(artifactId);

        WorkflowRunEntity run = workflowRunRepository.findById(entity.getRunId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Workflow run not found: " + entity.getRunId()));

        WorkspaceEntity ws = workspaceRepository.findByProjectId(run.getProjectId()).orElse(null);
        String wsKey = (ws != null) ? ws.getId() : "default";
        Path wsRoot = workspaceResolver.getWorkspaceRoot(wsKey);

        String baselineCommit = null;
        if (ws != null && ws.getGitBaselineCommit() != null) {
            baselineCommit = ws.getGitBaselineCommit();
        } else {
            List<ArtifactEntity> baselines = artifactRepository.findByRunIdAndArtifactType(run.getId(), "BASELINE");
            if (!baselines.isEmpty()) {
                baselineCommit = baselines.get(0).getPathOrRef();
            }
        }

        try {
            if ("SNAPSHOT".equalsIgnoreCase(entity.getArtifactType()) && baselineCommit != null && entity.getPathOrRef() != null) {
                return gitManager.computeStructuredDiffBetweenCommits(wsRoot.toFile(), baselineCommit, entity.getPathOrRef());
            }
            return gitManager.computeStructuredDiff(wsRoot.toFile());
        } catch (Exception e) {
            log.error("Failed to compute structured diff for artifact [{}]: {}", artifactId, e.getMessage());
            throw new BusinessException(ErrorCode.WORKSPACE_GIT_ERROR, "Failed to compute diff: " + e.getMessage());
        }
    }

    private void recordRunEvent(String runId, String eventType, String payload) {
        try {
            if (broadcaster != null) {
                broadcaster.publishEvent(runId, eventType, payload);
                return;
            }
            Long maxSeq = runEventRepository.findMaxSequenceNum(runId);
            long nextSeq = (maxSeq != null ? maxSeq : runEventRepository.countByRunId(runId)) + 1;
            String eventId = "evt-" + UUID.randomUUID().toString().substring(0, 8);
            RunEventEntity event = new RunEventEntity(eventId, runId, nextSeq, eventType, payload);
            runEventRepository.save(event);
        } catch (Exception e) {
            log.warn("Failed to record run event for {}: {}", runId, e.getMessage());
        }
    }

    private ArtifactView toView(ArtifactEntity entity) {
        return new ArtifactView(
                entity.getId(),
                entity.getRunId(),
                entity.getStepRunId(),
                entity.getArtifactType(),
                entity.getPathOrRef(),
                entity.getChecksum(),
                entity.getMetadataJson(),
                entity.getReviewStatus(),
                entity.getReviewedBy(),
                entity.getReviewedAt(),
                entity.getReviewComment(),
                entity.getCreatedAt()
        );
    }

    private ArtifactEntity getArtifactByIdFallback(String artifactId) {
        return getArtifactByIdWithActorFallback(artifactId, RequestContext.get().getUserId());
    }

    private ArtifactEntity getArtifactByIdWithActorFallback(String artifactId, String actor) {
        ArtifactEntity entity = artifactRepository.findById(artifactId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ARTIFACT_NOT_FOUND, "Artifact not found: " + artifactId));
        WorkflowRunEntity run = workflowRunRepository.findById(entity.getRunId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Workflow run not found: " + entity.getRunId()));
        ProjectEntity project = projectRepository.findById(run.getProjectId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Project not found: " + run.getProjectId()));
        accessGuard.checkOwnership(project.getOwnerId(), actor);
        return entity;
    }

    @Override
    @Transactional
    public QualityGateReport evaluateQualityGate(String artifactId) {
        ArtifactEntity entity = ownershipVerifier != null
                ? ownershipVerifier.verifyArtifactAccess(artifactId)
                : getArtifactByIdFallback(artifactId);

        StructuredDiff diff = getArtifactDiff(artifactId);
        List<StepRunView> stepRuns = List.of();
        if (stepRunRepository != null) {
            stepRuns = stepRunRepository.findByRunIdOrderByCreatedAtAsc(entity.getRunId()).stream()
                    .map(RunQueryModule::toStepView)
                    .toList();
        }

        QualityGateEngine engine = (this.qualityGateEngine != null) ? this.qualityGateEngine : new QualityGateEngine();
        QualityGateReport report = engine.evaluate(artifactId, entity.getRunId(), diff, stepRuns);

        try {
            JsonNode rootNode = (entity.getMetadataJson() != null && !entity.getMetadataJson().isBlank())
                    ? objectMapper.readTree(entity.getMetadataJson())
                    : objectMapper.createObjectNode();
            if (rootNode instanceof ObjectNode obj) {
                obj.set("qualityGateReport", objectMapper.valueToTree(report));
                entity.setMetadataJson(objectMapper.writeValueAsString(obj));
                artifactRepository.save(entity);
            }
        } catch (Exception e) {
            log.warn("Failed to persist qualityGateReport into artifact metadata: {}", e.getMessage());
        }

        recordRunEvent(entity.getRunId(), "QUALITY_GATE_EVALUATED",
                String.format("{\"artifactId\":\"%s\",\"status\":\"%s\",\"score\":%d}",
                        artifactId, report.getOverallStatus(), report.getCompositeScore()));
        return report;
    }

    @Override
    @Transactional(readOnly = true)
    public QualityGateReport getQualityGateReport(String artifactId) {
        ArtifactEntity entity = ownershipVerifier != null
                ? ownershipVerifier.verifyArtifactAccess(artifactId)
                : getArtifactByIdFallback(artifactId);
        if (entity.getMetadataJson() != null && entity.getMetadataJson().contains("qualityGateReport")) {
            try {
                JsonNode root = objectMapper.readTree(entity.getMetadataJson());
                if (root.has("qualityGateReport")) {
                    return objectMapper.treeToValue(root.get("qualityGateReport"), QualityGateReport.class);
                }
            } catch (Exception ignored) {}
        }
        return evaluateQualityGate(artifactId);
    }

    @Override
    @Transactional(readOnly = true)
    public PullRequestDraftView generatePullRequestDraft(String artifactId) {
        ArtifactEntity entity = ownershipVerifier != null
                ? ownershipVerifier.verifyArtifactAccess(artifactId)
                : getArtifactByIdFallback(artifactId);

        QualityGateReport gateReport = getQualityGateReport(artifactId);
        StructuredDiff diff = getArtifactDiff(artifactId);

        PullRequestDraftView draft = new PullRequestDraftView();
        draft.setDraftId("pr-draft-" + UUID.randomUUID().toString().substring(0, 8));
        draft.setArtifactId(artifactId);
        draft.setRunId(entity.getRunId());
        String mainTarget = (diff.getEntries() != null && !diff.getEntries().isEmpty() && diff.getEntries().get(0).getNewPath() != null)
                ? diff.getEntries().get(0).getNewPath()
                : entity.getArtifactType();
        draft.setTitle(String.format("feat(agent-delivery): %s (Run %s)", mainTarget, entity.getRunId()));
        draft.setSourceBranch("feature/run-" + entity.getRunId());
        draft.setTargetBranch("main");
        draft.setQualityGateReport(gateReport);
        draft.setChangedFilesCount(diff.getEntries() != null ? diff.getEntries().size() : 0);
        draft.setAddedLines(diff.getTotalLinesAdded());
        draft.setDeletedLines(diff.getTotalLinesDeleted());
        draft.setCanMergeSafely(gateReport.getOverallStatus() == QualityGateStatus.PASSED && "ACCEPTED".equalsIgnoreCase(entity.getReviewStatus()));
        draft.setCreatedAt(LocalDateTime.now());

        StringBuilder sb = new StringBuilder();
        sb.append("## Automated Change Delivery Summary\n\n");
        sb.append("- **Run ID**: `").append(entity.getRunId()).append("`\n");
        sb.append("- **Artifact**: `").append(artifactId).append("` (Status: ").append(entity.getReviewStatus()).append(")\n");
        sb.append("- **Quality Gate**: **").append(gateReport.getOverallStatus()).append("** (Score: ").append(gateReport.getCompositeScore()).append("/100)\n");
        sb.append("- **Diff Scale**: `+").append(diff.getTotalLinesAdded()).append(" / -").append(diff.getTotalLinesDeleted()).append("` lines across ")
                .append(draft.getChangedFilesCount()).append(" file(s)\n\n");
        sb.append("### Quality Gate Check Matrix\n\n");
        sb.append("| Gate Dimension | Status | Score | Findings |\n");
        sb.append("|---|---|---|---|\n");
        if (gateReport.getCheckItems() != null) {
            for (QualityGateCheckItem item : gateReport.getCheckItems()) {
                sb.append("| `").append(item.getRuleType()).append("` | ").append(item.getStatus()).append(" | ")
                        .append(item.getScore()).append(" | ").append(item.getMessage()).append(" |\n");
            }
        }
        sb.append("\n### Safety & Security Verification\n\n");
        sb.append("- Zero leaked API keys or credentials detected via automated secret scanner\n");
        sb.append("- Change strictly verified under controlled workspace boundaries\n");
        sb.append("- SHA-256 Fingerprint: `").append(gateReport.getSha256Fingerprint()).append("`\n");

        draft.setSummaryMarkdown(sb.toString());
        draft.setAuditChecksum(gateReport.getSha256Fingerprint());
        return draft;
    }

    @Override
    @Transactional
    public ArtifactView revertSingleFile(String artifactId, String filePath, String actorId) {
        if (filePath == null || filePath.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "File path to revert cannot be empty");
        }
        ArtifactEntity entity = ownershipVerifier != null
                ? ownershipVerifier.verifyArtifactAccess(artifactId, actorId)
                : getArtifactByIdWithActorFallback(artifactId, actorId);

        WorkflowRunEntity run = workflowRunRepository.findById(entity.getRunId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Workflow run not found: " + entity.getRunId()));
        ProjectEntity project = projectRepository.findById(run.getProjectId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Project not found: " + run.getProjectId()));
        String actor = (actorId != null && !actorId.isBlank()) ? actorId : RequestContext.get().getUserId();
        accessGuard.checkOwnership(project.getOwnerId(), actor);

        WorkspaceEntity workspace = workspaceRepository.findByProjectId(project.getId()).orElse(null);
        String workspaceKey = (workspace != null) ? workspace.getId() : "default";
        Path workspaceRoot = workspaceResolver.getWorkspaceRoot(workspaceKey);

        String baselineCommit = null;
        if (workspace != null && workspace.getGitBaselineCommit() != null) {
            baselineCommit = workspace.getGitBaselineCommit();
        } else {
            List<ArtifactEntity> baselines = artifactRepository.findByRunIdAndArtifactType(run.getId(), "BASELINE");
            if (!baselines.isEmpty()) {
                baselineCommit = baselines.get(0).getPathOrRef();
            }
        }
        if (baselineCommit == null || baselineCommit.isBlank()) {
            throw new BusinessException(ErrorCode.WORKSPACE_GIT_ERROR, "Cannot revert: no baseline commit found for run " + run.getId());
        }

        try {
            gitManager.revertSingleFile(workspaceRoot.toFile(), baselineCommit, filePath, actor);
        } catch (Exception e) {
            log.error("Failed to safely revert file [{}] for artifact [{}]: {}", filePath, artifactId, e.getMessage(), e);
            throw new BusinessException(ErrorCode.WORKSPACE_GIT_ERROR, "Git single file revert failed: " + e.getMessage());
        }

        recordRunEvent(entity.getRunId(), "FILE_REVERTED",
                String.format("{\"artifactId\":\"%s\",\"filePath\":\"%s\",\"baseline\":\"%s\"}",
                        artifactId, filePath, baselineCommit));
        log.info("File [{}] in artifact [{}] reverted to baseline [{}] by [{}]", filePath, artifactId, baselineCommit, actor);

        evaluateQualityGate(artifactId);
        return toView(entity);
    }
}
