package com.agenthub;

import com.agenthub.identity.application.AuthApplication;
import com.agenthub.identity.dto.AuthTokenView;
import com.agenthub.identity.dto.LoginCommand;
import com.agenthub.identity.dto.RegisterCommand;
import com.agenthub.identity.infrastructure.security.ResourceAccessGuard;
import com.agenthub.identity.infrastructure.security.TokenProvider;
import com.agenthub.project.application.ProjectApplication;
import com.agenthub.project.dto.CreateProjectCommand;
import com.agenthub.project.dto.ProjectView;
import com.agenthub.shared.context.RequestContext;
import com.agenthub.shared.exception.BusinessException;
import com.agenthub.shared.exception.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class AuthenticationAndResourceGuardTest {

    @Autowired
    private AuthApplication authApplication;

    @Autowired
    private ProjectApplication projectApplication;

    @Autowired
    private ResourceAccessGuard accessGuard;

    @Autowired
    private TokenProvider tokenProvider;

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    @Test
    @DisplayName("测试用户注册、BCrypt 加密密码存储与 JWT Token 签发")
    void shouldRegisterNewUserAndIssueToken() {
        String uniqueEmail = "alice_" + System.currentTimeMillis() + "@agenthub.local";
        RegisterCommand cmd = new RegisterCommand(uniqueEmail, "StrongPassword123!", "Alice");
        AuthTokenView authResult = authApplication.register(cmd);

        assertThat(authResult).isNotNull();
        assertThat(authResult.getToken()).isNotEmpty();
        assertThat(authResult.getUser().getEmail()).isEqualTo(uniqueEmail);
        assertThat(authResult.getUser().getDisplayName()).isEqualTo("Alice");

        // Verify token can be parsed and validated
        TokenProvider.TokenClaims claims = tokenProvider.parseAndValidateToken(authResult.getToken());
        assertThat(claims).isNotNull();
        assertThat(claims.getEmail()).isEqualTo(uniqueEmail);
        assertThat(claims.isExpired()).isFalse();
    }

    @Test
    @DisplayName("测试用户登录验证：成功登录返回 Token，错误密码拒绝访问")
    void shouldLoginSuccessfullyAndRejectBadCredentials() {
        String uniqueEmail = "bob_" + System.currentTimeMillis() + "@agenthub.local";
        authApplication.register(new RegisterCommand(uniqueEmail, "CorrectPassword99", "Bob"));

        // Valid login
        AuthTokenView loginSuccess = authApplication.login(new LoginCommand(uniqueEmail, "CorrectPassword99"));
        assertThat(loginSuccess.getToken()).isNotEmpty();

        // Invalid login password
        assertThatThrownBy(() -> authApplication.login(new LoginCommand(uniqueEmail, "WrongPassword")))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.AUTH_FAILED));
    }

    @Test
    @DisplayName("测试重复邮箱注册防御：拒绝相同邮箱再次注册")
    void shouldPreventDuplicateUserRegistration() {
        String uniqueEmail = "charlie_" + System.currentTimeMillis() + "@agenthub.local";
        authApplication.register(new RegisterCommand(uniqueEmail, "Pass123", "Charlie"));

        assertThatThrownBy(() -> authApplication.register(new RegisterCommand(uniqueEmail, "Pass456", "CharlieDuplicate")))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.USER_ALREADY_EXISTS));
    }

    @Test
    @DisplayName("测试跨用户资源归属越权检查：用户 B 试图读取用户 A 的受控项目被 403 严格拦截")
    void shouldEnforceResourceOwnershipAndBlockUnauthorizedAccess() {
        AuthTokenView userA = authApplication.register(new RegisterCommand("ownerA_" + System.currentTimeMillis() + "@agenthub.local", "PassA", "Owner A"));
        AuthTokenView userB = authApplication.register(new RegisterCommand("attackerB_" + System.currentTimeMillis() + "@agenthub.local", "PassB", "Attacker B"));

        // Setup User A creating a project
        RequestContext.get().setUserId(userA.getUser().getId());
        ProjectView projA = projectApplication.createProject(new CreateProjectCommand("Alpha Project", "Owner A only", "alpha"));
        assertThat(projA.getId()).isNotEmpty();

        // User A accessing own project succeeds
        ProjectView readByOwner = projectApplication.getProjectById(projA.getId());
        assertThat(readByOwner.getId()).isEqualTo(projA.getId());

        // Switch context to User B
        RequestContext.get().setUserId(userB.getUser().getId());

        // User B accessing User A's project must throw FORBIDDEN
        assertThatThrownBy(() -> projectApplication.getProjectById(projA.getId()))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        // Switch context to Super Admin (user-1)
        RequestContext.get().setUserId("user-1");
        ProjectView readByAdmin = projectApplication.getProjectById(projA.getId());
        assertThat(readByAdmin.getId()).isEqualTo(projA.getId());
    }
}
