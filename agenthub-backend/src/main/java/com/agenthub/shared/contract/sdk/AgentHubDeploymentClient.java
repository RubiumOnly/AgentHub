package com.agenthub.shared.contract.sdk;

import com.agenthub.sandbox.api.dto.CreateDeploymentRequest;
import com.agenthub.sandbox.api.dto.DeploymentResponse;

import java.util.List;

/**
 * Standard public SDK contract for controlled sandbox preview deployments
 * and active HTTP health probing.
 */
public interface AgentHubDeploymentClient {

    DeploymentResponse createDeployment(CreateDeploymentRequest request);

    DeploymentResponse getDeploymentById(String deploymentId);

    List<DeploymentResponse> listDeploymentsByProjectId(String projectId);

    DeploymentResponse probeDeployment(String deploymentId);

    DeploymentResponse stopDeployment(String deploymentId);
}
