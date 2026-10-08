package com.agenthub.agent.infrastructure.provider;

import com.agenthub.agent.domain.provider.model.*;
import com.agenthub.agent.infrastructure.security.SecretMasker;
import com.agenthub.execution.domain.model.CancelToken;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.core.env.Environment;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;

/**
 * Anthropic Claude Provider (Messages Protocol: /v1/messages).
 */
public class AnthropicProvider extends AbstractHttpLlmProvider {

    private static final String DEFAULT_ANTHROPIC_VERSION = "2023-06-01";

    public AnthropicProvider(String id,
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
        super(id, providerType, name, baseUrl, secretRef, model, priority, weight, capabilities, costPerMillionInput, costPerMillionOutput, environment);
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        long start = System.currentTimeMillis();
        String effectiveKey = getEffectiveApiKey("ANTHROPIC_API_KEY");

        if (effectiveKey == null || effectiveKey.isBlank() || isSimulated()) {
            return simulateChat(request, start);
        }

        try {
            String requestUrl = buildEndpointUrl();
            Map<String, Object> bodyMap = buildAnthropicRequestBody(request, false);
            String jsonPayload = objectMapper.writeValueAsString(bodyMap);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(requestUrl))
                    .header("x-api-key", effectiveKey)
                    .header("anthropic-version", DEFAULT_ANTHROPIC_VERSION)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            long latencyMs = System.currentTimeMillis() - start;

            if (response.statusCode() != 200) {
                handleHttpError(response.statusCode(), response.body());
            }

            JsonNode root = objectMapper.readTree(response.body());
            String content = "";
            if (root.has("content") && root.get("content").isArray()) {
                StringBuilder sb = new StringBuilder();
                for (JsonNode block : root.get("content")) {
                    if ("text".equals(block.path("type").asText())) {
                        sb.append(block.path("text").asText());
                    }
                }
                content = sb.toString();
            }

            int promptTokens = 120;
            int completionTokens = 60;
            if (root.has("usage")) {
                JsonNode usageNode = root.get("usage");
                if (usageNode.has("input_tokens")) promptTokens = usageNode.get("input_tokens").asInt();
                if (usageNode.has("output_tokens")) completionTokens = usageNode.get("output_tokens").asInt();
            }

            return buildResponse(content, promptTokens, completionTokens, latencyMs);

        } catch (Exception e) {
            if (e instanceof RuntimeException re && re.getClass().getName().contains("Provider")) {
                throw re;
            }
            circuitBreaker.recordFailure(e);
            throw new RuntimeException("Anthropic provider [" + id + "] call failed: " + SecretMasker.maskInText(e.getMessage()), e);
        }
    }

    @Override
    public void streamChat(ChatRequest request, Consumer<ChatChunk> chunkConsumer, CancelToken cancelToken) {
        long start = System.currentTimeMillis();
        String effectiveKey = getEffectiveApiKey("ANTHROPIC_API_KEY");

        if (effectiveKey == null || effectiveKey.isBlank() || isSimulated()) {
            simulateStream(request, chunkConsumer, cancelToken, start);
            return;
        }

        try {
            String requestUrl = buildEndpointUrl();
            Map<String, Object> bodyMap = buildAnthropicRequestBody(request, true);
            String jsonPayload = objectMapper.writeValueAsString(bodyMap);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(requestUrl))
                    .header("x-api-key", effectiveKey)
                    .header("anthropic-version", DEFAULT_ANTHROPIC_VERSION)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<java.io.InputStream> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() != 200) {
                String errorBody = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
                handleHttpError(response.statusCode(), errorBody);
            }

            int promptTokens = 150;
            int completionTokens = 0;

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (cancelToken != null && cancelToken.isCancelled()) break;
                    if (line.startsWith("data:")) {
                        String data = line.substring(5).trim();
                        try {
                            JsonNode node = objectMapper.readTree(data);
                            String type = node.path("type").asText();
                            if ("content_block_delta".equals(type)) {
                                String deltaText = node.path("delta").path("text").asText("");
                                if (!deltaText.isEmpty()) {
                                    completionTokens += Math.max(1, deltaText.length() / 4);
                                    chunkConsumer.accept(ChatChunk.of(deltaText));
                                }
                            } else if ("message_delta".equals(type) && node.has("usage")) {
                                completionTokens = node.path("usage").path("output_tokens").asInt(completionTokens);
                            }
                        } catch (Exception ignored) {}
                    }
                }
            }

            long latencyMs = System.currentTimeMillis() - start;
            circuitBreaker.recordSuccess(latencyMs);
            chunkConsumer.accept(ChatChunk.finish("stop", TokenUsage.of(promptTokens, completionTokens)));

        } catch (Exception e) {
            if (e instanceof RuntimeException re && re.getClass().getName().contains("Provider")) {
                throw re;
            }
            circuitBreaker.recordFailure(e);
            throw new RuntimeException("Stream call for [" + id + "] failed: " + SecretMasker.maskInText(e.getMessage()), e);
        }
    }

    private String buildEndpointUrl() {
        String base = baseUrl != null ? baseUrl.trim() : "https://api.anthropic.com/v1";
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        if (base.endsWith("/messages")) return base;
        if (base.endsWith("/v1")) return base + "/messages";
        return base + "/v1/messages";
    }

    private Map<String, Object> buildAnthropicRequestBody(ChatRequest request, boolean stream) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", request.getModel() != null && !request.getModel().isBlank() ? request.getModel() : this.model);
        body.put("max_tokens", request.getMaxTokens() != null ? request.getMaxTokens() : 2048);
        body.put("stream", stream);

        StringBuilder systemBuilder = new StringBuilder();
        List<Map<String, String>> messagesList = new ArrayList<>();

        if (request.getMessages() != null) {
            for (ChatMessage msg : request.getMessages()) {
                if (msg == null) continue;
                String content = msg.getContent() != null ? msg.getContent() : "";
                if ("system".equalsIgnoreCase(msg.getRole())) {
                    if (systemBuilder.length() > 0) systemBuilder.append("\n\n");
                    systemBuilder.append(content);
                } else {
                    String role = "assistant".equalsIgnoreCase(msg.getRole()) ? "assistant" : "user";
                    messagesList.add(Map.of("role", role, "content", content));
                }
            }
        }

        if (systemBuilder.length() > 0) {
            body.put("system", systemBuilder.toString());
        }
        if (messagesList.isEmpty()) {
            messagesList.add(Map.of("role", "user", "content", "Hello"));
        }
        body.put("messages", messagesList);
        return body;
    }

    private ChatResponse simulateChat(ChatRequest request, long startTime) {
        String promptSummary = "Task";
        if (request != null && request.getMessages() != null && !request.getMessages().isEmpty()) {
            ChatMessage last = request.getMessages().get(request.getMessages().size() - 1);
            if (last != null && last.getContent() != null && !last.getContent().isBlank()) {
                promptSummary = last.getContent();
            }
        }
        String safeSummary = promptSummary != null ? promptSummary.replace("\"", "\\\"") : "Task";
        String output = String.format("""
                // [Anthropic Claude Architecture Implementation]
                // Model: %s (Priority: %d, Weight: %d)
                // Capabilities: %s
                public class ClaudeVerifiedArtifact {
                    public static void execute() {
                        // Reasoning context processed for: %s
                    }
                }
                """, model, priority, weight, String.join(", ", capabilities), safeSummary);

        long latencyMs = Math.max(18, System.currentTimeMillis() - startTime);
        int promptTokens = Math.max(45, safeSummary.length() / 3);
        int completionTokens = Math.max(55, output.length() / 4);
        return buildResponse(output, promptTokens, completionTokens, latencyMs);
    }

    private void simulateStream(ChatRequest request, Consumer<ChatChunk> chunkConsumer, CancelToken cancelToken, long startTime) {
        ChatResponse resp = simulateChat(request, startTime);
        String[] tokens = resp.getContent().split("(?<=\\s)|(?<=\\n)");
        for (String t : tokens) {
            if (cancelToken != null && cancelToken.isCancelled()) break;
            chunkConsumer.accept(ChatChunk.of(t));
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        chunkConsumer.accept(ChatChunk.finish("stop", resp.getUsage()));
    }
}
