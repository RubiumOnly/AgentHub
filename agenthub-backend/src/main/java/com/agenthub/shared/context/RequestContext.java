package com.agenthub.shared.context;

import java.util.UUID;

/**
 * ThreadLocal-based context holding request-scoped metadata such as
 * requestId, correlationId, and authenticated user information.
 */
public class RequestContext {

    private static final ThreadLocal<RequestContext> CURRENT_CONTEXT = new ThreadLocal<>();

    private String requestId;
    private String correlationId;
    private String userId;
    private String username;

    public RequestContext() {}

    public RequestContext(String requestId, String correlationId, String userId, String username) {
        this.requestId = requestId;
        this.correlationId = correlationId;
        this.userId = userId;
        this.username = username;
    }

    public static RequestContext get() {
        RequestContext ctx = CURRENT_CONTEXT.get();
        if (ctx == null) {
            ctx = new RequestContext();
            ctx.setRequestId("req-" + UUID.randomUUID().toString().substring(0, 8));
            ctx.setCorrelationId(ctx.getRequestId());
            CURRENT_CONTEXT.set(ctx);
        }
        return ctx;
    }

    public static void set(RequestContext context) {
        CURRENT_CONTEXT.set(context);
    }

    public static void clear() {
        CURRENT_CONTEXT.remove();
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }
}
