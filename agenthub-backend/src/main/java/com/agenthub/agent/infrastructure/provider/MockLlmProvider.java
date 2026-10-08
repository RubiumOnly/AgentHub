package com.agenthub.agent.infrastructure.provider;

import com.agenthub.agent.domain.provider.exception.ProviderRateLimitException;
import com.agenthub.agent.domain.provider.exception.ProviderServerException;
import com.agenthub.agent.domain.provider.model.*;
import com.agenthub.agent.domain.provider.spi.LlmProvider;
import com.agenthub.execution.domain.model.CancelToken;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Deterministic Mock LLM Provider for unit and boundary fault tolerance testing.
 * Provides programmable hooks to simulate 429 rate limits, 5xx server errors, timeouts, and token metrics.
 */
public class MockLlmProvider implements LlmProvider {

    private final String id;
    private final String name;
    private String model;
    private int priority;
    private int weight;
    private Set<String> capabilities = new HashSet<>();
    private Double costPerMillionInput = 0.0;
    private Double costPerMillionOutput = 0.0;
    private final CircuitBreaker circuitBreaker;

    // Test simulation hooks
    private final AtomicInteger simulatedStatusCode = new AtomicInteger(200);
    private String simulatedErrorMessage = "Simulated error";
    private int simulatedPromptTokens = 50;
    private int simulatedCompletionTokens = 80;
    private long simulatedLatencyMs = 15;
    private final AtomicBoolean tripOnFailure = new AtomicBoolean(true);

    public MockLlmProvider(String id, String name, String model, int priority, int weight, Set<String> capabilities) {
        this.id = id;
        this.name = name;
        this.model = model;
        this.priority = priority;
        this.weight = weight;
        if (capabilities != null) {
            this.capabilities.addAll(capabilities);
        }
        this.circuitBreaker = new CircuitBreaker(id);
    }

    public void setSimulatedFailure(int statusCode, String errorMessage) {
        this.simulatedStatusCode.set(statusCode);
        this.simulatedErrorMessage = errorMessage;
    }

    public void clearFailure() {
        this.simulatedStatusCode.set(200);
        this.simulatedErrorMessage = null;
    }

    public void setSimulatedTokens(int prompt, int completion) {
        this.simulatedPromptTokens = prompt;
        this.simulatedCompletionTokens = completion;
    }

    public void setSimulatedLatencyMs(long latencyMs) {
        this.simulatedLatencyMs = latencyMs;
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        if (!circuitBreaker.allowRequest()) {
            throw new RuntimeException("Circuit breaker OPEN for provider " + id);
        }

        int code = simulatedStatusCode.get();
        if (code == 429) {
            circuitBreaker.recordFailure(new RuntimeException("429 Rate Limit"));
            throw new ProviderRateLimitException(id, model, simulatedErrorMessage != null ? simulatedErrorMessage : "Mock 429 Rate Limit");
        }
        if (code >= 500) {
            circuitBreaker.recordFailure(new RuntimeException("Server Error " + code));
            throw new ProviderServerException(id, code, simulatedErrorMessage != null ? simulatedErrorMessage : "Mock " + code + " Server Error");
        }

        try {
            if (simulatedLatencyMs > 0) {
                Thread.sleep(Math.min(50, simulatedLatencyMs));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        circuitBreaker.recordSuccess(simulatedLatencyMs);
        String promptText = request.getMessages().isEmpty() ? "Mock Task" : request.getMessages().get(request.getMessages().size() - 1).getContent();
        String content = "Mock response from " + name + " for: " + promptText;
        TokenUsage usage = TokenUsage.of(simulatedPromptTokens, simulatedCompletionTokens);
        double cost = ModelPricing.calculateCost(model, simulatedPromptTokens, simulatedCompletionTokens, costPerMillionInput, costPerMillionOutput);

        return new ChatResponse(
                "mock-resp-" + UUID.randomUUID().toString().substring(0, 8),
                model,
                id,
                "MOCK",
                content,
                "stop",
                usage,
                simulatedLatencyMs,
                cost
        );
    }

    @Override
    public void streamChat(ChatRequest request, Consumer<ChatChunk> chunkConsumer, CancelToken cancelToken) {
        if (!circuitBreaker.allowRequest()) {
            throw new RuntimeException("Circuit breaker OPEN for provider " + id);
        }

        int code = simulatedStatusCode.get();
        if (code == 429) {
            circuitBreaker.recordFailure(new RuntimeException("429 Rate Limit"));
            throw new ProviderRateLimitException(id, model, simulatedErrorMessage != null ? simulatedErrorMessage : "Mock 429 Rate Limit");
        }
        if (code >= 500) {
            circuitBreaker.recordFailure(new RuntimeException("Server Error " + code));
            throw new ProviderServerException(id, code, simulatedErrorMessage != null ? simulatedErrorMessage : "Mock " + code + " Server Error");
        }

        chunkConsumer.accept(ChatChunk.of("Mock "));
        chunkConsumer.accept(ChatChunk.of("Stream "));
        chunkConsumer.accept(ChatChunk.of("Result."));

        circuitBreaker.recordSuccess(simulatedLatencyMs);
        chunkConsumer.accept(ChatChunk.finish("stop", TokenUsage.of(simulatedPromptTokens, simulatedCompletionTokens)));
    }

    @Override
    public ProviderHealth checkHealth() {
        if (!circuitBreaker.allowRequest()) {
            return ProviderHealth.down("Circuit OPEN due to failures");
        }
        if (simulatedStatusCode.get() != 200) {
            return ProviderHealth.degraded(simulatedLatencyMs, "Simulating failure status " + simulatedStatusCode.get());
        }
        return ProviderHealth.up(simulatedLatencyMs, "Mock provider active");
    }

    @Override
    public boolean supports(ChatRequest request) {
        if (request == null) return true;
        if (request.getRequiredCapabilities() != null && !request.getRequiredCapabilities().isEmpty()) {
            return this.capabilities.containsAll(request.getRequiredCapabilities());
        }
        return true;
    }

    @Override public String getId() { return id; }
    @Override public String getProviderType() { return "MOCK"; }
    @Override public String getName() { return name; }
    @Override public String getBaseUrl() { return "mock://local"; }
    @Override public String getModel() { return model; }
    @Override public int getPriority() { return priority; }
    @Override public int getWeight() { return weight; }
    @Override public Set<String> getCapabilities() { return capabilities; }
    @Override public Double getCostPerMillionInput() { return costPerMillionInput; }
    @Override public Double getCostPerMillionOutput() { return costPerMillionOutput; }
    @Override public CircuitBreaker getCircuitBreaker() { return circuitBreaker; }
    @Override public boolean isSimulated() { return true; }

    public void setModel(String model) { this.model = model; }
    public void setPriority(int priority) { this.priority = priority; }
    public void setWeight(int weight) { this.weight = weight; }
    public void setCapabilities(Set<String> capabilities) { this.capabilities = capabilities; }
    public void setCostPerMillionInput(Double cost) { this.costPerMillionInput = cost; }
    public void setCostPerMillionOutput(Double cost) { this.costPerMillionOutput = cost; }
}
