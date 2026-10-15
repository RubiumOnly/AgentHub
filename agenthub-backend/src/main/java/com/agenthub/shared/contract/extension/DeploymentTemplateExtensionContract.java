package com.agenthub.shared.contract.extension;

import java.util.Map;

/**
 * Extension contract SPI for sandbox deployment templates (e.g. Next.js Static Web, Spring Boot Service).
 * Governs build commands, port mapping, environment specs, and health probe configurations.
 */
public interface DeploymentTemplateExtensionContract {

    String getTemplateName();

    String getTemplateVersion();

    String getContainerImage();

    String getBuildCommand();

    String getStartCommand();

    String getHealthProbePath();

    long getHealthProbeTimeoutMs();

    long getDefaultTtlSeconds();

    Map<String, String> getDefaultEnvironmentVariables();
}
