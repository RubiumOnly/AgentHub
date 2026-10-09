package com.agenthub;

import com.agenthub.sandbox.domain.model.SandboxExecutionRequest;
import com.agenthub.sandbox.domain.model.SandboxExecutionResult;
import com.agenthub.sandbox.domain.model.SandboxResourceQuota;
import com.agenthub.sandbox.domain.provider.DockerSandbox;
import com.agenthub.sandbox.domain.provider.LocalProcessSandbox;
import com.agenthub.sandbox.domain.provider.SandboxProvider;
import com.agenthub.sandbox.domain.provider.SandboxProviderFactory;
import com.agenthub.sandbox.domain.security.CommandSecurityGuard;
import com.agenthub.sandbox.domain.security.EnvironmentSanitizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SandboxProviderAndExecutionTest {

    private LocalProcessSandbox localSandbox;
    private DockerSandbox dockerSandbox;
    private SandboxProviderFactory providerFactory;

    @BeforeEach
    void setUp() {
        CommandSecurityGuard guard = new CommandSecurityGuard();
        EnvironmentSanitizer sanitizer = new EnvironmentSanitizer();
        localSandbox = new LocalProcessSandbox(guard, sanitizer);
        dockerSandbox = new DockerSandbox(guard, sanitizer);
        providerFactory = new SandboxProviderFactory();
    }

    @Test
    @DisplayName("进程沙箱 1：执行合规命令输出标准结果与 exitCode 0")
    void shouldExecuteBenignCommandInLocalSandbox(@TempDir Path tempDir) {
        SandboxExecutionRequest request = SandboxExecutionRequest.builder()
                .executionId("exec-benign-1")
                .command(isWindows() ? "cmd.exe" : "echo")
                .args(isWindows() ? List.of("/c", "echo", "sandbox-ok") : List.of("sandbox-ok"))
                .workingDirectory(tempDir)
                .resourceQuota(SandboxResourceQuota.of(5000L, 1024 * 1024))
                .build();

        SandboxExecutionResult result = localSandbox.execute(request);

        assertThat(result.getExecutionId()).isEqualTo("exec-benign-1");
        assertThat(result.getExitCode()).isEqualTo(0);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.isTimedOut()).isFalse();
        assertThat(result.getStdout()).contains("sandbox-ok");
    }

    @Test
    @DisplayName("看门狗超时监控 2：单命令执行超时被强平 (Timeout Kill) 并标记 TIMED_OUT 与 exitCode 137")
    void shouldKillProcessOnWatchdogTimeout(@TempDir Path tempDir) {
        // Run a command that takes 5 seconds, with a strict 400ms watchdog timeout quota
        SandboxExecutionRequest request = SandboxExecutionRequest.builder()
                .executionId("exec-timeout-test")
                .command(isWindows() ? "cmd.exe" : "sleep")
                .args(isWindows() ? List.of("/c", "powershell -NoProfile -Command \"Start-Sleep -Seconds 5\"") : List.of("5"))
                .workingDirectory(tempDir)
                .resourceQuota(SandboxResourceQuota.of(400L, 1024 * 1024))
                .build();

        SandboxExecutionResult result = localSandbox.execute(request);

        assertThat(result.isTimedOut()).isTrue();
        assertThat(result.getExitCode()).isEqualTo(137);
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("timed out by sandbox watchdog");
    }

    @Test
    @DisplayName("资源配额防撑爆 3：超大输出自动截断 (Output Buffer Truncation)，防止海量标准输出撑爆内存")
    void shouldTruncateOutputWhenExceedingMaxBufferLimit(@TempDir Path tempDir) {
        // Set maximum buffer quota to 200 bytes
        SandboxResourceQuota tightQuota = SandboxResourceQuota.of(5000L, 200);

        // Generate output much larger than 200 bytes
        String script = "1..20 | ForEach-Object { Write-Output 'This is a long repetitive output line from sandbox' }";
        SandboxExecutionRequest request = SandboxExecutionRequest.builder()
                .executionId("exec-trunc-test")
                .command(isWindows() ? "cmd.exe" : "sh")
                .args(isWindows() ? List.of("/c", "powershell -NoProfile -Command \"" + script + "\"")
                        : List.of("-c", "for i in $(seq 1 30); do echo 'This is a long repetitive output line from sandbox'; done"))
                .workingDirectory(tempDir)
                .resourceQuota(tightQuota)
                .build();

        SandboxExecutionResult result = localSandbox.execute(request);

        assertThat(result.isTruncated()).isTrue();
        assertThat(result.getStdout()).contains("[SANDBOX WARNING: Output truncated due to exceeding limit of 200 bytes]");
    }

    @Test
    @DisplayName("Docker沙箱 4：精确构建非特权用户、只读根文件系统与受控挂载参数")
    void shouldBuildSecureDockerArguments(@TempDir Path tempDir) {
        SandboxExecutionRequest request = SandboxExecutionRequest.builder()
                .executionId("exec-docker-test")
                .command("npm")
                .args(List.of("run", "build"))
                .workingDirectory(tempDir)
                .containerUser("1001:1001")
                .readOnlyRoot(true)
                .networkDisabled(false)
                .resourceQuota(new SandboxResourceQuota(30_000L, 1024 * 1024, 256, "1.5", 500))
                .environment(Map.of("BUILD_ENV", "prod"))
                .build();

        List<String> dockerArgs = dockerSandbox.buildDockerArgs(request, "node:20-alpine");

        // Verify non-root user
        assertThat(dockerArgs).contains("--user", "1001:1001");
        // Verify read-only rootfs and tmpfs
        assertThat(dockerArgs).contains("--read-only", "--tmpfs", "/tmp:rw,noexec,nosuid,size=64m");
        // Verify resource limits
        assertThat(dockerArgs).contains("--memory", "256m", "--cpus", "1.5");
        // Verify strictly mounting only the controlled workspace
        assertThat(dockerArgs).contains("-v");
        String expectedMount = tempDir.toAbsolutePath() + ":/workspace:rw";
        assertThat(dockerArgs).contains(expectedMount);
        assertThat(dockerArgs).contains("-w", "/workspace");
        // Verify target image and command
        assertThat(dockerArgs).contains("node:20-alpine", "npm", "run", "build");
    }

    @Test
    @DisplayName("沙箱工厂 5：正确解析 Provider 并优雅兜底")
    void shouldResolveProviderFromFactory() {
        SandboxProvider local = providerFactory.getProvider("LOCAL_PROCESS");
        assertThat(local).isNotNull();
        assertThat(local.getProviderType()).isEqualTo("LOCAL_PROCESS");

        SandboxProvider docker = providerFactory.getProvider("DOCKER");
        assertThat(docker).isNotNull();
        assertThat(docker.getProviderType()).isEqualTo("DOCKER");

        // Null or unknown falls back to default local sandbox
        SandboxProvider fallback = providerFactory.getProvider(null);
        assertThat(fallback).isNotNull();
        assertThat(fallback.getProviderType()).isEqualTo("LOCAL_PROCESS");
    }

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }
}
