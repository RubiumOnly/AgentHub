package com.agenthub.shared.exception;

/**
 * Enterprise standard ErrorCode dictionary for AgentHub.
 */
public enum ErrorCode {
    SUCCESS(0, "Operation Successful"),
    PARAM_ERROR(400, "Invalid Request Parameters"),
    UNAUTHORIZED(1001, "Authentication Required"),
    FORBIDDEN(1002, "Access Forbidden"),
    NOT_FOUND(404, "Requested Resource Not Found"),
    CONFLICT(409, "Resource Conflict"),
    INTERNAL_SERVER_ERROR(9001, "Internal Server Error"),

    // Identity & Authentication
    AUTH_FAILED(1003, "Invalid email or password"),
    USER_ALREADY_EXISTS(1004, "User with given email or username already exists"),
    USER_NOT_FOUND(1005, "User not found"),
    AUTH_TOKEN_EXPIRED(1006, "Authentication token has expired"),
    AUTH_TOKEN_INVALID(1007, "Invalid authentication token"),

    // Project & Workspace
    PROJECT_NOT_FOUND(2001, "Project not found"),
    PROJECT_ACCESS_DENIED(2002, "Access to project denied"),
    WORKSPACE_NOT_FOUND(3001, "Workspace not found"),
    WORKSPACE_LOCKED(3002, "Workspace is Currently Locked by Another Task"),
    WORKSPACE_PATH_INVALID(3003, "Invalid workspace path format or illegal characters"),
    WORKSPACE_TRAVERSAL_DENIED(3004, "Directory traversal outside workspace is strictly forbidden"),
    WORKSPACE_GIT_ACCESS_DENIED(3005, "Direct read or modification of .git directory is forbidden"),
    WORKSPACE_GIT_ERROR(3006, "Git Workspace Operation Failed"),
    WORKSPACE_FILE_TOO_LARGE(3007, "File size exceeds allowed limit"),
    WORKSPACE_BINARY_PREVIEW_DENIED(3008, "Binary file cannot be previewed as text"),
    WORKSPACE_RESERVED_DEVICE_DENIED(3009, "Device file name is not allowed"),

    // Audit & Artifacts
    ARTIFACT_NOT_FOUND(3010, "Artifact not found"),
    ARTIFACT_REVIEW_INVALID(3011, "Invalid artifact review operation or status"),

    // Conversation, Team & Message Bus
    CONVERSATION_NOT_FOUND(4001, "Conversation not found"),
    CONVERSATION_ACCESS_DENIED(4003, "Access to conversation denied"),
    MESSAGE_NOT_FOUND(4004, "Message not found"),
    TEAM_NOT_FOUND(4005, "Team not found"),
    TEAM_MEMBER_NOT_FOUND(4006, "Team member not found"),
    LOOP_DETECTED(4007, "Message loop or ping-pong oscillation detected"),
    MAX_TURNS_EXCEEDED(4008, "Maximum conversation turns exceeded"),
    MESSAGE_SEQUENCE_CONFLICT(4009, "Message sequence conflict"),
    MESSAGE_RECIPIENT_NOT_FOUND(4010, "Message recipient not found"),

    // Agent & Provider
    AGENT_NOT_FOUND(5001, "Agent Not Found"),
    AGENT_CLI_UNAVAILABLE(5002, "Agent CLI Runtime Not Available"),
    AGENT_EXECUTION_TIMEOUT(5003, "Agent Execution Timed Out"),
    AGENT_EXECUTION_FAILED(5004, "Agent Execution Failed"),
    PROVIDER_NOT_FOUND(5005, "LLM or CLI Provider not found"),
    PROVIDER_UNAVAILABLE(5006, "LLM Provider Unavailable or Circuit Broken"),
    PROVIDER_RATE_LIMIT(5007, "LLM Provider Rate Limit Exceeded (429)"),
    PROVIDER_AUTH_FAILED(5008, "LLM Provider Authentication Failed"),
    NO_AVAILABLE_PROVIDER(5009, "No Available Provider Satisfying Routing Constraints"),

    // Workflow & Execution
    WORKFLOW_INVALID(6001, "Workflow Definition Invalid or Cyclic"),
    RUN_NOT_FOUND(6002, "Workflow run instance not found"),
    STEP_RUN_NOT_FOUND(6003, "Workflow step run instance not found"),
    APPROVAL_NOT_FOUND(6004, "Approval request not found"),
    IDEMPOTENCY_CONFLICT(6005, "Concurrent run with same idempotency key already exists"),
    WORKFLOW_EXECUTION_FAILED(6006, "Workflow Execution Failed"),
    INVALID_STATE_TRANSITION(6007, "Invalid state machine transition"),
    RUN_ALREADY_FINISHED(6008, "Workflow run is already finished"),
    RUN_CANCELLED(6009, "Workflow run was cancelled"),
    RUN_TIMED_OUT(6010, "Workflow run execution timed out"),
    STEP_RETRY_EXCEEDED(6011, "Step retry limit exceeded"),
    WORKFLOW_DEFINITION_NOT_FOUND(6012, "Workflow definition not found"),
    APPROVAL_ALREADY_DECIDED(6013, "Approval request has already been decided"),

    // Sandbox & Deployment
    DEPLOYMENT_NOT_FOUND(7001, "Deployment record not found");

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
