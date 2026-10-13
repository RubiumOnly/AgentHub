package com.agenthub.shared.filter;

import com.agenthub.shared.context.RequestContext;
import com.agenthub.shared.contract.ApiContractHeaders;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

/**
 * Filter to establish Request-ID, Correlation-ID, API version (v1), and UTC timezone headers.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestCorrelationFilter implements Filter {

    public static final String HEADER_REQUEST_ID = "X-Request-ID";
    public static final String HEADER_CORRELATION_ID = "X-Correlation-ID";
    public static final String MDC_REQUEST_ID = "requestId";
    public static final String MDC_CORRELATION_ID = "correlationId";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (request instanceof HttpServletRequest httpRequest && response instanceof HttpServletResponse httpResponse) {
            String requestId = httpRequest.getHeader(HEADER_REQUEST_ID);
            if (requestId == null || requestId.isBlank()) {
                requestId = "req-" + UUID.randomUUID().toString().substring(0, 8);
            }

            String correlationId = httpRequest.getHeader(HEADER_CORRELATION_ID);
            if (correlationId == null || correlationId.isBlank()) {
                correlationId = requestId;
            }

            RequestContext context = RequestContext.get();
            context.setRequestId(requestId);
            context.setCorrelationId(correlationId);

            MDC.put(MDC_REQUEST_ID, requestId);
            MDC.put(MDC_CORRELATION_ID, correlationId);

            httpResponse.setHeader(HEADER_REQUEST_ID, requestId);
            httpResponse.setHeader(HEADER_CORRELATION_ID, correlationId);
            httpResponse.setHeader("X-Trace-Id", correlationId);
            httpResponse.setHeader(ApiContractHeaders.HEADER_API_VERSION, ApiContractHeaders.API_VERSION);
            httpResponse.setHeader(ApiContractHeaders.HEADER_SERVER_TIMEZONE, ApiContractHeaders.SERVER_TIMEZONE);

            try {
                chain.doFilter(request, response);
            } finally {
                RequestContext.clear();
                MDC.remove(MDC_REQUEST_ID);
                MDC.remove(MDC_CORRELATION_ID);
            }
        } else {
            chain.doFilter(request, response);
        }
    }
}
