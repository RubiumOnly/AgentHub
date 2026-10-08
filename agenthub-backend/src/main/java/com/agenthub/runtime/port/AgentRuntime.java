package com.agenthub.runtime.port;

import com.agenthub.domain.agent.model.AgentExecutionRequest;
import com.agenthub.execution.domain.model.CancelToken;

/**
 * Standard SPI interface for Agent execution runtimes (Mock, API, CLI).
 * Decouples the execution scheduler from concrete tool/model providers.
 */
public interface AgentRuntime {

    RuntimeDescriptor describe();

    RuntimeHealth checkHealth();

    ExecutionHandle start(AgentExecutionRequest request, RuntimeEventSink sink, CancelToken cancelToken);

    void cancel(ExecutionHandle handle);
}
