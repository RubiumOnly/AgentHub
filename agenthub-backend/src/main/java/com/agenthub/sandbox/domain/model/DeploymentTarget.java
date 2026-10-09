package com.agenthub.sandbox.domain.model;

/**
 * Target runtime environment for deployments.
 */
public enum DeploymentTarget {
    STATIC_PREVIEW,
    LOCAL_PROCESS,
    DOCKER_CONTAINER,
    DOCKER_NGINX
}
