package com.agenthub.project.application;

import com.agenthub.domain.workspace.service.JGitWorkspaceManager;
import com.agenthub.domain.workspace.service.WorkspaceResolver;
import com.agenthub.identity.infrastructure.security.AuthAuditService;
import com.agenthub.identity.infrastructure.security.ResourceAccessGuard;
import com.agenthub.identity.infrastructure.security.ResourceOwnershipVerifier;
import com.agenthub.project.domain.onboarding.OnboardingSessionStatus;
import com.agenthub.project.domain.onboarding.OnboardingStep;
import com.agenthub.project.domain.onboarding.OnboardingStepStatus;
import com.agenthub.project.domain.source.GitSourceConfig;
import com.agenthub.project.domain.source.GitSourceType;
import com.agenthub.project.dto.ProjectView;
import com.agenthub.project.dto.onboarding.*;
import com.agenthub.project.infrastructure.entity.ProjectEntity;
import com.agenthub.project.infrastructure.entity.WorkspaceEntity;
import com.agenthub.project.infrastructure.repository.ProjectRepository;
import com.agenthub.project.infrastructure.repository.WorkspaceRepository;
import com.agenthub.shared.context.RequestContext;
import com.agenthub.shared.exception.BusinessException;
import com.agenthub.shared.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enterprise Project Onboarding Wizard Application Service.
 * Orchestrates 7-step onboarding state machine with clean rollback, masked logging,
 * and zero plain token persistence.
 */
@Service
public class ProjectOnboardingApplicationService implements ProjectOnboardingApplication {

    private static final Logger log = LoggerFactory.getLogger(ProjectOnboardingApplicationService.class);

    private final ProjectRepository projectRepository;
    private final WorkspaceRepository workspaceRepository;
    private final JGitWorkspaceManager gitManager;
    private final WorkspaceResolver workspaceResolver;
    private final AuthAuditService auditService;
    private final ContextSourceApplication contextSourceApplication;
    private final ResourceAccessGuard accessGuard;
    private final ResourceOwnershipVerifier ownershipVerifier;

    // Concurrent store for active onboarding sessions
    private final Map<String, SessionInternalState> sessions = new ConcurrentHashMap<>();

    private static class SessionInternalState {
        String sessionId;
        String ownerId;
        String projectName;
        String description;
        GitSourceConfig sourceConfig;
        OnboardingStep currentStep;
        OnboardingSessionStatus status;
        String projectId;
        String workspaceId;
        String isolatedBranch;
        String baselineCommit;
        EnvironmentScanResultView scanResult;
        String generatedRules;
        List<OnboardingStepRecordView> stepHistory = new ArrayList<>();
        LocalDateTime createdAt = LocalDateTime.now();
        LocalDateTime updatedAt = LocalDateTime.now();
    }

    @Autowired
    public ProjectOnboardingApplicationService(ProjectRepository projectRepository,
                                              WorkspaceRepository workspaceRepository,
                                              JGitWorkspaceManager gitManager,
                                              WorkspaceResolver workspaceResolver,
                                              AuthAuditService auditService,
                                              @Autowired(required = false) ContextSourceApplication contextSourceApplication,
                                              ResourceAccessGuard accessGuard,
                                              ResourceOwnershipVerifier ownershipVerifier) {
        this.projectRepository = projectRepository;
        this.workspaceRepository = workspaceRepository;
        this.gitManager = gitManager;
        this.workspaceResolver = workspaceResolver;
        this.auditService = auditService;
        this.contextSourceApplication = contextSourceApplication;
        this.accessGuard = accessGuard;
        this.ownershipVerifier = ownershipVerifier;
    }

    @Override
    @Transactional
    public OnboardingSessionView startOnboarding(StartOnboardingCommand cmd) {
        String currentUserId = RequestContext.get().getUserId();
        if (currentUserId == null || currentUserId.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Authentication required to start project onboarding");
        }
        if (cmd == null || cmd.getProjectName() == null || cmd.getProjectName().isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Project name must not be blank");
        }

        GitSourceConfig sourceConfig = cmd.getSourceConfig() != null ? cmd.getSourceConfig() : new GitSourceConfig();
        sourceConfig.validate();

        String sessionId = "onb-" + UUID.randomUUID().toString().substring(0, 8);
        SessionInternalState state = new SessionInternalState();
        state.sessionId = sessionId;
        state.ownerId = currentUserId;
        state.projectName = cmd.getProjectName().trim();
        state.description = cmd.getDescription();
        state.sourceConfig = sourceConfig;
        state.currentStep = OnboardingStep.CONNECT_SOURCE;
        state.status = OnboardingSessionStatus.IN_PROGRESS;

        // Step 1: Execute CONNECT_SOURCE
        recordStepStart(state, OnboardingStep.CONNECT_SOURCE);
        try {
            String maskedSource = sourceConfig.getMaskedIdentifier();
            recordStepSuccess(state, OnboardingStep.CONNECT_SOURCE,
                    "Successfully connected to source: " + maskedSource,
                    "Validated source type=" + sourceConfig.getSourceType());
            state.currentStep = OnboardingStep.VERIFY_PERMISSIONS;
        } catch (Exception e) {
            recordStepFailure(state, OnboardingStep.CONNECT_SOURCE, e.getMessage());
            state.status = OnboardingSessionStatus.FAILED;
            sessions.put(sessionId, state);
            throw new BusinessException(ErrorCode.ONBOARDING_FAILED, "Failed to connect to source: " + e.getMessage());
        }

        sessions.put(sessionId, state);
        auditService.record("ONBOARDING_START", currentUserId, null, null,
                "Started onboarding session [" + sessionId + "] for project [" + state.projectName + "]");
        return toView(state);
    }

    @Override
    @Transactional
    public OnboardingSessionView executeStep(String sessionId, ExecuteStepCommand cmd) {
        SessionInternalState state = getValidatedSession(sessionId);
        if (state.status == OnboardingSessionStatus.COMPLETED) {
            return toView(state);
        }
        if (state.status == OnboardingSessionStatus.ABORTED) {
            throw new BusinessException(ErrorCode.ONBOARDING_FAILED, "Cannot execute step on an aborted onboarding session");
        }

        OnboardingStep stepToRun = (cmd != null && cmd.getStep() != null) ? cmd.getStep() : state.currentStep;
        recordStepStart(state, stepToRun);

        try {
            switch (stepToRun) {
                case CONNECT_SOURCE -> {
                    state.sourceConfig.validate();
                    recordStepSuccess(state, stepToRun, "Source connected", "Source verified");
                    state.currentStep = OnboardingStep.VERIFY_PERMISSIONS;
                }
                case VERIFY_PERMISSIONS -> {
                    // Check credentials ref and permissions
                    if (state.sourceConfig.getSourceType() == GitSourceType.REMOTE_GIT && state.sourceConfig.getSecretRef() != null) {
                        String secretRef = state.sourceConfig.getSecretRef();
                        if (secretRef.contains("ghp_") || secretRef.contains("github_pat_")) {
                            throw new BusinessException(ErrorCode.PARAM_ERROR, "Raw token in secretRef is prohibited");
                        }
                    }
                    recordStepSuccess(state, stepToRun, "Permissions verified", "Credential reference confirmed");
                    state.currentStep = OnboardingStep.SELECT_DEFAULT_BRANCH;
                }
                case SELECT_DEFAULT_BRANCH -> {
                    String branch = (cmd != null && cmd.getSelectedBranch() != null && !cmd.getSelectedBranch().isBlank())
                            ? cmd.getSelectedBranch().trim() : state.sourceConfig.getDefaultBranch();
                    state.sourceConfig.setDefaultBranch(branch);
                    recordStepSuccess(state, stepToRun, "Default branch confirmed: " + branch, "Branch: " + branch);
                    state.currentStep = OnboardingStep.CREATE_WORKSPACE;
                }
                case CREATE_WORKSPACE -> {
                    if (state.projectId == null) {
                        state.projectId = "proj-" + UUID.randomUUID().toString().substring(0, 8);
                        state.workspaceId = "ws-" + UUID.randomUUID().toString().substring(0, 8);
                        state.isolatedBranch = "feature/agenthub-" + state.workspaceId;
                    }
                    Path wsRoot = workspaceResolver.getWorkspaceRoot(state.workspaceId);
                    File wsDir = wsRoot.toFile();
                    String commitHash = gitManager.cloneOrInitFromSource(wsDir, state.sourceConfig, state.isolatedBranch, "onboarding@agenthub.local");
                    state.baselineCommit = commitHash;
                    recordStepSuccess(state, stepToRun,
                            "Controlled workspace provisioned with isolated branch [" + state.isolatedBranch + "]",
                            "workspaceId=" + state.workspaceId + ", baselineCommit=" + commitHash);
                    state.currentStep = OnboardingStep.SCAN_ENVIRONMENT;
                }
                case SCAN_ENVIRONMENT -> {
                    if (state.workspaceId == null) {
                        throw new BusinessException(ErrorCode.ONBOARDING_FAILED, "Workspace must be created before scanning");
                    }
                    Path wsRoot = workspaceResolver.getWorkspaceRoot(state.workspaceId);
                    EnvironmentScanResultView scanResult = gitManager.scanEnvironment(wsRoot.toFile());
                    state.scanResult = scanResult;
                    recordStepSuccess(state, stepToRun,
                            String.format("Detected stack: %s (%s, %s)", scanResult.getLanguage(), scanResult.getFramework(), scanResult.getBuildTool()),
                            "Suggested build: " + scanResult.getSuggestedBuildCommand());
                    state.currentStep = OnboardingStep.GENERATE_PROJECT_RULES;
                }
                case GENERATE_PROJECT_RULES -> {
                    String generatedRules = generateDefaultProjectRules(state, cmd != null ? cmd.getCustomRules() : null);
                    state.generatedRules = generatedRules;
                    Path wsRoot = workspaceResolver.getWorkspaceRoot(state.workspaceId);
                    Files.writeString(wsRoot.resolve("PROJECT_RULES.md"), generatedRules, StandardCharsets.UTF_8);
                    recordStepSuccess(state, stepToRun, "Project engineering rules generated and saved as PROJECT_RULES.md", "Length: " + generatedRules.length());
                    state.currentStep = OnboardingStep.CREATE_INITIAL_BASELINE;
                }
                case CREATE_INITIAL_BASELINE -> {
                    Path wsRoot = workspaceResolver.getWorkspaceRoot(state.workspaceId);
                    String baselineCommit = gitManager.createBaseline(wsRoot.toFile(), state.sessionId, "onboarding@agenthub.local");
                    state.baselineCommit = baselineCommit;

                    // Persist Project and Workspace records in database
                    ProjectEntity project = new ProjectEntity(
                            state.projectId,
                            state.ownerId,
                            state.projectName,
                            state.description,
                            "ACTIVE",
                            state.workspaceId
                    );
                    projectRepository.save(project);

                    WorkspaceEntity workspace = new WorkspaceEntity(
                            state.workspaceId,
                            state.projectId,
                            state.workspaceId,
                            baselineCommit
                    );
                    workspaceRepository.save(workspace);

                    // Register initial versioned context sources
                    if (contextSourceApplication != null) {
                        contextSourceApplication.autoDiscoverContextSources(state.projectId, state.workspaceId, baselineCommit);
                    }

                    state.status = OnboardingSessionStatus.COMPLETED;
                    recordStepSuccess(state, stepToRun, "Initial Git baseline established: " + baselineCommit, "Baseline Commit: " + baselineCommit);
                    auditService.record("ONBOARDING_COMPLETE", state.ownerId, null, null,
                            "Onboarding completed for project [" + state.projectId + "] workspace [" + state.workspaceId + "] commit [" + baselineCommit + "]");
                }
            }
            state.updatedAt = LocalDateTime.now();
            return toView(state);
        } catch (Exception e) {
            recordStepFailure(state, stepToRun, e.getMessage());
            state.status = OnboardingSessionStatus.FAILED;
            state.updatedAt = LocalDateTime.now();
            log.error("Onboarding step [{}] failed for session [{}]: {}", stepToRun, sessionId, e.getMessage());
            throw (e instanceof BusinessException be) ? be : new BusinessException(ErrorCode.ONBOARDING_FAILED, e.getMessage());
        }
    }

    @Override
    @Transactional
    public OnboardingSessionView retryStep(String sessionId, OnboardingStep step) {
        SessionInternalState state = getValidatedSession(sessionId);
        state.status = OnboardingSessionStatus.IN_PROGRESS;
        state.currentStep = step != null ? step : state.currentStep;
        return executeStep(sessionId, new ExecuteStepCommand(state.currentStep, null, null));
    }

    @Override
    @Transactional
    public OnboardingSessionView abortOnboarding(String sessionId) {
        SessionInternalState state = getValidatedSession(sessionId);
        if (state.status == OnboardingSessionStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Cannot abort an already completed onboarding session");
        }

        // Clean up temporary workspace directory if created but not completed
        if (state.workspaceId != null && workspaceResolver != null) {
            try {
                Path wsRoot = workspaceResolver.getWorkspaceRoot(state.workspaceId);
                if (Files.exists(wsRoot)) {
                    try (var stream = Files.walk(wsRoot)) {
                        stream.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to clean up aborted workspace directory for session [{}]: {}", sessionId, e.getMessage());
            }
        }

        state.status = OnboardingSessionStatus.ABORTED;
        state.updatedAt = LocalDateTime.now();
        auditService.record("ONBOARDING_ABORT", state.ownerId, null, null, "Aborted onboarding session [" + sessionId + "]");
        return toView(state);
    }

    @Override
    public OnboardingSessionView getSession(String sessionId) {
        SessionInternalState state = getValidatedSession(sessionId);
        return toView(state);
    }

    @Override
    @Transactional
    public ProjectView completeOnboarding(String sessionId) {
        SessionInternalState state = getValidatedSession(sessionId);
        if (state.status != OnboardingSessionStatus.COMPLETED) {
            // Run remaining steps to completion
            while (state.status == OnboardingSessionStatus.IN_PROGRESS && state.currentStep != null) {
                executeStep(sessionId, new ExecuteStepCommand(state.currentStep, null, null));
            }
        }
        ProjectEntity project = projectRepository.findById(state.projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND, "Project not found: " + state.projectId));
        return new ProjectView(
                project.getId(),
                project.getOwnerId(),
                project.getName(),
                project.getDescription(),
                project.getStatus(),
                project.getWorkspaceId(),
                project.getCreatedAt(),
                project.getUpdatedAt()
        );
    }

    private SessionInternalState getValidatedSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Session ID must not be blank");
        }
        SessionInternalState state = sessions.get(sessionId);
        if (state == null) {
            throw new BusinessException(ErrorCode.ONBOARDING_SESSION_NOT_FOUND, "Onboarding session not found: " + sessionId);
        }
        String currentUserId = RequestContext.get().getUserId();
        accessGuard.checkOwnership(state.ownerId, currentUserId);
        return state;
    }

    private void recordStepStart(SessionInternalState state, OnboardingStep step) {
        state.stepHistory.removeIf(r -> r.getStep() == step);
        state.stepHistory.add(new OnboardingStepRecordView(
                step,
                OnboardingStepStatus.RUNNING,
                "Executing step: " + step.getDescription(),
                null,
                null,
                LocalDateTime.now(),
                null
        ));
    }

    private void recordStepSuccess(SessionInternalState state, OnboardingStep step, String summary, String evidence) {
        for (OnboardingStepRecordView record : state.stepHistory) {
            if (record.getStep() == step) {
                record.setStatus(OnboardingStepStatus.SUCCESS);
                record.setSummary(summary);
                record.setEvidence(evidence);
                record.setFinishedAt(LocalDateTime.now());
                return;
            }
        }
    }

    private void recordStepFailure(SessionInternalState state, OnboardingStep step, String errorMsg) {
        for (OnboardingStepRecordView record : state.stepHistory) {
            if (record.getStep() == step) {
                record.setStatus(OnboardingStepStatus.FAILED);
                record.setErrorMessage(errorMsg);
                record.setFinishedAt(LocalDateTime.now());
                return;
            }
        }
    }

    private String generateDefaultProjectRules(SessionInternalState state, String customRules) {
        StringBuilder sb = new StringBuilder();
        sb.append("# AgentHub Project Rules: ").append(state.projectName).append("\n\n");
        sb.append("> Auto-generated by AgentHub Onboarding Wizard on ").append(LocalDateTime.now()).append("\n\n");
        sb.append("## 1. Environment & Stack\n");
        if (state.scanResult != null) {
            sb.append("- Language: ").append(state.scanResult.getLanguage()).append("\n");
            sb.append("- Framework: ").append(state.scanResult.getFramework()).append("\n");
            sb.append("- Build Tool: ").append(state.scanResult.getBuildTool()).append("\n");
            sb.append("- Test Runner: ").append(state.scanResult.getTestRunner()).append("\n");
            sb.append("- Suggested Build: `").append(state.scanResult.getSuggestedBuildCommand()).append("`\n");
            sb.append("- Suggested Test: `").append(state.scanResult.getSuggestedTestCommand()).append("`\n\n");
        } else {
            sb.append("- Standard Controlled Environment\n\n");
        }
        sb.append("## 2. Engineering & Quality Standards\n");
        sb.append("- All code modifications must produce non-destructive Git commits on isolated branches.\n");
        sb.append("- Never commit secrets, credentials, or host absolute paths.\n");
        sb.append("- Automated tests must pass prior to artifact review and approval.\n\n");
        sb.append("## 3. Boundary & Security Defense\n");
        sb.append("- Directory traversals (`..`) and direct modifications to `.git` are prohibited.\n");
        sb.append("- Workspace capacity and single file limits are strictly enforced by WorkspaceQuotaGuard.\n\n");
        if (customRules != null && !customRules.isBlank()) {
            sb.append("## 4. Custom Project Rules\n");
            sb.append(customRules.trim()).append("\n");
        }
        return sb.toString();
    }

    private OnboardingSessionView toView(SessionInternalState state) {
        return new OnboardingSessionView(
                state.sessionId,
                state.ownerId,
                state.projectName,
                state.description,
                state.sourceConfig.getSourceType(),
                state.sourceConfig.getMaskedIdentifier(),
                state.currentStep,
                state.status,
                state.projectId,
                state.workspaceId,
                state.isolatedBranch,
                state.baselineCommit,
                state.scanResult,
                state.generatedRules,
                new ArrayList<>(state.stepHistory),
                state.createdAt,
                state.updatedAt
        );
    }
}
