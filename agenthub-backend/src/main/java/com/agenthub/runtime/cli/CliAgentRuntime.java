package com.agenthub.runtime.cli;

import com.agenthub.domain.agent.model.AgentExecutionRequest;
import com.agenthub.execution.domain.model.CancelToken;
import com.agenthub.infrastructure.adapter.CliProcessAdapter;
import com.agenthub.runtime.port.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * CLI Process Agent Runtime (Codex CLI / Claude Code / OpenClaw).
 * Manages subprocess lifecycle, stream pumping, desensitization, and forcible termination.
 */
@Component("cliAgentRuntime")
public class CliAgentRuntime implements AgentRuntime {

    private static final Logger log = LoggerFactory.getLogger(CliAgentRuntime.class);
    private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");

    @Override
    public RuntimeDescriptor describe() {
        String cli = resolveExecutable();
        return new RuntimeDescriptor(
                "CLI",
                "Subprocess Native CLI Runtime",
                "claude/codex-cli",
                "Local-Host",
                List.of("LOCAL_PROCESS", "FILE_SYSTEM", "INTERACTION"),
                cli == null
        );
    }

    @Override
    public RuntimeHealth checkHealth() {
        String cli = resolveExecutable();
        if (cli == null) {
            return RuntimeHealth.degraded("CLI executable not installed on PATH", 0);
        }
        return RuntimeHealth.ok("CLI executable found at: " + cli, 10);
    }

    @Override
    public ExecutionHandle start(AgentExecutionRequest request, RuntimeEventSink sink, CancelToken cancelToken) {
        String handleId = "handle-cli-" + UUID.randomUUID().toString().substring(0, 8);
        String runId = request.getAgentName() != null ? request.getAgentName() : "run-default";
        String stepRunId = "step-cli-" + UUID.randomUUID().toString().substring(0, 8);
        ExecutionHandle handle = new ExecutionHandle(handleId, runId, stepRunId, "CLI");

        CompletableFuture.runAsync(() -> {
            try {
                if (cancelToken != null && cancelToken.isCancelled()) {
                    sink.onStepFailed(runId, stepRunId, "Execution cancelled before start: " + cancelToken.getReason(), null);
                    return;
                }

                String executable = resolveExecutable();
                if (executable == null) {
                    sink.onLog(runId, stepRunId, "WARN", "Host environment lacks CLI executable, using safe simulation fallback.");
                    String simulated = "// Simulated CLI output for: " + request.getPrompt();
                    sink.onToken(runId, stepRunId, simulated);
                    sink.onStepCompleted(runId, stepRunId, simulated);
                    return;
                }

                List<String> commandList = new ArrayList<>();
                commandList.add(executable);
                commandList.add("-p");
                commandList.add(request.getPrompt() != null ? request.getPrompt() : "");

                ProcessBuilder pb = new ProcessBuilder(commandList);
                if (request.getWorkspacePath() != null && new File(request.getWorkspacePath()).exists()) {
                    pb.directory(new File(request.getWorkspacePath()));
                }

                Process process = pb.start();
                if (cancelToken != null) {
                    cancelToken.registerProcess(process);
                }

                StringBuilder stdout = new StringBuilder();
                Thread streamReader = new Thread(() -> {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            String sanitized = CliProcessAdapter.sanitizeOutput(line);
                            stdout.append(sanitized).append("\n");
                            sink.onToken(runId, stepRunId, sanitized + "\n");
                        }
                    } catch (Exception ignored) {}
                });
                streamReader.start();

                long timeoutSec = request.getTimeoutSeconds() > 0 ? request.getTimeoutSeconds() : 60;
                boolean completed = process.waitFor(timeoutSec, TimeUnit.SECONDS);
                streamReader.join(1000);

                if (!completed) {
                    process.destroyForcibly();
                    sink.onStepFailed(runId, stepRunId, "CLI process timed out after " + timeoutSec + "s", null);
                    return;
                }

                if (cancelToken != null && cancelToken.isCancelled()) {
                    process.destroyForcibly();
                    sink.onStepFailed(runId, stepRunId, "CLI execution cancelled: " + cancelToken.getReason(), null);
                    return;
                }

                int exitCode = process.exitValue();
                if (exitCode == 0) {
                    sink.onStepCompleted(runId, stepRunId, stdout.toString());
                } else {
                    sink.onStepFailed(runId, stepRunId, "CLI process exited with non-zero code: " + exitCode, null);
                }

            } catch (Exception e) {
                log.error("CLI runtime error: {}", e.getMessage(), e);
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

    private String resolveExecutable() {
        String cmd = IS_WINDOWS ? "claude.cmd" : "claude";
        return CliProcessAdapter.resolveCommand(cmd);
    }
}
