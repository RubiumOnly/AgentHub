package com.agenthub.adapter.common;

public enum ErrorCode {
    SUCCESS(0, "Operation Successful"),
    PARAM_ERROR(400, "Invalid Request Parameters"),
    UNAUTHORIZED(401, "Authentication Required"),
    FORBIDDEN(403, "Access Forbidden"),
    NOT_FOUND(404, "Requested Resource Not Found"),
    
    // Agent & Provider Specific Codes
    AGENT_NOT_FOUND(1001, "Agent Not Found"),
    AGENT_CLI_UNAVAILABLE(1002, "Agent CLI Runtime Not Available"),
    AGENT_EXECUTION_TIMEOUT(1003, "Agent Execution Timed Out"),
    AGENT_EXECUTION_FAILED(1004, "Agent Execution Failed"),
    PROVIDER_NOT_FOUND(1005, "LLM Provider Not Found"),
    PROVIDER_UNAVAILABLE(1006, "LLM Provider Unavailable or Circuit Broken"),
    PROVIDER_RATE_LIMIT(1007, "LLM Provider Rate Limit Exceeded (429)"),
    PROVIDER_AUTHENTICATION_FAILED(1008, "LLM Provider Authentication Failed"),
    NO_AVAILABLE_PROVIDER(1009, "No Available Provider Satisfying Routing Constraints"),
    
    // Workflow Specific Codes
    WORKFLOW_INVALID(2001, "Workflow Definition Invalid or Cyclic"),
    WORKFLOW_EXECUTION_FAILED(2002, "Workflow Execution Failed"),
    INVALID_STATE_TRANSITION(6007, "Invalid state machine transition"),
    RUN_ALREADY_FINISHED(6008, "Workflow run is already finished"),
    RUN_CANCELLED(6009, "Workflow run was cancelled"),
    RUN_TIMED_OUT(6010, "Workflow run execution timed out"),
    STEP_RETRY_EXCEEDED(6011, "Step retry limit exceeded"),
    
    // Workspace Specific Codes
    WORKSPACE_LOCKED(3001, "Workspace is Currently Locked by Another Task"),
    WORKSPACE_GIT_ERROR(3002, "Git Workspace Operation Failed"),
    WORKSPACE_PATH_INVALID(3003, "Invalid workspace path format or illegal characters"),
    WORKSPACE_TRAVERSAL_DENIED(3004, "Directory traversal outside workspace is strictly forbidden"),
    WORKSPACE_GIT_ACCESS_DENIED(3005, "Direct read or modification of .git directory is forbidden"),
    WORKSPACE_FILE_TOO_LARGE(3007, "File size exceeds allowed limit"),
    WORKSPACE_BINARY_PREVIEW_DENIED(3008, "Binary file cannot be previewed as text"),
    WORKSPACE_RESERVED_DEVICE_DENIED(3009, "Device file name is not allowed"),

    // Audit and Artifact Codes
    ARTIFACT_NOT_FOUND(3010, "Artifact not found"),
    ARTIFACT_REVIEW_INVALID(3011, "Invalid artifact review operation or status"),
    
    // System Error
    INTERNAL_SERVER_ERROR(500, "Internal Server Error");

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
