package com.agenthub.domain.agent.model;

import java.io.Serializable;
import java.time.Instant;

public class AgentExecutionResult implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum Status {
        SUCCESS,
        FAILED,
        TIMEOUT,
        DEGRADED
    }

    private Status status;
    private String output;
    private int exitCode;
    private long durationMs;
    private String errorDetails;
    private boolean simulated;
    private long timestamp;

    public AgentExecutionResult() {
        this.timestamp = Instant.now().toEpochMilli();
    }

    public static AgentExecutionResult success(String output, long durationMs, boolean simulated) {
        AgentExecutionResult result = new AgentExecutionResult();
        result.status = Status.SUCCESS;
        result.output = output;
        result.exitCode = 0;
        result.durationMs = durationMs;
        result.simulated = simulated;
        return result;
    }

    public static AgentExecutionResult fail(String errorDetails, int exitCode, long durationMs) {
        AgentExecutionResult result = new AgentExecutionResult();
        result.status = Status.FAILED;
        result.errorDetails = errorDetails;
        result.exitCode = exitCode;
        result.durationMs = durationMs;
        return result;
    }

    public static AgentExecutionResult timeout(long durationMs) {
        AgentExecutionResult result = new AgentExecutionResult();
        result.status = Status.TIMEOUT;
        result.errorDetails = "Execution timed out";
        result.exitCode = -1;
        result.durationMs = durationMs;
        return result;
    }

    public static AgentExecutionResult degraded(String fallbackOutput, String reason) {
        AgentExecutionResult result = new AgentExecutionResult();
        result.status = Status.DEGRADED;
        result.output = fallbackOutput;
        result.errorDetails = reason;
        result.exitCode = 0;
        result.simulated = true;
        return result;
    }

    public static AgentExecutionResult degraded(String fallbackOutput, String reason, long durationMs) {
        AgentExecutionResult result = new AgentExecutionResult();
        result.status = Status.DEGRADED;
        result.output = fallbackOutput;
        result.errorDetails = reason;
        result.exitCode = 0;
        result.durationMs = durationMs;
        result.simulated = true;
        return result;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public String getOutput() {
        return output;
    }

    public void setOutput(String output) {
        this.output = output;
    }

    public int getExitCode() {
        return exitCode;
    }

    public void setExitCode(int exitCode) {
        this.exitCode = exitCode;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }

    public String getErrorDetails() {
        return errorDetails;
    }

    public void setErrorDetails(String errorDetails) {
        this.errorDetails = errorDetails;
    }

    public boolean isSimulated() {
        return simulated;
    }

    public void setSimulated(boolean simulated) {
        this.simulated = simulated;
    }

    public boolean isDegraded() {
        return status == Status.DEGRADED || simulated;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
