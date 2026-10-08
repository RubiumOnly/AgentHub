-- ==============================================================================
-- V3__phase2_workspace_audit_artifacts.sql: Phase 2 Controlled Workspace & Audit
-- ==============================================================================

-- 1. Artifact Review and Governance Columns
ALTER TABLE artifacts ADD COLUMN review_status VARCHAR(32) NOT NULL DEFAULT 'PENDING';
ALTER TABLE artifacts ADD COLUMN reviewed_by VARCHAR(64);
ALTER TABLE artifacts ADD COLUMN reviewed_at TIMESTAMP;
ALTER TABLE artifacts ADD COLUMN review_comment VARCHAR(512);

CREATE INDEX idx_artifacts_step_run ON artifacts(step_run_id);
CREATE INDEX idx_artifacts_review_status ON artifacts(review_status);

-- 2. Multi-instance Workspace Lease Lock Registry
CREATE TABLE IF NOT EXISTS workspace_locks (
    workspace_key VARCHAR(128) NOT NULL PRIMARY KEY,
    owner_id VARCHAR(64) NOT NULL,
    acquired_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    lease_ttl_ms BIGINT NOT NULL
);
