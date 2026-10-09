package com.agenthub.sandbox.api;

import com.agenthub.sandbox.api.dto.CreateDeploymentRequest;
import com.agenthub.sandbox.api.dto.DeploymentResponse;
import com.agenthub.sandbox.application.DeploymentApplication;
import com.agenthub.shared.response.Result;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for workspace deployment lifecycle and preview runtime management.
 */
@RestController
@RequestMapping("/api/deployments")
public class DeploymentController {

    private final DeploymentApplication deploymentApplication;

    public DeploymentController(DeploymentApplication deploymentApplication) {
        this.deploymentApplication = deploymentApplication;
    }

    @PostMapping
    public Result<DeploymentResponse> createAndDeploy(@RequestBody CreateDeploymentRequest request) {
        DeploymentResponse response = deploymentApplication.createAndDeploy(request);
        return Result.ok(response);
    }

    @GetMapping("/{id}")
    public Result<DeploymentResponse> getDeployment(@PathVariable("id") String id) {
        DeploymentResponse response = deploymentApplication.getDeployment(id);
        return Result.ok(response);
    }

    @GetMapping("/{id}/logs")
    public Result<String> getDeploymentLogs(@PathVariable("id") String id) {
        String logs = deploymentApplication.getDeploymentLogs(id);
        return Result.ok(logs);
    }

    @PostMapping("/{id}/stop")
    public Result<DeploymentResponse> stopDeployment(@PathVariable("id") String id) {
        DeploymentResponse response = deploymentApplication.stopDeployment(id);
        return Result.ok(response);
    }

    @GetMapping
    public Result<List<DeploymentResponse>> listDeployments(@RequestParam("projectId") String projectId) {
        List<DeploymentResponse> list = deploymentApplication.listDeployments(projectId);
        return Result.ok(list);
    }
}
