package com.agenthub.adapter.common;

public enum ErrorCode {
    SUCCESS(0, "Operation Successful"),
    PARAM_ERROR(400, "Invalid Request Parameters"),
    UNAUTHORIZED(401, "Authentication Required"),
    FORBIDDEN(403, "Access Forbidden"),
    NOT_FOUND(404, "Requested Resource Not Found"),
    
    // Agent Specific Codes
    AGENT_NOT_FOUND(1001, "Agent Not Found"),
    AGENT_CLI_UNAVAILABLE(1002, "Agent CLI Runtime Not Available"),
    AGENT_EXECUTION_TIMEOUT(1003, "Agent Execution Timed Out"),
    AGENT_EXECUTION_FAILED(1004, "Agent Execution Failed"),
    
    // Workflow Specific Codes
    WORKFLOW_INVALID(2001, "Workflow Definition Invalid or Cyclic"),
    WORKFLOW_EXECUTION_FAILED(2002, "Workflow Execution Failed"),
    
    // Workspace Specific Codes
    WORKSPACE_LOCKED(3001, "Workspace is Currently Locked by Another Task"),
    WORKSPACE_GIT_ERROR(3002, "Git Workspace Operation Failed"),
    
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
