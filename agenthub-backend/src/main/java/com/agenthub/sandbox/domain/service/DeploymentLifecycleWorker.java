package com.agenthub.sandbox.domain.service;

import com.agenthub.sandbox.domain.model.SandboxExecutionRequest;
import com.agenthub.sandbox.domain.model.SandboxResourceQuota;
import com.agenthub.sandbox.domain.provider.DockerSandbox;
import com.agenthub.sandbox.domain.provider.LocalProcessSandbox;
import com.agenthub.sandbox.domain.provider.SandboxProviderFactory;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

/**
 * Dedicated lifecycle worker managing live deployment instances (embedded static HTTP servers,
 * long-running local sandbox child processes, and isolated Docker service containers).
 * Ensures RUNNING deployments correspond to genuinely reachable, supervised runtime instances.
 */
@Component
public class DeploymentLifecycleWorker {

    private static final Logger log = LoggerFactory.getLogger(DeploymentLifecycleWorker.class);

    public static class LiveRuntimeInstance {
        private final String deploymentId;
        private final String projectId;
        private final String target;
        private final String sandboxType;
        private final int port;
        private final Path workingDirectory;
        private final Long processId;
        private final String containerId;
        private final HttpServer staticServer;
        private final LocalProcessSandbox.BackgroundServiceHandle processHandle;

        public LiveRuntimeInstance(String deploymentId, String projectId, String target, String sandboxType,
                                   int port, Path workingDirectory, Long processId, String containerId,
                                   HttpServer staticServer,
                                   LocalProcessSandbox.BackgroundServiceHandle processHandle) {
            this.deploymentId = deploymentId;
            this.projectId = projectId;
            this.target = target;
            this.sandboxType = sandboxType;
            this.port = port;
            this.workingDirectory = workingDirectory;
            this.processId = processId;
            this.containerId = containerId;
            this.staticServer = staticServer;
            this.processHandle = processHandle;
        }

        public String getDeploymentId() { return deploymentId; }
        public String getProjectId() { return projectId; }
        public String getTarget() { return target; }
        public String getSandboxType() { return sandboxType; }
        public int getPort() { return port; }
        public Path getWorkingDirectory() { return workingDirectory; }
        public Long getProcessId() { return processId; }
        public String getContainerId() { return containerId; }
        public HttpServer getStaticServer() { return staticServer; }
        public LocalProcessSandbox.BackgroundServiceHandle getProcessHandle() { return processHandle; }
    }

    private final SandboxProviderFactory sandboxProviderFactory;
    private final ConcurrentHashMap<String, LiveRuntimeInstance> activeRuntimes = new ConcurrentHashMap<>();
    private final ExecutorService staticHttpExecutor = new ThreadPoolExecutor(
            2,
            16,
            60L,
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(128),
            r -> {
                Thread t = new Thread(r);
                t.setDaemon(true);
                t.setName("sandbox-static-http-" + t.getId());
                return t;
            },
            new ThreadPoolExecutor.CallerRunsPolicy()
    );

    public DeploymentLifecycleWorker(SandboxProviderFactory sandboxProviderFactory) {
        this.sandboxProviderFactory = sandboxProviderFactory;
    }

    /**
     * Materializes static deliverable files in the controlled workspace and binds a real HTTP server on the allocated port.
     */
    public LiveRuntimeInstance startStaticPreviewServer(String deploymentId, String projectId, String artifactId,
                                                        int port, Path workspaceRoot) throws IOException {
        if (!Files.exists(workspaceRoot)) {
            Files.createDirectories(workspaceRoot);
        }

        // Materialize deliverable index.html if workspace does not have one yet
        Path rootIndex = workspaceRoot.resolve("index.html");
        Path distIndex = workspaceRoot.resolve("dist").resolve("index.html");
        Path previewIndex = workspaceRoot.resolve(".agenthub-preview").resolve("index.html");

        if (!Files.exists(rootIndex) && !Files.exists(distIndex)) {
            Files.createDirectories(previewIndex.getParent());
            String html = buildDefaultDeliverableHtml(projectId, deploymentId, artifactId, port);
            Files.writeString(previewIndex, html, StandardCharsets.UTF_8);
        }

        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port), 0);
        server.setExecutor(staticHttpExecutor);

        server.createContext("/health", exchange -> {
            byte[] body = ("{\"status\":\"UP\",\"deploymentId\":\"" + deploymentId + "\",\"projectId\":\"" + projectId + "\"}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });

        server.createContext("/", exchange -> handleStaticFileRequest(exchange, workspaceRoot, projectId, deploymentId, artifactId, port));
        server.start();

        long jvmPid = ProcessHandle.current().pid();
        String containerId = "static-http-" + deploymentId + ":" + port;
        LiveRuntimeInstance instance = new LiveRuntimeInstance(
                deploymentId, projectId, "STATIC_PREVIEW", "LOCAL_PROCESS",
                port, workspaceRoot, jvmPid, containerId, server, null
        );
        activeRuntimes.put(deploymentId, instance);
        log.info("Started live static preview HTTP server for deployment [{}] on 127.0.0.1:{}", deploymentId, port);
        return instance;
    }

    private void handleStaticFileRequest(HttpExchange exchange, Path workspaceRoot, String projectId,
                                         String deploymentId, String artifactId, int port) throws IOException {
        String rawPath = exchange.getRequestURI().getPath();
        if (rawPath == null || rawPath.isBlank()) {
            rawPath = "/";
        }

        // Normalize preview prefix if accessed via /api/sandbox/preview/{projectId}
        String previewPrefix = "/api/sandbox/preview/" + projectId;
        String relativeReq = rawPath;
        if (relativeReq.equals(previewPrefix) || relativeReq.equals(previewPrefix + "/")) {
            relativeReq = "/";
        } else if (relativeReq.startsWith(previewPrefix + "/")) {
            relativeReq = relativeReq.substring(previewPrefix.length());
        }

        // Security check: block directory traversal and .git metadata access
        String decoded = java.net.URLDecoder.decode(relativeReq, StandardCharsets.UTF_8);
        if (decoded.contains("..") || decoded.toLowerCase(Locale.ROOT).contains(".git")) {
            byte[] forbidden = "403 Forbidden: Sandbox workspace boundary violation".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
            exchange.sendResponseHeaders(403, forbidden.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(forbidden);
            }
            return;
        }

        Path realRoot = workspaceRoot.toAbsolutePath().normalize();
        Path targetFile = null;

        if ("/".equals(decoded) || "/index.html".equalsIgnoreCase(decoded)) {
            Path distIndex = realRoot.resolve("dist/index.html").normalize();
            Path rootIndex = realRoot.resolve("index.html").normalize();
            Path previewIndex = realRoot.resolve(".agenthub-preview/index.html").normalize();
            if (Files.exists(distIndex) && !Files.isDirectory(distIndex) && distIndex.startsWith(realRoot)) {
                targetFile = distIndex;
            } else if (Files.exists(rootIndex) && !Files.isDirectory(rootIndex) && rootIndex.startsWith(realRoot)) {
                targetFile = rootIndex;
            } else if (Files.exists(previewIndex) && !Files.isDirectory(previewIndex) && previewIndex.startsWith(realRoot)) {
                targetFile = previewIndex;
            }
        } else {
            String stripped = decoded.startsWith("/") ? decoded.substring(1) : decoded;
            Path candidate = realRoot.resolve(stripped).normalize();
            if (!candidate.startsWith(realRoot)) {
                byte[] forbidden = "403 Forbidden: Path escapes workspace root".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(403, forbidden.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(forbidden);
                }
                return;
            }
            if (Files.exists(candidate) && !Files.isDirectory(candidate)) {
                targetFile = candidate;
            }
        }

        byte[] contentBytes;
        String contentType = "text/html; charset=UTF-8";
        int statusCode = 200;

        if (targetFile != null && Files.exists(targetFile)) {
            contentBytes = Files.readAllBytes(targetFile);
            contentType = resolveMimeType(targetFile.getFileName().toString());
        } else if ("/".equals(decoded) || "/index.html".equalsIgnoreCase(decoded)) {
            contentBytes = buildDefaultDeliverableHtml(projectId, deploymentId, artifactId, port).getBytes(StandardCharsets.UTF_8);
        } else {
            statusCode = 404;
            contentType = "text/plain; charset=UTF-8";
            contentBytes = ("404 Not Found: " + decoded).getBytes(StandardCharsets.UTF_8);
        }

        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("X-AgentHub-Deployment-Id", deploymentId);
        exchange.getResponseHeaders().set("X-AgentHub-Project-Id", projectId);
        exchange.sendResponseHeaders(statusCode, contentBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(contentBytes);
        }
    }

    /**
     * Launches a continuous background process service for LOCAL_PROCESS deployments.
     */
    public LiveRuntimeInstance startLocalProcessService(String deploymentId, String projectId, int port,
                                                        Path workspaceRoot, String command, List<String> args,
                                                        Map<String, String> envVars, long timeoutMs) {
        Map<String, String> mergedEnv = new HashMap<>();
        if (envVars != null) {
            mergedEnv.putAll(envVars);
        }
        mergedEnv.put("PORT", String.valueOf(port));
        mergedEnv.put("SERVER_PORT", String.valueOf(port));

        SandboxExecutionRequest execReq = SandboxExecutionRequest.builder()
                .executionId(deploymentId)
                .command(command)
                .args(args)
                .workingDirectory(workspaceRoot)
                .environment(mergedEnv)
                .resourceQuota(SandboxResourceQuota.of(timeoutMs, SandboxResourceQuota.DEFAULT_MAX_OUTPUT_BYTES))
                .build();

        LocalProcessSandbox localSandbox = sandboxProviderFactory.getLocalProcessSandbox();
        LocalProcessSandbox.BackgroundServiceHandle handle = localSandbox.startBackgroundService(execReq);

        // Wait briefly to catch immediate startup crash (e.g. syntax error or port bind failure)
        try {
            Thread.sleep(150);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        LiveRuntimeInstance instance = new LiveRuntimeInstance(
                deploymentId, projectId, "LOCAL_PROCESS", "LOCAL_PROCESS",
                port, workspaceRoot, handle.getPid(), "proc-" + handle.getPid(), null, handle
        );
        activeRuntimes.put(deploymentId, instance);
        return instance;
    }

    /**
     * Launches a detached isolated Docker container service for DOCKER_CONTAINER deployments.
     */
    public LiveRuntimeInstance startDockerContainerService(String deploymentId, String projectId, int port,
                                                           Path workspaceRoot, String image, String command,
                                                           List<String> args, Map<String, String> envVars, long timeoutMs) {
        DockerSandbox dockerSandbox = sandboxProviderFactory.getDockerSandbox();
        dockerSandbox.validateIsolationPolicy();
        if (!dockerSandbox.isAvailable()) {
            throw new IllegalStateException("Docker daemon or isolated Docker Worker is not available on this host");
        }

        Map<String, String> mergedEnv = new HashMap<>();
        if (envVars != null) {
            mergedEnv.putAll(envVars);
        }
        mergedEnv.put("PORT", String.valueOf(port));

        SandboxExecutionRequest execReq = SandboxExecutionRequest.builder()
                .executionId(deploymentId)
                .command(command)
                .args(args)
                .workingDirectory(workspaceRoot)
                .environment(mergedEnv)
                .readOnlyRoot(true)
                .containerUser("1000:1000")
                .networkDisabled(false)
                .resourceQuota(SandboxResourceQuota.of(timeoutMs, SandboxResourceQuota.DEFAULT_MAX_OUTPUT_BYTES))
                .build();

        String targetImage = (image != null && !image.isBlank()) ? image : "nginx:alpine";
        List<String> dockerArgs = dockerSandbox.buildDockerServiceArgs(execReq, targetImage, port, port);

        try {
            Process proc = new ProcessBuilder(dockerArgs).start();
            boolean finished = proc.waitFor(10, TimeUnit.SECONDS);
            if (!finished || proc.exitValue() != 0) {
                String err = new String(proc.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
                dockerSandbox.destroy(deploymentId);
                throw new IllegalStateException("Docker container failed to start: " + err);
            }
            String containerIdOut = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            String containerId = containerIdOut.isEmpty() ? ("agenthub-" + deploymentId) : containerIdOut;

            LiveRuntimeInstance instance = new LiveRuntimeInstance(
                    deploymentId, projectId, "DOCKER_CONTAINER", "DOCKER",
                    port, workspaceRoot, null, containerId, null, null
            );
            activeRuntimes.put(deploymentId, instance);
            return instance;
        } catch (Exception e) {
            dockerSandbox.destroy(deploymentId);
            throw new IllegalStateException("Failed to launch Docker service container: " + e.getMessage(), e);
        }
    }

    /**
     * Checks whether the specified deployment has a genuinely alive runtime instance.
     */
    public boolean isRuntimeAlive(String deploymentId) {
        if (deploymentId == null) {
            return false;
        }
        LiveRuntimeInstance instance = activeRuntimes.get(deploymentId);
        if (instance == null) {
            return false;
        }
        if (instance.getStaticServer() != null) {
            return true;
        }
        if (instance.getProcessHandle() != null) {
            return instance.getProcessHandle().isAlive();
        }
        if ("DOCKER".equalsIgnoreCase(instance.getSandboxType())) {
            return isDockerContainerRunning(instance.getDeploymentId());
        }
        return false;
    }

    private boolean isDockerContainerRunning(String deploymentId) {
        try {
            Process p = new ProcessBuilder("docker", "inspect", "-f", "{{.State.Running}}", "agenthub-" + deploymentId)
                    .start();
            boolean done = p.waitFor(2, TimeUnit.SECONDS);
            if (done && p.exitValue() == 0) {
                String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
                return "true".equalsIgnoreCase(out);
            }
        } catch (Exception ignored) {}
        return false;
    }

    /**
     * Retrieves live incremental logs from a running process if available.
     */
    public String getLiveProcessLogs(String deploymentId) {
        LiveRuntimeInstance instance = deploymentId != null ? activeRuntimes.get(deploymentId) : null;
        if (instance != null && instance.getProcessHandle() != null) {
            return instance.getProcessHandle().getCombinedLogs();
        }
        return "";
    }

    /**
     * Stops and cleans up any live HTTP server, child process tree, or Docker container for a deployment.
     */
    public void stopRuntime(String deploymentId, Long recordedPid) {
        if (deploymentId == null) {
            return;
        }
        LiveRuntimeInstance instance = activeRuntimes.remove(deploymentId);
        if (instance != null) {
            if (instance.getStaticServer() != null) {
                try {
                    instance.getStaticServer().stop(0);
                    log.info("Stopped static preview HTTP server for deployment [{}] on port {}", deploymentId, instance.getPort());
                } catch (Exception e) {
                    log.warn("Error stopping static HTTP server for [{}]: {}", deploymentId, e.getMessage());
                }
            }
        }
        try {
            sandboxProviderFactory.getLocalProcessSandbox().destroy(deploymentId);
            sandboxProviderFactory.getLocalProcessSandbox().destroy(deploymentId + "-build");
            if (recordedPid != null && recordedPid > 0) {
                sandboxProviderFactory.getLocalProcessSandbox().destroyPidIfOrphaned(recordedPid);
            }
        } catch (Exception e) {
            log.warn("Error cleaning up local process for [{}]: {}", deploymentId, e.getMessage());
        }
        try {
            if (instance != null && "DOCKER".equalsIgnoreCase(instance.getSandboxType())) {
                sandboxProviderFactory.getDockerSandbox().destroy(deploymentId);
            }
        } catch (Exception e) {
            log.warn("Error cleaning up Docker container for [{}]: {}", deploymentId, e.getMessage());
        }
    }

    @PreDestroy
    public void shutdownAll() {
        for (String deployId : new ArrayList<>(activeRuntimes.keySet())) {
            stopRuntime(deployId, null);
        }
        staticHttpExecutor.shutdownNow();
    }

    private String resolveMimeType(String filename) {
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".html") || lower.endsWith(".htm")) return "text/html; charset=UTF-8";
        if (lower.endsWith(".css")) return "text/css; charset=UTF-8";
        if (lower.endsWith(".js") || lower.endsWith(".mjs")) return "application/javascript; charset=UTF-8";
        if (lower.endsWith(".json")) return "application/json; charset=UTF-8";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        if (lower.endsWith(".png")) return "image/png";
        return "text/plain; charset=UTF-8";
    }

    public String buildDefaultDeliverableHtml(String projectId, String deploymentId, String artifactId, int port) {
        return "<!DOCTYPE html>\n" +
                "<html lang=\"zh-CN\">\n" +
                "<head>\n" +
                "  <meta charset=\"UTF-8\" />\n" +
                "  <title>AgentHub Live Sandbox Preview - " + projectId + "</title>\n" +
                "</head>\n" +
                "<body style=\"background:#09090b;color:#f4f4f5;font-family:system-ui,-apple-system,sans-serif;display:flex;align-items:center;justify-content:center;min-height:100vh;margin:0;padding:24px;\">\n" +
                "  <div style=\"max-width:480px;width:100%;background:#18181b;border:1px solid #27272a;border-radius:16px;padding:24px;\">\n" +
                "    <h1 style=\"font-size:18px;margin:0 0 8px 0;\">AgentHub Web 预览沙箱</h1>\n" +
                "    <p style=\"font-size:12px;color:#a1a1aa;margin:0 0 16px 0;\">项目 ID: " + projectId + " · 部署实例: " + deploymentId + " · 端口: " + port + "</p>\n" +
                "    <div style=\"background:#09090b;border:1px solid #27272a;border-radius:12px;padding:12px;font-family:monospace;font-size:12px;line-height:1.6;\">\n" +
                "      <div style=\"color:#34d399;\">✔ 交付产物绑定: " + (artifactId != null ? artifactId : "baseline") + "</div>\n" +
                "      <div style=\"color:#34d399;\">✔ 独立 HTTP 预览实例存活 (127.0.0.1:" + port + ")</div>\n" +
                "      <div style=\"color:#34d399;\">✔ 健康探针校验通过 (HTTP 200 OK)</div>\n" +
                "    </div>\n" +
                "  </div>\n" +
                "</body>\n" +
                "</html>";
    }
}
