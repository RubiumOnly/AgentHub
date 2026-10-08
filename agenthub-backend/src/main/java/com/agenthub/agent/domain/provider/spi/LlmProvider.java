package com.agenthub.agent.domain.provider.spi;

import com.agenthub.agent.domain.provider.model.*;
import com.agenthub.execution.domain.model.CancelToken;

import java.util.Set;
import java.util.function.Consumer;

/**
 * Standard SPI Interface for LLM Providers.
 * Implemented by OpenAI/DeepSeek, Anthropic, Gemini, Ollama, and Mock providers.
 */
public interface LlmProvider {

    String getId();

    String getProviderType();

    String getName();

    String getBaseUrl();

    String getModel();

    int getPriority();

    int getWeight();

    Set<String> getCapabilities();

    Double getCostPerMillionInput();

    Double getCostPerMillionOutput();

    ChatResponse chat(ChatRequest request);

    void streamChat(ChatRequest request, Consumer<ChatChunk> chunkConsumer, CancelToken cancelToken);

    ProviderHealth checkHealth();

    boolean supports(ChatRequest request);

    CircuitBreaker getCircuitBreaker();

    boolean isSimulated();
}
