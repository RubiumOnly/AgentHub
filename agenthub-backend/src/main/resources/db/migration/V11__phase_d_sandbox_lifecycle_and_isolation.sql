-- ==============================================================================
-- V11__phase_d_sandbox_lifecycle_and_isolation.sql: Phase D Sandbox Lifecycle,
-- Process/Container Tracking, Log Cursor, Template Type, and Reconciliation
-- Compatible with MySQL 8.x and H2 (MySQL Mode)
-- ==============================================================================

ALTER TABLE deployments ADD COLUMN process_id BIGINT;
ALTER TABLE deployments ADD COLUMN working_directory VARCHAR(512);
ALTER TABLE deployments ADD COLUMN log_cursor BIGINT NOT NULL DEFAULT 0;
ALTER TABLE deployments ADD COLUMN termination_reason VARCHAR(256);
ALTER TABLE deployments ADD COLUMN template_type VARCHAR(64) NOT NULL DEFAULT 'STATIC_WEB_TEMPLATE';
ALTER TABLE deployments ADD COLUMN lifecycle_trace VARCHAR(512);

CREATE INDEX idx_deployments_artifact ON deployments(artifact_id);
