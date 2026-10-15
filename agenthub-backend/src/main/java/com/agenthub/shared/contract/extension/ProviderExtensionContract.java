package com.agenthub.shared.contract.extension;

import com.agenthub.agent.domain.provider.model.ChatRequest;
import com.agenthub.agent.domain.provider.model.ChatResponse;
import com.agenthub.agent.domain.provider.model.ProviderExecutionMode;

import java.util.List;

/**
 * Extension contract SPI for registering third-party or custom LLM providers.
 * All extensions must support mode isolation, model capabilities, and cost catalog.
 */
public interface ProviderExtensionContract {

    String getProviderType();

    String getProviderVersion();

    List<String> getSupportedModels();

    boolean supportsExecutionMode(ProviderExecutionMode mode);

    ChatResponse executeChat(ChatRequest request);

    double estimateCostUsd(String model, long promptTokens, long completionTokens);
}
