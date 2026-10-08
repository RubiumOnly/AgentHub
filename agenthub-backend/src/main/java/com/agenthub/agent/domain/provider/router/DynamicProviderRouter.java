package com.agenthub.agent.domain.provider.router;

import com.agenthub.agent.domain.provider.exception.NoAvailableProviderException;
import com.agenthub.agent.domain.provider.exception.ProviderRateLimitException;
import com.agenthub.agent.domain.provider.exception.ProviderServerException;
import com.agenthub.agent.domain.provider.model.*;
import com.agenthub.agent.domain.provider.spi.LlmProvider;
import com.agenthub.agent.infrastructure.entity.ProviderEntity;
import com.agenthub.agent.infrastructure.provider.*;
import com.agenthub.agent.infrastructure.repository.ProviderRepository;
import com.agenthub.agent.infrastructure.security.SecretMasker;
import com.agenthub.execution.domain.model.CancelToken;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Enterprise Dynamic Provider Router with Automated HA Failover & Circuit Breaking.
 * Evaluates candidate matrix based on priority, model capability, measured latency, and availability.
 */
@Component
public class DynamicProviderRouter implements ProviderRouter {

    private static final Logger log = LoggerFactory.getLogger(DynamicProviderRouter.class);

    private final Map<String, LlmProvider> providerRegistry = new ConcurrentHashMap<>();
    private final Environment environment;
    private final ProviderRepository providerRepository;

    public DynamicProviderRouter(Environment environment, ProviderRepository providerRepository) {
        this.environment = environment;
        this.providerRepository = providerRepository;
    }

    @PostConstruct
    public void init() {
        // Register default ecosystem providers
        registerDefaultProviders();

        // Synchronize with database providers if present
        syncFromDatabase();
    }

    private void registerDefaultProviders() {
        // 1. OpenAI
        registerProvider(new OpenAiCompatibleProvider(
                "prov-openai",
                "OPENAI",
                "OpenAI GPT-4o",
                "https://api.openai.com/v1",
                "env:OPENAI_API_KEY",
                "gpt-4o",
                100,
                1,
                Set.of("code", "general", "reasoning", "fast"),
                5.0000,
                15.0000,
                environment
        ));

        // 2. DeepSeek
        registerProvider(new OpenAiCompatibleProvider(
                "prov-deepseek",
                "DEEPSEEK",
                "DeepSeek Chat",
                "https://api.deepseek.com",
                "env:DEEPSEEK_API_KEY",
                "deepseek-chat",
                95,
                1,
                Set.of("code", "general", "fast", "reasoning"),
                0.1400,
                0.2800,
                environment
        ));

        // 3. Anthropic Claude
        registerProvider(new AnthropicProvider(
                "prov-anthropic",
                "ANTHROPIC",
                "Anthropic Claude 3.5 Sonnet",
                "https://api.anthropic.com/v1",
                "env:ANTHROPIC_API_KEY",
                "claude-3-5-sonnet-20241022",
                90,
                1,
                Set.of("code", "general", "reasoning"),
                3.0000,
                15.0000,
                environment
        ));

        // 4. Google Gemini
        registerProvider(new GeminiProvider(
                "prov-gemini",
                "GEMINI",
                "Google Gemini 1.5 Flash",
                "https://generativelanguage.googleapis.com",
                "env:GEMINI_API_KEY",
                "gemini-1.5-flash",
                85,
                1,
                Set.of("general", "fast", "long_context"),
                0.0750,
                0.3000,
                environment
        ));

        // 5. Local Ollama
        registerProvider(new OllamaProvider(
                "prov-ollama",
                "OLLAMA",
                "Local Ollama Llama 3.2",
                "http://localhost:11434",
                "none",
                "llama3.2:3b",
                50,
                1,
                Set.of("code", "general", "fast", "local"),
                0.0000,
                0.0000,
                environment
        ));

        // 6. Mock Fallback Baseline
        registerProvider(new MockLlmProvider(
                "prov-mock",
                "Simulated Offline Engine",
                "mock-v1",
                10,
                1,
                Set.of("code", "general", "fast", "reasoning", "local")
        ));
    }

    public synchronized void syncFromDatabase() {
        try {
            if (providerRepository == null) return;
            List<ProviderEntity> dbProviders = providerRepository.findAll();
            for (ProviderEntity entity : dbProviders) {
                if (!"ACTIVE".equalsIgnoreCase(entity.getStatus())) {
                    providerRegistry.remove(entity.getId());
                    continue;
                }
                LlmProvider existing = providerRegistry.get(entity.getId());
                Set<String> caps = parseCapabilities(entity.getCapabilities());

                if (existing == null) {
                    LlmProvider created = createFromEntity(entity, caps);
                    if (created != null) {
                        providerRegistry.put(created.getId(), created);
                    }
                } else if (existing instanceof AbstractHttpLlmProvider httpProvider) {
                    httpProvider.setBaseUrl(entity.getBaseUrl());
                    httpProvider.setModel(entity.getModel());
                    httpProvider.setPriority(entity.getPriority());
                    httpProvider.setWeight(entity.getWeight());
                    httpProvider.setCapabilities(caps);
                    httpProvider.setCostPerMillionInput(entity.getCostPerMillionInput());
                    httpProvider.setCostPerMillionOutput(entity.getCostPerMillionOutput());
                }
            }
        } catch (Exception e) {
            log.warn("Error syncing providers from database: {}", e.getMessage());
        }
    }

    private LlmProvider createFromEntity(ProviderEntity entity, Set<String> caps) {
        String type = entity.getProviderType() != null ? entity.getProviderType().toUpperCase() : "OPENAI";
        return switch (type) {
            case "OPENAI", "DEEPSEEK" -> new OpenAiCompatibleProvider(
                    entity.getId(), type, type + " (" + entity.getModel() + ")", entity.getBaseUrl(),
                    entity.getSecretRef(), entity.getModel(), entity.getPriority(), entity.getWeight(),
                    caps, entity.getCostPerMillionInput(), entity.getCostPerMillionOutput(), environment
            );
            case "ANTHROPIC" -> new AnthropicProvider(
                    entity.getId(), type, "Anthropic (" + entity.getModel() + ")", entity.getBaseUrl(),
                    entity.getSecretRef(), entity.getModel(), entity.getPriority(), entity.getWeight(),
                    caps, entity.getCostPerMillionInput(), entity.getCostPerMillionOutput(), environment
            );
            case "GEMINI" -> new GeminiProvider(
                    entity.getId(), type, "Gemini (" + entity.getModel() + ")", entity.getBaseUrl(),
                    entity.getSecretRef(), entity.getModel(), entity.getPriority(), entity.getWeight(),
                    caps, entity.getCostPerMillionInput(), entity.getCostPerMillionOutput(), environment
            );
            case "OLLAMA" -> new OllamaProvider(
                    entity.getId(), type, "Ollama (" + entity.getModel() + ")", entity.getBaseUrl(),
                    entity.getSecretRef(), entity.getModel(), entity.getPriority(), entity.getWeight(),
                    caps, entity.getCostPerMillionInput(), entity.getCostPerMillionOutput(), environment
            );
            default -> new MockLlmProvider(
                    entity.getId(), "Custom " + type, entity.getModel(), entity.getPriority(), entity.getWeight(), caps
            );
        };
    }

    private Set<String> parseCapabilities(String raw) {
        if (raw == null || raw.isBlank()) return Set.of("general");
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    @Override
    public List<LlmProvider> selectCandidates(ChatRequest request) {
        List<LlmProvider> eligible = new ArrayList<>();

        for (LlmProvider provider : providerRegistry.values()) {
            // 1. Skip providers whose circuit is OPEN
            if (!provider.getCircuitBreaker().allowRequest()) {
                continue;
            }

            // 2. Skip providers that report health status DOWN
            if (provider.checkHealth().getStatus() == ProviderHealth.Status.DOWN) {
                continue;
            }

            // 3. Check capability matching
            if (request != null && request.getRequiredCapabilities() != null && !request.getRequiredCapabilities().isEmpty()) {
                if (!provider.getCapabilities().containsAll(request.getRequiredCapabilities())) {
                    continue;
                }
            }

            // 4. Check general support
            if (request != null && !provider.supports(request)) {
                continue;
            }

            eligible.add(provider);
        }

        if (eligible.isEmpty()) {
            // If all preferred providers are circuit-broken or missing, fall back to mock
            LlmProvider mock = providerRegistry.get("prov-mock");
            if (mock != null) {
                return List.of(mock);
            }
            return Collections.emptyList();
        }

        // Sort candidates
        eligible.sort((p1, p2) -> {
            // Preferred provider takes precedence
            if (request != null && request.getPreferredProvider() != null) {
                boolean p1Matches = p1.getId().equalsIgnoreCase(request.getPreferredProvider()) ||
                                   p1.getProviderType().equalsIgnoreCase(request.getPreferredProvider());
                boolean p2Matches = p2.getId().equalsIgnoreCase(request.getPreferredProvider()) ||
                                   p2.getProviderType().equalsIgnoreCase(request.getPreferredProvider());
                if (p1Matches && !p2Matches) return -1;
                if (!p1Matches && p2Matches) return 1;
            }

            // Priority DESC
            int prioDiff = Integer.compare(p2.getPriority(), p1.getPriority());
            if (prioDiff != 0) return prioDiff;

            // Measured latency ASC (only for positive latency, otherwise penalize non-positive/unknown)
            long lat1 = p1.checkHealth().getLatencyMs();
            long lat2 = p2.checkHealth().getLatencyMs();
            long effLat1 = lat1 > 0 ? lat1 : Long.MAX_VALUE;
            long effLat2 = lat2 > 0 ? lat2 : Long.MAX_VALUE;
            int latDiff = Long.compare(effLat1, effLat2);
            if (latDiff != 0) return latDiff;

            // Weight DESC
            return Integer.compare(p2.getWeight(), p1.getWeight());
        });

        return eligible;
    }

    private int healthScore(ProviderHealth.Status status) {
        if (status == null) return 0;
        return switch (status) {
            case UP -> 2;
            case DEGRADED -> 1;
            case DOWN -> 0;
        };
    }

    @Override
    public LlmProvider selectPrimary(ChatRequest request) {
        List<LlmProvider> candidates = selectCandidates(request);
        if (candidates.isEmpty()) {
            throw new NoAvailableProviderException("No active LLM provider found matching request requirements");
        }
        return candidates.get(0);
    }

    @Override
    public ChatResponse routeAndExecute(ChatRequest request, Consumer<FallbackEvent> fallbackListener) {
        List<LlmProvider> candidates = selectCandidates(request);
        if (candidates.isEmpty()) {
            throw new NoAvailableProviderException("No active LLM provider available to execute request");
        }

        LlmProvider originalPrimary = candidates.get(0);
        Exception lastException = null;

        for (int i = 0; i < candidates.size(); i++) {
            LlmProvider candidate = candidates.get(i);
            try {
                ChatResponse response = candidate.chat(request);
                if (i > 0) {
                    response.setFallbackUsed(true);
                    response.setOriginalProviderId(originalPrimary.getId());
                }
                return response;
            } catch (Exception e) {
                lastException = e;
                int statusCode = 500;
                if (e instanceof ProviderRateLimitException) statusCode = 429;
                else if (e instanceof ProviderServerException pse) statusCode = pse.getStatusCode();

                log.warn("Provider [{}] execution failed (status={}): {}", candidate.getName(), statusCode, SecretMasker.maskInText(e.getMessage()));

                if (i + 1 < candidates.size()) {
                    LlmProvider backup = candidates.get(i + 1);
                    FallbackEvent fallbackEvent = new FallbackEvent(
                            candidate.getId(),
                            candidate.getName(),
                            candidate.getModel(),
                            backup.getId(),
                            backup.getName(),
                            backup.getModel(),
                            e.getMessage(),
                            statusCode
                    );
                    if (fallbackListener != null) {
                        try {
                            fallbackListener.accept(fallbackEvent);
                        } catch (Exception ignored) {}
                    }
                }
            }
        }

        throw new NoAvailableProviderException("All " + candidates.size() + " candidate LLM providers failed. Last error: " +
                (lastException != null ? SecretMasker.maskInText(lastException.getMessage()) : "unknown"));
    }

    @Override
    public ChatResponse routeAndStream(ChatRequest request,
                                       Consumer<ChatChunk> chunkConsumer,
                                       Consumer<FallbackEvent> fallbackListener,
                                       CancelToken cancelToken) {
        List<LlmProvider> candidates = selectCandidates(request);
        if (candidates.isEmpty()) {
            throw new NoAvailableProviderException("No active LLM provider available for streaming");
        }

        LlmProvider originalPrimary = candidates.get(0);
        Exception lastException = null;

        for (int i = 0; i < candidates.size(); i++) {
            LlmProvider candidate = candidates.get(i);
            AtomicBoolean chunkEmitted = new AtomicBoolean(false);
            StringBuilder contentAccumulator = new StringBuilder();
            java.util.concurrent.atomic.AtomicReference<TokenUsage> usageRef = new java.util.concurrent.atomic.AtomicReference<>(null);
            long start = System.currentTimeMillis();

            try {
                final int candidateIndex = i;
                candidate.streamChat(request, chunk -> {
                    chunkEmitted.set(true);
                    if (chunk != null) {
                        if (chunk.getDeltaContent() != null && !chunk.getDeltaContent().isEmpty()) {
                            contentAccumulator.append(chunk.getDeltaContent());
                        }
                        if (chunk.getUsage() != null) {
                            usageRef.set(chunk.getUsage());
                        }
                    }
                    if (chunkConsumer != null) {
                        chunkConsumer.accept(chunk);
                    }
                }, cancelToken);

                long latencyMs = Math.max(1, System.currentTimeMillis() - start);
                String fullContent = contentAccumulator.toString();
                TokenUsage finalUsage = usageRef.get();
                if (finalUsage == null) {
                    int pTokens = request != null && request.getMessages() != null && !request.getMessages().isEmpty() && request.getMessages().get(0).getContent() != null
                            ? Math.max(10, request.getMessages().get(0).getContent().length() / 4) : 30;
                    int cTokens = Math.max(10, fullContent.length() / 4);
                    finalUsage = TokenUsage.of(pTokens, cTokens);
                }

                double cost = ModelPricing.calculateCost(candidate.getModel(),
                        finalUsage.getPromptTokens(), finalUsage.getCompletionTokens(),
                        candidate.getCostPerMillionInput(), candidate.getCostPerMillionOutput());

                ChatResponse response = new ChatResponse(
                        "resp-stream-" + UUID.randomUUID().toString().substring(0, 8),
                        candidate.getModel(),
                        candidate.getId(),
                        candidate.getProviderType(),
                        fullContent,
                        "stop",
                        finalUsage,
                        latencyMs,
                        cost
                );

                if (candidateIndex > 0) {
                    response.setFallbackUsed(true);
                    response.setOriginalProviderId(originalPrimary.getId());
                }

                return response; // Successfully completed streaming!

            } catch (Exception e) {
                lastException = e;
                int statusCode = (e instanceof ProviderRateLimitException) ? 429 : 500;

                // If chunks have already started streaming to the client, falling back halfway might corrupt output,
                // but if failed before any chunk, seamlessly fall back!
                if (!chunkEmitted.get() && (i + 1 < candidates.size())) {
                    LlmProvider backup = candidates.get(i + 1);
                    FallbackEvent fallbackEvent = new FallbackEvent(
                            candidate.getId(), candidate.getName(), candidate.getModel(),
                            backup.getId(), backup.getName(), backup.getModel(),
                            e.getMessage(), statusCode
                    );
                    if (fallbackListener != null) {
                        try {
                            fallbackListener.accept(fallbackEvent);
                        } catch (Exception ignored) {}
                    }
                    continue; // Try next candidate
                }

                // If already emitted chunks or last candidate, rethrow
                throw new RuntimeException("Stream interrupted on provider [" + candidate.getId() + "]: " + SecretMasker.maskInText(e.getMessage()), e);
            }
        }

        throw new NoAvailableProviderException("All candidate providers failed for stream. Last error: " +
                (lastException != null ? SecretMasker.maskInText(lastException.getMessage()) : "unknown"));
    }

    @Override
    public void registerProvider(LlmProvider provider) {
        if (provider != null) {
            providerRegistry.put(provider.getId(), provider);
        }
    }

    @Override
    public void unregisterProvider(String id) {
        if (id != null) {
            providerRegistry.remove(id);
        }
    }

    @Override
    public List<LlmProvider> listProviders() {
        return new ArrayList<>(providerRegistry.values());
    }

    @Override
    public LlmProvider getProviderById(String id) {
        if (id == null) return null;
        return providerRegistry.get(id);
    }
}
