-- ==============================================================================
-- V12__phase_e_production_ops_and_lock_precision.sql: Phase E Production Ops,
-- Millisecond-Precision Distributed Workspace Lock Expiry for MySQL 8 & H2
-- Compatible with MySQL 8.x and H2 (MySQL Mode)
-- ==============================================================================

ALTER TABLE workspace_locks ADD COLUMN expires_at_epoch_ms BIGINT NOT NULL DEFAULT 0;

CREATE INDEX idx_workspace_locks_expires_ms ON workspace_locks(expires_at_epoch_ms);
