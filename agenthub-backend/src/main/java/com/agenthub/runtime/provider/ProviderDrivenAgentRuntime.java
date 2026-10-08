package com.agenthub.runtime.provider;

import com.agenthub.agent.application.TokenUsageApplication;
import com.agenthub.agent.domain.provider.model.*;
import com.agenthub.agent.domain.provider.router.DynamicProviderRouter;
import com.agenthub.agent.domain.provider.spi.LlmProvider;
import com.agenthub.agent.infrastructure.security.SecretMasker;
import com.agenthub.domain.agent.model.AgentExecutionRequest;
import com.agenthub.execution.domain.model.CancelToken;
import com.agenthub.runtime.port.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Enterprise Provider-Driven Agent Runtime.
 * Dispatches agent tasks through the Dynamic Provider Router with automated HA fallback,
 * token accounting, measured latency, and database audit logging.
 */
@Component("providerDrivenAgentRuntime")
public class ProviderDrivenAgentRuntime implements AgentRuntime {

    private static final Logger log = LoggerFactory.getLogger(ProviderDrivenAgentRuntime.class);

    private final DynamicProviderRouter router;
    private final TokenUsageApplication tokenUsageApplication;

    public ProviderDrivenAgentRuntime(DynamicProviderRouter router,
                                      TokenUsageApplication tokenUsageApplication) {
        this.router = router;
        this.tokenUsageApplication = tokenUsageApplication;
    }

    @Override
    public RuntimeDescriptor describe() {
        return new RuntimeDescriptor(
                "PROVIDER_DRIVEN",
                "Dynamic Multi-Provider Router Runtime (OpenAI/DeepSeek/Anthropic/Gemini/Ollama)",
                "v2-dynamic-router",
                "AgentHub-Router",
                List.of("DYNAMIC_ROUTING", "HA_FAILOVER", "CIRCUIT_BREAKER", "TOKEN_ACCOUNTING", "STREAMING"),
                false
        );
    }

    @Override
    public RuntimeHealth checkHealth() {
        List<LlmProvider> active = router.listProviders().stream()
                .filter(p -> p.getCircuitBreaker().allowRequest())
                .toList();
        if (active.isEmpty()) {
            return RuntimeHealth.degraded("All LLM providers are circuit broken or offline", 0);
        }
        return RuntimeHealth.ok(active.size() + " active providers available in router matrix", 15);
    }

    @Override
    public ExecutionHandle start(AgentExecutionRequest request, RuntimeEventSink sink, CancelToken cancelToken) {
        String handleId = "handle-prov-" + UUID.randomUUID().toString().substring(0, 8);
        String runId = request.getAgentName() != null ? request.getAgentName() : "run-default";
        String stepRunId = "step-prov-" + UUID.randomUUID().toString().substring(0, 8);
        ExecutionHandle handle = new ExecutionHandle(handleId, runId, stepRunId, "PROVIDER_DRIVEN");

        CompletableFuture.runAsync(() -> {
            try {
                if (cancelToken != null && cancelToken.isCancelled()) {
                    sink.onStepFailed(runId, stepRunId, "Execution cancelled before start: " + cancelToken.getReason(), null);
                    return;
                }

                sink.onLog(runId, stepRunId, "INFO", "Dispatching to Dynamic Provider Router with HA failover protection");

                ChatRequest chatRequest = buildChatRequest(request);
                StringBuilder outputBuffer = new StringBuilder();
                long startTime = System.currentTimeMillis();

                router.routeAndStream(
                        chatRequest,
                        chunk -> {
                            outputBuffer.append(chunk.getDeltaContent());
                            sink.onToken(runId, stepRunId, chunk.getDeltaContent());
                        },
                        fallbackEvent -> {
                            String alert = fallbackEvent.toAlertMessage();
                            sink.onLog(runId, stepRunId, "WARN", alert);
                            log.warn("HA Fallback triggered during execution: {}", alert);
                        },
                        cancelToken
                );

                long latencyMs = System.currentTimeMillis() - startTime;
                String fullOutput = outputBuffer.toString();

                // Persist file into workspace if path is specified
                if (request.getWorkspacePath() != null && !fullOutput.isBlank()) {
                    persistWorkspaceFile(request.getWorkspacePath(), fullOutput, sink, runId, stepRunId);
                }

                int promptTokens = Math.max(30, chatRequest.getMessages().get(0).getContent().length() / 4);
                int completionTokens = Math.max(20, fullOutput.length() / 4);
                double cost = ModelPricing.calculateCost(chatRequest.getModel(), promptTokens, completionTokens, 0.0, 0.0);

                // Persist token usage audit
                tokenUsageApplication.recordUsage(
                        runId,
                        stepRunId,
                        chatRequest.getPreferredProvider() != null ? chatRequest.getPreferredProvider() : "router-selected",
                        "ROUTER",
                        chatRequest.getModel() != null ? chatRequest.getModel() : "auto",
                        promptTokens,
                        completionTokens,
                        latencyMs,
                        cost,
                        "SUCCESS",
                        null
                );

                sink.onUsage(runId, stepRunId, promptTokens, completionTokens, cost);
                sink.onStepCompleted(runId, stepRunId, fullOutput);

            } catch (Exception e) {
                String safeMsg = SecretMasker.maskInText(e.getMessage());
                log.error("Execution failed in ProviderDrivenAgentRuntime: {}", safeMsg, e);
                try {
                    tokenUsageApplication.recordUsage(
                            runId,
                            stepRunId,
                            "unknown",
                            "ROUTER",
                            "unknown",
                            0, 0, 0, 0.0,
                            "FAILED",
                            safeMsg
                    );
                } catch (Exception ignored) {}
                sink.onStepFailed(runId, stepRunId, safeMsg, e);
            }
        });

        return handle;
    }

    @Override
    public void cancel(ExecutionHandle handle) {
        log.info("Cancel requested for handle [{}]", handle.getHandleId());
    }

    private ChatRequest buildChatRequest(AgentExecutionRequest req) {
        ChatRequest chatReq = new ChatRequest();
        chatReq.setTimeoutSeconds(req.getTimeoutSeconds());

        String prompt = req.getPrompt() != null ? req.getPrompt() : "Execute agent step task";
        chatReq.setMessages(List.of(ChatMessage.user(prompt)));

        // Extract capabilities based on agent role
        Set<String> caps = new HashSet<>();
        String agentName = req.getAgentName() != null ? req.getAgentName().toLowerCase() : "";
        if (agentName.contains("backend") || agentName.contains("frontend") || agentName.contains("code")) {
            caps.add("code");
        } else {
            caps.add("general");
        }

        if (req.getEnvironmentVariables() != null) {
            String customCaps = req.getEnvironmentVariables().get("REQUIRED_CAPABILITIES");
            if (customCaps != null) {
                Arrays.stream(customCaps.split(",")).map(String::trim).forEach(caps::add);
            }
            String prefModel = req.getEnvironmentVariables().get("PREFERRED_MODEL");
            if (prefModel != null) {
                chatReq.setModel(prefModel);
            }
        }

        chatReq.setRequiredCapabilities(caps);
        return chatReq;
    }

    private void persistWorkspaceFile(String workspacePath, String output, RuntimeEventSink sink, String runId, String stepRunId) {
        try {
            File dir = new File(workspacePath);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            File target = new File(dir, "agent-output.md");
            Files.writeString(target.toPath(), output, StandardCharsets.UTF_8);
            sink.onFileChange(runId, stepRunId, target.getName(), "MODIFIED");
            sink.onLog(runId, stepRunId, "INFO", "Persisted generated output to " + target.getName());
        } catch (Exception e) {
            log.warn("Failed to persist workspace file in provider runtime: {}", e.getMessage());
        }
    }
}
