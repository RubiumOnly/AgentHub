package com.agenthub.sandbox.domain.model;

/**
 * Standard lifecycle status for sandboxed deployments.
 */
public enum DeploymentStatus {
    CREATED,
    BUILD,
    BUILDING,
    START,
    HEALTH_CHECK,
    RUNNING,
    STOP,
    CLEANUP,
    STOPPED,
    FAILED
}
