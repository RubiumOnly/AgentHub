package com.agenthub.sandbox.domain.model;

/**
 * Explicitly supported deployment delivery templates.
 * Arbitrary untrusted host commands are rejected in favor of controlled templates
 * and whitelisted sandbox commands.
 */
public enum DeploymentTemplateType {
    STATIC_WEB_TEMPLATE,
    NODE_SERVICE_TEMPLATE,
    JAVA_SPRING_TEMPLATE,
    CUSTOM_SANDBOX_COMMAND;

    public static DeploymentTemplateType fromStringOrDefault(String value, DeploymentTarget target) {
        if (value != null && !value.isBlank()) {
            for (DeploymentTemplateType type : values()) {
                if (type.name().equalsIgnoreCase(value.trim())) {
                    return type;
                }
            }
        }
        if (target == DeploymentTarget.LOCAL_PROCESS || target == DeploymentTarget.DOCKER_CONTAINER) {
            return NODE_SERVICE_TEMPLATE;
        }
        return STATIC_WEB_TEMPLATE;
    }
}
