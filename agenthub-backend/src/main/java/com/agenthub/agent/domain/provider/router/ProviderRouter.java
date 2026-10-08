package com.agenthub.agent.domain.provider.router;

import com.agenthub.agent.domain.provider.model.*;
import com.agenthub.agent.domain.provider.spi.LlmProvider;
import com.agenthub.execution.domain.model.CancelToken;

import java.util.List;
import java.util.function.Consumer;

/**
 * Dynamic Router Interface for LLM Providers.
 * Resolves optimal providers based on capability, priority, latency, and circuit status,
 * with automated HA fallback resilience.
 */
public interface ProviderRouter {

    ChatResponse routeAndExecute(ChatRequest request, Consumer<FallbackEvent> fallbackListener);

    void routeAndStream(ChatRequest request,
                        Consumer<ChatChunk> chunkConsumer,
                        Consumer<FallbackEvent> fallbackListener,
                        CancelToken cancelToken);

    List<LlmProvider> selectCandidates(ChatRequest request);

    LlmProvider selectPrimary(ChatRequest request);

    void registerProvider(LlmProvider provider);

    void unregisterProvider(String id);

    List<LlmProvider> listProviders();

    LlmProvider getProviderById(String id);
}
