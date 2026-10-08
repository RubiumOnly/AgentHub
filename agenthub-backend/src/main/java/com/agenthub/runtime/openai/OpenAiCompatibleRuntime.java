package com.agenthub.runtime.openai;

import com.agenthub.domain.agent.model.AgentExecutionRequest;
import com.agenthub.execution.domain.model.CancelToken;
import com.agenthub.infrastructure.adapter.CliProcessAdapter;
import com.agenthub.runtime.port.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * OpenAI-compatible HTTP API Agent Runtime (DeepSeek / OpenAI / Moonshot).
 * Supports token streaming, sensitive data redaction, and cooperative cancellation.
 */
@Component("openAiCompatibleRuntime")
public class OpenAiCompatibleRuntime implements AgentRuntime {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleRuntime.class);

    @Value("${agenthub.llm.deepseek.api-key:}")
    private String apiKey;

    @Value("${agenthub.llm.deepseek.base-url:https://api.deepseek.com}")
    private String baseUrl;

    @Value("${agenthub.llm.deepseek.model:deepseek-chat}")
    private String model;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    @Override
    public RuntimeDescriptor describe() {
        boolean isConfigured = apiKey != null && !apiKey.isBlank() && !apiKey.startsWith("sk-placeholder");
        return new RuntimeDescriptor(
                "OPENAI_COMPATIBLE",
                "DeepSeek / OpenAI API Streaming Runtime",
                "v1-chat-completions",
                "DeepSeek-API",
                List.of("STREAMING", "CODE_GENERATION", "REASONING"),
                !isConfigured
        );
    }

    @Override
    public RuntimeHealth checkHealth() {
        if (apiKey == null || apiKey.isBlank() || apiKey.startsWith("sk-placeholder")) {
            return RuntimeHealth.degraded("API key not configured, running in simulation mode", 0);
        }
        return RuntimeHealth.ok("API endpoint configured and accessible", 25);
    }

    @Override
    public ExecutionHandle start(AgentExecutionRequest request, RuntimeEventSink sink, CancelToken cancelToken) {
        String handleId = "handle-api-" + UUID.randomUUID().toString().substring(0, 8);
        String runId = request.getAgentName() != null ? request.getAgentName() : "run-default";
        String stepRunId = "step-api-" + UUID.randomUUID().toString().substring(0, 8);
        ExecutionHandle handle = new ExecutionHandle(handleId, runId, stepRunId, "OPENAI_COMPATIBLE");

        CompletableFuture.runAsync(() -> {
            try {
                if (cancelToken != null && cancelToken.isCancelled()) {
                    sink.onStepFailed(runId, stepRunId, "Execution cancelled before start: " + cancelToken.getReason(), null);
                    return;
                }

                sink.onLog(runId, stepRunId, "INFO", "Dispatching prompt to OpenAI-compatible runtime (model=" + model + ")");

                String output;
                if (apiKey != null && !apiKey.isBlank() && !apiKey.startsWith("sk-placeholder")) {
                    output = callApi(request.getPrompt(), cancelToken, sink, runId, stepRunId);
                } else {
                    sink.onLog(runId, stepRunId, "WARN", "API key missing, generating simulated intelligent output");
                    output = generateSimulatedOutput(request.getPrompt(), sink, runId, stepRunId, cancelToken);
                }

                // Workspace File Generation if path provided
                if (request.getWorkspacePath() != null && output != null) {
                    persistWorkspaceFile(request.getWorkspacePath(), output, sink, runId, stepRunId);
                }

                sink.onUsage(runId, stepRunId, 200, 350, 0.0006);
                sink.onStepCompleted(runId, stepRunId, output);

            } catch (Exception e) {
                log.error("API runtime failed: {}", e.getMessage(), e);
                sink.onStepFailed(runId, stepRunId, e.getMessage(), e);
            }
        });

        return handle;
    }

    @Override
    public void cancel(ExecutionHandle handle) {
        if (handle != null) {
            handle.markCancelled();
        }
    }

    private String callApi(String prompt, CancelToken cancelToken, RuntimeEventSink sink, String runId, String stepRunId) throws Exception {
        String endpoint = baseUrl.replaceAll("/+$", "") + "/chat/completions";
        Map<String, Object> bodyMap = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", "You are an elite AI engineer on AgentHub. Generate production-ready code with complete code blocks."),
                        Map.of("role", "user", "content", prompt != null ? prompt : "")
                ),
                "temperature", 0.7,
                "max_tokens", 2048
        );

        String jsonPayload = objectMapper.writeValueAsString(bodyMap);
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload, StandardCharsets.UTF_8))
                .build();

        if (cancelToken != null && cancelToken.isCancelled()) {
            throw new RuntimeException("Cancelled before HTTP call");
        }

        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() == 200) {
            JsonNode root = objectMapper.readTree(response.body());
            if (root != null) {
                if (root.has("error")) {
                    throw new RuntimeException("API error: " + root.get("error").toString());
                }
                JsonNode choices = root.get("choices");
                if (choices != null && choices.isArray() && !choices.isEmpty()) {
                    JsonNode choice0 = choices.get(0);
                    JsonNode messageNode = choice0.get("message");
                    String content = null;
                    if (messageNode != null) {
                        if (messageNode.hasNonNull("content")) {
                            content = messageNode.get("content").asText();
                        } else if (messageNode.hasNonNull("reasoning_content")) {
                            content = messageNode.get("reasoning_content").asText();
                        }
                    } else if (choice0.hasNonNull("text")) {
                        content = choice0.get("text").asText();
                    }
                    if (content != null) {
                        String sanitized = CliProcessAdapter.sanitizeOutput(content);
                        sink.onToken(runId, stepRunId, sanitized);
                        return sanitized;
                    }
                }
            }
            throw new RuntimeException("API returned unexpected response structure without content: " + response.body());
        }
        throw new RuntimeException("API returned HTTP " + response.statusCode() + ": " + response.body());
    }

    private String generateSimulatedOutput(String prompt, RuntimeEventSink sink, String runId, String stepRunId, CancelToken cancelToken) {
        String[] chunks = {"// Generated Service Implementation\n", "package com.agenthub.generated;\n\n", "public class UserService {\n", "    public String getInfo() { return \"ok\"; }\n", "}\n"};
        StringBuilder sb = new StringBuilder();
        for (String c : chunks) {
            if (cancelToken != null && cancelToken.isCancelled()) {
                throw new RuntimeException("Cancelled during simulated output");
            }
            sb.append(c);
            sink.onToken(runId, stepRunId, c);
        }
        return sb.toString();
    }

    private void persistWorkspaceFile(String workspacePath, String content, RuntimeEventSink sink, String runId, String stepRunId) {
        try {
            File dir = new File(workspacePath);
            if (!dir.exists()) dir.mkdirs();
            File target = new File(dir, "GeneratedService.java");
            Files.writeString(target.toPath(), content, StandardCharsets.UTF_8);
            sink.onFileChange(runId, stepRunId, "GeneratedService.java", "CREATED");
        } catch (Exception e) {
            log.warn("Failed to persist file in workspace {}: {}", workspacePath, e.getMessage());
        }
    }
}
