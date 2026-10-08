package com.agenthub.identity.infrastructure.security;

import com.agenthub.shared.context.RequestContext;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class AuthFilter implements Filter {

    private final TokenProvider tokenProvider;

    public AuthFilter(TokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (request instanceof HttpServletRequest httpRequest) {
            String authHeader = httpRequest.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7).trim();
                TokenProvider.TokenClaims claims = tokenProvider.parseAndValidateToken(token);
                if (claims != null) {
                    RequestContext context = RequestContext.get();
                    context.setUserId(claims.getUserId());
                    context.setUsername(claims.getEmail());
                }
            } else {
                // If X-User-Id header is passed (for service-to-service or dev convenience)
                String userIdHeader = httpRequest.getHeader("X-User-Id");
                if (userIdHeader != null && !userIdHeader.isBlank()) {
                    RequestContext.get().setUserId(userIdHeader);
                } else if (RequestContext.get().getUserId() == null) {
                    // Default fallback to user-1 for local development compatibility
                    RequestContext.get().setUserId("user-1");
                    RequestContext.get().setUsername("admin@agenthub.local");
                }
            }
        }
        chain.doFilter(request, response);
    }
}
