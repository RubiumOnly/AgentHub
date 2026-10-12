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
import com.agenthub.execution.domain.model.CancelTokenRegistry;
import com.agenthub.execution.domain.model.StepRunStatus;
import com.agenthub.execution.domain.model.WorkflowRunStatus;
import com.agenthub.execution.dto.RunEventView;
import com.agenthub.execution.dto.RunEvidenceBundleView;
import com.agenthub.execution.dto.StartRunCommand;
import com.agenthub.execution.dto.StepRunView;
import com.agenthub.execution.dto.WorkflowRunView;
import com.agenthub.execution.infrastructure.entity.RunEventEntity;
import com.agenthub.execution.infrastructure.entity.StepRunEntity;
import com.agenthub.execution.infrastructure.entity.WorkflowRunEntity;
import com.agenthub.execution.infrastructure.repository.RunEventRepository;
import com.agenthub.execution.infrastructure.repository.StepRunRepository;
import com.agenthub.execution.infrastructure.repository.WorkflowRunRepository;
import com.agenthub.execution.scheduler.ExecutionScheduler;
import com.agenthub.execution.service.ExecutionStateMachine;
import com.agenthub.execution.service.RunEventBroadcaster;
import com.agenthub.identity.infrastructure.security.ResourceAccessGuard;
import com.agenthub.orchestration.domain.dsl.WorkflowDsl;
import com.agenthub.orchestration.domain.dsl.WorkflowNodeDsl;
import com.agenthub.orchestration.dto.ApprovalView;
import com.agenthub.orchestration.infrastructure.entity.ApprovalEntity;
import com.agenthub.orchestration.infrastructure.entity.WorkflowDefinitionEntity;
import com.agenthub.orchestration.infrastructure.repository.ApprovalRepository;
import com.agenthub.orchestration.infrastructure.repository.WorkflowDefinitionRepository;
import com.agenthub.orchestration.scheduler.DagExecutionEngine;
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
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ExecutionApplicationService implements ExecutionApplication {

    private static final ObjectMapper EVIDENCE_MAPPER = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .build();

    @Autowired(required = false)
    private WorkflowDefinitionRepository workflowDefinitionRepository;

    @Autowired(required = false)
    private DagExecutionEngine dagExecutionEngine;

    @Autowired(required = false)
    private ObjectMapper objectMapper;

    @Autowired(required = false)
    private TokenUsageAuditRepository tokenUsageAuditRepository;

    @Autowired(required = false)
    private ApprovalRepository approvalRepository;

    @Autowired(required = false)
    private DeploymentRepository deploymentRepository;

    private static final Logger log = LoggerFactory.getLogger(ExecutionApplicationService.class);

    private final WorkflowRunRepository workflowRunRepository;
    private final StepRunRepository stepRunRepository;
    private final RunEventRepository runEventRepository;
    private final ProjectRepository projectRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceResolver workspaceResolver;
    private final JGitWorkspaceManager gitManager;
    private final ArtifactRepository artifactRepository;
    private final ResourceAccessGuard accessGuard;
    private final ExecutionStateMachine stateMachine;
    private final CancelTokenRegistry cancelTokenRegistry;
    private final RunEventBroadcaster broadcaster;
    private final ExecutionScheduler executionScheduler;

    public ExecutionApplicationService(WorkflowRunRepository workflowRunRepository,
                                       StepRunRepository stepRunRepository,
                                       RunEventRepository runEventRepository,
                                       ProjectRepository projectRepository,
                                       WorkspaceRepository workspaceRepository,
                                       WorkspaceResolver workspaceResolver,
                                       JGitWorkspaceManager gitManager,
                                       ArtifactRepository artifactRepository,
                                       ResourceAccessGuard accessGuard,
                                       ExecutionStateMachine stateMachine,
                                       CancelTokenRegistry cancelTokenRegistry,
                                       RunEventBroadcaster broadcaster,
                                       ExecutionScheduler executionScheduler) {
        this.workflowRunRepository = workflowRunRepository;
        this.stepRunRepository = stepRunRepository;
        this.runEventRepository = runEventRepository;
        this.projectRepository = projectRepository;
        this.workspaceRepository = workspaceRepository;
        this.workspaceResolver = workspaceResolver;
        this.gitManager = gitManager;
        this.artifactRepository = artifactRepository;
        this.accessGuard = accessGuard;
        this.stateMachine = stateMachine;
        this.cancelTokenRegistry = cancelTokenRegistry;
        this.broadcaster = broadcaster;
        this.executionScheduler = executionScheduler;
    }

    @Override
    @Transactional
    public WorkflowRunView startRun(StartRunCommand cmd) {
        if (cmd == null || cmd.getProjectId() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Project ID must not be null");
        }

        ProjectEntity project = projectRepository.findById(cmd.getProjectId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND, "Project not found: " + cmd.getProjectId()));
        accessGuard.checkOwnership(project.getOwnerId(), RequestContext.get().getUserId());

        // Idempotency check
        if (cmd.getIdempotencyKey() != null && !cmd.getIdempotencyKey().isBlank()) {
            Optional<WorkflowRunEntity> existing = workflowRunRepository.findByIdempotencyKey(cmd.getIdempotencyKey());
            if (existing.isPresent()) {
                return toRunView(existing.get());
            }
        }

        String runId = "run-" + UUID.randomUUID().toString().substring(0, 8);
        String targetDefId = cmd.getDefinitionId() != null && !cmd.getDefinitionId().isBlank() ? cmd.getDefinitionId() : "def-default";
        WorkflowRunEntity run = new WorkflowRunEntity(
                runId,
                cmd.getProjectId(),
                targetDefId,
                "RUNNING",
                cmd.getIdempotencyKey()
        );
        run.setStartedAt(LocalDateTime.now());
        run.setCorrelationId(RequestContext.get().getCorrelationId());

        // Resolve workflow DSL if present
        WorkflowDsl dslToRun = null;
        if (cmd.getWorkflowDsl() != null) {
            dslToRun = cmd.getWorkflowDsl();
        } else if (workflowDefinitionRepository != null) {
            Optional<WorkflowDefinitionEntity> defOpt = workflowDefinitionRepository.findById(targetDefId);
            if (defOpt.isPresent() && objectMapper != null) {
                try {
                    dslToRun = objectMapper.readValue(defOpt.get().getDslJson(), WorkflowDsl.class);
                } catch (Exception e) {
                    log.warn("Failed to parse DSL JSON for run [{}]: {}", runId, e.getMessage());
                }
            }
        }

        if (dslToRun != null && objectMapper != null) {
            try {
                run.setDslSnapshot(objectMapper.writeValueAsString(dslToRun));
            } catch (Exception ignored) {}
        }

        run.setLeaseOwner(DagExecutionEngine.INSTANCE_WORKER_ID);
        run.setLeaseUntil(LocalDateTime.now().plusSeconds(180));
        run.setHeartbeatAt(LocalDateTime.now());
        run.setAttempt(1);
        workflowRunRepository.save(run);

        // Record initial event through broadcaster (produces sequence 1)
        broadcaster.publishEvent(runId, "RUN_STARTED", "Workflow execution started");

        // Establish JGit baseline commit if workspace exists
        workspaceRepository.findByProjectId(cmd.getProjectId()).ifPresent(ws -> {
            try {
                Path root = workspaceResolver.getWorkspaceRoot(ws.getId());
                String author = RequestContext.get().getUserId();
                String baselineHash = gitManager.createBaseline(root.toFile(), runId, author);
                ws.setGitBaselineCommit(baselineHash);
                workspaceRepository.save(ws);

                String artId = "art-base-" + UUID.randomUUID().toString().substring(0, 8);
                ArtifactEntity baselineArtifact = new ArtifactEntity(
                        artId, runId, null, "BASELINE", baselineHash, baselineHash,
                        "{\"baselineCommit\":\"" + baselineHash + "\"}"
                );
                baselineArtifact.setReviewStatus("ACCEPTED");
                artifactRepository.save(baselineArtifact);
                log.info("Established JGit baseline commit [{}] for run [{}] in workspace [{}]", baselineHash, runId, ws.getId());
            } catch (Exception e) {
                log.warn("Failed to initialize git baseline for run {}: {}", runId, e.getMessage());
            }
        });

        // If workflow DSL or saved definition is provided, trigger DAG execution post-commit
        if (dagExecutionEngine != null && dslToRun != null) {
            String wsPath = null;
            Optional<WorkspaceEntity> wsOpt = workspaceRepository.findByProjectId(cmd.getProjectId());
            if (wsOpt.isPresent()) {
                try {
                    wsPath = workspaceResolver.getWorkspaceRoot(wsOpt.get().getId()).toAbsolutePath().toString();
                } catch (Exception ignored) {}
            }
            final WorkflowDsl finalDsl = dslToRun;
            final String finalWsPath = wsPath;
            final Map<String, Object> finalInputs = cmd.getInputs();
            long timeout = finalDsl.getTimeoutSeconds() != null && finalDsl.getTimeoutSeconds() > 0 ? finalDsl.getTimeoutSeconds() : 120;
            String currentUserId = RequestContext.get().getUserId();

            if (org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) {
                org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                        new org.springframework.transaction.support.TransactionSynchronization() {
                            @Override
                            public void afterCommit() {
                                dagExecutionEngine.executeDag(runId, finalDsl, finalWsPath, finalInputs, timeout, currentUserId);
                            }
                        }
                );
            } else {
                dagExecutionEngine.executeDag(runId, finalDsl, finalWsPath, finalInputs, timeout, currentUserId);
            }
        }

        return toRunView(run);
    }

    @Override
    @Transactional(readOnly = true)
    public WorkflowRunView getRunById(String runId) {
        WorkflowRunEntity run = workflowRunRepository.findById(runId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RUN_NOT_FOUND, "Run not found: " + runId));
        ProjectEntity project = projectRepository.findById(run.getProjectId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND, "Project not found: " + run.getProjectId()));
        accessGuard.checkOwnership(project.getOwnerId(), RequestContext.get().getUserId());
        return toRunView(run);
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkflowRunView> listRunsByProjectId(String projectId) {
        ProjectEntity project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND, "Project not found: " + projectId));
        accessGuard.checkOwnership(project.getOwnerId(), RequestContext.get().getUserId());
        return workflowRunRepository.findByProjectIdOrderByCreatedAtDesc(projectId).stream()
                .map(this::toRunView)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<StepRunView> listStepRuns(String runId) {
        WorkflowRunEntity run = workflowRunRepository.findById(runId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RUN_NOT_FOUND, "Run not found: " + runId));
        ProjectEntity project = projectRepository.findById(run.getProjectId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND, "Project not found: " + run.getProjectId()));
        accessGuard.checkOwnership(project.getOwnerId(), RequestContext.get().getUserId());
        return stepRunRepository.findByRunIdOrderByCreatedAtAsc(runId).stream()
                .map(this::toStepView)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public RunEventView appendEvent(String runId, String eventType, String payload) {
        WorkflowRunEntity run = workflowRunRepository.findById(runId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RUN_NOT_FOUND, "Run not found: " + runId));
        ProjectEntity project = projectRepository.findById(run.getProjectId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND, "Project not found: " + run.getProjectId()));
        accessGuard.checkOwnership(project.getOwnerId(), RequestContext.get().getUserId());

        RunEventEntity event = broadcaster.publishEvent(runId, eventType, payload);
        return toEventView(event);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RunEventView> listEvents(String runId, Long afterSeq) {
        WorkflowRunEntity run = workflowRunRepository.findById(runId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RUN_NOT_FOUND, "Run not found: " + runId));
        ProjectEntity project = projectRepository.findById(run.getProjectId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND, "Project not found: " + run.getProjectId()));
        accessGuard.checkOwnership(project.getOwnerId(), RequestContext.get().getUserId());

        List<RunEventEntity> events;
        if (afterSeq != null && afterSeq > 0) {
            events = runEventRepository.findByRunIdAndSequenceNumGreaterThanOrderBySequenceNumAsc(runId, afterSeq);
        } else {
            events = runEventRepository.findByRunIdOrderBySequenceNumAsc(runId);
        }
        return events.stream().map(this::toEventView).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public WorkflowRunView cancelRun(String runId, String reason) {
        WorkflowRunEntity run = workflowRunRepository.findById(runId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RUN_NOT_FOUND, "Run not found: " + runId));
        ProjectEntity project = projectRepository.findById(run.getProjectId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND, "Project not found: " + run.getProjectId()));
        accessGuard.checkOwnership(project.getOwnerId(), RequestContext.get().getUserId());

        String cancelReason = reason != null && !reason.isBlank() ? reason : "User cancelled execution";

        // Signal CancelToken
        cancelTokenRegistry.cancel(runId, cancelReason);

        // Cancel running and pending steps
        List<StepRunEntity> steps = stepRunRepository.findByRunIdOrderByCreatedAtAsc(runId);
        for (StepRunEntity step : steps) {
            StepRunStatus status = StepRunStatus.fromString(step.getStatus());
            if (!status.isTerminal()) {
                stateMachine.transitionStep(step.getId(), StepRunStatus.CANCELLED, cancelReason);
            }
        }

        // Transition run state
        WorkflowRunEntity updated = stateMachine.transitionRun(runId, WorkflowRunStatus.CANCELLED, cancelReason);
        broadcaster.publishEvent(runId, "RUN_CANCELLED", "{\"reason\":\"" + cancelReason + "\"}");

        return toRunView(updated);
    }

    @Override
    @Transactional
    public WorkflowRunView pauseRun(String runId) {
        WorkflowRunEntity run = workflowRunRepository.findById(runId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RUN_NOT_FOUND, "Run not found: " + runId));
        ProjectEntity project = projectRepository.findById(run.getProjectId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND, "Project not found: " + run.getProjectId()));
        accessGuard.checkOwnership(project.getOwnerId(), RequestContext.get().getUserId());

        WorkflowRunEntity updated = stateMachine.transitionRun(runId, WorkflowRunStatus.PAUSED, "Paused by user");
        broadcaster.publishEvent(runId, "RUN_PAUSED", "{\"reason\":\"Paused by user\"}");
        return toRunView(updated);
    }

    @Override
    @Transactional
    public WorkflowRunView resumeRun(String runId) {
        WorkflowRunEntity run = workflowRunRepository.findById(runId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RUN_NOT_FOUND, "Run not found: " + runId));
        ProjectEntity project = projectRepository.findById(run.getProjectId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND, "Project not found: " + run.getProjectId()));
        accessGuard.checkOwnership(project.getOwnerId(), RequestContext.get().getUserId());

        WorkflowRunEntity updated = stateMachine.transitionRun(runId, WorkflowRunStatus.RUNNING, "Resumed by user");
        broadcaster.publishEvent(runId, "RUN_RESUMED", "{\"reason\":\"Resumed by user\"}");
        return toRunView(updated);
    }

    @Override
    @Transactional
    public StepRunView retryStep(String stepRunId) {
        StepRunEntity step = stepRunRepository.findById(stepRunId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STEP_RUN_NOT_FOUND, "Step run not found: " + stepRunId));
        WorkflowRunEntity run = workflowRunRepository.findById(step.getRunId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RUN_NOT_FOUND, "Run not found: " + step.getRunId()));
        ProjectEntity project = projectRepository.findById(run.getProjectId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND, "Project not found: " + run.getProjectId()));
        accessGuard.checkOwnership(project.getOwnerId(), RequestContext.get().getUserId());

        StepRunEntity updated = stateMachine.transitionStep(stepRunId, StepRunStatus.PENDING, "Retry requested by user");
        broadcaster.publishEvent(run.getId(), "STEP_RETRY_INITIATED", "{\"stepRunId\":\"" + stepRunId + "\"}");
        return toStepView(updated);
    }

    @Override
    public SseEmitter subscribeRunStream(String runId, Long lastEventId) {
        WorkflowRunEntity run = workflowRunRepository.findById(runId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RUN_NOT_FOUND, "Run not found: " + runId));
        ProjectEntity project = projectRepository.findById(run.getProjectId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND, "Project not found: " + run.getProjectId()));
        accessGuard.checkOwnership(project.getOwnerId(), RequestContext.get().getUserId());

        return broadcaster.subscribe(runId, lastEventId);
    }

    @Override
    @Transactional(readOnly = true)
    public RunEvidenceBundleView exportRunEvidenceBundle(String runId) {
        if (runId == null || runId.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Run ID must not be empty");
        }
        String currentUserId = RequestContext.get().getUserId();
        if (!RequestContext.get().isSystem() && (currentUserId == null || currentUserId.isBlank())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Authentication required to export run evidence bundle");
        }

        WorkflowRunEntity run = workflowRunRepository.findById(runId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RUN_NOT_FOUND, "Run not found: " + runId));
        ProjectEntity project = projectRepository.findById(run.getProjectId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND, "Project not found: " + run.getProjectId()));
        accessGuard.checkOwnership(project.getOwnerId(), currentUserId);

        Optional<WorkspaceEntity> wsOpt = workspaceRepository.findByProjectId(project.getId());
        String wsId = wsOpt.map(WorkspaceEntity::getId).orElse(null);
        String baselineCommit = wsOpt.map(WorkspaceEntity::getGitBaselineCommit).orElse(null);

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

        // 1. Sanitized Workflow DSL Snapshot
        String rawDslSnapshot = run.getDslSnapshot();
        if ((rawDslSnapshot == null || rawDslSnapshot.isBlank()) && workflowDefinitionRepository != null && run.getDefinitionId() != null) {
            rawDslSnapshot = workflowDefinitionRepository.findById(run.getDefinitionId())
                    .map(WorkflowDefinitionEntity::getDslJson)
                    .orElse(null);
        }
        bundle.setWorkflowDslSnapshot(rawDslSnapshot != null ? SecretMasker.maskInText(rawDslSnapshot) : null);

        // 2. Sanitized Run & Steps
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

        // 3. Monotonic Event Sequence (Sanitized)
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

        // 4. Provider & Runtime Evidence + Simulated Mode Classification
        Set<String> runtimeTypes = new LinkedHashSet<>();
        ObjectMapper mapper = objectMapper != null ? objectMapper : EVIDENCE_MAPPER;
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

        Boolean eventSimulatedFlag = null;
        long eventPromptTokens = 0L;
        long eventCompletionTokens = 0L;
        double eventCost = 0.0;
        List<String> verificationLogs = new ArrayList<>();

        for (RunEventView ev : sanitizedEvents) {
            String payload = ev.getPayload();
            if (payload == null || payload.isBlank()) continue;
            if ("STEP_STARTED".equalsIgnoreCase(ev.getEventType()) || "STEP_COMPLETED".equalsIgnoreCase(ev.getEventType())) {
                try {
                    JsonNode node = mapper.readTree(payload);
                    if (node.hasNonNull("runtimeType")) {
                        runtimeTypes.add(node.get("runtimeType").asText().trim().toUpperCase());
                    }
                    if (node.hasNonNull("simulated")) {
                        boolean sim = node.get("simulated").asBoolean();
                        eventSimulatedFlag = (eventSimulatedFlag == null) ? sim : (eventSimulatedFlag && sim);
                    }
                } catch (Exception ignored) {}
            } else if ("USAGE".equalsIgnoreCase(ev.getEventType())) {
                try {
                    JsonNode node = mapper.readTree(payload);
                    eventPromptTokens += node.path("promptTokens").asLong(0L);
                    eventCompletionTokens += node.path("completionTokens").asLong(0L);
                    eventCost += node.path("cost").asDouble(0.0);
                } catch (Exception ignored) {}
            } else if ("LOG".equalsIgnoreCase(ev.getEventType()) || "TOOL_CALL".equalsIgnoreCase(ev.getEventType())) {
                verificationLogs.add("[" + ev.getEventType() + "#" + ev.getSequenceNum() + "] " + payload);
            }
        }

        List<TokenUsageAuditView> usageViews = new ArrayList<>();
        Set<String> providerTypes = new LinkedHashSet<>();
        Set<String> models = new LinkedHashSet<>();
        long totalPrompt = 0L;
        long totalCompletion = 0L;
        long totalTokens = 0L;
        double totalCost = 0.0;

        if (tokenUsageAuditRepository != null) {
            Map<String, TokenUsageAuditEntity> usageById = new LinkedHashMap<>();
            for (TokenUsageAuditEntity u : tokenUsageAuditRepository.findByRunIdOrderByCreatedAtAsc(runId)) {
                usageById.put(u.getId(), u);
            }
            for (StepRunEntity se : stepEntities) {
                for (TokenUsageAuditEntity u : tokenUsageAuditRepository.findByStepRunIdOrderByCreatedAtAsc(se.getId())) {
                    usageById.putIfAbsent(u.getId(), u);
                }
            }
            for (TokenUsageAuditEntity u : usageById.values()) {
                if (u.getProviderType() != null && !u.getProviderType().isBlank()) {
                    providerTypes.add(u.getProviderType());
                }
                if (u.getModel() != null && !u.getModel().isBlank()) {
                    models.add(u.getModel());
                }
                totalPrompt += u.getPromptTokens();
                totalCompletion += u.getCompletionTokens();
                totalTokens += u.getTotalTokens();
                totalCost += u.getEstimatedCost();
                usageViews.add(new TokenUsageAuditView(
                        u.getId(),
                        u.getRunId(),
                        u.getStepRunId(),
                        u.getProviderId(),
                        u.getProviderType(),
                        u.getModel(),
                        u.getPromptTokens(),
                        u.getCompletionTokens(),
                        u.getTotalTokens(),
                        u.getLatencyMs(),
                        u.getEstimatedCost(),
                        u.getStatus(),
                        u.getErrorMessage() != null ? SecretMasker.maskInText(u.getErrorMessage()) : null,
                        u.getCreatedAt()
                ));
            }
        }

        if (usageViews.isEmpty() && (eventPromptTokens > 0 || eventCompletionTokens > 0)) {
            totalPrompt = eventPromptTokens;
            totalCompletion = eventCompletionTokens;
            totalTokens = eventPromptTokens + eventCompletionTokens;
            totalCost = Math.round(eventCost * 1_000_000.0) / 1_000_000.0;
        }

        boolean hasLiveTokenUsage = usageViews.stream()
                .anyMatch(u -> u.getProviderType() != null
                        && !"MOCK".equalsIgnoreCase(u.getProviderType())
                        && !"SIMULATED".equalsIgnoreCase(u.getProviderType()));
        boolean hasNonMockRuntime = runtimeTypes.stream()
                .anyMatch(rt -> rt != null && !"MOCK".equalsIgnoreCase(rt));
        boolean hasMockRuntime = runtimeTypes.stream()
                .anyMatch(rt -> "MOCK".equalsIgnoreCase(rt));

        boolean isSimulated;
        if (hasLiveTokenUsage) {
            isSimulated = false;
        } else if (eventSimulatedFlag != null) {
            isSimulated = eventSimulatedFlag;
        } else if (hasNonMockRuntime && !hasMockRuntime) {
            isSimulated = false;
        } else {
            isSimulated = true;
        }

        if (runtimeTypes.isEmpty()) {
            runtimeTypes.add(isSimulated ? "MOCK" : "PROVIDER_DRIVEN");
        }
        if (providerTypes.isEmpty()) {
            providerTypes.add(isSimulated ? "MOCK_SIMULATOR" : "DYNAMIC_ROUTER");
        }
        if (models.isEmpty()) {
            models.add(isSimulated ? "mock-offline-v1" : "router-default");
        }

        RunEvidenceBundleView.ProviderRuntimeEvidence providerEvidence = new RunEvidenceBundleView.ProviderRuntimeEvidence();
        providerEvidence.setRuntimeTypes(new ArrayList<>(runtimeTypes));
        providerEvidence.setProviderTypes(new ArrayList<>(providerTypes));
        providerEvidence.setModels(new ArrayList<>(models));
        providerEvidence.setSimulated(isSimulated);
        providerEvidence.setTotalPromptTokens(totalPrompt);
        providerEvidence.setTotalCompletionTokens(totalCompletion);
        providerEvidence.setTotalTokens(totalTokens);
        providerEvidence.setTotalEstimatedCost(Math.round(totalCost * 1_000_000.0) / 1_000_000.0);
        providerEvidence.setTokenUsageRecords(usageViews);

        bundle.setSimulated(isSimulated);
        bundle.setExecutionMode(isSimulated ? "DEMO_SIMULATED" : "LIVE_PROVIDER");
        bundle.setProviderRuntime(providerEvidence);

        // 5. Sanitized JGit Structured Diff & Artifacts
        List<ArtifactEntity> artifactEntities = new ArrayList<>(artifactRepository.findByRunId(runId));
        artifactEntities.sort(Comparator.comparing(ArtifactEntity::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(ArtifactEntity::getId));
        Set<String> runArtifactIds = new LinkedHashSet<>();
        List<ArtifactView> sanitizedArtifacts = new ArrayList<>();
        for (ArtifactEntity ae : artifactEntities) {
            runArtifactIds.add(ae.getId());
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
        bundle.setArtifacts(sanitizedArtifacts);

        StructuredDiff sanitizedDiff = new StructuredDiff(wsId, baselineCommit, baselineCommit, 0, 0, 0, new ArrayList<>());
        if (wsOpt.isPresent()) {
            try {
                Path wsRoot = workspaceResolver.getWorkspaceRoot(wsOpt.get().getId());
                StructuredDiff rawDiff = gitManager.computeStructuredDiff(wsRoot.toFile());
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

        // 6. Test Report & Verification Summary
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

        // 7. Approvals (Sanitized)
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

        // 8. Deployments & Health Check Trace (Sanitized)
        List<RunEvidenceBundleView.DeploymentEvidence> deploymentViews = new ArrayList<>();
        if (deploymentRepository != null) {
            List<DeploymentEntity> projectDeployments = deploymentRepository.findByProjectIdOrderByStartedAtDesc(project.getId());
            List<DeploymentEntity> matched = projectDeployments.stream()
                    .filter(d -> (d.getArtifactId() != null && runArtifactIds.contains(d.getArtifactId()))
                            || (run.getCreatedAt() != null && d.getCreatedAt() != null && !d.getCreatedAt().isBefore(run.getCreatedAt().minusSeconds(2))))
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

        // 9. Deterministic SHA-256 Checksum
        bundle.setChecksum(computeDeterministicChecksum(bundle));
        return bundle;
    }

    @Override
    public boolean verifyEvidenceBundleChecksum(RunEvidenceBundleView bundle) {
        if (bundle == null || bundle.getChecksum() == null || bundle.getChecksum().isBlank()) {
            return false;
        }
        String expected = computeDeterministicChecksum(bundle);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                bundle.getChecksum().trim().toLowerCase().getBytes(StandardCharsets.UTF_8)
        );
    }

    public static String computeDeterministicChecksum(RunEvidenceBundleView bundle) {
        try {
            Map<String, Object> canonical = new LinkedHashMap<>();
            canonical.put("schemaVersion", bundle.getSchemaVersion());
            canonical.put("bundleId", bundle.getBundleId());
            canonical.put("simulated", bundle.isSimulated());
            canonical.put("executionMode", bundle.getExecutionMode());
            canonical.put("project", bundle.getProject());
            canonical.put("workflowDslSnapshot", bundle.getWorkflowDslSnapshot());
            canonical.put("run", bundle.getRun());
            canonical.put("steps", bundle.getSteps());
            canonical.put("events", bundle.getEvents());
            canonical.put("providerRuntime", bundle.getProviderRuntime());
            canonical.put("gitDiff", bundle.getGitDiff());
            canonical.put("artifacts", bundle.getArtifacts());
            canonical.put("testReportSummary", bundle.getTestReportSummary());
            canonical.put("approvals", bundle.getApprovals());
            canonical.put("deployments", bundle.getDeployments());

            String json = EVIDENCE_MAPPER.writeValueAsString(canonical);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(json.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute deterministic SHA-256 checksum for RunEvidenceBundleView", e);
        }
    }

    private WorkflowRunView toRunView(WorkflowRunEntity entity) {
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

    private StepRunView toStepView(StepRunEntity entity) {
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

    private RunEventView toEventView(RunEventEntity entity) {
        return new RunEventView(
                entity.getId(),
                entity.getRunId(),
                entity.getSequenceNum(),
                entity.getEventType(),
                entity.getPayload(),
                entity.getCreatedAt()
        );
    }
}
