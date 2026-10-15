package com.agenthub.agent.infrastructure.provider;

import com.agenthub.agent.domain.provider.exception.ProviderUnavailableException;
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
 * Supports Gemini function declarations / tool calls, streaming SSE,
 * request timeout configuration, and exponential backoff retry.
 */
public class GeminiProvider extends AbstractHttpLlmProvider {

    public GeminiProvider(String id,
                          String baseUrl,
                          String secretRef,
                          String model,
                          int priority,
                          int weight,
                          Set<String> capabilities,
                          Double costPerMillionInput,
                          Double costPerMillionOutput) {
        this(id, "GEMINI", id, baseUrl, secretRef, model, priority, weight, capabilities, costPerMillionInput, costPerMillionOutput, null);
    }

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

        if (!circuitBreaker.allowRequest()) {
            throw new ProviderUnavailableException(id, "Circuit breaker is OPEN or probe in flight for provider " + id);
        }

        String effectiveKey = getEffectiveApiKey("GEMINI_API_KEY");

        if (effectiveKey == null || effectiveKey.isBlank() || isSimulated()) {
            if (isStrictLiveMode(request)) {
                throw new ProviderUnavailableException(id,
                        "Live mode [" + (request != null ? request.getExecutionMode() : "LIVE") +
                        "] strictly forbids simulated fallback for provider [" + id + "]. Valid API key required.");
            }
            return simulateChat(request, start);
        }

        try {
            return executeWithBackoff(() -> doChatCall(request, effectiveKey, start), 3, 500L);
        } catch (Exception e) {
            if (e instanceof RuntimeException re && re.getClass().getName().contains("Provider")) {
                throw re;
            }
            circuitBreaker.recordFailure(e);
            throw new RuntimeException("Gemini provider [" + id + "] call failed: " + SecretMasker.maskInText(e.getMessage()), e);
        }
    }

    private ChatResponse doChatCall(ChatRequest request, String effectiveKey, long start) throws Exception {
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
        applyRequestTimeout(reqBuilder, request);
        HttpRequest httpRequest = reqBuilder.build();

        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        long latencyMs = Math.max(1, System.currentTimeMillis() - start);

        if (response.statusCode() != 200) {
            handleHttpError(response.statusCode(), response.body());
        }

        return parseResponse(response.body(), latencyMs);
    }

    public ChatResponse parseResponse(String responseBody, long latencyMs) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        String content = "";
        List<ToolCall> toolCalls = new ArrayList<>();

        if (root.has("candidates") && root.get("candidates").size() > 0) {
            JsonNode parts = root.get("candidates").get(0).path("content").path("parts");
            if (parts.isArray()) {
                StringBuilder sb = new StringBuilder();
                for (JsonNode part : parts) {
                    if (part.has("text")) {
                        sb.append(part.path("text").asText(""));
                    }
                    if (part.has("functionCall")) {
                        JsonNode fc = part.get("functionCall");
                        String fnName = fc.path("name").asText("");
                        JsonNode argsNode = fc.path("args");
                        String fnArgs = (argsNode == null || argsNode.isMissingNode() || argsNode.isNull())
                                ? "{}" : objectMapper.writeValueAsString(argsNode);
                        toolCalls.add(new ToolCall("call-" + UUID.randomUUID().toString().substring(0, 8), fnName, fnArgs));
                    }
                }
                content = sb.toString();
            }
        }

        int promptTokens = 100;
        int completionTokens = 50;
        if (root.has("usageMetadata")) {
            JsonNode usageNode = root.get("usageMetadata");
            if (usageNode.has("promptTokenCount")) promptTokens = usageNode.get("promptTokenCount").asInt();
            if (usageNode.has("candidatesTokenCount")) completionTokens = usageNode.get("candidatesTokenCount").asInt();
        }

        ChatResponse chatResponse = buildResponse(content, promptTokens, completionTokens, latencyMs);
        if (!toolCalls.isEmpty()) {
            chatResponse.setToolCalls(toolCalls);
        }
        return chatResponse;
    }

    @Override
    public void streamChat(ChatRequest request, Consumer<ChatChunk> chunkConsumer, CancelToken cancelToken) {
        long start = System.currentTimeMillis();

        if (!circuitBreaker.allowRequest()) {
            throw new ProviderUnavailableException(id, "Circuit breaker is OPEN or probe in flight for provider " + id);
        }

        String effectiveKey = getEffectiveApiKey("GEMINI_API_KEY");

        if (effectiveKey == null || effectiveKey.isBlank() || isSimulated()) {
            if (isStrictLiveMode(request)) {
                throw new ProviderUnavailableException(id,
                        "Live mode [" + (request != null ? request.getExecutionMode() : "LIVE") +
                        "] strictly forbids simulated fallback for provider [" + id + "]. Valid API key required.");
            }
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
            applyRequestTimeout(reqBuilder, request);
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
                                    for (JsonNode part : parts) {
                                        if (part.has("text")) {
                                            String text = part.path("text").asText("");
                                            if (!text.isEmpty()) {
                                                completionTokens += Math.max(1, text.length() / 4);
                                                chunkConsumer.accept(ChatChunk.of(text));
                                            }
                                        }
                                        if (part.has("functionCall")) {
                                            JsonNode fc = part.get("functionCall");
                                            String fnName = fc.path("name").asText("");
                                            JsonNode argsNode = fc.path("args");
                                            String fnArgs = (argsNode == null || argsNode.isMissingNode() || argsNode.isNull())
                                                    ? "{}" : objectMapper.writeValueAsString(argsNode);
                                            chunkConsumer.accept(ChatChunk.ofToolCalls(List.of(
                                                    new ToolCall("call-" + UUID.randomUUID().toString().substring(0, 8), fnName, fnArgs)
                                            )));
                                        }
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

    public Map<String, Object> buildRequestBody(ChatRequest request) {
        return buildGeminiRequestBody(request);
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

        // Gemini Function Declarations
        if (request != null && request.getTools() != null && !request.getTools().isEmpty()) {
            List<Map<String, Object>> functionDecls = new ArrayList<>();
            for (ToolDefinition td : request.getTools()) {
                Map<String, Object> fn = new HashMap<>();
                fn.put("name", td.getName());
                if (td.getDescription() != null) fn.put("description", td.getDescription());
                if (td.getParameters() != null) fn.put("parameters", td.getParameters());
                functionDecls.add(fn);
            }
            body.put("tools", List.of(Map.of("function_declarations", functionDecls)));
        }

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

        ChatResponse resp = buildResponse(output, promptTokens, completionTokens, latencyMs);

        // If tools were requested, simulate tool call
        if (request != null && request.getTools() != null && !request.getTools().isEmpty()) {
            ToolDefinition firstTool = request.getTools().get(0);
            resp.setToolCalls(List.of(new ToolCall(
                    "call-gemini-" + UUID.randomUUID().toString().substring(0, 8),
                    firstTool.getName(),
                    "{\"mode\":\"gemini-query\",\"validated\":true}"
            )));
        }

        return resp;
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
        if (resp.hasToolCalls()) {
            chunkConsumer.accept(ChatChunk.ofToolCalls(resp.getToolCalls()));
        }
        chunkConsumer.accept(ChatChunk.finish("stop", resp.getUsage()));
    }
}
