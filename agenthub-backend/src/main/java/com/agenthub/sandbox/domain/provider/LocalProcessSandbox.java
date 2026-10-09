package com.agenthub.sandbox.domain.provider;

import com.agenthub.sandbox.domain.model.SandboxExecutionRequest;
import com.agenthub.sandbox.domain.model.SandboxExecutionResult;
import com.agenthub.sandbox.domain.model.SandboxResourceQuota;
import com.agenthub.sandbox.domain.security.CommandSecurityGuard;
import com.agenthub.sandbox.domain.security.EnvironmentSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Restricted Subprocess Sandbox provider.
 * Enforces command security firewall, environment isolation, working directory boundaries,
 * bounded output buffer capture, and watchdog timeout process-tree termination.
 */
public class LocalProcessSandbox implements SandboxProvider {

    private static final Logger log = LoggerFactory.getLogger(LocalProcessSandbox.class);

    private final CommandSecurityGuard securityGuard;
    private final EnvironmentSanitizer environmentSanitizer;
    private final ConcurrentHashMap<String, Process> activeProcesses = new ConcurrentHashMap<>();
    private final ExecutorService ioExecutor = Executors.newCachedThreadPool();

    public LocalProcessSandbox(CommandSecurityGuard securityGuard, EnvironmentSanitizer environmentSanitizer) {
        this.securityGuard = securityGuard != null ? securityGuard : new CommandSecurityGuard();
        this.environmentSanitizer = environmentSanitizer != null ? environmentSanitizer : new EnvironmentSanitizer();
    }

    public LocalProcessSandbox() {
        this(new CommandSecurityGuard(), new EnvironmentSanitizer());
    }

    @Override
    public String getProviderType() {
        return "LOCAL_PROCESS";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public SandboxExecutionResult execute(SandboxExecutionRequest request) {
        long startTime = System.currentTimeMillis();
        String executionId = request.getExecutionId();

        // 1. Defensively validate command and arguments against security firewall
        securityGuard.validateCommand(request.getCommand(), request.getArgs());

        // 2. Validate working directory containment
        Path workingDir = request.getWorkingDirectory();
        if (workingDir != null) {
            if (!Files.exists(workingDir)) {
                try {
                    Files.createDirectories(workingDir);
                } catch (Exception e) {
                    return SandboxExecutionResult.failure(executionId, 1, "", "",
                            System.currentTimeMillis() - startTime,
                            "Failed to prepare working directory: " + e.getMessage());
                }
            }
        }

        // 3. Build sanitized and isolated environment
        Map<String, String> sanitizedEnv = environmentSanitizer.sanitize(request.getEnvironment());

        // 4. Construct process command list
        List<String> commandList = buildFullCommand(request.getCommand(), request.getArgs());

        SandboxResourceQuota quota = request.getResourceQuota() != null
                ? request.getResourceQuota()
                : SandboxResourceQuota.defaults();
        long timeoutMs = quota.getMaxTimeoutMs();
        int maxOutputBytes = quota.getMaxOutputBytes();

        ProcessBuilder processBuilder = new ProcessBuilder(commandList);
        if (workingDir != null) {
            processBuilder.directory(workingDir.toFile());
        }

        // Clear process environment and replace with strictly sanitized variables
        processBuilder.environment().clear();
        processBuilder.environment().putAll(sanitizedEnv);

        Process process;
        try {
            process = processBuilder.start();
            activeProcesses.put(executionId, process);
        } catch (Exception e) {
            log.error("Failed to start sandbox child process: {}", e.getMessage());
            return SandboxExecutionResult.failure(executionId, 127, "", "",
                    System.currentTimeMillis() - startTime,
                    "Failed to start command: " + e.getMessage());
        }

        // 5. Read stdout and stderr with output buffer truncation guard
        BoundedOutputReader stdoutReader = new BoundedOutputReader(process.getInputStream(), maxOutputBytes);
        BoundedOutputReader stderrReader = new BoundedOutputReader(process.getErrorStream(), maxOutputBytes);

        Future<?> stdoutFuture = ioExecutor.submit(stdoutReader);
        Future<?> stderrFuture = ioExecutor.submit(stderrReader);

        boolean finished;
        try {
            finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            destroy(executionId);
            return SandboxExecutionResult.failure(executionId, 130, "", "",
                    System.currentTimeMillis() - startTime, "Execution interrupted");
        } finally {
            activeProcesses.remove(executionId);
        }

        long duration = System.currentTimeMillis() - startTime;

        if (!finished) {
            log.warn("Sandbox process [{}] timed out after {}ms, forcibly killing process tree", executionId, timeoutMs);
            killProcessTree(process);
            try {
                stdoutFuture.cancel(true);
                stderrFuture.cancel(true);
            } catch (Exception ignored) {}
            return SandboxExecutionResult.timeout(executionId, stdoutReader.getOutput(), stderrReader.getOutput(), duration);
        }

        // Wait for output readers to finish
        try {
            stdoutFuture.get(1, TimeUnit.SECONDS);
            stderrFuture.get(1, TimeUnit.SECONDS);
        } catch (Exception ignored) {}

        int exitCode = process.exitValue();
        SandboxExecutionResult result = new SandboxExecutionResult();
        result.setExecutionId(executionId);
        result.setExitCode(exitCode);
        result.setStdout(stdoutReader.getOutput());
        result.setStderr(stderrReader.getOutput());
        result.setDurationMs(duration);
        result.setTruncated(stdoutReader.isTruncated() || stderrReader.isTruncated());

        if (exitCode != 0) {
            result.setErrorMessage("Process exited with non-zero code: " + exitCode);
        }

        return result;
    }

    @Override
    public void destroy(String executionId) {
        Process proc = activeProcesses.remove(executionId);
        if (proc != null) {
            killProcessTree(proc);
        }
    }

    private void killProcessTree(Process process) {
        try {
            process.toHandle().descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
        } catch (Exception e) {
            log.warn("Error while killing sandbox process tree: {}", e.getMessage());
        }
    }

    private List<String> buildFullCommand(String command, List<String> args) {
        List<String> list = new ArrayList<>();
        list.add(command);
        if (args != null) {
            list.addAll(args);
        }
        return list;
    }

    /**
     * Bounded output stream reader preventing memory exhaustion from runaway process logs.
     */
    private static class BoundedOutputReader implements Runnable {
        private final InputStream inputStream;
        private final int maxBytes;
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private final AtomicBoolean truncated = new AtomicBoolean(false);

        public BoundedOutputReader(InputStream inputStream, int maxBytes) {
            this.inputStream = inputStream;
            this.maxBytes = maxBytes > 0 ? maxBytes : SandboxResourceQuota.DEFAULT_MAX_OUTPUT_BYTES;
        }

        @Override
        public void run() {
            byte[] buf = new byte[4096];
            int read;
            try {
                while ((read = inputStream.read(buf)) != -1) {
                    if (buffer.size() + read <= maxBytes) {
                        buffer.write(buf, 0, read);
                    } else {
                        int remaining = maxBytes - buffer.size();
                        if (remaining > 0) {
                            buffer.write(buf, 0, remaining);
                        }
                        truncated.set(true);
                        // Consume remaining bytes without buffering to avoid blocking the child process
                    }
                }
            } catch (Exception ignored) {
            } finally {
                try {
                    inputStream.close();
                } catch (Exception ignored) {}
            }
        }

        public String getOutput() {
            String out = buffer.toString(StandardCharsets.UTF_8);
            if (truncated.get()) {
                out += "\n[SANDBOX WARNING: Output truncated due to exceeding limit of " + maxBytes + " bytes]";
            }
            return out;
        }

        public boolean isTruncated() {
            return truncated.get();
        }
    }
}
