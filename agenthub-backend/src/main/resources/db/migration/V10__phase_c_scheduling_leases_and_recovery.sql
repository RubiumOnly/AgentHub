-- ==============================================================================
-- V10__phase_c_scheduling_leases_and_recovery.sql: Phase C Persistent Leases, Recovery & Seed Workflow
-- Compatible with MySQL 8.x and H2 (MySQL Mode)
-- ==============================================================================

-- 1. Extend workflow_runs with persistence leases and restart recovery snapshot
ALTER TABLE workflow_runs ADD COLUMN lease_owner VARCHAR(64);
ALTER TABLE workflow_runs ADD COLUMN lease_until TIMESTAMP;
ALTER TABLE workflow_runs ADD COLUMN heartbeat_at TIMESTAMP;
ALTER TABLE workflow_runs ADD COLUMN attempt INT DEFAULT 1;
ALTER TABLE workflow_runs ADD COLUMN dsl_snapshot TEXT;

-- 2. Extend step_runs with persistence leases
ALTER TABLE step_runs ADD COLUMN lease_owner VARCHAR(64);
ALTER TABLE step_runs ADD COLUMN lease_until TIMESTAMP;
ALTER TABLE step_runs ADD COLUMN heartbeat_at TIMESTAMP;

-- 3. Atomic event sequence uniqueness per workflow run
ALTER TABLE run_events ADD CONSTRAINT uk_run_events_run_seq UNIQUE (run_id, sequence_num);

-- 4. Indices for lease scanning and recovery watchdog
CREATE INDEX idx_wf_runs_lease ON workflow_runs(status, lease_until);
CREATE INDEX idx_step_runs_lease ON step_runs(status, lease_until);

-- 5. Seed default workflow definition for live execution main loop (wf-enterprise-auth-delivery)
INSERT INTO workflow_definitions (
    id, name, description, version, schema_version, dsl_json, checksum, created_at, updated_at
) VALUES (
    'wf-enterprise-auth-delivery',
    '企业级全链路认证与交付工作流',
    '5节点标准软件交付流程：架构设计 -> 后端编码 -> 质量门禁 -> 生产人工审批 -> 自动化交付',
    '1.0.0',
    'v1',
    '{"id":"wf-enterprise-auth-delivery","name":"企业级全链路认证与交付工作流","description":"5节点标准软件交付流程","version":"1.0.0","schemaVersion":"v1","timeoutSeconds":300,"nodes":[{"id":"architect_agent","name":"架构设计与契约审查","type":"AGENT","runtime":"MOCK","prompt":"设计微服务接口契约与数据模型"},{"id":"backend_agent","name":"后端业务编码与单元测试","type":"AGENT","runtime":"MOCK","prompt":"实现领域模型与持久化层"},{"id":"qa_gate","name":"全链路质量门禁与安全扫描","type":"AGENT","runtime":"MOCK","prompt":"执行防御性边界测试与安全检查"},{"id":"human_approval","name":"生产交付人工审批","type":"APPROVAL","requiresApproval":true},{"id":"deploy_agent","name":"受限沙箱交付部署","type":"AGENT","runtime":"MOCK","prompt":"部署预览实例并执行健康探测"}],"edges":[{"source":"architect_agent","target":"backend_agent"},{"source":"backend_agent","target":"qa_gate"},{"source":"qa_gate","target":"human_approval"},{"source":"human_approval","target":"deploy_agent"}]}',
    'sha256-seed-wf-auth-delivery',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);
