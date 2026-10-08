package com.agenthub.shared.exception;

/**
 * Enterprise standard ErrorCode dictionary for AgentHub.
 */
public enum ErrorCode {
    SUCCESS(0, "Operation Successful"),
    PARAM_ERROR(400, "Invalid Request Parameters"),
    UNAUTHORIZED(401, "Authentication Required"),
    FORBIDDEN(403, "Access Forbidden"),
    NOT_FOUND(404, "Requested Resource Not Found"),
    CONFLICT(409, "Resource Conflict"),
    INTERNAL_SERVER_ERROR(500, "Internal Server Error"),

    // Identity & Authentication
    AUTH_FAILED(1101, "Invalid username, email or password"),
    AUTH_TOKEN_EXPIRED(1102, "Authentication token has expired"),
    AUTH_TOKEN_INVALID(1103, "Invalid authentication token"),
    USER_ALREADY_EXISTS(1104, "User with given email or username already exists"),
    USER_NOT_FOUND(1105, "User not found"),

    // Agent & Provider
    AGENT_NOT_FOUND(1001, "Agent Not Found"),
    AGENT_CLI_UNAVAILABLE(1002, "Agent CLI Runtime Not Available"),
    AGENT_EXECUTION_TIMEOUT(1003, "Agent Execution Timed Out"),
    AGENT_EXECUTION_FAILED(1004, "Agent Execution Failed"),
    PROVIDER_NOT_FOUND(1005, "LLM or CLI Provider not found"),

    // Project & Workspace
    PROJECT_NOT_FOUND(1201, "Project not found"),
    PROJECT_ACCESS_DENIED(1202, "Access to project denied"),
    WORKSPACE_NOT_FOUND(1203, "Workspace not found"),
    WORKSPACE_LOCKED(3001, "Workspace is Currently Locked by Another Task"),
    WORKSPACE_GIT_ERROR(3002, "Git Workspace Operation Failed"),
    WORKSPACE_PATH_INVALID(3003, "Invalid workspace path format or illegal characters"),
    WORKSPACE_TRAVERSAL_DENIED(3004, "Directory traversal outside workspace is strictly forbidden"),
    WORKSPACE_GIT_ACCESS_DENIED(3005, "Direct read or modification of .git directory is forbidden"),

    // Conversation & IM
    CONVERSATION_NOT_FOUND(1301, "Conversation not found"),
    CONVERSATION_ACCESS_DENIED(1302, "Access to conversation denied"),
    MESSAGE_NOT_FOUND(1303, "Message not found"),

    // Workflow & Execution
    WORKFLOW_INVALID(2001, "Workflow Definition Invalid or Cyclic"),
    WORKFLOW_EXECUTION_FAILED(2002, "Workflow Execution Failed"),
    RUN_NOT_FOUND(2003, "Workflow run instance not found"),
    STEP_RUN_NOT_FOUND(2004, "Workflow step run instance not found"),
    APPROVAL_NOT_FOUND(2005, "Approval request not found"),
    IDEMPOTENCY_CONFLICT(2006, "Concurrent run with same idempotency key already exists"),

    // Sandbox & Deployment
    DEPLOYMENT_NOT_FOUND(1401, "Deployment record not found");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
