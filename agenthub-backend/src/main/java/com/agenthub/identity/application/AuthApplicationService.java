package com.agenthub.identity.application;

import com.agenthub.identity.dto.AuthTokenView;
import com.agenthub.identity.dto.LoginCommand;
import com.agenthub.identity.dto.RegisterCommand;
import com.agenthub.identity.dto.UserView;
import com.agenthub.identity.infrastructure.entity.UserEntity;
import com.agenthub.identity.infrastructure.repository.UserRepository;
import com.agenthub.identity.infrastructure.security.TokenProvider;
import com.agenthub.shared.context.RequestContext;
import com.agenthub.shared.exception.BusinessException;
import com.agenthub.shared.exception.ErrorCode;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AuthApplicationService implements AuthApplication {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenProvider tokenProvider;

    public AuthApplicationService(UserRepository userRepository,
                                  PasswordEncoder passwordEncoder,
                                  TokenProvider tokenProvider) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
    }

    @Override
    @Transactional
    public AuthTokenView register(RegisterCommand cmd) {
        if (cmd == null || cmd.getEmail() == null || cmd.getPassword() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Email and password must not be empty");
        }
        if (userRepository.existsByEmail(cmd.getEmail())) {
            throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS, "User with email already exists");
        }

        String userId = "user-" + UUID.randomUUID().toString().substring(0, 8);
        String hash = passwordEncoder.encode(cmd.getPassword());
        String displayName = cmd.getDisplayName() != null ? cmd.getDisplayName() : cmd.getEmail().split("@")[0];

        UserEntity user = new UserEntity(userId, cmd.getEmail(), hash, displayName, "ACTIVE");
        userRepository.save(user);

        String token = tokenProvider.generateToken(user.getId(), user.getEmail());
        UserView userView = toView(user);
        return new AuthTokenView(token, "Bearer", tokenProvider.getTokenValiditySeconds(), userView);
    }

    @Override
    @Transactional(readOnly = true)
    public AuthTokenView login(LoginCommand cmd) {
        if (cmd == null || cmd.getEmail() == null || cmd.getPassword() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Email and password must not be empty");
        }

        UserEntity user = userRepository.findByEmail(cmd.getEmail())
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_FAILED, "Invalid email or password"));

        if (!passwordEncoder.matches(cmd.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.AUTH_FAILED, "Invalid email or password");
        }

        String token = tokenProvider.generateToken(user.getId(), user.getEmail());
        UserView userView = toView(user);
        return new AuthTokenView(token, "Bearer", tokenProvider.getTokenValiditySeconds(), userView);
    }

    @Override
    @Transactional(readOnly = true)
    public UserView getCurrentUser() {
        String currentUserId = RequestContext.get().getUserId();
        if (currentUserId == null || currentUserId.isBlank()) {
            currentUserId = "user-1"; // dev default fallback
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
