package com.agenthub.identity.infrastructure.security;

import com.agenthub.shared.context.RequestContext;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

/**
 * Phase E Security Guard for Management & Prometheus Observability Endpoints.
 * Ensures that while /actuator/health remains accessible for container orchestrator probes,
 * sensitive observability endpoints (/actuator/prometheus, /actuator/metrics, /actuator/info)
 * in production are strictly restricted to:
 *   1. Requests bearing a valid observability scrape token (ACTUATOR_PROMETHEUS_TOKEN),
 *   2. Requests bearing a valid authenticated JWT user session, or
 *   3. Direct internal container network scrapes when trusted-network access is enabled
 *      and no untrusted external X-Forwarded-For / X-Real-IP proxy header is present.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class ActuatorSecurityFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(ActuatorSecurityFilter.class);

    private final TokenProvider tokenProvider;
    private volatile boolean securityEnabledOverride;
    private volatile String scrapeToken;
    private volatile boolean allowTrustedNetwork;

    public ActuatorSecurityFilter(
            TokenProvider tokenProvider,
            @Value("${agenthub.management.security-enabled:false}") boolean securityEnabledOverride,
            @Value("${agenthub.management.scrape-token:}") String scrapeToken,
            @Value("${agenthub.management.allow-trusted-network:true}") boolean allowTrustedNetwork) {
        this.tokenProvider = tokenProvider;
        this.securityEnabledOverride = securityEnabledOverride;
        this.scrapeToken = scrapeToken != null ? scrapeToken.trim() : "";
        this.allowTrustedNetwork = allowTrustedNetwork;
    }

    public void configureForTesting(boolean securityEnabledOverride, String scrapeToken, boolean allowTrustedNetwork) {
        this.securityEnabledOverride = securityEnabledOverride;
        this.scrapeToken = scrapeToken != null ? scrapeToken.trim() : "";
        this.allowTrustedNetwork = allowTrustedNetwork;
    }

    public boolean isEnforcementActive() {
        return securityEnabledOverride || (tokenProvider != null && tokenProvider.isProd());
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (!(request instanceof HttpServletRequest httpRequest) || !(response instanceof HttpServletResponse httpResponse)) {
            chain.doFilter(request, response);
            return;
        }

        String uri = httpRequest.getRequestURI();
        if (uri == null || !uri.startsWith("/actuator")) {
            chain.doFilter(request, response);
            return;
        }

        // Liveness and readiness health probes are always permitted (prod hides internal details via show-details: never)
        if (uri.equals("/actuator/health") || uri.startsWith("/actuator/health/")) {
            chain.doFilter(request, response);
            return;
        }

        if (!isEnforcementActive()) {
            chain.doFilter(request, response);
            return;
        }

        if (isAuthorized(httpRequest)) {
            chain.doFilter(request, response);
            return;
        }

        log.warn("Blocked unauthorized access to management/observability endpoint [{}] from remoteAddr=[{}], XFF=[{}]",
                uri, httpRequest.getRemoteAddr(), httpRequest.getHeader("X-Forwarded-For"));
        httpResponse.setStatus(HttpServletResponse.SC_FORBIDDEN);
        httpResponse.setContentType("application/json;charset=UTF-8");
        httpResponse.getWriter().write("{\"code\":1002,\"message\":\"Management/Prometheus endpoint requires trusted network or valid observability credentials\",\"data\":null}");
    }

    public boolean isAuthorized(HttpServletRequest httpRequest) {
        // 1. Check dedicated observability scrape token (X-Observability-Token or Authorization: Bearer <scrapeToken>)
        if (scrapeToken != null && !scrapeToken.isBlank() && !isPlaceholderToken(scrapeToken)) {
            String obsHeader = httpRequest.getHeader("X-Observability-Token");
            if (obsHeader != null && constantTimeEquals(obsHeader.trim(), scrapeToken)) {
                return true;
            }
            String authHeader = httpRequest.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String bearer = authHeader.substring(7).trim();
                if (constantTimeEquals(bearer, scrapeToken)) {
                    return true;
                }
            }
        }

        // 2. Check authenticated JWT principal in RequestContext
        String currentUserId = RequestContext.get().getUserId();
        if (currentUserId != null && !currentUserId.isBlank()) {
            return true;
        }

        // 3. Check trusted internal network (loopback / private Docker bridge subnet) ONLY when not proxied from external IP
        if (allowTrustedNetwork) {
            if (!isPrivateOrLoopbackIp(httpRequest.getRemoteAddr())) {
                return false;
            }
            String xff = httpRequest.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) {
                String[] hops = xff.split(",");
                for (String hop : hops) {
                    if (!isPrivateOrLoopbackIp(hop.trim())) {
                        return false;
                    }
                }
            }
            String realIp = httpRequest.getHeader("X-Real-IP");
            if (realIp != null && !realIp.isBlank()) {
                if (!isPrivateOrLoopbackIp(realIp.trim())) {
                    return false;
                }
            }
            return true;
        }

        return false;
    }

    private boolean isPlaceholderToken(String token) {
        String lower = token.toLowerCase(Locale.ROOT);
        return lower.contains("changeme") || lower.contains("placeholder") || lower.contains("your-");
    }

    public static boolean isPrivateOrLoopbackIp(String ip) {
        if (ip == null || ip.isBlank()) {
            return false;
        }
        String normalized = ip.trim().toLowerCase(Locale.ROOT);
        if ("127.0.0.1".equals(normalized) || "::1".equals(normalized) || "0:0:0:0:0:0:0:1".equals(normalized) || "localhost".equals(normalized)) {
            return true;
        }
        if (normalized.startsWith("10.") || normalized.startsWith("192.168.")) {
            return true;
        }
        if (normalized.startsWith("172.")) {
            String[] parts = normalized.split("\\.");
            if (parts.length >= 2) {
                try {
                    int secondOctet = Integer.parseInt(parts[1]);
                    return secondOctet >= 16 && secondOctet <= 31;
                } catch (NumberFormatException ignored) {
                    return false;
                }
            }
        }
        return false;
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
