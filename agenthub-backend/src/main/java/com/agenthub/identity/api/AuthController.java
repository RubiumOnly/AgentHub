package com.agenthub.identity.api;

import com.agenthub.adapter.common.Result;
import com.agenthub.identity.application.AuthApplication;
import com.agenthub.identity.dto.AuthTokenView;
import com.agenthub.identity.dto.LoginCommand;
import com.agenthub.identity.dto.RegisterCommand;
import com.agenthub.identity.dto.UserView;
import com.agenthub.shared.context.RequestContext;
import com.agenthub.shared.exception.BusinessException;
import com.agenthub.shared.exception.ErrorCode;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthApplication authApplication;

    public AuthController(AuthApplication authApplication) {
        this.authApplication = authApplication;
    }

    public static class StreamTicketResponse {
        private String ticket;
        private long expiresInSeconds;

        public StreamTicketResponse(String ticket, long expiresInSeconds) {
            this.ticket = ticket;
            this.expiresInSeconds = expiresInSeconds;
        }

        public String getTicket() { return ticket; }
        public long getExpiresInSeconds() { return expiresInSeconds; }
    }

    @PostMapping("/register")
    public Result<AuthTokenView> register(@RequestBody RegisterCommand cmd) {
        AuthTokenView token = authApplication.register(cmd);
        return Result.ok(token);
    }

    @PostMapping("/login")
    public Result<AuthTokenView> login(@RequestBody LoginCommand cmd) {
        AuthTokenView token = authApplication.login(cmd);
        return Result.ok(token);
    }

    @PostMapping("/refresh")
    public Result<AuthTokenView> refresh(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new BusinessException(
                    ErrorCode.UNAUTHORIZED,
                    "Bearer token required to refresh"
            );
        }
        String token = authHeader.substring(7).trim();
        AuthTokenView refreshed = authApplication.refreshToken(token);
        return Result.ok(refreshed);
    }

    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();
            authApplication.logout(token);
        } else {
            authApplication.logout(null);
        }
        return Result.ok();
    }

    @GetMapping("/me")
    public Result<UserView> getCurrentUser() {
        UserView user = authApplication.getCurrentUser();
        return Result.ok(user);
    }

    @PostMapping("/stream-ticket")
    public Result<StreamTicketResponse> createStreamTicket() {
        String currentUserId = RequestContext.get().getUserId();
        String currentEmail = RequestContext.get().getUsername();
        if (currentUserId == null || currentUserId.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Authentication required to generate stream ticket");
        }
        String ticket = authApplication.createStreamTicket(currentUserId, currentEmail);
        return Result.ok(new StreamTicketResponse(ticket, 60));
    }
}
