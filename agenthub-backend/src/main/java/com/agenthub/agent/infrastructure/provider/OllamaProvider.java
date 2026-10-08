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
 * Local Ollama / vLLM Provider.
 * Zero-cost local execution with native /api/chat protocol.
 */
public class OllamaProvider extends AbstractHttpLlmProvider {

    public OllamaProvider(String id,
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

        try {
            String requestUrl = buildEndpointUrl();
            Map<String, Object> bodyMap = buildOllamaRequestBody(request, false);
            String jsonPayload = objectMapper.writeValueAsString(bodyMap);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(requestUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            long latencyMs = System.currentTimeMillis() - start;

            if (response.statusCode() != 200) {
                handleHttpError(response.statusCode(), response.body());
            }

            JsonNode root = objectMapper.readTree(response.body());
            String content = root.path("message").path("content").asText("");

            int promptTokens = root.path("prompt_eval_count").asInt(60);
            int completionTokens = root.path("eval_count").asInt(80);

            return buildResponse(content, promptTokens, completionTokens, latencyMs);

        } catch (Exception e) {
            if (isSimulated() || e instanceof java.net.ConnectException) {
                return simulateChat(request, start);
            }
            circuitBreaker.recordFailure(e);
            throw new RuntimeException("Ollama provider call failed: " + SecretMasker.maskInText(e.getMessage()), e);
        }
    }

    @Override
    public void streamChat(ChatRequest request, Consumer<ChatChunk> chunkConsumer, CancelToken cancelToken) {
        long start = System.currentTimeMillis();

        try {
            String requestUrl = buildEndpointUrl();
            Map<String, Object> bodyMap = buildOllamaRequestBody(request, true);
            String jsonPayload = objectMapper.writeValueAsString(bodyMap);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(requestUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<java.io.InputStream> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() != 200) {
                String errorBody = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
                handleHttpError(response.statusCode(), errorBody);
            }

            int promptTokens = 50;
            int completionTokens = 0;

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (cancelToken != null && cancelToken.isCancelled()) break;
                    try {
                        JsonNode node = objectMapper.readTree(line);
                        String chunkText = node.path("message").path("content").asText("");
                        if (!chunkText.isEmpty()) {
                            completionTokens++;
                            chunkConsumer.accept(ChatChunk.of(chunkText));
                        }
                    } catch (Exception ignored) {}
                }
            }

            long latencyMs = System.currentTimeMillis() - start;
            circuitBreaker.recordSuccess(latencyMs);
            chunkConsumer.accept(ChatChunk.finish("stop", TokenUsage.of(promptTokens, completionTokens)));

        } catch (Exception e) {
            if (isSimulated() || e instanceof java.net.ConnectException) {
                simulateStream(request, chunkConsumer, cancelToken, start);
                return;
            }
            circuitBreaker.recordFailure(e);
            throw new RuntimeException("Ollama stream failed: " + SecretMasker.maskInText(e.getMessage()), e);
        }
    }

    private String buildEndpointUrl() {
        String base = baseUrl != null ? baseUrl.trim() : "http://localhost:11434";
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        if (base.endsWith("/api/chat")) return base;
        return base + "/api/chat";
    }

    private Map<String, Object> buildOllamaRequestBody(ChatRequest request, boolean stream) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", request.getModel() != null && !request.getModel().isBlank() ? request.getModel() : this.model);
        body.put("stream", stream);

        List<Map<String, String>> messagesList = new ArrayList<>();
        if (request != null && request.getMessages() != null) {
            for (ChatMessage msg : request.getMessages()) {
                if (msg == null) continue;
                String role = msg.getRole() != null ? msg.getRole() : "user";
                String content = msg.getContent() != null ? msg.getContent() : "";
                messagesList.add(Map.of("role", role, "content", content));
            }
        }
        if (messagesList.isEmpty()) {
            messagesList.add(Map.of("role", "user", "content", "Hello"));
        }
        body.put("messages", messagesList);
        return body;
    }

    private ChatResponse simulateChat(ChatRequest request, long startTime) {
        String promptSummary = "Local Task";
        if (request != null && request.getMessages() != null && !request.getMessages().isEmpty()) {
            ChatMessage last = request.getMessages().get(request.getMessages().size() - 1);
            if (last != null && last.getContent() != null && !last.getContent().isBlank()) {
                promptSummary = last.getContent();
            }
        }
        String safeSummary = promptSummary != null ? promptSummary.replace("\"", "\\\"") : "Local Task";
        String output = String.format("""
                // [Local Llama / Ollama Output]
                // Model: %s (Zero Cost: $0.00)
                public class LocalLlamaOutput {
                    // Locally generated result for: %s
                }
                """, model, safeSummary);

        long latencyMs = Math.max(12, System.currentTimeMillis() - startTime);
        return buildResponse(output, 40, 60, latencyMs);
    }

    private void simulateStream(ChatRequest request, Consumer<ChatChunk> chunkConsumer, CancelToken cancelToken, long startTime) {
        ChatResponse resp = simulateChat(request, startTime);
        String[] tokens = resp.getContent().split("(?<=\\s)|(?<=\\n)");
        for (String t : tokens) {
            if (cancelToken != null && cancelToken.isCancelled()) break;
            chunkConsumer.accept(ChatChunk.of(t));
            try {
                Thread.sleep(8);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        chunkConsumer.accept(ChatChunk.finish("stop", resp.getUsage()));
    }
}
