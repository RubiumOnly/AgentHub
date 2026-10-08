package com.agenthub;

import com.agenthub.agent.application.TokenUsageApplication;
import com.agenthub.agent.dto.TokenUsageAuditView;
import com.agenthub.domain.agent.model.AgentExecutionRequest;
import com.agenthub.execution.domain.model.CancelToken;
import com.agenthub.runtime.port.ExecutionHandle;
import com.agenthub.runtime.port.RuntimeEventSink;
import com.agenthub.runtime.provider.ProviderDrivenAgentRuntime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ProviderDrivenAgentRuntimeIntegrationTest {

    @Autowired
    private ProviderDrivenAgentRuntime runtime;

    @Autowired
    private TokenUsageApplication tokenUsageApplication;

    @Test
    @DisplayName("端到端集成测试 ProviderDrivenAgentRuntime：流式调用、Token消耗核算与审计数据库持久化闭环")
    void shouldExecuteAgentRunEndToEndViaProviderRuntime(@TempDir Path tempDir) throws Exception {
        String runId = "run-prov-" + UUID.randomUUID().toString().substring(0, 8);
        AgentExecutionRequest req = new AgentExecutionRequest();
        req.setAgentId(runId);
        req.setPrompt("Generate a secure authentication filter in Java");
        req.setWorkspacePath(tempDir.toString());
        req.setTimeoutSeconds(30);

        List<String> streamedTokens = new CopyOnWriteArrayList<>();
        AtomicReference<String> completedOutput = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        RuntimeEventSink sink = new RuntimeEventSink() {
            @Override public void onToken(String rId, String sId, String token) { streamedTokens.add(token); }
            @Override public void onMessage(String rId, String sId, String sender, String content) {}
            @Override public void onToolCall(String rId, String sId, String toolName, String inputJson) {}
            @Override public void onFileChange(String rId, String sId, String path, String changeType) {}
            @Override public void onLog(String rId, String sId, String level, String message) {}
            @Override public void onUsage(String rId, String sId, int promptTokens, int completionTokens, double costEstimate) {}
            @Override public void onStepCompleted(String rId, String sId, String output) {
                completedOutput.set(output);
                latch.countDown();
            }
            @Override public void onStepFailed(String rId, String sId, String error, Throwable cause) {
                latch.countDown();
            }
        };

        ExecutionHandle handle = runtime.start(req, sink, new CancelToken(runId));
        assertThat(handle).isNotNull();
        assertThat(handle.getRunId()).isEqualTo(runId);

        boolean finished = latch.await(10, TimeUnit.SECONDS);
        assertThat(finished).isTrue();
        assertThat(completedOutput.get()).isNotNull();
        assertThat(streamedTokens).isNotEmpty();

        // Verify file written to workspace
        File targetFile = new File(tempDir.toFile(), "agent-output.md");
        assertThat(targetFile).exists();
        assertThat(targetFile.length()).isGreaterThan(0);

        // Verify token_usages audit record in database
        List<TokenUsageAuditView> usages = tokenUsageApplication.getRunTokenUsages(runId);
        assertThat(usages).hasSize(1);

        TokenUsageAuditView audit = usages.get(0);
        assertThat(audit.getRunId()).isEqualTo(runId);
        assertThat(audit.getProviderId()).isNotNull();
        assertThat(audit.getProviderId()).isNotEqualTo("router-selected"); // Actual provider id, not dummy
        assertThat(audit.getPromptTokens()).isGreaterThan(0);
        assertThat(audit.getCompletionTokens()).isGreaterThan(0);
        assertThat(audit.getTotalTokens()).isEqualTo(audit.getPromptTokens() + audit.getCompletionTokens());
        assertThat(audit.getStatus()).isEqualTo("SUCCESS");
    }

    @Test
    @DisplayName("测试 ProviderDrivenAgentRuntime 预先取消防御：CancelToken 触发时迅速终止并回调 onStepFailed")
    void shouldAbortExecutionWhenCancelTokenIsCancelled() throws Exception {
        String runId = "run-cancel-" + UUID.randomUUID().toString().substring(0, 8);
        AgentExecutionRequest req = new AgentExecutionRequest();
        req.setAgentId(runId);
        req.setPrompt("Cancel immediately test");

        CancelToken cancelToken = new CancelToken(runId);
        cancelToken.cancel("User pre-emptively aborted step");

        AtomicBoolean failedCalled = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(1);

        RuntimeEventSink sink = new RuntimeEventSink() {
            @Override public void onToken(String rId, String sId, String token) {}
            @Override public void onMessage(String rId, String sId, String sender, String content) {}
            @Override public void onToolCall(String rId, String sId, String toolName, String inputJson) {}
            @Override public void onFileChange(String rId, String sId, String path, String changeType) {}
            @Override public void onLog(String rId, String sId, String level, String message) {}
            @Override public void onUsage(String rId, String sId, int p, int c, double cost) {}
            @Override public void onStepCompleted(String rId, String sId, String output) { latch.countDown(); }
            @Override public void onStepFailed(String rId, String sId, String error, Throwable cause) {
                failedCalled.set(true);
                latch.countDown();
            }
        };

        ExecutionHandle handle = runtime.start(req, sink, cancelToken);
        runtime.cancel(handle);

        boolean finished = latch.await(5, TimeUnit.SECONDS);
        assertThat(finished).isTrue();
        assertThat(failedCalled.get()).isTrue();
    }
}
