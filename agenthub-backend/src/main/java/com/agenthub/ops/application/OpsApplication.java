package com.agenthub.ops.application;

import java.util.Map;

/**
 * Operations bounded context application contract for system health, API v1 contracts,
 * OpenAPI specification snapshot, and legacy endpoint migration telemetry.
 */
public interface OpsApplication {

    Map<String, Object> getSystemHealth();

    Map<String, Object> getApiContractMetadataV1();

    Map<String, Object> getOpenApiSpecV1();

    Map<String, Long> getLegacyEndpointInvocationCounts();
}
