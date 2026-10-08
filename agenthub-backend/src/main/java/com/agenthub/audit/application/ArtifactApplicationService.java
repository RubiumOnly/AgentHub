package com.agenthub.audit.application;

import com.agenthub.adapter.common.BusinessException;
import com.agenthub.adapter.common.ErrorCode;
import com.agenthub.audit.dto.ArtifactView;
import com.agenthub.audit.infrastructure.entity.ArtifactEntity;
import com.agenthub.audit.infrastructure.repository.ArtifactRepository;
import com.agenthub.domain.workspace.service.JGitWorkspaceManager;
import com.agenthub.domain.workspace.service.WorkspaceResolver;
import com.agenthub.execution.infrastructure.entity.RunEventEntity;
import com.agenthub.execution.infrastructure.entity.WorkflowRunEntity;
import com.agenthub.execution.infrastructure.repository.RunEventRepository;
import com.agenthub.execution.infrastructure.repository.WorkflowRunRepository;
import com.agenthub.identity.infrastructure.security.ResourceAccessGuard;
import com.agenthub.project.infrastructure.entity.ProjectEntity;
import com.agenthub.project.infrastructure.entity.WorkspaceEntity;
import com.agenthub.project.infrastructure.repository.ProjectRepository;
import com.agenthub.project.infrastructure.repository.WorkspaceRepository;
import com.agenthub.shared.context.RequestContext;
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

    public ArtifactApplicationService(ArtifactRepository artifactRepository,
                                    WorkflowRunRepository workflowRunRepository,
                                    ProjectRepository projectRepository,
                                    WorkspaceRepository workspaceRepository,
                                    WorkspaceResolver workspaceResolver,
                                    JGitWorkspaceManager gitManager,
                                    RunEventRepository runEventRepository,
                                    ResourceAccessGuard accessGuard) {
        this(artifactRepository, workflowRunRepository, projectRepository, workspaceRepository,
                workspaceResolver, gitManager, runEventRepository, accessGuard, new com.fasterxml.jackson.databind.ObjectMapper());
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
                                    @org.springframework.beans.factory.annotation.Autowired(required = false) com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        this.artifactRepository = artifactRepository;
        this.workflowRunRepository = workflowRunRepository;
        this.projectRepository = projectRepository;
        this.workspaceRepository = workspaceRepository;
        this.workspaceResolver = workspaceResolver;
        this.gitManager = gitManager;
        this.runEventRepository = runEventRepository;
        this.accessGuard = accessGuard;
        this.objectMapper = (objectMapper != null) ? objectMapper : new com.fasterxml.jackson.databind.ObjectMapper();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ArtifactView> listArtifactsByRunId(String runId) {
        WorkflowRunEntity run = workflowRunRepository.findById(runId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Workflow run not found: " + runId));
        ProjectEntity project = projectRepository.findById(run.getProjectId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Project not found: " + run.getProjectId()));
        accessGuard.checkOwnership(project.getOwnerId(), RequestContext.get().getUserId());

        return artifactRepository.findByRunIdOrderByCreatedAtDesc(runId).stream()
                .map(this::toView)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public ArtifactView getArtifactById(String artifactId) {
        ArtifactEntity entity = artifactRepository.findById(artifactId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ARTIFACT_NOT_FOUND, "Artifact not found: " + artifactId));
        WorkflowRunEntity run = workflowRunRepository.findById(entity.getRunId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Workflow run not found: " + entity.getRunId()));
        ProjectEntity project = projectRepository.findById(run.getProjectId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Project not found: " + run.getProjectId()));
        accessGuard.checkOwnership(project.getOwnerId(), RequestContext.get().getUserId());

        return toView(entity);
    }

    @Override
    @Transactional
    public ArtifactView acceptArtifact(String artifactId, String reviewerId) {
        ArtifactEntity entity = artifactRepository.findById(artifactId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ARTIFACT_NOT_FOUND, "Artifact not found: " + artifactId));
        WorkflowRunEntity run = workflowRunRepository.findById(entity.getRunId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Workflow run not found: " + entity.getRunId()));
        ProjectEntity project = projectRepository.findById(run.getProjectId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Project not found: " + run.getProjectId()));
        String actor = (reviewerId != null && !reviewerId.isBlank()) ? reviewerId : RequestContext.get().getUserId();
        accessGuard.checkOwnership(project.getOwnerId(), actor);

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
        ArtifactEntity entity = artifactRepository.findById(artifactId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ARTIFACT_NOT_FOUND, "Artifact not found: " + artifactId));
        WorkflowRunEntity run = workflowRunRepository.findById(entity.getRunId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Workflow run not found: " + entity.getRunId()));
        ProjectEntity project = projectRepository.findById(run.getProjectId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Project not found: " + run.getProjectId()));
        String actor = (reviewerId != null && !reviewerId.isBlank()) ? reviewerId : RequestContext.get().getUserId();
        accessGuard.checkOwnership(project.getOwnerId(), actor);

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
    @Transactional
    public ArtifactView recordStepSnapshot(String runId, String stepRunId, String workspaceId,
                                           String commitHash, String tagName, List<String> changedFiles,
                                           Map<String, String> fileChecksums, String author) {
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

    private void recordRunEvent(String runId, String eventType, String payload) {
        try {
            long nextSeq = runEventRepository.countByRunId(runId) + 1;
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
}
