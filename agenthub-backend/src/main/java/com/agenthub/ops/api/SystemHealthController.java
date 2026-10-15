package com.agenthub.ops.api;

import com.agenthub.adapter.common.Result;
import com.agenthub.identity.dto.AuthAuditLogView;
import com.agenthub.infrastructure.ops.DisasterRecoveryBackupService;
import com.agenthub.ops.application.OpsApplication;
import com.agenthub.ops.dto.BackupRestoreCommand;
import com.agenthub.ops.dto.BackupTriggerCommand;
import com.agenthub.ops.dto.BackupVerifyCommand;
import com.agenthub.ops.dto.OpsMetricsSummaryView;
import com.agenthub.shared.contract.PageRequestQuery;
import com.agenthub.shared.contract.PageResultView;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Operations bounded context controller providing system health, API v1 contract metadata,
 * OpenAPI v1 specification, legacy endpoint invocation telemetry, audit logs, metrics summary,
 * and disaster recovery backup/restore operations.
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

    @GetMapping("/api/system/audit-logs")
    public Result<PageResultView<AuthAuditLogView>> getAuditLogs(
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequestQuery query = PageRequestQuery.of(page, size);
        if (opsApplication == null) {
            return Result.ok(query.slice(List.of()));
        }
        return Result.ok(opsApplication.queryAuditLogs(eventType, userId, query));
    }

    @GetMapping("/api/system/metrics/summary")
    public Result<OpsMetricsSummaryView> getMetricsSummary() {
        if (opsApplication == null) {
            return Result.ok(new OpsMetricsSummaryView("UP", Map.of(), Map.of(), "HEALTHY"));
        }
        return Result.ok(opsApplication.getMetricsSummary());
    }

    @PostMapping("/api/system/backup")
    public Result<DisasterRecoveryBackupService.BackupManifest> triggerBackup(
            @RequestBody(required = false) BackupTriggerCommand cmd) {
        if (opsApplication == null) {
            return Result.fail(500, "OpsApplication unavailable");
        }
        BackupTriggerCommand request = cmd != null ? cmd : new BackupTriggerCommand(null, null);
        return Result.ok(opsApplication.triggerBackup(request));
    }

    @PostMapping("/api/system/backup/verify")
    public Result<DisasterRecoveryBackupService.BackupManifest> verifyBackup(
            @Valid @RequestBody BackupVerifyCommand cmd) {
        if (opsApplication == null) {
            return Result.fail(500, "OpsApplication unavailable");
        }
        return Result.ok(opsApplication.verifyBackup(cmd));
    }

    @PostMapping("/api/system/backup/restore")
    public Result<DisasterRecoveryBackupService.RestoreVerificationReport> restoreBackup(
            @Valid @RequestBody BackupRestoreCommand cmd) {
        if (opsApplication == null) {
            return Result.fail(500, "OpsApplication unavailable");
        }
        return Result.ok(opsApplication.restoreBackup(cmd));
    }
}
