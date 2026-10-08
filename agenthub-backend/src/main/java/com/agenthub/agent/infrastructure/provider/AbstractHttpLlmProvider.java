package com.agenthub.agent.infrastructure.provider;

import com.agenthub.agent.domain.provider.exception.ProviderRateLimitException;
import com.agenthub.agent.domain.provider.exception.ProviderServerException;
import com.agenthub.agent.domain.provider.exception.ProviderUnavailableException;
import com.agenthub.agent.domain.provider.model.*;
import com.agenthub.agent.domain.provider.spi.LlmProvider;
import com.agenthub.agent.infrastructure.security.SecretMasker;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.*;

/**
 * Base abstract HTTP LLM provider providing common HTTP transport, circuit breaking,
 * secret resolution, pricing calculation, and simulated fallback capabilities.
 */
public abstract class AbstractHttpLlmProvider implements LlmProvider {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected final String id;
    protected final String providerType;
    protected final String name;
    protected String baseUrl;
    protected String secretRef;
    protected String model;
    protected int priority;
    protected int weight;
    protected Set<String> capabilities;
    protected Double costPerMillionInput;
    protected Double costPerMillionOutput;

    protected final CircuitBreaker circuitBreaker;
    protected final HttpClient httpClient;
    protected final ObjectMapper objectMapper;
    protected final Environment environment;

    public AbstractHttpLlmProvider(String id,
                                   String providerType,
                                   String name,
                                   String baseUrl,
                                   String secretRef,
                                   String model,
                                   int priority,
                                   int weight,
                                   Set<String> capabilities,
                                   Double costPerMillionInput,
                                   Double costPerMillionOutput,
                                   Environment environment) {
        this.id = id;
        this.providerType = providerType;
        this.name = name;
        this.baseUrl = baseUrl;
        this.secretRef = secretRef;
        this.model = model;
        this.priority = priority;
        this.weight = weight;
        this.capabilities = capabilities != null ? new HashSet<>(capabilities) : new HashSet<>();
        this.costPerMillionInput = costPerMillionInput != null ? costPerMillionInput : 0.0;
        this.costPerMillionOutput = costPerMillionOutput != null ? costPerMillionOutput : 0.0;
        this.circuitBreaker = new CircuitBreaker(id);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
        this.objectMapper = new ObjectMapper();
        this.environment = environment;
    }

    protected String getEffectiveApiKey(String envFallback) {
        return SecretMasker.resolveSecret(this.secretRef, this.environment, envFallback);
    }

    @Override
    public boolean supports(ChatRequest request) {
        if (request == null) return true;
        if (request.getModel() != null && !request.getModel().isBlank()) {
            if (request.getModel().equalsIgnoreCase(this.model) ||
                request.getModel().toLowerCase().contains(this.providerType.toLowerCase())) {
                return true;
            }
        }
        if (request.getRequiredCapabilities() != null && !request.getRequiredCapabilities().isEmpty()) {
            return this.capabilities.containsAll(request.getRequiredCapabilities());
        }
        return true;
    }

    @Override
    public ProviderHealth checkHealth() {
        if (!circuitBreaker.allowRequest()) {
            return ProviderHealth.down("Circuit breaker is OPEN due to " + circuitBreaker.getConsecutiveFailures() + " consecutive failures");
        }
        String key = getEffectiveApiKey(providerType + "_API_KEY");
        if (key == null || key.isBlank() || key.startsWith("sk-placeholder") || "none".equalsIgnoreCase(key)) {
            return ProviderHealth.degraded(5, "Running in simulation mode (API key not configured)");
        }
        return ProviderHealth.up(25, "Provider endpoint configured: " + baseUrl);
    }

    @Override
    public boolean isSimulated() {
        String key = getEffectiveApiKey(providerType + "_API_KEY");
        return key == null || key.isBlank() || key.startsWith("sk-placeholder") || "none".equalsIgnoreCase(key);
    }

    protected void handleHttpError(int statusCode, String responseBody) {
        String sanitized = SecretMasker.maskInText(responseBody);
        circuitBreaker.recordFailure(new RuntimeException("HTTP " + statusCode + ": " + sanitized));
        if (statusCode == 429) {
            log.warn("Provider [{}] hit Rate Limit 429: {}", id, sanitized);
            throw new ProviderRateLimitException(id, model, "HTTP 429 Too Many Requests: " + sanitized);
        }
        if (statusCode >= 500) {
            log.warn("Provider [{}] encountered server error {}: {}", id, statusCode, sanitized);
            throw new ProviderServerException(id, statusCode, "HTTP " + statusCode + " Server Error: " + sanitized);
        }
        throw new ProviderUnavailableException(id, "HTTP " + statusCode + " Client Error: " + sanitized);
    }

    protected ChatResponse buildResponse(String content, int promptTokens, int completionTokens, long latencyMs) {
        TokenUsage usage = TokenUsage.of(promptTokens, completionTokens);
        double cost = ModelPricing.calculateCost(model, promptTokens, completionTokens, costPerMillionInput, costPerMillionOutput);
        circuitBreaker.recordSuccess(latencyMs);
        return new ChatResponse(
                "resp-" + UUID.randomUUID().toString().substring(0, 8),
                model,
                id,
                providerType,
                content,
                "stop",
                usage,
                latencyMs,
                cost
        );
    }

    @Override public String getId() { return id; }
    @Override public String getProviderType() { return providerType; }
    @Override public String getName() { return name; }
    @Override public String getBaseUrl() { return baseUrl; }
    @Override public String getModel() { return model; }
    @Override public int getPriority() { return priority; }
    @Override public int getWeight() { return weight; }
    @Override public Set<String> getCapabilities() { return Collections.unmodifiableSet(capabilities); }
    @Override public Double getCostPerMillionInput() { return costPerMillionInput; }
    @Override public Double getCostPerMillionOutput() { return costPerMillionOutput; }
    @Override public CircuitBreaker getCircuitBreaker() { return circuitBreaker; }

    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public void setSecretRef(String secretRef) { this.secretRef = secretRef; }
    public void setModel(String model) { this.model = model; }
    public void setPriority(int priority) { this.priority = priority; }
    public void setWeight(int weight) { this.weight = weight; }
    public void setCapabilities(Set<String> capabilities) { this.capabilities = capabilities; }
    public void setCostPerMillionInput(Double cost) { this.costPerMillionInput = cost; }
    public void setCostPerMillionOutput(Double cost) { this.costPerMillionOutput = cost; }
}
