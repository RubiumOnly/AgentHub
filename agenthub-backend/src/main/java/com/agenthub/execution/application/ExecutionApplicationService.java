package com.agenthub.execution.application;

import com.agenthub.audit.infrastructure.repository.ArtifactRepository;
import com.agenthub.domain.workspace.service.JGitWorkspaceManager;
import com.agenthub.domain.workspace.service.WorkspaceResolver;
import com.agenthub.execution.domain.model.CancelTokenRegistry;
import com.agenthub.execution.dto.RunEventView;
import com.agenthub.execution.dto.RunEvidenceBundleView;
import com.agenthub.execution.dto.StartRunCommand;
import com.agenthub.execution.dto.StepRunView;
import com.agenthub.execution.dto.WorkflowRunView;
import com.agenthub.execution.infrastructure.repository.RunEventRepository;
import com.agenthub.execution.infrastructure.repository.StepRunRepository;
import com.agenthub.execution.infrastructure.repository.WorkflowRunRepository;
import com.agenthub.execution.service.ExecutionStateMachine;
import com.agenthub.execution.service.RunEventBroadcaster;
import com.agenthub.identity.infrastructure.security.ResourceAccessGuard;
import com.agenthub.project.infrastructure.repository.ProjectRepository;
import com.agenthub.project.infrastructure.repository.WorkspaceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * Facade application service for the Execution bounded context.
 * Delegates all operations to four internal deep modules:
 * {@link RunCommandModule}, {@link RunQueryModule}, {@link RunEventStreamModule}, and {@link RunEvidenceModule}.
 */
@Service
public class ExecutionApplicationService implements ExecutionApplication {

    private final RunCommandModule runCommandModule;
    private final RunQueryModule runQueryModule;
    private final RunEventStreamModule runEventStreamModule;
    private final RunEvidenceModule runEvidenceModule;

    @Autowired
    public ExecutionApplicationService(RunCommandModule runCommandModule,
                                       RunQueryModule runQueryModule,
                                       RunEventStreamModule runEventStreamModule,
                                       RunEvidenceModule runEvidenceModule) {
        this.runCommandModule = runCommandModule;
        this.runQueryModule = runQueryModule;
        this.runEventStreamModule = runEventStreamModule;
        this.runEvidenceModule = runEvidenceModule;
    }

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
                                       RunEventBroadcaster broadcaster) {
        this.runCommandModule = new RunCommandModule(
                workflowRunRepository, stepRunRepository, projectRepository, workspaceRepository,
                workspaceResolver, gitManager, artifactRepository, accessGuard, stateMachine,
                cancelTokenRegistry, broadcaster);
        this.runQueryModule = new RunQueryModule(
                workflowRunRepository, stepRunRepository, runEventRepository, projectRepository, accessGuard);
        this.runEventStreamModule = new RunEventStreamModule(
                workflowRunRepository, projectRepository, accessGuard, broadcaster);
        this.runEvidenceModule = new RunEvidenceModule(
                workflowRunRepository, stepRunRepository, runEventRepository, projectRepository,
                workspaceRepository, workspaceResolver, gitManager, artifactRepository, accessGuard);
    }

    @Override
    @Transactional
    public WorkflowRunView startRun(StartRunCommand cmd) {
        return runCommandModule.startRun(cmd);
    }

    @Override
    @Transactional(readOnly = true)
    public WorkflowRunView getRunById(String runId) {
        return runQueryModule.getRunById(runId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkflowRunView> listRunsByProjectId(String projectId) {
        return runQueryModule.listRunsByProjectId(projectId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StepRunView> listStepRuns(String runId) {
        return runQueryModule.listStepRuns(runId);
    }

    @Override
    @Transactional
    public RunEventView appendEvent(String runId, String eventType, String payload) {
        return runEventStreamModule.appendEvent(runId, eventType, payload);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RunEventView> listEvents(String runId, Long afterSeq) {
        return runQueryModule.listEvents(runId, afterSeq);
    }

    @Override
    @Transactional
    public WorkflowRunView cancelRun(String runId, String reason) {
        return runCommandModule.cancelRun(runId, reason);
    }

    @Override
    @Transactional
    public WorkflowRunView pauseRun(String runId) {
        return runCommandModule.pauseRun(runId);
    }

    @Override
    @Transactional
    public WorkflowRunView resumeRun(String runId) {
        return runCommandModule.resumeRun(runId);
    }

    @Override
    @Transactional
    public StepRunView retryStep(String stepRunId) {
        return runCommandModule.retryStep(stepRunId);
    }

    @Override
    public SseEmitter subscribeRunStream(String runId, Long lastEventId) {
        return runEventStreamModule.subscribeRunStream(runId, lastEventId);
    }

    @Override
    @Transactional(readOnly = true)
    public RunEvidenceBundleView exportRunEvidenceBundle(String runId) {
        return runEvidenceModule.exportRunEvidenceBundle(runId);
    }

    @Override
    public boolean verifyEvidenceBundleChecksum(RunEvidenceBundleView bundle) {
        return runEvidenceModule.verifyEvidenceBundleChecksum(bundle);
    }

    public static String computeDeterministicChecksum(RunEvidenceBundleView bundle) {
        return RunEvidenceModule.computeDeterministicChecksum(bundle);
    }

    @Override
    @Transactional(readOnly = true)
    public com.agenthub.execution.dto.RunOperationsView getRunOperations(String runId) {
        return runQueryModule.getRunOperations(runId);
    }
}
