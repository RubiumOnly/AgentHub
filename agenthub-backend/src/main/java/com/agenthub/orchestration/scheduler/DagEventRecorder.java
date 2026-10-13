package com.agenthub.orchestration.scheduler;

import com.agenthub.execution.service.RunEventBroadcaster;
import com.agenthub.runtime.port.RuntimeEventSink;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Internal DAG seam responsible for publishing structured DAG, step, approval, and runtime
 * streaming events via RunEventBroadcaster.
 */
@Component
public class DagEventRecorder {

    private final RunEventBroadcaster broadcaster;
    private final ObjectMapper objectMapper;

    public DagEventRecorder(RunEventBroadcaster broadcaster, ObjectMapper objectMapper) {
        this.broadcaster = broadcaster;
        this.objectMapper = objectMapper;
    }

    public void publishDagStarted(String runId, int nodeCount) {
        broadcaster.publishEvent(runId, "DAG_STARTED",
                "{\"runId\":\"" + runId + "\",\"nodeCount\":" + nodeCount + "}");
    }

    public void publishRunTimedOut(String runId, long timeoutSeconds) {
        broadcaster.publishEvent(runId, "RUN_TIMED_OUT",
                "{\"timeoutSeconds\":" + timeoutSeconds + "}");
    }

    public void publishRunFailed(String runId, String reason) {
        broadcaster.publishEvent(runId, "RUN_FAILED",
                "{\"reason\":\"" + escapeJson(reason) + "\"}");
    }

    public void publishRunCompleted(String runId) {
        broadcaster.publishEvent(runId, "RUN_COMPLETED", "{\"status\":\"SUCCEEDED\"}");
    }

    public void publishStepStarted(String runId, String stepRunId, String nodeId, int attempt, String runtimeType, boolean simulated) {
        broadcaster.publishEvent(runId, "STEP_STARTED",
                "{\"stepRunId\":\"" + stepRunId + "\",\"nodeId\":\"" + nodeId
                        + "\",\"attempt\":" + attempt
                        + ",\"runtimeType\":\"" + escapeJson(runtimeType) + "\""
                        + ",\"simulated\":" + simulated + "}");
    }

    public void publishStepCompleted(String runId, String stepRunId, String nodeId, String runtimeType, boolean simulated) {
        broadcaster.publishEvent(runId, "STEP_COMPLETED",
                "{\"stepRunId\":\"" + stepRunId + "\",\"nodeId\":\"" + nodeId
                        + "\",\"runtimeType\":\"" + escapeJson(runtimeType) + "\""
                        + ",\"simulated\":" + simulated + "}");
    }

    public void publishStepRetrying(String runId, String stepRunId, int nextAttempt) {
        broadcaster.publishEvent(runId, "STEP_RETRYING",
                "{\"stepRunId\":\"" + stepRunId + "\",\"nextAttempt\":" + nextAttempt + "}");
    }

    public void publishStepSkipped(String runId, String stepRunId, String nodeId, String reason) {
        broadcaster.publishEvent(runId, "STEP_SKIPPED",
                "{\"stepRunId\":\"" + stepRunId + "\",\"nodeId\":\"" + nodeId + "\",\"reason\":\"" + escapeJson(reason) + "\"}");
    }

    public void publishApprovalRequested(String runId, String approvalId, String stepRunId, String nodeId, String nodeName) {
        Map<String, Object> eventData = Map.of(
                "approvalId", approvalId,
                "runId", runId,
                "stepRunId", stepRunId,
                "nodeId", nodeId,
                "nodeName", nodeName != null ? nodeName : nodeId
        );
        try {
            broadcaster.publishEvent(runId, "APPROVAL_REQUESTED", objectMapper.writeValueAsString(eventData));
        } catch (Exception ignored) {}
    }

    public void evictRun(String runId) {
        broadcaster.evictRun(runId);
    }

    public RuntimeEventSink createStepSink(String runId, CompletableFuture<String> stepFuture) {
        return new RuntimeEventSink() {
            @Override
            public void onToken(String rId, String sId, String token) {
                broadcaster.publishEvent(runId, "TOKEN", "{\"token\":\"" + escapeJson(token) + "\"}");
            }

            @Override
            public void onMessage(String rId, String sId, String sender, String content) {
                broadcaster.publishEvent(runId, "MESSAGE", "{\"sender\":\"" + sender + "\",\"content\":\"" + escapeJson(content) + "\"}");
            }

            @Override
            public void onToolCall(String rId, String sId, String toolName, String inputJson) {
                broadcaster.publishEvent(runId, "TOOL_CALL", "{\"tool\":\"" + toolName + "\",\"input\":" + inputJson + "}");
            }

            @Override
            public void onFileChange(String rId, String sId, String path, String changeType) {
                broadcaster.publishEvent(runId, "FILE_CHANGE", "{\"path\":\"" + path + "\",\"changeType\":\"" + changeType + "\"}");
            }

            @Override
            public void onLog(String rId, String sId, String level, String message) {
                broadcaster.publishEvent(runId, "LOG", "{\"level\":\"" + level + "\",\"message\":\"" + escapeJson(message) + "\"}");
            }

            @Override
            public void onUsage(String rId, String sId, int promptTokens, int completionTokens, double costEstimate) {
                broadcaster.publishEvent(runId, "USAGE", "{\"promptTokens\":" + promptTokens + ",\"completionTokens\":" + completionTokens + ",\"cost\":" + costEstimate + "}");
            }

            @Override
            public void onStepCompleted(String rId, String sId, String output) {
                stepFuture.complete(output);
            }

            @Override
            public void onStepFailed(String rId, String sId, String error, Throwable cause) {
                stepFuture.completeExceptionally(new RuntimeException(error != null ? error : "Step failed"));
            }
        };
    }

    public static String escapeJson(String raw) {
        if (raw == null) return "";
        return raw.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
