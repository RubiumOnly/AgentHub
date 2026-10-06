package com.agenthub.infrastructure.adapter;

import com.agenthub.domain.agent.model.AgentExecutionRequest;
import com.agenthub.domain.agent.model.AgentExecutionResult;
import com.agenthub.domain.agent.model.AgentPlatformType;
import com.agenthub.domain.agent.spi.UnifiedAgentAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Pattern;

@Component
public class CliProcessAdapter implements UnifiedAgentAdapter {

    private static final Logger log = LoggerFactory.getLogger(CliProcessAdapter.class);
    private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");
    private static final Pattern SECRET_PATTERN = Pattern.compile("(?i)(sk-[a-zA-Z0-9_-]{12,}|Bearer\\s+[a-zA-Z0-9_.-]{16,})");

    @Override
    public AgentPlatformType getSupportedPlatform() {
        return AgentPlatformType.CLAUDE_CODE;
    }

    @Override
    public boolean isAvailable() {
        return resolveCommand(getCommandName()) != null;
    }

    @Override
    public String checkVersion() {
        String cmd = resolveCommand(getCommandName());
        if (cmd == null) {
            return "CLI not found on host PATH";
        }
        try {
            Process process = new ProcessBuilder(cmd, "--version").start();
            boolean finished = process.waitFor(3, TimeUnit.SECONDS);
            if (finished && process.exitValue() == 0) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    return reader.readLine();
                }
            }
        } catch (Exception e) {
            log.debug("Failed to check CLI version: {}", e.getMessage());
        }
        return "Unknown Version";
    }

    @Override
    public AgentExecutionResult execute(AgentExecutionRequest request) {
        long startTime = System.currentTimeMillis();
        String executable = resolveCommand(getCommandName());

        if (executable == null) {
            log.warn("CLI [{}] not installed on host, falling back to graceful simulation.", getCommandName());
            return AgentExecutionResult.degraded(
                    generateFallbackOutput(request),
                    "Host environment lacks '" + getCommandName() + "' CLI. Degraded gracefully."
            );
        }

        List<String> commandList = buildCommandArgs(executable, request);
        ProcessBuilder processBuilder = new ProcessBuilder(commandList);

        if (request.getWorkspacePath() != null && new File(request.getWorkspacePath()).exists()) {
            processBuilder.directory(new File(request.getWorkspacePath()));
        }

        Map<String, String> env = processBuilder.environment();
        if (request.getEnvironmentVariables() != null) {
            env.putAll(request.getEnvironmentVariables());
        }

        try {
            Process process = processBuilder.start();
            StringBuilder stdoutBuffer = new StringBuilder();
            StringBuilder stderrBuffer = new StringBuilder();

            Thread outThread = new Thread(() -> readStream(process.getInputStream(), stdoutBuffer));
            Thread errThread = new Thread(() -> readStream(process.getErrorStream(), stderrBuffer));
            outThread.start();
            errThread.start();

            boolean finished = process.waitFor(request.getTimeoutSeconds(), TimeUnit.SECONDS);
            outThread.join(1000);
            errThread.join(1000);

            long duration = System.currentTimeMillis() - startTime;
            if (!finished) {
                process.destroyForcibly();
                return AgentExecutionResult.timeout(duration);
            }

            int exitCode = process.exitValue();
            if (exitCode == 0) {
                return AgentExecutionResult.success(sanitizeOutput(stdoutBuffer.toString()), duration, false);
            } else {
                return AgentExecutionResult.fail(sanitizeOutput(stderrBuffer.toString()), exitCode, duration);
            }
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            return AgentExecutionResult.fail("Process execution error: " + e.getMessage(), -1, duration);
        }
    }

    @Override
    public void executeStream(AgentExecutionRequest request, Consumer<String> onChunk, Consumer<AgentExecutionResult> onComplete) {
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            AgentExecutionResult result = execute(request);
            if (result.getOutput() != null) {
                onChunk.accept(result.getOutput());
            }
            onComplete.accept(result);
        });
    }

    protected String getCommandName() {
        return IS_WINDOWS ? "claude.cmd" : "claude";
    }

    private List<String> buildCommandArgs(String executable, AgentExecutionRequest request) {
        List<String> args = new ArrayList<>();
        args.add(executable);
        args.add("-p");
        args.add(request.getPrompt() != null ? request.getPrompt() : "");
        return args;
    }

    private void readStream(java.io.InputStream inputStream, StringBuilder targetBuffer) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                targetBuffer.append(line).append("\n");
            }
        } catch (Exception ignored) {
        }
    }

    public static String resolveCommand(String command) {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null) {
            return null;
        }
        String[] paths = pathEnv.split(File.pathSeparator);
        for (String p : paths) {
            File file = new File(p, command);
            if (file.exists() && file.canExecute()) {
                return file.getAbsolutePath();
            }
            if (IS_WINDOWS && !command.endsWith(".cmd") && !command.endsWith(".exe")) {
                File cmdFile = new File(p, command + ".cmd");
                if (cmdFile.exists() && cmdFile.canExecute()) {
                    return cmdFile.getAbsolutePath();
                }
                File exeFile = new File(p, command + ".exe");
                if (exeFile.exists() && exeFile.canExecute()) {
                    return exeFile.getAbsolutePath();
                }
            }
        }
        return null;
    }

    public static String sanitizeOutput(String raw) {
        if (raw == null) return "";
        return SECRET_PATTERN.matcher(raw).replaceAll("[REDACTED_SECRET]");
    }

    private String generateFallbackOutput(AgentExecutionRequest request) {
        return String.format(
                "// [AgentHub Fallback Simulated Response]\n" +
                "// Platform: %s\n" +
                "// Prompt: %s\n" +
                "// Execution simulated safely without blocking platform workflow.",
                request.getPlatformType(), request.getPrompt()
        );
    }
}
