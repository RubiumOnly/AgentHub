package com.agenthub.runtime.port;

/**
 * Event sink receiving real-time runtime events during Agent execution.
 */
public interface RuntimeEventSink {
    void onToken(String runId, String stepRunId, String token);
    void onMessage(String runId, String stepRunId, String sender, String content);
    void onToolCall(String runId, String stepRunId, String toolName, String inputJson);
    void onFileChange(String runId, String stepRunId, String path, String changeType);
    void onLog(String runId, String stepRunId, String level, String message);
    void onUsage(String runId, String stepRunId, int promptTokens, int completionTokens, double costEstimate);
    void onStepCompleted(String runId, String stepRunId, String output);
    void onStepFailed(String runId, String stepRunId, String error, Throwable cause);
}
