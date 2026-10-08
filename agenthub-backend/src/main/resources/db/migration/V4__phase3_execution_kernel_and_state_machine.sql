-- ==============================================================================
-- V4__phase3_execution_kernel_and_state_machine.sql: Phase 3 Execution Kernel
-- Compatible with MySQL 8.x and H2 (MySQL Mode)
-- ==============================================================================

-- 1. Workflow Runs execution metadata
ALTER TABLE workflow_runs ADD COLUMN cancel_reason VARCHAR(512);
ALTER TABLE workflow_runs ADD COLUMN cancelled_at TIMESTAMP;
ALTER TABLE workflow_runs ADD COLUMN correlation_id VARCHAR(64);

-- 2. Step Runs execution lifecycle metadata
ALTER TABLE step_runs ADD COLUMN started_at TIMESTAMP;
ALTER TABLE step_runs ADD COLUMN finished_at TIMESTAMP;
ALTER TABLE step_runs ADD COLUMN duration_ms BIGINT;
ALTER TABLE step_runs ADD COLUMN correlation_id VARCHAR(64);

-- 3. Performance indices for status querying and scheduler polling
CREATE INDEX idx_wf_runs_status ON workflow_runs(status);
CREATE INDEX idx_step_runs_status ON step_runs(status);
