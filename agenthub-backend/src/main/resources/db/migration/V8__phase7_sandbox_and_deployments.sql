-- ==============================================================================
-- V8__phase7_sandbox_and_deployments.sql: Workspace Sandbox & Deployment Automation
-- Compatible with MySQL 8.x and H2 (MySQL Mode)
-- ==============================================================================

-- 1. Extend deployments table with port, commands, health check, sandbox type, container id, logs
ALTER TABLE deployments ADD COLUMN port INT;
ALTER TABLE deployments ADD COLUMN build_command VARCHAR(256);
ALTER TABLE deployments ADD COLUMN start_command VARCHAR(256);
ALTER TABLE deployments ADD COLUMN health_check_path VARCHAR(128);
ALTER TABLE deployments ADD COLUMN log_output TEXT;
ALTER TABLE deployments ADD COLUMN error_message VARCHAR(1024);
ALTER TABLE deployments ADD COLUMN sandbox_type VARCHAR(32) NOT NULL DEFAULT 'LOCAL_PROCESS';
ALTER TABLE deployments ADD COLUMN container_id VARCHAR(128);
ALTER TABLE deployments ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

-- 2. Performance and audit indices for status, port, and query acceleration
CREATE INDEX idx_deployments_status ON deployments(status);
CREATE INDEX idx_deployments_proj_status ON deployments(project_id, status);
CREATE INDEX idx_deployments_port ON deployments(port);
CREATE INDEX idx_deployments_created ON deployments(created_at);
