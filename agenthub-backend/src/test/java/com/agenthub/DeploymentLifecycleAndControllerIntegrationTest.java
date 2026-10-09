package com.agenthub;

import com.agenthub.sandbox.api.dto.CreateDeploymentRequest;
import com.fasterxml.jackson.databind.JsonNode;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DeploymentLifecycleAndControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("部署生命周期 1：创建一键静态预览部署，状态转为 RUNNING 并分配可用端口")
    void shouldCreateAndRunDeployment() throws Exception {
        CreateDeploymentRequest request = new CreateDeploymentRequest("proj-default", "STATIC_PREVIEW", "LOCAL_PROCESS");

        MvcResult result = mockMvc.perform(post("/api/deployments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andExpect(jsonPath("$.data.projectId").value("proj-default"))
                .andExpect(jsonPath("$.data.status").value("RUNNING"))
                .andExpect(jsonPath("$.data.port").isNumber())
                .andExpect(jsonPath("$.data.url").value(containsString("proj-default")))
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        String deploymentId = root.path("data").path("id").asText();

        // Query deployment details by ID
        mockMvc.perform(get("/api/deployments/" + deploymentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(deploymentId))
                .andExpect(jsonPath("$.data.status").value("RUNNING"));

        // Query deployment logs
        mockMvc.perform(get("/api/deployments/" + deploymentId + "/logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(containsString("BUILD PHASE SKIPPED")));

        // Stop deployment
        mockMvc.perform(post("/api/deployments/" + deploymentId + "/stop"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("STOPPED"));

        // Query after stop
        mockMvc.perform(get("/api/deployments/" + deploymentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("STOPPED"));
    }

    @Test
    @DisplayName("部署生命周期 2：按项目查询历史部署列表")
    void shouldListDeploymentsForProject() throws Exception {
        CreateDeploymentRequest request = new CreateDeploymentRequest("proj-default", "STATIC_PREVIEW", "LOCAL_PROCESS");
        mockMvc.perform(post("/api/deployments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/deployments?projectId=proj-default"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    @DisplayName("异常处理 3：查询不存在的部署 ID 返回 404 / 7001 错误码")
    void shouldReturnErrorWhenDeploymentNotFound() throws Exception {
        mockMvc.perform(get("/api/deployments/dep-non-existent-999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(7001));
    }

    @Test
    @DisplayName("安全拦截 4：构建命令包含恶意注入时阻断并返回 7002 错误码")
    void shouldBlockDangerousBuildCommandInDeployment() throws Exception {
        CreateDeploymentRequest request = new CreateDeploymentRequest("proj-default", "STATIC_PREVIEW", "LOCAL_PROCESS");
        request.setBuildCommand("npm run build; rm -rf /");

        mockMvc.perform(post("/api/deployments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(7002));
    }
}
