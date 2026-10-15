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

    com.agenthub.shared.contract.PageResultView<com.agenthub.identity.dto.AuthAuditLogView> queryAuditLogs(String eventType, String userId, com.agenthub.shared.contract.PageRequestQuery query);

    com.agenthub.infrastructure.ops.DisasterRecoveryBackupService.BackupManifest triggerBackup(com.agenthub.ops.dto.BackupTriggerCommand cmd);

    com.agenthub.infrastructure.ops.DisasterRecoveryBackupService.BackupManifest verifyBackup(com.agenthub.ops.dto.BackupVerifyCommand cmd);

    com.agenthub.infrastructure.ops.DisasterRecoveryBackupService.RestoreVerificationReport restoreBackup(com.agenthub.ops.dto.BackupRestoreCommand cmd);

    com.agenthub.ops.dto.OpsMetricsSummaryView getMetricsSummary();
}
