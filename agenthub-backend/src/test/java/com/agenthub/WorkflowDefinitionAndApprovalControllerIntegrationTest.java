package com.agenthub;

import com.agenthub.execution.infrastructure.entity.WorkflowRunEntity;
import com.agenthub.execution.infrastructure.repository.WorkflowRunRepository;
import com.agenthub.orchestration.domain.dsl.WorkflowDsl;
import com.agenthub.orchestration.domain.dsl.WorkflowEdgeDsl;
import com.agenthub.orchestration.domain.dsl.WorkflowNodeDsl;
import com.agenthub.orchestration.dto.ApprovalDecisionCommand;
import com.agenthub.orchestration.dto.CreateWorkflowDefinitionCommand;
import com.agenthub.orchestration.infrastructure.entity.ApprovalEntity;
import com.agenthub.orchestration.infrastructure.repository.ApprovalRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorkflowDefinitionAndApprovalControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WorkflowRunRepository workflowRunRepository;

    @Autowired
    private ApprovalRepository approvalRepository;

    @Test
    @DisplayName("测试工作流定义 REST 接口：创建定义、Schema与环路校验、查询详情与列表")
    void testWorkflowDefinitionEndpoints() throws Exception {
        WorkflowDsl dsl = new WorkflowDsl("wf-api-test", "API测试工作流");
        dsl.setNodes(List.of(
                new WorkflowNodeDsl("n1", "第一步", "AGENT"),
                new WorkflowNodeDsl("n2", "第二步", "AGENT")
        ));
        dsl.setEdges(List.of(
                new WorkflowEdgeDsl("n1", "n2")
        ));

        CreateWorkflowDefinitionCommand cmd = new CreateWorkflowDefinitionCommand("API测试工作流", "描述", dsl);

        // 1. 创建合法工作流定义
        MvcResult createResult = mockMvc.perform(post("/api/workflows/definitions")
                        .header("X-User-Id", "user-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cmd)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andExpect(jsonPath("$.data.name").value("API测试工作流"))
                .andReturn();

        String defId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("data").get("id").asText();

        // 2. 根据 ID 查询工作流定义详情
        mockMvc.perform(get("/api/workflows/definitions/" + defId)
                        .header("X-User-Id", "user-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(defId))
                .andExpect(jsonPath("$.data.dsl.nodes").isArray());

        // 3. 查询工作流列表
        mockMvc.perform(get("/api/workflows/definitions")
                        .header("X-User-Id", "user-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray());

        // 4. 单独验证 DSL（不落库）
        mockMvc.perform(post("/api/workflows/definitions/validate")
                        .header("X-User-Id", "user-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dsl)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.valid").value(true))
                .andExpect(jsonPath("$.data.topologicalOrder").isArray());

        // 5. 校验死锁环路 DSL 拦截（预期返回 6001 错误码）
        WorkflowDsl cyclicDsl = new WorkflowDsl("wf-cyclic", "死锁环路");
        cyclicDsl.setNodes(List.of(
                new WorkflowNodeDsl("cA", "节点A", "AGENT"),
                new WorkflowNodeDsl("cB", "节点B", "AGENT")
        ));
        cyclicDsl.setEdges(List.of(
                new WorkflowEdgeDsl("cA", "cB"),
                new WorkflowEdgeDsl("cB", "cA")
        ));
        CreateWorkflowDefinitionCommand cyclicCmd = new CreateWorkflowDefinitionCommand("死锁", "死锁描述", cyclicDsl);

        mockMvc.perform(post("/api/workflows/definitions")
                        .header("X-User-Id", "user-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cyclicCmd)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(6001))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Cycle detected")));
    }

    @Test
    @DisplayName("测试人工审批 REST 接口：查询审批项、执行 approve / reject、防二次审批冲突")
    void testApprovalEndpoints() throws Exception {
        String runId = "run-appr-api-" + UUID.randomUUID().toString().substring(0, 8);
        WorkflowRunEntity runEntity = new WorkflowRunEntity(runId, "proj-default", "def-default", "WAITING_APPROVAL", null);
        workflowRunRepository.save(runEntity);

        String approvalId = "appr-" + UUID.randomUUID().toString().substring(0, 8);
        ApprovalEntity approvalEntity = new ApprovalEntity(approvalId, runId, "step-test-1", "PENDING", "user-1");
        approvalRepository.save(approvalEntity);

        // 1. 查询审批详情
        mockMvc.perform(get("/api/approvals/" + approvalId)
                        .header("X-User-Id", "user-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(approvalId))
                .andExpect(jsonPath("$.data.status").value("PENDING"));

        // 2. 按 RunId 查询审批列表
        mockMvc.perform(get("/api/approvals/runs/" + runId)
                        .header("X-User-Id", "user-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value(approvalId));

        // 3. 执行 Approve
        mockMvc.perform(post("/api/approvals/" + approvalId + "/approve")
                        .header("X-User-Id", "user-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"准予发布\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.decision").value("APPROVED"))
                .andExpect(jsonPath("$.data.reviewedBy").value("user-1"));

        // 4. 重复审批防御校验（预期返回 6013 错误码）
        mockMvc.perform(post("/api/approvals/" + approvalId + "/decision")
                        .header("X-User-Id", "user-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ApprovalDecisionCommand("REJECTED", "重复尝试"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(6013))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("already been decided")));
    }
}
