package com.agenthub.identity.infrastructure.security;

import com.agenthub.shared.context.RequestContext;
import jakarta.servlet.*;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class AuthFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(AuthFilter.class);

    private final TokenProvider tokenProvider;

    public AuthFilter(TokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (request instanceof HttpServletRequest httpRequest) {
            TokenProvider.TokenClaims claims = null;

            // 1. Bearer Token via Authorization header (Primary & standard mechanism)
            String authHeader = httpRequest.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7).trim();
                claims = tokenProvider.parseAndValidateToken(token);
            }

            // 2. Cookie authentication (agenthub_token) for same-origin browser sessions / SSE
            if (claims == null && httpRequest.getCookies() != null) {
                for (Cookie cookie : httpRequest.getCookies()) {
                    if ("agenthub_token".equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                        claims = tokenProvider.parseAndValidateToken(cookie.getValue().trim());
                        if (claims != null) break;
                    }
                }
            }

            // 3. Short-lived single-use Stream Ticket for SSE without bearer exposure in URL logs
            if (claims == null) {
                String ticket = httpRequest.getParameter("ticket");
                if (ticket == null || ticket.isBlank()) {
                    ticket = httpRequest.getParameter("streamTicket");
                }
                if (ticket != null && !ticket.isBlank()) {
                    claims = tokenProvider.validateAndConsumeStreamTicket(ticket.trim());
                }
            }

            // 4. URL query parameter token fallback (STRONGLY DEPRECATED, strictly forbidden in production)
            if (claims == null) {
                String tokenParam = httpRequest.getParameter("token");
                if (tokenParam == null || tokenParam.isBlank()) {
                    tokenParam = httpRequest.getParameter("accessToken");
                }
                if (tokenParam != null && !tokenParam.isBlank()) {
                    if (tokenProvider.isProd()) {
                        log.warn("Blocked URL query parameter token attempt in production profile on URI [{}]", httpRequest.getRequestURI());
                    } else {
                        claims = tokenProvider.parseAndValidateToken(tokenParam.trim());
                    }
                }
            }

            // Set context if authenticated
            if (claims != null) {
                RequestContext context = RequestContext.get();
                context.setUserId(claims.getUserId());
                context.setUsername(claims.getEmail());
            } else {
                // 5. Header X-User-Id (STRONGLY DEPRECATED, strictly forbidden in production)
                String userIdHeader = httpRequest.getHeader("X-User-Id");
                if (userIdHeader != null && !userIdHeader.isBlank()) {
                    if (tokenProvider.isProd()) {
                        log.warn("Blocked X-User-Id header bypass attempt in production profile on URI [{}]", httpRequest.getRequestURI());
                    } else {
                        RequestContext.get().setUserId(userIdHeader.trim());
                    }
                }
            }
        }

        try {
            chain.doFilter(request, response);
        } finally {
            RequestContext.clear();
        }
    }
}
