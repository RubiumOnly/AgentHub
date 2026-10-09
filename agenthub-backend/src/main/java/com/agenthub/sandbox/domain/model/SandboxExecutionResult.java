package com.agenthub.sandbox.domain.model;

import java.io.Serializable;

/**
 * Result of executing a command inside an isolated sandbox.
 */
public class SandboxExecutionResult implements Serializable {
    private static final long serialVersionUID = 1L;

    private String executionId;
    private int exitCode;
    private String stdout;
    private String stderr;
    private boolean timedOut;
    private boolean truncated;
    private long durationMs;
    private String errorMessage;

    public SandboxExecutionResult() {
        this.exitCode = 0;
        this.stdout = "";
        this.stderr = "";
        this.timedOut = false;
        this.truncated = false;
        this.durationMs = 0L;
    }

    public static SandboxExecutionResult success(String executionId, String stdout, long durationMs) {
        SandboxExecutionResult res = new SandboxExecutionResult();
        res.setExecutionId(executionId);
        res.setExitCode(0);
        res.setStdout(stdout != null ? stdout : "");
        res.setDurationMs(durationMs);
        return res;
    }

    public static SandboxExecutionResult failure(String executionId, int exitCode, String stdout, String stderr, long durationMs, String errorMessage) {
        SandboxExecutionResult res = new SandboxExecutionResult();
        res.setExecutionId(executionId);
        res.setExitCode(exitCode);
        res.setStdout(stdout != null ? stdout : "");
        res.setStderr(stderr != null ? stderr : "");
        res.setDurationMs(durationMs);
        res.setErrorMessage(errorMessage);
        return res;
    }

    public static SandboxExecutionResult timeout(String executionId, String stdout, String stderr, long durationMs) {
        SandboxExecutionResult res = new SandboxExecutionResult();
        res.setExecutionId(executionId);
        res.setExitCode(137); // Standard SIGKILL / Watchdog exit code
        res.setStdout(stdout != null ? stdout : "");
        res.setStderr(stderr != null ? stderr : "");
        res.setTimedOut(true);
        res.setDurationMs(durationMs);
        res.setErrorMessage("Execution timed out by sandbox watchdog after " + durationMs + "ms");
        return res;
    }

    public boolean isSuccess() {
        return exitCode == 0 && !timedOut && (errorMessage == null || errorMessage.isBlank());
    }

    public String getCombinedOutput() {
        StringBuilder sb = new StringBuilder();
        if (stdout != null && !stdout.isBlank()) {
            sb.append(stdout);
        }
        if (stderr != null && !stderr.isBlank()) {
            if (sb.length() > 0 && !sb.toString().endsWith("\n")) {
                sb.append("\n");
            }
            sb.append(stderr);
        }
        return sb.toString();
    }

    public String getExecutionId() { return executionId; }
    public void setExecutionId(String executionId) { this.executionId = executionId; }

    public int getExitCode() { return exitCode; }
    public void setExitCode(int exitCode) { this.exitCode = exitCode; }

    public String getStdout() { return stdout; }
    public void setStdout(String stdout) { this.stdout = stdout; }

    public String getStderr() { return stderr; }
    public void setStderr(String stderr) { this.stderr = stderr; }

    public boolean isTimedOut() { return timedOut; }
    public void setTimedOut(boolean timedOut) { this.timedOut = timedOut; }

    public boolean isTruncated() { return truncated; }
    public void setTruncated(boolean truncated) { this.truncated = truncated; }

    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
