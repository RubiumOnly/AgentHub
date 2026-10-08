package com.agenthub;

import com.agenthub.execution.application.ExecutionApplication;
import com.agenthub.execution.dto.RunEventView;
import com.agenthub.execution.dto.StartRunCommand;
import com.agenthub.execution.dto.WorkflowRunView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ExecutionEnginePersistenceAndReplayTest {

    @Autowired
    private ExecutionApplication executionApplication;

    @Test
    @DisplayName("测试 WorkflowRun 持久化与幂等键防御：重复提交同一幂等键不产生新 Run")
    void shouldPersistRunAndEnforceIdempotency() {
        String idempotencyKey = "idemp-" + UUID.randomUUID();
        StartRunCommand cmd1 = new StartRunCommand("proj-default", "def-default", idempotencyKey);
        WorkflowRunView run1 = executionApplication.startRun(cmd1);

        assertThat(run1.getId()).isNotEmpty();
        assertThat(run1.getStatus()).isEqualTo("RUNNING");
        assertThat(run1.getIdempotencyKey()).isEqualTo(idempotencyKey);

        // Second submission with exact same idempotency key
        StartRunCommand cmd2 = new StartRunCommand("proj-default", "def-default", idempotencyKey);
        WorkflowRunView run2 = executionApplication.startRun(cmd2);

        // Must return the exact same run instance
        assertThat(run2.getId()).isEqualTo(run1.getId());
    }

    @Test
    @DisplayName("测试持久化事件序列流与基于游标的断点回放能力")
    void shouldPersistRunEventsAndSupportReplayCursor() {
        WorkflowRunView run = executionApplication.startRun(new StartRunCommand("proj-default", "def-default", null));
        String runId = run.getId();

        // The startRun method produces event sequence 1 (RUN_STARTED)
        executionApplication.appendEvent(runId, "NODE_DISPATCHED", "{\"nodeId\":\"node-backend\"}");
        executionApplication.appendEvent(runId, "CODE_GENERATED", "{\"file\":\"UserService.java\"}");
        executionApplication.appendEvent(runId, "TEST_PASSED", "{\"passed\":true}");

        List<RunEventView> allEvents = executionApplication.listEvents(runId, null);
        assertThat(allEvents).hasSize(4);
        assertThat(allEvents.get(0).getSequenceNum()).isEqualTo(1L);
        assertThat(allEvents.get(1).getSequenceNum()).isEqualTo(2L);
        assertThat(allEvents.get(2).getSequenceNum()).isEqualTo(3L);
        assertThat(allEvents.get(3).getSequenceNum()).isEqualTo(4L);

        // Replay events strictly after sequence 2 (simulating reconnect with Last-Event-ID = 2)
        List<RunEventView> resumedEvents = executionApplication.listEvents(runId, 2L);
        assertThat(resumedEvents).hasSize(2);
        assertThat(resumedEvents.get(0).getSequenceNum()).isEqualTo(3L);
        assertThat(resumedEvents.get(0).getEventType()).isEqualTo("CODE_GENERATED");
        assertThat(resumedEvents.get(1).getSequenceNum()).isEqualTo(4L);
        assertThat(resumedEvents.get(1).getEventType()).isEqualTo("TEST_PASSED");
    }
}
