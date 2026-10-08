package com.agenthub;

import com.agenthub.shared.context.RequestContext;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TeamAndConversationControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        RequestContext.get().setUserId("user-1");

        String email = "ctrl_test_" + System.currentTimeMillis() + "@agenthub.local";
        String regPayload = String.format("{\"email\":\"%s\",\"password\":\"SecurePass123!\",\"displayName\":\"CtrlTester\"}", email);
        MvcResult res = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(regPayload))
                .andReturn();
        this.token = objectMapper.readTree(res.getResponse().getContentAsString()).path("data").path("token").asText();
    }

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    @Test
    @DisplayName("测试 /api/teams 端点：团队创建、查询与拓扑决策 REST 接口")
    void shouldTestTeamsRestEndpoints() throws Exception {
        Map<String, Object> createReq = Map.of(
                "projectId", "proj-default",
                "name", "RestTestTeam",
                "description", "REST Integration test squad",
                "topology", "ROUND_ROBIN",
                "maxTurns", 15,
                "initialMembers", List.of(
                        Map.of("agentInstanceId", "agent-1", "role", "Worker1", "roleType", "CODER", "sortOrder", 0),
                        Map.of("agentInstanceId", "agent-2", "role", "Worker2", "roleType", "REVIEWER", "sortOrder", 1)
                )
        );

        String createRes = mockMvc.perform(post("/api/teams")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andExpect(jsonPath("$.data.name").value("RestTestTeam"))
                .andExpect(jsonPath("$.data.topology").value("ROUND_ROBIN"))
                .andReturn().getResponse().getContentAsString();

        String teamId = objectMapper.readTree(createRes).path("data").path("id").asText();

        // GET /api/teams
        mockMvc.perform(get("/api/teams")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))));

        // GET /api/teams/{id}
        mockMvc.perform(get("/api/teams/" + teamId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.name").value("RestTestTeam"))
                .andExpect(jsonPath("$.data.members", hasSize(2)));

        // POST /api/teams/{id}/coordinate
        Map<String, Object> coordReq = Map.of(
                "currentSpeakerId", "agent-1",
                "currentTurn", 1,
                "taskGoal", "Review pull request"
        );

        mockMvc.perform(post("/api/teams/" + teamId + "/coordinate")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(coordReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.nextSpeakerId").value("agent-2"));
    }

    @Test
    @DisplayName("测试 /api/im/conversations 与 /api/conversations 端点完整交互链路")
    void shouldTestConversationRestEndpoints() throws Exception {
        Map<String, Object> convReq = Map.of(
                "title", "REST端到端测试会话",
                "type", "DIRECT_CHAT",
                "agentIds", List.of("BackendArchitect", "FrontendEngineer"),
                "projectId", "proj-default"
        );

        String convRes = mockMvc.perform(post("/api/im/conversations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(convReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        String convId = objectMapper.readTree(convRes).path("data").path("id").asText();

        // Send broadcast message
        Map<String, Object> msgReq1 = Map.of(
                "senderId", "user-1",
                "senderType", "USER",
                "messageType", "BROADCAST",
                "protocolType", "NORMAL",
                "content", "第一条需求指令"
        );
        mockMvc.perform(post("/api/im/conversations/" + convId + "/messages")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(msgReq1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.sequenceNum").value(1));

        // Send direct message
        Map<String, Object> msgReq2 = Map.of(
                "senderId", "BackendArchitect",
                "senderType", "AGENT",
                "recipientId", "FrontendEngineer",
                "messageType", "DIRECT",
                "protocolType", "REQUEST_REPLY",
                "content", "第二条私聊消息"
        );
        mockMvc.perform(post("/api/im/conversations/" + convId + "/messages")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(msgReq2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.sequenceNum").value(2));

        // GET messages as FrontendEngineer (should see both)
        mockMvc.perform(get("/api/im/conversations/" + convId + "/messages")
                        .header("Authorization", "Bearer " + token)
                        .param("viewerId", "FrontendEngineer"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)));

        // GET context window
        mockMvc.perform(get("/api/im/conversations/" + convId + "/context")
                        .header("Authorization", "Bearer " + token)
                        .param("windowSize", "5")
                        .param("maxTokens", "2000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.activeMessages", hasSize(2)));

        // POST summarize
        mockMvc.perform(post("/api/im/conversations/" + convId + "/summarize")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // Also test the /api/conversations path
        mockMvc.perform(get("/api/conversations/" + convId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.title").value("REST端到端测试会话"));
    }
}
