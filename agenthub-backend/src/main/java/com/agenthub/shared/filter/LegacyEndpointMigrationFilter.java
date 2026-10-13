package com.agenthub.shared.filter;

import com.agenthub.identity.infrastructure.security.TokenProvider;
import com.agenthub.shared.context.RequestContext;
import com.agenthub.shared.contract.ApiContractHeaders;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;

/**
 * Intercepts deprecated legacy routes (/api/workspace/*, /api/im/*, /api/sandbox/*, /api/runs/*),
 * records invocation telemetry, attaches migration headers, and returns HTTP 410 Gone in production
 * (or when X-Enforce-Legacy-Gone header / agenthub.api.legacy.enforce-gone=true is active).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 15)
public class LegacyEndpointMigrationFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(LegacyEndpointMigrationFilter.class);

    private final LegacyEndpointCallTracker callTracker;
    private final TokenProvider tokenProvider;

    @Value("${agenthub.api.legacy.enforce-gone:false}")
    private volatile boolean enforceGoneOverride;

    @Autowired
    public LegacyEndpointMigrationFilter(LegacyEndpointCallTracker callTracker,
                                         @Autowired(required = false) TokenProvider tokenProvider) {
        this.callTracker = callTracker;
        this.tokenProvider = tokenProvider;
    }

    public void setEnforceGoneOverride(boolean enforceGoneOverride) {
        this.enforceGoneOverride = enforceGoneOverride;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (!(request instanceof HttpServletRequest httpRequest) || !(response instanceof HttpServletResponse httpResponse)) {
            chain.doFilter(request, response);
            return;
        }

        String uri = httpRequest.getRequestURI();
        String legacyPrefix = callTracker.matchLegacyPrefix(uri);
        if (legacyPrefix == null) {
            chain.doFilter(request, response);
            return;
        }

        String migrationTarget = callTracker.resolveMigrationTarget(legacyPrefix);
        httpResponse.setHeader(ApiContractHeaders.HEADER_DEPRECATION, "true");
        httpResponse.setHeader(ApiContractHeaders.HEADER_DEPRECATED_ENDPOINT, legacyPrefix);
        httpResponse.setHeader(ApiContractHeaders.HEADER_MIGRATION_TARGET, migrationTarget);
        httpResponse.setHeader(ApiContractHeaders.HEADER_SUNSET, "Phase-2");
        httpResponse.setHeader(ApiContractHeaders.HEADER_LINK, "<" + migrationTarget + ">; rel=\"successor-version\"");

        boolean enforceHeader = "true".equalsIgnoreCase(httpRequest.getHeader(ApiContractHeaders.HEADER_ENFORCE_LEGACY_GONE));
        boolean shouldReturn410 = enforceGoneOverride
                || enforceHeader
                || (tokenProvider != null && tokenProvider.isProd());

        callTracker.recordCall(legacyPrefix, shouldReturn410);

        if (shouldReturn410) {
            log.warn("Rejected legacy endpoint [{}] with HTTP 410 Gone; migrate to [{}]", uri, migrationTarget);
            String reqId = RequestContext.get().getRequestId();
            long ts = Instant.now().toEpochMilli();
            httpResponse.setStatus(HttpServletResponse.SC_GONE);
            httpResponse.setContentType("application/json;charset=UTF-8");
            String safeUri = escapeJson(uri);
            String safeTarget = escapeJson(migrationTarget);
            String safePrefix = escapeJson(legacyPrefix);
            String safeReqId = escapeJson(reqId != null ? reqId : "");
            String body = String.format(
                    "{\"code\":410,\"message\":\"Legacy endpoint [%s] is retired (410 Gone). Migrate to [%s].\","
                            + "\"data\":{\"deprecatedPrefix\":\"%s\",\"migrationTarget\":\"%s\",\"status\":410},"
                            + "\"timestamp\":%d,\"requestId\":\"%s\"}",
                    safeUri, safeTarget, safePrefix, safeTarget, ts, safeReqId
            );
            httpResponse.getWriter().write(body);
            return;
        }

        chain.doFilter(request, response);
    }

    private static String escapeJson(String raw) {
        if (raw == null) return "";
        return raw.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }
}
