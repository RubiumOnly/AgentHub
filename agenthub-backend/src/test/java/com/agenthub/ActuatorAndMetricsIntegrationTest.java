package com.agenthub;

import com.agenthub.infrastructure.metrics.AgentHubMetricsCollector;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class ActuatorAndMetricsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AgentHubMetricsCollector metricsCollector;

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    @DisplayName("Actuator /actuator/health 端点应返回状态 UP")
    void testActuatorHealthEndpoint() throws Exception {
        mockMvc.perform(get("/actuator/health").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("Actuator /actuator/prometheus 端点应正常导出 Prometheus 文本指标")
    void testActuatorPrometheusEndpoint() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN));
    }

    @Test
    @DisplayName("AgentHubMetricsCollector 业务指标记录应正确写入 MeterRegistry")
    void testMetricsCollectorRecording() throws Exception {
        // 1. 测试活跃度 Gauges
        int initialRuns = metricsCollector.getActiveRuns();
        metricsCollector.incrementActiveRuns();
        assertThat(metricsCollector.getActiveRuns()).isEqualTo(initialRuns + 1);
        metricsCollector.decrementActiveRuns();
        assertThat(metricsCollector.getActiveRuns()).isEqualTo(initialRuns);

        int initialSse = metricsCollector.getActiveSseConnections();
        metricsCollector.incrementActiveSseConnections();
        assertThat(metricsCollector.getActiveSseConnections()).isEqualTo(initialSse + 1);
        metricsCollector.decrementActiveSseConnections();
        assertThat(metricsCollector.getActiveSseConnections()).isEqualTo(initialSse);

        // 2. 测试执行完成 Counter & Timer
        metricsCollector.recordRunCompleted("SUCCEEDED", 1250);
        metricsCollector.recordStepExecution("AGENT", "SUCCEEDED", 350);
        metricsCollector.recordTokenUsage("openai", "gpt-4o", 120, 80);
        metricsCollector.recordWorkspaceLockWait(15);
        metricsCollector.recordDeployment("RUNNING");

        // 3. 验证 MeterRegistry 中存在对应的指标
        assertThat(meterRegistry.find("agenthub.runs.total").counter()).isNotNull();
        assertThat(meterRegistry.find("agenthub.steps.total").counter()).isNotNull();
        assertThat(meterRegistry.find("agenthub.steps.duration").timer()).isNotNull();
        assertThat(meterRegistry.find("agenthub.tokens.prompt.total").counter()).isNotNull();
        assertThat(meterRegistry.find("agenthub.workspace.lock.wait").timer()).isNotNull();
        assertThat(meterRegistry.find("agenthub.deployments.total").counter()).isNotNull();

        // 4. 验证 Prometheus 输出包含自定义指标前缀
        String prometheusOutput = mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(prometheusOutput).contains("agenthub_runs_total");
    }
}
