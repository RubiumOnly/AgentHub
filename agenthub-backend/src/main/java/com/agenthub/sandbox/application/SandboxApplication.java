package com.agenthub.sandbox.application;

import com.agenthub.domain.sandbox.model.DeploymentManifest;

public interface SandboxApplication {
    String renderPreviewHtml(String projectId);
    DeploymentManifest deployProject(String projectId);
}
