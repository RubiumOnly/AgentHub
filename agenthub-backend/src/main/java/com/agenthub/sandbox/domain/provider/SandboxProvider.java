package com.agenthub.sandbox.domain.provider;

import com.agenthub.sandbox.domain.model.SandboxExecutionRequest;
import com.agenthub.sandbox.domain.model.SandboxExecutionResult;

/**
 * Service Provider Interface (SPI) for isolated sandbox execution runtimes.
 */
public interface SandboxProvider {

    /**
     * Unique identifier of the provider (e.g. LOCAL_PROCESS, DOCKER).
     */
    String getProviderType();

    /**
     * Checks whether the sandbox runtime is installed and operational.
     */
    boolean isAvailable();

    /**
     * Executes a command within the sandbox with isolation and resource constraints.
     */
    SandboxExecutionResult execute(SandboxExecutionRequest request);

    /**
     * Forcibly destroys and cleans up an ongoing sandbox execution.
     */
    void destroy(String executionId);
}
