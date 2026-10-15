package com.agenthub.shared.contract.sdk;

import com.agenthub.execution.dto.RunEvidenceBundleView;
import com.agenthub.execution.dto.StartRunCommand;
import com.agenthub.execution.dto.StepRunView;
import com.agenthub.execution.dto.WorkflowRunView;

import java.util.List;

/**
 * Standard public SDK contract for workflow run lifecycle, step rework,
 * run rejection, and evidence bundle retrieval.
 */
public interface AgentHubRunClient {

    WorkflowRunView startRun(StartRunCommand command);

    WorkflowRunView getRunById(String runId);

    List<WorkflowRunView> listRunsByProjectId(String projectId);

    List<StepRunView> listStepRuns(String runId);

    WorkflowRunView cancelRun(String runId);

    StepRunView reworkStep(String runId, String stepRunId, String reviewerFeedback);

    WorkflowRunView rejectRun(String runId, String rejectionReason);

    RunEvidenceBundleView exportEvidenceBundle(String runId);
}
