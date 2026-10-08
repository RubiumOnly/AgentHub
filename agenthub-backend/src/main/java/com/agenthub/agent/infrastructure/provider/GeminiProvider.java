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
 * Google Gemini Provider (REST generateContent protocol).
 */
public class GeminiProvider extends AbstractHttpLlmProvider {

    public GeminiProvider(String id,
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
        String effectiveKey = getEffectiveApiKey("GEMINI_API_KEY");

        if (effectiveKey == null || effectiveKey.isBlank() || isSimulated()) {
            return simulateChat(request, start);
        }

        try {
            String requestUrl = buildEndpointUrl(effectiveKey, false);
            Map<String, Object> bodyMap = buildGeminiRequestBody(request);
            String jsonPayload = objectMapper.writeValueAsString(bodyMap);

            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(requestUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload, StandardCharsets.UTF_8));
            if (effectiveKey != null && !effectiveKey.isBlank() && !isSimulated()) {
                reqBuilder.header("x-goog-api-key", effectiveKey);
            }
            HttpRequest httpRequest = reqBuilder.build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            long latencyMs = System.currentTimeMillis() - start;

            if (response.statusCode() != 200) {
                handleHttpError(response.statusCode(), response.body());
            }

            JsonNode root = objectMapper.readTree(response.body());
            String content = "";
            if (root.has("candidates") && root.get("candidates").size() > 0) {
                JsonNode parts = root.get("candidates").get(0).path("content").path("parts");
                if (parts.isArray() && parts.size() > 0) {
                    content = parts.get(0).path("text").asText("");
                }
            }

            int promptTokens = 100;
            int completionTokens = 50;
            if (root.has("usageMetadata")) {
                JsonNode usageNode = root.get("usageMetadata");
                if (usageNode.has("promptTokenCount")) promptTokens = usageNode.get("promptTokenCount").asInt();
                if (usageNode.has("candidatesTokenCount")) completionTokens = usageNode.get("candidatesTokenCount").asInt();
            }

            return buildResponse(content, promptTokens, completionTokens, latencyMs);

        } catch (Exception e) {
            if (e instanceof RuntimeException re && re.getClass().getName().contains("Provider")) {
                throw re;
            }
            circuitBreaker.recordFailure(e);
            throw new RuntimeException("Gemini provider [" + id + "] call failed: " + SecretMasker.maskInText(e.getMessage()), e);
        }
    }

    @Override
    public void streamChat(ChatRequest request, Consumer<ChatChunk> chunkConsumer, CancelToken cancelToken) {
        long start = System.currentTimeMillis();
        String effectiveKey = getEffectiveApiKey("GEMINI_API_KEY");

        if (effectiveKey == null || effectiveKey.isBlank() || isSimulated()) {
            simulateStream(request, chunkConsumer, cancelToken, start);
            return;
        }

        try {
            String requestUrl = buildEndpointUrl(effectiveKey, true);
            Map<String, Object> bodyMap = buildGeminiRequestBody(request);
            String jsonPayload = objectMapper.writeValueAsString(bodyMap);

            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(requestUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload, StandardCharsets.UTF_8));
            if (effectiveKey != null && !effectiveKey.isBlank() && !isSimulated()) {
                reqBuilder.header("x-goog-api-key", effectiveKey);
            }
            HttpRequest httpRequest = reqBuilder.build();

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
                    if (cancelToken != null && cancelToken.isCancelled()) break;
                    if (line.startsWith("data:")) {
                        String data = line.substring(5).trim();
                        try {
                            JsonNode node = objectMapper.readTree(data);
                            JsonNode candidates = node.path("candidates");
                            if (candidates.isArray() && candidates.size() > 0) {
                                JsonNode parts = candidates.get(0).path("content").path("parts");
                                if (parts.isArray() && parts.size() > 0) {
                                    String text = parts.get(0).path("text").asText("");
                                    if (!text.isEmpty()) {
                                        completionTokens += Math.max(1, text.length() / 4);
                                        chunkConsumer.accept(ChatChunk.of(text));
                                    }
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

    private String buildEndpointUrl(String apiKey, boolean stream) {
        String base = baseUrl != null ? baseUrl.trim() : "https://generativelanguage.googleapis.com";
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        String action = stream ? "streamGenerateContent?alt=sse" : "generateContent";
        return base + "/v1beta/models/" + model + ":" + action;
    }

    private Map<String, Object> buildGeminiRequestBody(ChatRequest request) {
        Map<String, Object> body = new HashMap<>();

        List<Map<String, Object>> contents = new ArrayList<>();
        Map<String, Object> systemInstruction = null;

        if (request != null && request.getMessages() != null) {
            for (ChatMessage msg : request.getMessages()) {
                if (msg == null) continue;
                String content = msg.getContent() != null ? msg.getContent() : "";
                if ("system".equalsIgnoreCase(msg.getRole())) {
                    systemInstruction = Map.of("parts", List.of(Map.of("text", content)));
                } else {
                    String geminiRole = "assistant".equalsIgnoreCase(msg.getRole()) ? "model" : "user";
                    contents.add(Map.of("role", geminiRole, "parts", List.of(Map.of("text", content))));
                }
            }
        }

        if (contents.isEmpty()) {
            contents.add(Map.of("role", "user", "parts", List.of(Map.of("text", "Hello"))));
        }

        body.put("contents", contents);
        if (systemInstruction != null) {
            body.put("systemInstruction", systemInstruction);
        }
        body.put("generationConfig", Map.of(
                "temperature", request != null && request.getTemperature() != null ? request.getTemperature() : 0.7,
                "maxOutputTokens", request != null && request.getMaxTokens() != null ? request.getMaxTokens() : 2048
        ));
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
                // [Google Gemini Multimodal and Reasoning Result]
                // Model: %s (Priority: %d, Capabilities: %s)
                export function generateGeminiReport() {
                    return { status: "OK", prompt: "%s" };
                }
                """, model, priority, String.join(",", capabilities), safeSummary);

        long latencyMs = Math.max(16, System.currentTimeMillis() - startTime);
        int promptTokens = Math.max(35, safeSummary.length() / 3);
        int completionTokens = Math.max(45, output.length() / 4);
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
