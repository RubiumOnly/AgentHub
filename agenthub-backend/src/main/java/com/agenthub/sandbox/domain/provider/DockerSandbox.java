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
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Containerized Sandbox provider using Docker.
 * Enforces non-privileged user (1000:1000), read-only root filesystem, tmpfs limits,
 * CPU and memory limits, and strictly mounts only the controlled workspace.
 */
public class DockerSandbox implements SandboxProvider {

    private static final Logger log = LoggerFactory.getLogger(DockerSandbox.class);

    private final CommandSecurityGuard securityGuard;
    private final EnvironmentSanitizer environmentSanitizer;
    private final ConcurrentHashMap<String, Process> activeProcesses = new ConcurrentHashMap<>();
    private final ExecutorService ioExecutor = Executors.newCachedThreadPool();

    private Boolean dockerAvailableCache = null;

    public DockerSandbox(CommandSecurityGuard securityGuard, EnvironmentSanitizer environmentSanitizer) {
        this.securityGuard = securityGuard != null ? securityGuard : new CommandSecurityGuard();
        this.environmentSanitizer = environmentSanitizer != null ? environmentSanitizer : new EnvironmentSanitizer();
    }

    public DockerSandbox() {
        this(new CommandSecurityGuard(), new EnvironmentSanitizer());
    }

    @Override
    public String getProviderType() {
        return "DOCKER";
    }

    @Override
    public synchronized boolean isAvailable() {
        if (dockerAvailableCache != null) {
            return dockerAvailableCache;
        }
        try {
            Process process = new ProcessBuilder("docker", "--version").start();
            boolean finished = process.waitFor(3, TimeUnit.SECONDS);
            dockerAvailableCache = finished && process.exitValue() == 0;
        } catch (Exception e) {
            dockerAvailableCache = false;
        }
        return dockerAvailableCache;
    }

    /**
     * Builds the complete list of Docker command-line arguments according to security isolation rules.
     */
    public List<String> buildDockerArgs(SandboxExecutionRequest request, String image) {
        List<String> cmd = new ArrayList<>();
        cmd.add("docker");
        cmd.add("run");
        cmd.add("--rm");

        // 1. Non-root unprivileged execution
        String user = request.getContainerUser() != null ? request.getContainerUser() : "1000:1000";
        cmd.add("--user");
        cmd.add(user);

        // 2. Read-only root filesystem with isolated ephemeral tmpfs
        if (request.isReadOnlyRoot()) {
            cmd.add("--read-only");
            cmd.add("--tmpfs");
            cmd.add("/tmp:rw,noexec,nosuid,size=64m");
        }

        // 3. Resource quotas: memory and CPU constraints
        SandboxResourceQuota quota = request.getResourceQuota() != null
                ? request.getResourceQuota()
                : SandboxResourceQuota.defaults();
        cmd.add("--memory");
        cmd.add(quota.getMaxMemoryMb() + "m");
        cmd.add("--cpus");
        cmd.add(quota.getMaxCpu());

        // 4. Network restriction if requested
        if (request.isNetworkDisabled()) {
            cmd.add("--net=none");
        }

        // 5. Strictly mount only the controlled workspace directory
        Path workingDir = request.getWorkingDirectory();
        if (workingDir != null) {
            String hostPath = workingDir.toAbsolutePath().toString();
            cmd.add("-v");
            cmd.add(hostPath + ":/workspace:rw");
            cmd.add("-w");
            cmd.add("/workspace");
        }

        // 6. Inject sanitized environment variables
        Map<String, String> sanitizedEnv = environmentSanitizer.sanitize(request.getEnvironment());
        for (Map.Entry<String, String> entry : sanitizedEnv.entrySet()) {
            cmd.add("-e");
            cmd.add(entry.getKey() + "=" + entry.getValue());
        }

        // 7. Container image
        String targetImage = (image != null && !image.isBlank()) ? image : "alpine:latest";
        cmd.add(targetImage);

        // 8. Command and arguments
        cmd.add(request.getCommand());
        if (request.getArgs() != null) {
            cmd.addAll(request.getArgs());
        }

        return cmd;
    }

    @Override
    public SandboxExecutionResult execute(SandboxExecutionRequest request) {
        long startTime = System.currentTimeMillis();
        String executionId = request.getExecutionId();

        // 1. Validate security firewall
        securityGuard.validateCommand(request.getCommand(), request.getArgs());

        if (!isAvailable()) {
            return SandboxExecutionResult.failure(executionId, 1, "", "",
                    System.currentTimeMillis() - startTime,
                    "Docker daemon is not available on this host");
        }

        List<String> dockerCmd = buildDockerArgs(request, "alpine:latest");

        SandboxResourceQuota quota = request.getResourceQuota() != null
                ? request.getResourceQuota()
                : SandboxResourceQuota.defaults();
        long timeoutMs = quota.getMaxTimeoutMs();
        int maxOutputBytes = quota.getMaxOutputBytes();

        ProcessBuilder builder = new ProcessBuilder(dockerCmd);
        Process process;
        try {
            process = builder.start();
            activeProcesses.put(executionId, process);
        } catch (Exception e) {
            log.error("Failed to start docker sandbox process: {}", e.getMessage());
            return SandboxExecutionResult.failure(executionId, 127, "", "",
                    System.currentTimeMillis() - startTime,
                    "Failed to invoke docker CLI: " + e.getMessage());
        }

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
            log.warn("Docker sandbox [{}] timed out after {}ms, terminating container", executionId, timeoutMs);
            destroy(executionId);
            return SandboxExecutionResult.timeout(executionId, stdoutReader.getOutput(), stderrReader.getOutput(), duration);
        }

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
            result.setErrorMessage("Docker container exited with code: " + exitCode);
        }

        return result;
    }

    @Override
    public void destroy(String executionId) {
        Process proc = activeProcesses.remove(executionId);
        if (proc != null) {
            try {
                proc.toHandle().descendants().forEach(ProcessHandle::destroyForcibly);
                proc.destroyForcibly();
            } catch (Exception e) {
                log.warn("Error terminating docker sandbox process: {}", e.getMessage());
            }
        }
    }

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
