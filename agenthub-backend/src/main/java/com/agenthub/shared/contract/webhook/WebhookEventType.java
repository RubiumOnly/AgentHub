package com.agenthub.shared.contract.webhook;

/**
 * Standardized Webhook Event Types for AgentHub enterprise external integration.
 * Represents core lifecycle events across Project, Run, Step, Artifact, Approval, and Deployment.
 */
public enum WebhookEventType {

    PROJECT_CREATED("project.created", "PROJECT"),
    WORKSPACE_BASELINE_INITIALIZED("workspace.baseline_initialized", "WORKSPACE"),

    RUN_STARTED("run.started", "RUN"),
    RUN_COMPLETED("run.completed", "RUN"),
    RUN_FAILED("run.failed", "RUN"),
    RUN_CANCELLED("run.cancelled", "RUN"),
    RUN_REJECTED("run.rejected", "RUN"),

    STEP_STARTED("step.started", "STEP"),
    STEP_COMPLETED("step.completed", "STEP"),
    STEP_FAILED("step.failed", "STEP"),
    STEP_REWORK_REQUESTED("step.rework_requested", "STEP"),

    APPROVAL_REQUESTED("approval.requested", "APPROVAL"),
    APPROVAL_DECIDED("approval.decided", "APPROVAL"),

    ARTIFACT_CREATED("artifact.created", "ARTIFACT"),
    QUALITY_GATE_EVALUATED("quality_gate.evaluated", "ARTIFACT"),
    ARTIFACT_ACCEPTED("artifact.accepted", "ARTIFACT"),
    ARTIFACT_REJECTED("artifact.rejected", "ARTIFACT"),
    ARTIFACT_REVERTED("artifact.reverted", "ARTIFACT"),

    DEPLOYMENT_CREATED("deployment.created", "DEPLOYMENT"),
    DEPLOYMENT_HEALTHY("deployment.healthy", "DEPLOYMENT"),
    DEPLOYMENT_FAILED("deployment.failed", "DEPLOYMENT"),
    DEPLOYMENT_STOPPED("deployment.stopped", "DEPLOYMENT");

    private final String eventName;
    private final String resourceType;

    WebhookEventType(String eventName, String resourceType) {
        this.eventName = eventName;
        this.resourceType = resourceType;
    }

    public String getEventName() {
        return eventName;
    }

    public String getResourceType() {
        return resourceType;
    }

    public static WebhookEventType fromEventName(String eventName) {
        if (eventName == null) {
            return null;
        }
        for (WebhookEventType type : values()) {
            if (type.eventName.equalsIgnoreCase(eventName)) {
                return type;
            }
        }
        return null;
    }
}
