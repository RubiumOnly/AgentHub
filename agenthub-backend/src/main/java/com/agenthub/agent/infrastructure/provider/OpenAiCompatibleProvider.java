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
 * Standard OpenAI & DeepSeek Compatible Provider.
 * Implements standard /chat/completions REST endpoint, SSE streaming protocol,
 * function calling / tool_calls, timeout configuration, and exponential backoff retry.
 */
public class OpenAiCompatibleProvider extends AbstractHttpLlmProvider {

    public OpenAiCompatibleProvider(String id,
                                    String baseUrl,
                                    String secretRef,
                                    String model,
                                    int priority,
                                    int weight,
                                    Set<String> capabilities,
                                    Double costPerMillionInput,
                                    Double costPerMillionOutput) {
        this(id, "OPENAI", id, baseUrl, secretRef, model, priority, weight, capabilities, costPerMillionInput, costPerMillionOutput, null);
    }

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

        if (!circuitBreaker.allowRequest()) {
            throw new ProviderUnavailableException(id, "Circuit breaker is OPEN or probe in flight for provider " + id);
        }

        String effectiveKey = getEffectiveApiKey(providerType.toUpperCase() + "_API_KEY");

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
            throw new RuntimeException("Provider [" + id + "] call failed: " + SecretMasker.maskInText(e.getMessage()), e);
        }
    }

    private ChatResponse doChatCall(ChatRequest request, String effectiveKey, long start) throws Exception {
        String requestUrl = buildEndpointUrl();
        Map<String, Object> bodyMap = buildRequestBody(request, false);
        String jsonPayload = objectMapper.writeValueAsString(bodyMap);

        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                .uri(URI.create(requestUrl))
                .header("Authorization", "Bearer " + effectiveKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload, StandardCharsets.UTF_8));
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

        if (root.has("choices") && root.get("choices").size() > 0) {
            JsonNode messageNode = root.get("choices").get(0).get("message");
            if (messageNode != null) {
                if (messageNode.has("content") && !messageNode.get("content").isNull()) {
                    content = messageNode.get("content").asText();
                }
                if (messageNode.has("tool_calls") && messageNode.get("tool_calls").isArray()) {
                    for (JsonNode tc : messageNode.get("tool_calls")) {
                        String callId = tc.path("id").asText("call-" + UUID.randomUUID().toString().substring(0, 8));
                        String fnName = tc.path("function").path("name").asText("");
                        String fnArgs = tc.path("function").path("arguments").asText("{}");
                        toolCalls.add(new ToolCall(callId, fnName, fnArgs));
                    }
                }
            }
        }

        int promptTokens = 100;
        int completionTokens = 50;
        if (root.has("usage")) {
            JsonNode usageNode = root.get("usage");
            if (usageNode.has("prompt_tokens")) promptTokens = usageNode.get("prompt_tokens").asInt();
            if (usageNode.has("completion_tokens")) completionTokens = usageNode.get("completion_tokens").asInt();
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

        String effectiveKey = getEffectiveApiKey(providerType.toUpperCase() + "_API_KEY");

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
            String requestUrl = buildEndpointUrl();
            Map<String, Object> bodyMap = buildRequestBody(request, true);
            String jsonPayload = objectMapper.writeValueAsString(bodyMap);

            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(requestUrl))
                    .header("Authorization", "Bearer " + effectiveKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload, StandardCharsets.UTF_8));
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
                                if (delta != null) {
                                    if (delta.has("content") && !delta.get("content").isNull()) {
                                        String text = delta.get("content").asText();
                                        completionTokens += Math.max(1, text.length() / 4);
                                        chunkConsumer.accept(ChatChunk.of(text));
                                    }
                                    if (delta.has("tool_calls") && delta.get("tool_calls").isArray()) {
                                        List<ToolCall> deltaCalls = new ArrayList<>();
                                        for (JsonNode tc : delta.get("tool_calls")) {
                                            String callId = tc.path("id").asText();
                                            String fnName = tc.path("function").path("name").asText("");
                                            String fnArgs = tc.path("function").path("arguments").asText("");
                                            deltaCalls.add(new ToolCall(callId, fnName, fnArgs));
                                        }
                                        if (!deltaCalls.isEmpty()) {
                                            chunkConsumer.accept(ChatChunk.ofToolCalls(deltaCalls));
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

    private String buildEndpointUrl() {
        String base = baseUrl != null ? baseUrl.trim() : "https://api.openai.com/v1";
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        if (base.endsWith("/chat/completions")) return base;
        if (base.endsWith("/v1")) return base + "/chat/completions";
        return base + "/v1/chat/completions";
    }

    public Map<String, Object> buildRequestBody(ChatRequest request, boolean stream) {
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

        // Standard OpenAI & DeepSeek tools payload
        if (request.getTools() != null && !request.getTools().isEmpty()) {
            List<Map<String, Object>> toolsList = new ArrayList<>();
            for (ToolDefinition td : request.getTools()) {
                Map<String, Object> toolMap = new HashMap<>();
                toolMap.put("type", "function");
                Map<String, Object> fn = new HashMap<>();
                fn.put("name", td.getName());
                if (td.getDescription() != null) fn.put("description", td.getDescription());
                if (td.getParameters() != null) fn.put("parameters", td.getParameters());
                toolMap.put("function", fn);
                toolsList.add(toolMap);
            }
            body.put("tools", toolsList);
            if (request.getToolChoice() != null && !request.getToolChoice().isBlank()) {
                body.put("tool_choice", request.getToolChoice());
            }
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

        ChatResponse resp = buildResponse(output, promptTokens, completionTokens, latencyMs);

        // If tools were requested in simulation, simulate a tool call
        if (request != null && request.getTools() != null && !request.getTools().isEmpty()) {
            ToolDefinition firstTool = request.getTools().get(0);
            resp.setToolCalls(List.of(new ToolCall(
                    "call-sim-" + UUID.randomUUID().toString().substring(0, 8),
                    firstTool.getName(),
                    "{\"query\":\"simulated-query\",\"action\":\"analyze\"}"
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
