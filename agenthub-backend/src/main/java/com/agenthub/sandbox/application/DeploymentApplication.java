package com.agenthub.sandbox.application;

import com.agenthub.sandbox.api.dto.CreateDeploymentRequest;
import com.agenthub.sandbox.api.dto.DeploymentResponse;

import java.util.List;

/**
 * Application boundary interface for sandbox deployment and preview lifecycle management.
 */
public interface DeploymentApplication {

    DeploymentResponse createAndDeploy(CreateDeploymentRequest request);

    DeploymentResponse stopDeployment(String deploymentId);

    DeploymentResponse getDeployment(String deploymentId);

    List<DeploymentResponse> listDeployments(String projectId);

    String getDeploymentLogs(String deploymentId);
}
