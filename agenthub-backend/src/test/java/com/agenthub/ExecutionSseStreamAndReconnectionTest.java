package com.agenthub;

import com.agenthub.execution.application.ExecutionApplication;
import com.agenthub.execution.dto.RunEventView;
import com.agenthub.execution.dto.StartRunCommand;
import com.agenthub.execution.dto.WorkflowRunView;
import com.agenthub.execution.service.RunEventBroadcaster;
import com.agenthub.shared.context.RequestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ExecutionSseStreamAndReconnectionTest {

    @Autowired
    private ExecutionApplication executionApplication;

    @Autowired
    private RunEventBroadcaster broadcaster;

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        RequestContext.get().setUserId("user-1");
    }

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    @Test
    @DisplayName("测试实时 SSE 输出具备严格单调递增 sequenceId，并持久化到 run_events")
    void shouldProduceMonotonicallyIncreasingSequenceEvents() {
        WorkflowRunView run = executionApplication.startRun(new StartRunCommand("proj-default", "def-default", null));
        String runId = run.getId();

        // startRun produces event sequence 1 (RUN_STARTED)
        RunEventView e2 = executionApplication.appendEvent(runId, "TOKEN", "{\"token\":\"Hello\"}");
        RunEventView e3 = executionApplication.appendEvent(runId, "TOKEN", "{\"token\":\" World\"}");
        RunEventView e4 = executionApplication.appendEvent(runId, "TOOL_CALL", "{\"tool\":\"git_commit\"}");
        RunEventView e5 = executionApplication.appendEvent(runId, "STEP_COMPLETED", "{\"node\":\"node-1\"}");

        assertThat(e2.getSequenceNum()).isEqualTo(2L);
        assertThat(e3.getSequenceNum()).isEqualTo(3L);
        assertThat(e4.getSequenceNum()).isEqualTo(4L);
        assertThat(e5.getSequenceNum()).isEqualTo(5L);

        List<RunEventView> allEvents = executionApplication.listEvents(runId, null);
        assertThat(allEvents).hasSize(5);
        for (int i = 0; i < allEvents.size(); i++) {
            assertThat(allEvents.get(i).getSequenceNum()).isEqualTo((long) (i + 1));
        }
    }

    @Test
    @DisplayName("测试 Last-Event-ID 重连与断点补发机制：客户端断线后重连，精准补齐遗漏的历史事件")
    void shouldSupportLastEventIdReplayOnReconnection() {
        WorkflowRunView run = executionApplication.startRun(new StartRunCommand("proj-default", "def-default", null));
        String runId = run.getId();

        // Append events 2, 3, 4, 5
        executionApplication.appendEvent(runId, "EVT_A", "payload-A");
        executionApplication.appendEvent(runId, "EVT_B", "payload-B");
        executionApplication.appendEvent(runId, "EVT_C", "payload-C");
        executionApplication.appendEvent(runId, "EVT_D", "payload-D");

        // 1. Reconnect with Last-Event-ID = 3 -> Must replay events 4 and 5
        List<RunEventView> missedFrom3 = executionApplication.listEvents(runId, 3L);
        assertThat(missedFrom3).hasSize(2);
        assertThat(missedFrom3.get(0).getSequenceNum()).isEqualTo(4L);
        assertThat(missedFrom3.get(0).getEventType()).isEqualTo("EVT_C");
        assertThat(missedFrom3.get(1).getSequenceNum()).isEqualTo(5L);
        assertThat(missedFrom3.get(1).getEventType()).isEqualTo("EVT_D");

        // 2. Reconnect with Last-Event-ID = 5 (caught up) -> Returns 0 missed events
        List<RunEventView> missedFrom5 = executionApplication.listEvents(runId, 5L);
        assertThat(missedFrom5).isEmpty();

        // 3. Reconnect with Last-Event-ID = 1 -> Must replay events 2, 3, 4, 5
        List<RunEventView> missedFrom1 = executionApplication.listEvents(runId, 1L);
        assertThat(missedFrom1).hasSize(4);
        assertThat(missedFrom1.get(0).getSequenceNum()).isEqualTo(2L);
        assertThat(missedFrom1.get(3).getSequenceNum()).isEqualTo(5L);
    }

    @Test
    @DisplayName("测试 SseEmitter 订阅注册、多订阅者并发推送与退订回收")
    void shouldManageSseEmittersAndBroadcastLiveEvents() {
        WorkflowRunView run = executionApplication.startRun(new StartRunCommand("proj-default", "def-default", null));
        String runId = run.getId();

        // Subscribe two concurrent clients
        SseEmitter emitter1 = executionApplication.subscribeRunStream(runId, null);
        SseEmitter emitter2 = executionApplication.subscribeRunStream(runId, null);
        assertThat(emitter1).isNotNull();
        assertThat(emitter2).isNotNull();
        assertThat(broadcaster.getActiveSubscriberCount(runId)).isEqualTo(2);

        // Broadcast a live event
        broadcaster.publishEvent(runId, "LIVE_METRIC", "{\"cpu\":45}");

        // Complete one emitter -> subscriber count decrements
        emitter1.complete();
        // Trigger a publish to let dead emitters flush
        broadcaster.publishEvent(runId, "LIVE_METRIC", "{\"cpu\":48}");
        assertThat(broadcaster.getActiveSubscriberCount(runId)).isLessThanOrEqualTo(1);
    }

    @Test
    @DisplayName("通过 HTTP GET /api/executions/runs/{runId}/stream 测试携带 Last-Event-ID 头的断点重连流式响应")
    void shouldReturnSseStreamWithLastEventIdReplayViaHttp() throws Exception {
        WorkflowRunView run = executionApplication.startRun(new StartRunCommand("proj-default", "def-default", null));
        String runId = run.getId();

        executionApplication.appendEvent(runId, "CHUNK_1", "Hello");
        executionApplication.appendEvent(runId, "CHUNK_2", "SSE");
        executionApplication.appendEvent(runId, "CHUNK_3", "Stream");

        MvcResult result = mockMvc.perform(get("/api/executions/runs/" + runId + "/stream")
                        .header("Last-Event-ID", "2")
                        .accept(MediaType.TEXT_EVENT_STREAM_VALUE))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.startsWith(MediaType.TEXT_EVENT_STREAM_VALUE)))
                .andReturn();

        String responseContent = result.getResponse().getContentAsString();
        // Should replay events 3 and 4 (CHUNK_2 and CHUNK_3)
        assertThat(responseContent).contains("id:3");
        assertThat(responseContent).contains("event:CHUNK_2");
        assertThat(responseContent).contains("id:4");
        assertThat(responseContent).contains("event:CHUNK_3");
        // Should contain stream opened handshake
        assertThat(responseContent).contains("event:connected");
    }
}
