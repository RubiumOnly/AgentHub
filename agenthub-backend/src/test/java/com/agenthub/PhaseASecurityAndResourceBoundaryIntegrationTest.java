package com.agenthub;

import com.agenthub.identity.application.AuthApplication;
import com.agenthub.identity.dto.AuthTokenView;
import com.agenthub.identity.dto.LoginCommand;
import com.agenthub.identity.dto.RegisterCommand;
import com.agenthub.identity.infrastructure.entity.AuthAuditLogEntity;
import com.agenthub.identity.infrastructure.entity.InvalidatedTokenEntity;
import com.agenthub.identity.infrastructure.repository.AuthAuditLogRepository;
import com.agenthub.identity.infrastructure.repository.InvalidatedTokenRepository;
import com.agenthub.identity.infrastructure.security.ResourceAccessGuard;
import com.agenthub.identity.infrastructure.security.TokenProvider;
import com.agenthub.project.application.ProjectApplication;
import com.agenthub.project.dto.CreateProjectCommand;
import com.agenthub.project.dto.ProjectView;
import com.agenthub.sandbox.api.dto.CreateDeploymentRequest;
import com.agenthub.shared.context.RequestContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class PhaseASecurityAndResourceBoundaryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuthApplication authApplication;

    @Autowired
    private ProjectApplication projectApplication;

    @Autowired
    private TokenProvider tokenProvider;

    @Autowired
    private ResourceAccessGuard accessGuard;

    @Autowired
    private InvalidatedTokenRepository invalidatedTokenRepository;

    @Autowired
    private AuthAuditLogRepository authAuditLogRepository;

    @Autowired
    private Environment environment;

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    // =========================================================================
    // 1. JWT Secret 生产防御与启动校验
    // =========================================================================

    @Test
    @DisplayName("阶段A测试1：生产环境 (prod profile) 缺失、过短或命中示例值密钥时必须强制抛出异常拒绝启动")
    void shouldRejectInsecureSecretsInProductionProfile() {
        MockEnvironment prodEnv = new MockEnvironment();
        prodEnv.setActiveProfiles("prod");

        // 1. 缺失 / 空密钥
        TokenProvider emptySecretProvider = new TokenProvider("", 86400, prodEnv, null);
        assertThatThrownBy(emptySecretProvider::validateConfigurationForProfile)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("required in production");

        // 2. 过短密钥 (< 32 字符)
        TokenProvider shortSecretProvider = new TokenProvider("short-secret-123", 86400, prodEnv, null);
        assertThatThrownBy(shortSecretProvider::validateConfigurationForProfile)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 characters");

        // 3. 命中示例默认值
        TokenProvider defaultSecretProvider = new TokenProvider(
                "AgentHub-Secure-JWT-Secret-Key-Phase1-2026-SuperStrong", 86400, prodEnv, null);
        assertThatThrownBy(defaultSecretProvider::validateConfigurationForProfile)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must not use sample, placeholder or default values");

        // 4. 合法 32+ 字符高强生产密钥验证通过
        TokenProvider validProdProvider = new TokenProvider(
                "Production-Grade-Random-Cryptographic-Secret-2026-X99Z#$!", 86400, prodEnv, null);
        validProdProvider.validateConfigurationForProfile(); // 正常通过不抛异常
    }

    @Test
    @DisplayName("阶段A测试2：生产环境下禁止透传 dev-token-* 伪造凭据")
    void shouldBlockDevTokenInProduction() {
        MockEnvironment prodEnv = new MockEnvironment();
        prodEnv.setActiveProfiles("prod");

        TokenProvider prodProvider = new TokenProvider(
                "Production-Grade-Random-Cryptographic-Secret-2026-X99Z#$!", 86400, prodEnv, null);

        TokenProvider.TokenClaims claims = prodProvider.parseAndValidateToken("dev-token-attacker");
        assertThat(claims).isNull();
    }

    // =========================================================================
    // 2. SSE Stream Ticket 与 Cookie 认证 (消除 URL 中暴露长期 Bearer Token)
    // =========================================================================

    @Test
    @DisplayName("阶段A测试3：支持签发 60s 一次性 Stream Ticket，完成 SSE 认证并在使用后即刻失效")
    void shouldIssueAndConsumeOneTimeStreamTicketForSse() throws Exception {
        String email = "ticket_user_" + System.currentTimeMillis() + "@agenthub.local";
        AuthTokenView reg = authApplication.register(new RegisterCommand(email, "Password123!", "TicketUser"));

        // 1. 调用 POST /api/auth/stream-ticket 申请一次性流票据
        MvcResult ticketResult = mockMvc.perform(post("/api/auth/stream-ticket")
                        .header("Authorization", "Bearer " + reg.getToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.ticket").isNotEmpty())
                .andExpect(jsonPath("$.data.expiresInSeconds").value(60))
                .andReturn();

        JsonNode root = objectMapper.readTree(ticketResult.getResponse().getContentAsString());
        String ticket = root.at("/data/ticket").asText();
        assertThat(ticket).startsWith("st-");

        // 2. 第一次使用 ticket 验证
        TokenProvider.TokenClaims claims1 = tokenProvider.validateAndConsumeStreamTicket(ticket);
        assertThat(claims1).isNotNull();
        assertThat(claims1.getUserId()).isEqualTo(reg.getUser().getId());

        // 3. 第二次使用相同 ticket 必须失效 (一次性原则)
        TokenProvider.TokenClaims claims2 = tokenProvider.validateAndConsumeStreamTicket(ticket);
        assertThat(claims2).isNull();
    }

    @Test
    @DisplayName("阶段A测试4：支持通过 Cookie (agenthub_token) 认证请求")
    void shouldAuthenticateViaCookie() throws Exception {
        String email = "cookie_user_" + System.currentTimeMillis() + "@agenthub.local";
        AuthTokenView reg = authApplication.register(new RegisterCommand(email, "Password123!", "CookieUser"));

        jakarta.servlet.http.Cookie authCookie = new jakarta.servlet.http.Cookie("agenthub_token", reg.getToken());

        mockMvc.perform(get("/api/auth/me").cookie(authCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.email").value(email));
    }

    // =========================================================================
    // 3. 彻底消除生产 superuser 旁路 (user-1 不再拥有硬编码上帝特权)
    // =========================================================================

    @Test
    @DisplayName("阶段A测试5：当 allowSuperuserBypass 为 false 时，user-1 尝试越权访问 User A 项目被严格拦截 (1002/FORBIDDEN)")
    void shouldDenyUser1AccessWhenSuperuserBypassDisabled() {
        AuthTokenView userA = authApplication.register(new RegisterCommand("alice_owner_" + System.currentTimeMillis() + "@agenthub.local", "Pass123", "Alice"));
        RequestContext.get().setUserId(userA.getUser().getId());
        ProjectView proj = projectApplication.createProject(new CreateProjectCommand("Alice Project", "Desc", "alice"));

        boolean originalBypass = accessGuard.isAllowSuperuserBypass();
        try {
            // 模拟生产环境关闭旁路
            accessGuard.setAllowSuperuserBypass(false);

            // user-1 试图访问 Alice 的项目
            RequestContext.get().setUserId("user-1");
            assertThatThrownBy(() -> projectApplication.getProjectById(proj.getId()))
                    .isInstanceOf(com.agenthub.shared.exception.BusinessException.class)
                    .satisfies(e -> assertThat(((com.agenthub.shared.exception.BusinessException) e).getErrorCode())
                            .isEqualTo(com.agenthub.shared.exception.ErrorCode.FORBIDDEN));
        } finally {
            accessGuard.setAllowSuperuserBypass(originalBypass);
        }
    }

    // =========================================================================
    // 4. 工作区锁 API 服务端派生所有权与水平越权防御
    // =========================================================================

    @Test
    @DisplayName("阶段A测试6：工作区锁 API 由服务端强制派生当前用户 ID，客户端无法伪造 ownerId 且攻击者无法抢锁或刺探状态")
    void shouldDeriveWorkspaceLockOwnerAndPreventCrossUserTampering() throws Exception {
        String tokenAlice = registerAndGetToken("alice_ws_" + System.currentTimeMillis() + "@agenthub.local");
        String tokenBob = registerAndGetToken("bob_ws_" + System.currentTimeMillis() + "@agenthub.local");

        // Alice 创建项目与工作区
        MvcResult projRes = mockMvc.perform(post("/api/projects")
                        .header("Authorization", "Bearer " + tokenAlice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Alice Workspace Secure Proj\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String projId = extractJsonField(projRes, "/data/id");
        String wsId = extractJsonField(projRes, "/data/workspaceId");
        String aliceUserId = extractJsonField(projRes, "/data/ownerId");

        // Alice 请求获取锁，虽然 payload 传入伪造的 ownerId "hacker-custom"，服务端仍强制派生为 aliceUserId
        mockMvc.perform(post("/api/workspaces/" + wsId + "/lock/acquire")
                        .header("Authorization", "Bearer " + tokenAlice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ownerId\":\"hacker-custom\",\"waitTimeoutMs\":1000,\"leaseTtlMs\":10000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(true));

        // 查看锁状态，证明锁定者为 Alice
        mockMvc.perform(get("/api/workspaces/" + wsId + "/lock/status")
                        .header("Authorization", "Bearer " + tokenAlice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.locked").value(true))
                .andExpect(jsonPath("$.data.ownerId").value(aliceUserId));

        // Bob 试图刺探 Alice 的工作区锁状态 -> 被 1002 (FORBIDDEN) 拦截
        mockMvc.perform(get("/api/workspaces/" + wsId + "/lock/status")
                        .header("Authorization", "Bearer " + tokenBob))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1002));

        // Bob 试图续期 Alice 的锁 -> 被 1002 (FORBIDDEN) 拦截
        mockMvc.perform(post("/api/workspaces/" + wsId + "/lock/renew")
                        .header("Authorization", "Bearer " + tokenBob)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"additionalTtlMs\":5000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1002));

        // Bob 试图释放 Alice 的锁 -> 被 1002 (FORBIDDEN) 拦截
        mockMvc.perform(post("/api/workspaces/" + wsId + "/lock/release")
                        .header("Authorization", "Bearer " + tokenBob))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1002));

        // Alice 正常释放自己的锁
        mockMvc.perform(post("/api/workspaces/" + wsId + "/lock/release")
                        .header("Authorization", "Bearer " + tokenAlice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));
    }

    // =========================================================================
    // 5. 部署控制器 DeploymentController 多租户归属防御
    // =========================================================================

    @Test
    @DisplayName("阶段A测试7：用户 B 试图对用户 A 的项目创建、查看、启停部署被严格拦截 (1002/FORBIDDEN)")
    void shouldEnforceDeploymentOwnership() throws Exception {
        String tokenAlice = registerAndGetToken("alice_deploy_" + System.currentTimeMillis() + "@agenthub.local");
        String tokenBob = registerAndGetToken("bob_deploy_" + System.currentTimeMillis() + "@agenthub.local");

        // Alice 创建项目
        MvcResult projRes = mockMvc.perform(post("/api/projects")
                        .header("Authorization", "Bearer " + tokenAlice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Alice Deployable Proj\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String projId = extractJsonField(projRes, "/data/id");

        // Bob 试图为 Alice 的项目创建部署 -> 拦截 1002
        CreateDeploymentRequest maliciousRequest = new CreateDeploymentRequest(projId, "STATIC_PREVIEW", "LOCAL_PROCESS");
        mockMvc.perform(post("/api/deployments")
                        .header("Authorization", "Bearer " + tokenBob)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(maliciousRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1002));

        // Alice 正常创建部署
        CreateDeploymentRequest aliceRequest = new CreateDeploymentRequest(projId, "STATIC_PREVIEW", "LOCAL_PROCESS");
        MvcResult deployRes = mockMvc.perform(post("/api/deployments")
                        .header("Authorization", "Bearer " + tokenAlice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(aliceRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        String deployId = extractJsonField(deployRes, "/data/id");

        // Bob 试图查看 Alice 的部署详情 -> 拦截 1002
        mockMvc.perform(get("/api/deployments/" + deployId)
                        .header("Authorization", "Bearer " + tokenBob))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1002));

        // Bob 试图查看 Alice 的部署日志 -> 拦截 1002
        mockMvc.perform(get("/api/deployments/" + deployId + "/logs")
                        .header("Authorization", "Bearer " + tokenBob))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1002));

        // Bob 试图停止 Alice 的部署 -> 拦截 1002
        mockMvc.perform(post("/api/deployments/" + deployId + "/stop")
                        .header("Authorization", "Bearer " + tokenBob))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1002));

        // Bob 试图列出 Alice 项目的部署列表 -> 拦截 1002
        mockMvc.perform(get("/api/deployments?projectId=" + projId)
                        .header("Authorization", "Bearer " + tokenBob))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1002));

        // Alice 自己可以正常停止部署
        mockMvc.perform(post("/api/deployments/" + deployId + "/stop")
                        .header("Authorization", "Bearer " + tokenAlice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    // =========================================================================
    // 6. 持久化 Token 吊销与认证审计
    // =========================================================================

    @Test
    @DisplayName("阶段A测试8：Token 登出废止持久化到数据库并在多实例/重启后持续阻断访问")
    void shouldPersistTokenRevocationAndBlockSubsequentAccess() throws Exception {
        String email = "revoke_test_" + System.currentTimeMillis() + "@agenthub.local";
        AuthTokenView reg = authApplication.register(new RegisterCommand(email, "Password123!", "RevokeTest"));
        String token = reg.getToken();

        // 登出
        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // 验证数据库已存在持久化废弃记录
        List<InvalidatedTokenEntity> records = invalidatedTokenRepository.findAll();
        assertThat(records).isNotEmpty();

        // 再次访问必须被拒绝
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1001));
    }

    @Test
    @DisplayName("阶段A测试9：认证审计日志持久化记录 (REGISTER, LOGIN, LOGOUT)")
    void shouldRecordAuthenticationAuditLogs() throws Exception {
        String email = "audit_user_" + System.currentTimeMillis() + "@agenthub.local";
        authApplication.register(new RegisterCommand(email, "Password123!", "AuditUser"));
        AuthTokenView login = authApplication.login(new LoginCommand(email, "Password123!"));
        authApplication.logout(login.getToken());

        List<AuthAuditLogEntity> logs = authAuditLogRepository.findAll();
        assertThat(logs).isNotEmpty();

        boolean hasRegister = logs.stream().anyMatch(l -> "REGISTER_SUCCESS".equals(l.getEventType()) && email.equals(l.getEmail()));
        boolean hasLogin = logs.stream().anyMatch(l -> "LOGIN_SUCCESS".equals(l.getEventType()) && email.equals(l.getEmail()));
        boolean hasLogout = logs.stream().anyMatch(l -> "LOGOUT".equals(l.getEventType()));

        assertThat(hasRegister).isTrue();
        assertThat(hasLogin).isTrue();
        assertThat(hasLogout).isTrue();
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
