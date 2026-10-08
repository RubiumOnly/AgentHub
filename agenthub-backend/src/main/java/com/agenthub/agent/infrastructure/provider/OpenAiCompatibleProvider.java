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
 * Standard OpenAI & DeepSeek Compatible Provider.
 * Implements standard /chat/completions REST endpoint and SSE streaming protocol.
 */
public class OpenAiCompatibleProvider extends AbstractHttpLlmProvider {

    public OpenAiCompatibleProvider(String id,
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
        String effectiveKey = getEffectiveApiKey(providerType.toUpperCase() + "_API_KEY");

        if (effectiveKey == null || effectiveKey.isBlank() || isSimulated()) {
            return simulateChat(request, start);
        }

        try {
            String requestUrl = buildEndpointUrl();
            Map<String, Object> bodyMap = buildRequestBody(request, false);
            String jsonPayload = objectMapper.writeValueAsString(bodyMap);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(requestUrl))
                    .header("Authorization", "Bearer " + effectiveKey)
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
            if (root.has("choices") && root.get("choices").size() > 0) {
                JsonNode messageNode = root.get("choices").get(0).get("message");
                if (messageNode != null && messageNode.has("content")) {
                    content = messageNode.get("content").asText();
                }
            }

            int promptTokens = 100;
            int completionTokens = 50;
            if (root.has("usage")) {
                JsonNode usageNode = root.get("usage");
                if (usageNode.has("prompt_tokens")) promptTokens = usageNode.get("prompt_tokens").asInt();
                if (usageNode.has("completion_tokens")) completionTokens = usageNode.get("completion_tokens").asInt();
            }

            return buildResponse(content, promptTokens, completionTokens, latencyMs);

        } catch (Exception e) {
            if (e instanceof RuntimeException re && re.getClass().getName().contains("Provider")) {
                throw re;
            }
            circuitBreaker.recordFailure(e);
            throw new RuntimeException("Provider [" + id + "] call failed: " + SecretMasker.maskInText(e.getMessage()), e);
        }
    }

    @Override
    public void streamChat(ChatRequest request, Consumer<ChatChunk> chunkConsumer, CancelToken cancelToken) {
        long start = System.currentTimeMillis();
        String effectiveKey = getEffectiveApiKey(providerType.toUpperCase() + "_API_KEY");

        if (effectiveKey == null || effectiveKey.isBlank() || isSimulated()) {
            simulateStream(request, chunkConsumer, cancelToken, start);
            return;
        }

        try {
            String requestUrl = buildEndpointUrl();
            Map<String, Object> bodyMap = buildRequestBody(request, true);
            String jsonPayload = objectMapper.writeValueAsString(bodyMap);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(requestUrl))
                    .header("Authorization", "Bearer " + effectiveKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<java.io.InputStream> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() != 200) {
                String errorBody = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
                handleHttpError(response.statusCode(), errorBody);
            }

            int promptTokens = 120;
            int completionTokens = 0;

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (cancelToken != null && cancelToken.isCancelled()) {
                        break;
                    }
                    if (line.startsWith("data:")) {
                        String data = line.substring(5).trim();
                        if ("[DONE]".equals(data)) {
                            break;
                        }
                        try {
                            JsonNode chunkNode = objectMapper.readTree(data);
                            if (chunkNode.has("choices") && chunkNode.get("choices").size() > 0) {
                                JsonNode delta = chunkNode.get("choices").get(0).get("delta");
                                if (delta != null && delta.has("content")) {
                                    String text = delta.get("content").asText();
                                    completionTokens += Math.max(1, text.length() / 4);
                                    chunkConsumer.accept(ChatChunk.of(text));
                                }
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
        String base = baseUrl != null ? baseUrl.trim() : "https://api.openai.com/v1";
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        if (base.endsWith("/chat/completions")) return base;
        if (base.endsWith("/v1")) return base + "/chat/completions";
        return base + "/v1/chat/completions";
    }

    private Map<String, Object> buildRequestBody(ChatRequest request, boolean stream) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", request.getModel() != null && !request.getModel().isBlank() ? request.getModel() : this.model);
        body.put("temperature", request.getTemperature() != null ? request.getTemperature() : 0.7);
        body.put("max_tokens", request.getMaxTokens() != null ? request.getMaxTokens() : 2048);
        body.put("stream", stream);

        List<Map<String, String>> messagesList = new ArrayList<>();
        if (request.getMessages() != null) {
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
        String promptSummary = "Task";
        if (request != null && request.getMessages() != null && !request.getMessages().isEmpty()) {
            ChatMessage last = request.getMessages().get(request.getMessages().size() - 1);
            if (last != null && last.getContent() != null && !last.getContent().isBlank()) {
                promptSummary = last.getContent();
            }
        }
        String safeSummary = promptSummary != null ? promptSummary.replace("\"", "\\\"") : "Task";
        String output = String.format("""
                // [%s - Simulated %s Response]
                // Model: %s (Priority: %d, Capabilities: %s)
                public class GeneratedSolution {
                    public static void main(String[] args) {
                        System.out.println("Executed successfully for prompt: %s");
                    }
                }
                """, name, providerType, model, priority, String.join(",", capabilities), safeSummary);

        long latencyMs = Math.max(15, System.currentTimeMillis() - startTime);
        int promptTokens = Math.max(30, safeSummary.length() / 3);
        int completionTokens = Math.max(40, output.length() / 4);
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
