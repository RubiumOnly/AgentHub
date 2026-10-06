package com.agenthub;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class SandboxAndDeployTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("测试 Web 沙箱实时预览接口输出正确 HTML")
    void shouldReturnPreviewHtml() throws Exception {
        mockMvc.perform(get("/api/sandbox/preview/proj-demo"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("AgentHub Web 预览沙箱")));
    }

    @Test
    @DisplayName("测试一键部署接口返回部署清单与 Dockerfile 内容")
    void shouldGenerateDeploymentManifest() throws Exception {
        mockMvc.perform(post("/api/sandbox/deploy/proj-demo").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("DEPLOYED"))
                .andExpect(jsonPath("$.data.previewUrl").value(org.hamcrest.Matchers.containsString("proj-demo")))
                .andExpect(jsonPath("$.data.dockerfileContent").value(org.hamcrest.Matchers.containsString("FROM nginx:alpine")));
    }
}
