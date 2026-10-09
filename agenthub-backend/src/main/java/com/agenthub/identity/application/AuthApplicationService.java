package com.agenthub.identity.application;

import com.agenthub.identity.dto.AuthTokenView;
import com.agenthub.identity.dto.LoginCommand;
import com.agenthub.identity.dto.RegisterCommand;
import com.agenthub.identity.dto.UserView;
import com.agenthub.identity.infrastructure.entity.UserEntity;
import com.agenthub.identity.infrastructure.repository.UserRepository;
import com.agenthub.identity.infrastructure.security.AuthAuditService;
import com.agenthub.identity.infrastructure.security.AuthRateLimiter;
import com.agenthub.identity.infrastructure.security.TokenProvider;
import com.agenthub.shared.context.RequestContext;
import com.agenthub.shared.exception.BusinessException;
import com.agenthub.shared.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AuthApplicationService implements AuthApplication {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenProvider tokenProvider;
    private final AuthRateLimiter rateLimiter;
    private final AuthAuditService auditService;

    public AuthApplicationService(UserRepository userRepository,
                                  PasswordEncoder passwordEncoder,
                                  TokenProvider tokenProvider,
                                  @Autowired(required = false) AuthRateLimiter rateLimiter,
                                  @Autowired(required = false) AuthAuditService auditService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.rateLimiter = rateLimiter != null ? rateLimiter : new AuthRateLimiter();
        this.auditService = auditService;
    }

    @Override
    @Transactional
    public AuthTokenView register(RegisterCommand cmd) {
        if (cmd == null || cmd.getEmail() == null || cmd.getPassword() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Email and password must not be empty");
        }

        String rateKey = "register:" + cmd.getEmail().trim().toLowerCase();
        if (!rateLimiter.tryAcquire(rateKey, 10, 60)) {
            recordAudit("RATE_LIMIT_EXCEEDED", null, cmd.getEmail(), null, "Register rate limit exceeded");
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Too many registration attempts. Please try again later.");
        }

        if (userRepository.existsByEmail(cmd.getEmail())) {
            recordAudit("REGISTER_FAILED", null, cmd.getEmail(), null, "Email already exists");
            throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS, "User with email already exists");
        }

        String userId = "user-" + UUID.randomUUID().toString().substring(0, 8);
        String hash = passwordEncoder.encode(cmd.getPassword());
        String displayName = cmd.getDisplayName() != null ? cmd.getDisplayName() : cmd.getEmail().split("@")[0];

        UserEntity user = new UserEntity(userId, cmd.getEmail(), hash, displayName, "ACTIVE");
        userRepository.save(user);

        String token = tokenProvider.generateToken(user.getId(), user.getEmail());
        UserView userView = toView(user);

        recordAudit("REGISTER_SUCCESS", user.getId(), user.getEmail(), null, "User successfully registered");
        return new AuthTokenView(token, "Bearer", tokenProvider.getTokenValiditySeconds(), userView);
    }

    @Override
    @Transactional(readOnly = true)
    public AuthTokenView login(LoginCommand cmd) {
        if (cmd == null || cmd.getEmail() == null || cmd.getPassword() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Email and password must not be empty");
        }

        String rateKey = "login:" + cmd.getEmail().trim().toLowerCase();
        if (!rateLimiter.tryAcquire(rateKey, 15, 60)) {
            recordAudit("RATE_LIMIT_EXCEEDED", null, cmd.getEmail(), null, "Login rate limit exceeded");
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Too many login attempts. Please try again later.");
        }

        UserEntity user = userRepository.findByEmail(cmd.getEmail())
                .orElse(null);

        if (user == null || !passwordEncoder.matches(cmd.getPassword(), user.getPasswordHash())) {
            recordAudit("LOGIN_FAILED", user != null ? user.getId() : null, cmd.getEmail(), null, "Invalid credentials");
            throw new BusinessException(ErrorCode.AUTH_FAILED, "Invalid email or password");
        }

        String token = tokenProvider.generateToken(user.getId(), user.getEmail());
        UserView userView = toView(user);

        recordAudit("LOGIN_SUCCESS", user.getId(), user.getEmail(), null, "User successfully logged in");
        return new AuthTokenView(token, "Bearer", tokenProvider.getTokenValiditySeconds(), userView);
    }

    @Override
    public AuthTokenView refreshToken(String oldToken) {
        if (oldToken == null || oldToken.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Token must not be blank");
        }
        String newToken = tokenProvider.refreshToken(oldToken);
        TokenProvider.TokenClaims claims = tokenProvider.parseAndValidateToken(newToken);
        if (claims == null) {
            recordAudit("TOKEN_REFRESH_FAILED", null, null, null, "Failed to validate refreshed token");
            throw new BusinessException(ErrorCode.AUTH_TOKEN_INVALID, "Failed to validate refreshed token");
        }
        UserView userView = getUserById(claims.getUserId());
        recordAudit("TOKEN_REFRESH_SUCCESS", claims.getUserId(), claims.getEmail(), null, "Token successfully rotated");
        return new AuthTokenView(newToken, "Bearer", tokenProvider.getTokenValiditySeconds(), userView);
    }

    @Override
    public void logout(String token) {
        String currentUserId = RequestContext.get().getUserId();
        if (token != null && !token.isBlank()) {
            tokenProvider.invalidateToken(token);
        }
        recordAudit("LOGOUT", currentUserId, null, null, "User logged out and token invalidated");
        RequestContext.clear();
    }

    @Override
    @Transactional(readOnly = true)
    public UserView getCurrentUser() {
        String currentUserId = RequestContext.get().getUserId();
        if (currentUserId == null || currentUserId.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Authentication required");
        }
        return getUserById(currentUserId);
    }

    @Override
    @Transactional(readOnly = true)
    public UserView getUserById(String userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "User not found: " + userId));
        return toView(user);
    }

    @Override
    public String createStreamTicket(String userId, String email) {
        String ticket = tokenProvider.createStreamTicket(userId, email);
        recordAudit("STREAM_TICKET_ISSUED", userId, email, null, "Stream ticket issued for SSE stream");
        return ticket;
    }

    private void recordAudit(String eventType, String userId, String email, String ipAddress, String details) {
        if (auditService != null) {
            auditService.record(eventType, userId, email, ipAddress, details);
        }
    }

    private UserView toView(UserEntity entity) {
        return new UserView(
                entity.getId(),
                entity.getEmail(),
                entity.getDisplayName(),
                entity.getStatus(),
                entity.getCreatedAt()
        );
    }
}
