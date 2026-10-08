package com.agenthub;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RestApiSecurityAndMultiTenancyIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("端到端测试用户认证全生命周期：注册、登录、刷新令牌、查看个人信息、退出注销及失效校验")
    void testAuthenticationLifecycle() throws Exception {
        String email = "lifecycle_" + System.currentTimeMillis() + "@agenthub.local";
        String password = "SecurePassword123!";

        // 1. 注册
        String regPayload = String.format("{\"email\":\"%s\",\"password\":\"%s\",\"displayName\":\"LifecycleUser\"}", email, password);
        MvcResult regResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(regPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.user.email").value(email))
                .andReturn();

        String token1 = extractToken(regResult);

        // 2. 错误密码登录拦截
        String badLoginPayload = String.format("{\"email\":\"%s\",\"password\":\"WrongPassword\"}", email);
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badLoginPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1003)); // AUTH_FAILED

        // 3. 正确密码登录
        String goodLoginPayload = String.format("{\"email\":\"%s\",\"password\":\"%s\"}", email, password);
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(goodLoginPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andReturn();

        String token2 = extractToken(loginResult);

        // 4. 使用有效 Token 获取当前用户
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.email").value(email));

        // 5. 刷新 Token (Refresh)
        MvcResult refreshResult = mockMvc.perform(post("/api/auth/refresh")
                        .header("Authorization", "Bearer " + token2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andReturn();

        String token3 = extractToken(refreshResult);
        assertThat(token3).isNotEqualTo(token2);

        // 6. 旧 Token2 已被轮转废止，再次访问 /api/auth/me 应被 1001 拒绝
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1001));

        // 7. 新 Token3 访问正常
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token3))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // 8. 登出注销 (Logout)
        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + token3))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // 9. 登出后 Token3 失效
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token3))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1001));
    }

    @Test
    @DisplayName("安全边界测试：无认证请求访问受控资源严格拒绝，杜绝默认隐式降级至 user-1 越权")
    void testUnauthenticatedAccessRejectedWithoutUser1Fallback() throws Exception {
        // 未传 Token 访问项目列表
        mockMvc.perform(get("/api/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1001));

        // 未传 Token 创建项目
        mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"HackerProject\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1001));

        // 未传 Token 获取会话列表
        mockMvc.perform(get("/api/im/conversations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1001));

        // 未传 Token 启动工作流
        mockMvc.perform(post("/api/executions/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":\"proj-default\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1001));
    }

    @Test
    @DisplayName("水平越权测试：用户 B 试图越权读取或访问用户 A 的 Project 和 Workspace 严格拦截 (403/1002)")
    void testCrossUserProjectAndWorkspaceForbidden() throws Exception {
        // 创建 Alice 与 Bob
        String tokenA = registerAndGetToken("alice_proj_" + System.currentTimeMillis() + "@agenthub.local");
        String tokenB = registerAndGetToken("bob_proj_" + System.currentTimeMillis() + "@agenthub.local");

        // Alice 创建项目
        MvcResult createProjResult = mockMvc.perform(post("/api/projects")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Alice Secret Core\",\"description\":\"Confidential\",\"relativeRoot\":\"alice-core\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

        String aliceProjId = extractJsonField(createProjResult, "/data/id");

        // Alice 自己可以正常访问项目与工作区
        mockMvc.perform(get("/api/projects/" + aliceProjId)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(aliceProjId));

        mockMvc.perform(get("/api/projects/" + aliceProjId + "/workspace")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // Bob 试图读取 Alice 的项目 -> 拦截 1002 (FORBIDDEN)
        mockMvc.perform(get("/api/projects/" + aliceProjId)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1002));

        // Bob 试图读取 Alice 的工作区 -> 拦截 1002 (FORBIDDEN)
        mockMvc.perform(get("/api/projects/" + aliceProjId + "/workspace")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1002));

        mockMvc.perform(get("/api/projects/" + aliceProjId + "/workspaces")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1002));
    }

    @Test
    @DisplayName("水平越权测试：用户 B 试图刺探用户 A 的 IM 会话与私密消息列表严格拦截 (403/1002)")
    void testCrossUserConversationAndMessagesForbidden() throws Exception {
        String tokenA = registerAndGetToken("alice_im_" + System.currentTimeMillis() + "@agenthub.local");
        String tokenB = registerAndGetToken("bob_im_" + System.currentTimeMillis() + "@agenthub.local");

        // Alice 创建私密会话
        MvcResult createConvResult = mockMvc.perform(post("/api/im/conversations")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Alice Private Room\",\"type\":\"DIRECT_CHAT\",\"agentIds\":[\"BackendArchitect\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

        String aliceConvId = extractJsonField(createConvResult, "/data/id");

        // Alice 发送消息
        mockMvc.perform(post("/api/im/conversations/" + aliceConvId + "/messages")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Top secret architecture plan\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // Bob 查询自身会话列表，不应包含 Alice 的会话
        MvcResult bobConvListResult = mockMvc.perform(get("/api/im/conversations")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

        assertThat(bobConvListResult.getResponse().getContentAsString()).doesNotContain(aliceConvId);

        // Bob 尝试强行读取 Alice 的会话详情 -> 拦截 1002 (FORBIDDEN)
        mockMvc.perform(get("/api/im/conversations/" + aliceConvId)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1002));

        // Bob 尝试强行读取 Alice 的消息列表 -> 拦截 1002 (FORBIDDEN)
        mockMvc.perform(get("/api/im/conversations/" + aliceConvId + "/messages")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1002));

        // Bob 尝试强行向 Alice 的会话发送消息 -> 拦截 1002 (FORBIDDEN)
        mockMvc.perform(post("/api/im/conversations/" + aliceConvId + "/messages")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Malicious injected message\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1002));
    }

    @Test
    @DisplayName("端到端测试 ExecutionController：运行状态机持久化、游标回放与跨用户归属越权防御")
    void testExecutionRunsAndReplayEventsOwnershipAndCursor() throws Exception {
        String tokenA = registerAndGetToken("alice_exec_" + System.currentTimeMillis() + "@agenthub.local");
        String tokenB = registerAndGetToken("bob_exec_" + System.currentTimeMillis() + "@agenthub.local");

        // Alice 创建项目
        MvcResult projResult = mockMvc.perform(post("/api/projects")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Execution Test Project\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String projId = extractJsonField(projResult, "/data/id");

        // Bob 试图在 Alice 的项目上启动 WorkflowRun -> 拦截 1002 (FORBIDDEN)
        mockMvc.perform(post("/api/executions/runs")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"projectId\":\"%s\"}", projId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1002));

        // Alice 正常启动 WorkflowRun
        MvcResult startRunResult = mockMvc.perform(post("/api/executions/runs")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"projectId\":\"%s\"}", projId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andReturn();

        String runId = extractJsonField(startRunResult, "/data/id");

        // Alice 追加运行事件
        mockMvc.perform(post("/api/executions/runs/" + runId + "/events")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventType\":\"STEP_COMPLETED\",\"payload\":\"{\\\"step\\\":1}\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.sequenceNum").value(2L));

        mockMvc.perform(post("/api/executions/runs/" + runId + "/events")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventType\":\"STEP_COMPLETED\",\"payload\":\"{\\\"step\\\":2}\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.sequenceNum").value(3L));

        // Alice 按 sequence 游标回放 (afterSeq = 2 -> 只获取第 3 个事件)
        mockMvc.perform(get("/api/executions/runs/" + runId + "/events")
                        .param("afterSeq", "2")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].sequenceNum").value(3L));

        // Bob 试图读取 Alice 的 Run -> 拦截 1002 (FORBIDDEN)
        mockMvc.perform(get("/api/executions/runs/" + runId)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1002));

        // Bob 试图读取 Alice 的 Run Events -> 拦截 1002 (FORBIDDEN)
        mockMvc.perform(get("/api/executions/runs/" + runId + "/events")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1002));
    }

    private String registerAndGetToken(String email) throws Exception {
        String payload = String.format("{\"email\":\"%s\",\"password\":\"Pass123456!\",\"displayName\":\"TestUser\"}", email);
        MvcResult res = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return extractToken(res);
    }

    private String extractToken(MvcResult res) throws Exception {
        JsonNode root = objectMapper.readTree(res.getResponse().getContentAsString());
        return root.at("/data/token").asText();
    }

    private String extractJsonField(MvcResult res, String jsonPointer) throws Exception {
        JsonNode root = objectMapper.readTree(res.getResponse().getContentAsString());
        return root.at(jsonPointer).asText();
    }
}
