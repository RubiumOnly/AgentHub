package com.agenthub.sandbox.domain.model;

/**
 * Standard lifecycle status for sandboxed deployments.
 */
public enum DeploymentStatus {
    CREATED,
    BUILDING,
    RUNNING,
    STOPPED,
    FAILED
}
