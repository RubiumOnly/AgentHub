-- ==============================================================================
-- V5__phase4_workflow_dag_and_orchestration.sql: Phase 4 Workflow DAG & Orchestration
-- Compatible with MySQL 8.x and H2 (MySQL Mode)
-- ==============================================================================

-- 1. Extend workflow_definitions with name, description, updated_at
ALTER TABLE workflow_definitions ADD COLUMN name VARCHAR(128);
ALTER TABLE workflow_definitions ADD COLUMN description VARCHAR(512);
ALTER TABLE workflow_definitions ADD COLUMN updated_at TIMESTAMP;

-- 2. Extend workflow_runs with execution context data (json)
ALTER TABLE workflow_runs ADD COLUMN context_data_json TEXT;

-- 3. Extend step_runs with inputs, outputs, requires_approval
ALTER TABLE step_runs ADD COLUMN inputs_json TEXT;
ALTER TABLE step_runs ADD COLUMN outputs_json TEXT;
ALTER TABLE step_runs ADD COLUMN requires_approval BOOLEAN DEFAULT FALSE;

-- 4. Extend approvals with decision, reviewed_at, comments
ALTER TABLE approvals ADD COLUMN decision VARCHAR(32);
ALTER TABLE approvals ADD COLUMN reviewed_at TIMESTAMP;
ALTER TABLE approvals ADD COLUMN comments VARCHAR(512);

-- 5. Additional indices for workflow query and approval lookups
CREATE INDEX idx_approvals_step_run ON approvals(step_run_id);
CREATE INDEX idx_approvals_status ON approvals(status);
CREATE INDEX idx_approvals_created ON approvals(created_at);
