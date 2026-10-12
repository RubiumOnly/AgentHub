package com.agenthub.sandbox.domain.provider;

import com.agenthub.sandbox.domain.model.SandboxExecutionRequest;
import com.agenthub.sandbox.domain.model.SandboxExecutionResult;
import com.agenthub.sandbox.domain.model.SandboxResourceQuota;
import com.agenthub.sandbox.domain.security.CommandSecurityGuard;
import com.agenthub.sandbox.domain.security.EnvironmentSanitizer;
import com.agenthub.shared.exception.BusinessException;
import com.agenthub.shared.exception.ErrorCode;
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
 * CPU/memory/PIDs limits, capability drop, no-new-privileges, and strictly mounts only
 * the controlled workspace. Refuses unisolated local daemon assumptions in production.
 */
public class DockerSandbox implements SandboxProvider {

    private static final Logger log = LoggerFactory.getLogger(DockerSandbox.class);

    private static final Set<String> FORBIDDEN_ROOT_USERS = Set.of("0", "0:0", "root", "root:root", "0:root", "root:0");
    private static final Set<String> FORBIDDEN_HOST_MOUNTS = Set.of(
            "/", "/etc", "/var", "/var/run", "/var/run/docker.sock", "/proc", "/sys", "/root", "/boot",
            "c:\\", "c:/", "c:\\windows", "c:/windows", "c:\\system32", "c:/system32"
    );

    private final CommandSecurityGuard securityGuard;
    private final EnvironmentSanitizer environmentSanitizer;
    private final ConcurrentHashMap<String, Process> activeProcesses = new ConcurrentHashMap<>();
    private final ExecutorService ioExecutor = new ThreadPoolExecutor(
            2,
            16,
            60L,
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(128),
            r -> {
                Thread t = new Thread(r);
                t.setDaemon(true);
                t.setName("docker-sandbox-io-" + t.getId());
                return t;
            },
            new ThreadPoolExecutor.CallerRunsPolicy()
    );

    private Boolean dockerAvailableCache = null;
    private boolean productionProfile = false;
    private boolean dockerWorkerEnabled = false;
    private String remoteDockerHost = null;
    private String dockerContext = null;

    public DockerSandbox(CommandSecurityGuard securityGuard, EnvironmentSanitizer environmentSanitizer) {
        this.securityGuard = securityGuard != null ? securityGuard : new CommandSecurityGuard();
        this.environmentSanitizer = environmentSanitizer != null ? environmentSanitizer : new EnvironmentSanitizer();
    }

    public DockerSandbox() {
        this(new CommandSecurityGuard(), new EnvironmentSanitizer());
    }

    public void configureIsolationPolicy(boolean productionProfile, boolean dockerWorkerEnabled,
                                         String remoteDockerHost, String dockerContext) {
        this.productionProfile = productionProfile;
        this.dockerWorkerEnabled = dockerWorkerEnabled;
        this.remoteDockerHost = remoteDockerHost;
        this.dockerContext = dockerContext;
        this.dockerAvailableCache = null;
    }

    public boolean isRemoteWorkerConfigured() {
        return dockerWorkerEnabled
                || (remoteDockerHost != null && !remoteDockerHost.isBlank())
                || (dockerContext != null && !dockerContext.isBlank());
    }

    /**
     * Validates that production environments do not implicitly assume raw local docker.sock access
     * inside the main backend container.
     */
    public void validateIsolationPolicy() {
        if (productionProfile && !isRemoteWorkerConfigured()) {
            throw new BusinessException(ErrorCode.SANDBOX_EXECUTION_FAILED,
                    "Production Docker sandbox requires an explicit isolated Docker Worker configuration " +
                    "(AGENTHUB_DOCKER_WORKER_ENABLED=true with AGENTHUB_DOCKER_HOST or AGENTHUB_DOCKER_CONTEXT). " +
                    "Implicit local Docker CLI/daemon assumption inside backend container is forbidden.");
        }
        if (remoteDockerHost != null && !remoteDockerHost.isBlank()) {
            String normalizedHost = remoteDockerHost.trim().toLowerCase(Locale.ROOT);
            if (normalizedHost.contains("/var/run/docker.sock") && productionProfile) {
                throw new BusinessException(ErrorCode.SANDBOX_EXECUTION_FAILED,
                        "Mounting raw host /var/run/docker.sock in production is forbidden; use an isolated Docker Worker or restricted TCP/SSH context.");
            }
        }
    }

    @Override
    public String getProviderType() {
        return "DOCKER";
    }

    @Override
    public synchronized boolean isAvailable() {
        if (productionProfile && !isRemoteWorkerConfigured()) {
            return false;
        }
        if (dockerAvailableCache != null) {
            return dockerAvailableCache;
        }
        try {
            List<String> checkCmd = new ArrayList<>();
            checkCmd.add("docker");
            appendRemoteTargetFlags(checkCmd);
            checkCmd.add("--version");
            Process process = new ProcessBuilder(checkCmd).start();
            boolean finished = process.waitFor(3, TimeUnit.SECONDS);
            dockerAvailableCache = finished && process.exitValue() == 0;
        } catch (Exception e) {
            dockerAvailableCache = false;
        }
        return dockerAvailableCache;
    }

    private void appendRemoteTargetFlags(List<String> cmd) {
        if (remoteDockerHost != null && !remoteDockerHost.isBlank()) {
            cmd.add("-H");
            cmd.add(remoteDockerHost.trim());
        } else if (dockerContext != null && !dockerContext.isBlank()) {
            cmd.add("--context");
            cmd.add(dockerContext.trim());
        }
    }

    private void validateContainerUserAndMount(String user, Path workingDir) {
        if (user != null && FORBIDDEN_ROOT_USERS.contains(user.trim().toLowerCase(Locale.ROOT))) {
            throw new BusinessException(ErrorCode.SANDBOX_COMMAND_BLOCKED,
                    "Container execution as privileged root user [" + user + "] is strictly forbidden in sandbox");
        }
        if (workingDir != null) {
            String rawPath = workingDir.toString().replace('\\', '/').trim();
            if (rawPath.contains("..")) {
                throw new BusinessException(ErrorCode.WORKSPACE_TRAVERSAL_DENIED,
                        "Workspace volume mount path must not contain parent traversal segments: " + workingDir);
            }
            String normalizedRaw = rawPath.toLowerCase(Locale.ROOT);
            if (normalizedRaw.endsWith("/") && normalizedRaw.length() > 1) {
                normalizedRaw = normalizedRaw.substring(0, normalizedRaw.length() - 1);
            }
            Path absPath = workingDir.toAbsolutePath().normalize();
            String normalizedAbs = absPath.toString().replace('\\', '/').toLowerCase(Locale.ROOT);
            if (normalizedAbs.endsWith("/") && normalizedAbs.length() > 1) {
                normalizedAbs = normalizedAbs.substring(0, normalizedAbs.length() - 1);
            }
            boolean isDriveOrFsRoot = absPath.getNameCount() == 0 || normalizedAbs.matches("^[a-z]:$");
            if (isDriveOrFsRoot
                    || FORBIDDEN_HOST_MOUNTS.contains(normalizedRaw)
                    || FORBIDDEN_HOST_MOUNTS.contains(normalizedAbs)
                    || normalizedRaw.contains("docker.sock")
                    || normalizedAbs.contains("docker.sock")) {
                throw new BusinessException(ErrorCode.WORKSPACE_TRAVERSAL_DENIED,
                        "Mounting sensitive host directory [" + workingDir + "] into sandbox container is forbidden");
            }
        }
    }

    /**
     * Builds the complete list of Docker command-line arguments according to security isolation rules.
     */
    public List<String> buildDockerArgs(SandboxExecutionRequest request, String image) {
        return buildDockerArgsInternal(request, image, false, null, null);
    }

    /**
     * Builds Docker command-line arguments for a detached long-running service container with port mapping.
     */
    public List<String> buildDockerServiceArgs(SandboxExecutionRequest request, String image, int hostPort, int containerPort) {
        return buildDockerArgsInternal(request, image, true, hostPort, containerPort);
    }

    private List<String> buildDockerArgsInternal(SandboxExecutionRequest request, String image,
                                                 boolean detachedService, Integer hostPort, Integer containerPort) {
        String user = request.getContainerUser() != null ? request.getContainerUser() : "1000:1000";
        Path workingDir = request.getWorkingDirectory();
        validateContainerUserAndMount(user, workingDir);

        List<String> cmd = new ArrayList<>();
        cmd.add("docker");
        appendRemoteTargetFlags(cmd);
        cmd.add("run");
        if (detachedService) {
            cmd.add("-d");
        } else {
            cmd.add("--rm");
        }

        String containerName = "agenthub-" + (request.getExecutionId() != null ? request.getExecutionId() : UUID.randomUUID());
        cmd.add("--name");
        cmd.add(containerName);
        cmd.add("--label");
        cmd.add("agenthub.managed=true");

        // 1. Non-root unprivileged execution & capability drop
        cmd.add("--user");
        cmd.add(user);
        cmd.add("--security-opt=no-new-privileges:true");
        cmd.add("--cap-drop=ALL");
        cmd.add("--pids-limit");
        cmd.add("64");

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

        // 4. Network restriction or explicit port binding
        if (request.isNetworkDisabled()) {
            cmd.add("--net=none");
        } else if (hostPort != null && hostPort > 0 && containerPort != null && containerPort > 0) {
            cmd.add("-p");
            cmd.add("127.0.0.1:" + hostPort + ":" + containerPort);
        }

        // 5. Strictly mount only the controlled workspace directory
        if (workingDir != null) {
            String hostPath = workingDir.toAbsolutePath().normalize().toString();
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
        if (request.getCommand() != null && !request.getCommand().isBlank()) {
            cmd.add(request.getCommand());
            if (request.getArgs() != null) {
                cmd.addAll(request.getArgs());
            }
        }

        return cmd;
    }

    @Override
    public SandboxExecutionResult execute(SandboxExecutionRequest request) {
        long startTime = System.currentTimeMillis();
        String executionId = request.getExecutionId();

        // 1. Validate security firewall & isolation policy
        securityGuard.validateCommand(request.getCommand(), request.getArgs());
        validateIsolationPolicy();

        if (!isAvailable()) {
            return SandboxExecutionResult.failure(executionId, 1, "", "",
                    System.currentTimeMillis() - startTime,
                    "Docker daemon or isolated Docker Worker is not available on this host");
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
        if (executionId == null) {
            return;
        }
        Process proc = activeProcesses.remove(executionId);
        if (proc != null) {
            try {
                proc.toHandle().descendants().forEach(ProcessHandle::destroyForcibly);
                proc.destroyForcibly();
            } catch (Exception e) {
                log.warn("Error terminating docker sandbox process: {}", e.getMessage());
            }
        }
        // Proactively cleanup docker container if created
        try {
            List<String> rmCmd = new ArrayList<>();
            rmCmd.add("docker");
            appendRemoteTargetFlags(rmCmd);
            rmCmd.add("rm");
            rmCmd.add("-f");
            String containerName = executionId.startsWith("agenthub-") ? executionId : "agenthub-" + executionId;
            rmCmd.add(containerName);
            new ProcessBuilder(rmCmd)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start()
                    .waitFor(2, TimeUnit.SECONDS);
        } catch (Exception ignored) {}
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
                    synchronized (buffer) {
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
                }
            } catch (Exception ignored) {
            } finally {
                try {
                    inputStream.close();
                } catch (Exception ignored) {}
            }
        }

        public String getOutput() {
            String out;
            synchronized (buffer) {
                out = buffer.toString(StandardCharsets.UTF_8);
            }
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
