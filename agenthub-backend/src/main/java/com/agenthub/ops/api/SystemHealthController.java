package com.agenthub.ops.api;

import com.agenthub.adapter.common.Result;
import com.agenthub.ops.application.OpsApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Operations bounded context controller providing system health, API v1 contract metadata,
 * OpenAPI v1 specification, and legacy endpoint invocation telemetry.
 */
@RestController
public class SystemHealthController {

    private final OpsApplication opsApplication;

    public SystemHealthController() {
        this.opsApplication = null;
    }

    @Autowired
    public SystemHealthController(OpsApplication opsApplication) {
        this.opsApplication = opsApplication;
    }

    @GetMapping("/api/system/health")
    public Result<Map<String, Object>> health() {
        if (opsApplication != null) {
            return Result.ok(opsApplication.getSystemHealth());
        }
        return Result.ok(Map.of(
                "status", "UP",
                "app", "AgentHub Enterprise Core",
                "version", "1.0.0",
                "javaVersion", System.getProperty("java.version"),
                "os", System.getProperty("os.name")
        ));
    }

    @GetMapping("/api/system/contracts/v1")
    public Result<Map<String, Object>> getContractV1() {
        return Result.ok(opsApplication != null ? opsApplication.getApiContractMetadataV1() : Map.of("apiVersion", "v1"));
    }

    @GetMapping("/api/v1/openapi.json")
    public Map<String, Object> getOpenApiV1() {
        return opsApplication != null ? opsApplication.getOpenApiSpecV1() : Map.of("openapi", "3.0.3");
    }

    @GetMapping("/api/system/legacy-endpoints")
    public Result<Map<String, Long>> getLegacyEndpointCounts() {
        return Result.ok(opsApplication != null ? opsApplication.getLegacyEndpointInvocationCounts() : Map.of());
    }
}
