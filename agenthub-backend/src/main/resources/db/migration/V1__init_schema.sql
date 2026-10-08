-- ==============================================================================
-- V1__init_schema.sql: AgentHub Enterprise Modular Monolith Schema Foundation
-- Compatible with MySQL 8.x and H2 (MySQL Mode)
-- ==============================================================================

CREATE TABLE users (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    email VARCHAR(128) NOT NULL UNIQUE,
    password_hash VARCHAR(256) NOT NULL,
    display_name VARCHAR(128),
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE projects (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    owner_id VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(512),
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    workspace_id VARCHAR(64),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_projects_owner FOREIGN KEY (owner_id) REFERENCES users(id)
);

CREATE TABLE workspaces (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    project_id VARCHAR(64) NOT NULL,
    relative_root VARCHAR(256) NOT NULL,
    git_baseline_commit VARCHAR(64),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_workspaces_project FOREIGN KEY (project_id) REFERENCES projects(id)
);

CREATE TABLE agent_definitions (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    def_key VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(128) NOT NULL,
    role VARCHAR(64) NOT NULL,
    manifest TEXT,
    version VARCHAR(32) NOT NULL DEFAULT '1.0.0',
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE providers (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    owner_id VARCHAR(64) NOT NULL,
    provider_type VARCHAR(64) NOT NULL,
    base_url VARCHAR(256),
    secret_ref VARCHAR(128),
    model VARCHAR(128),
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_providers_owner FOREIGN KEY (owner_id) REFERENCES users(id)
);

CREATE TABLE agent_instances (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    owner_id VARCHAR(64) NOT NULL,
    project_id VARCHAR(64),
    definition_id VARCHAR(64) NOT NULL,
    runtime_type VARCHAR(64) NOT NULL,
    provider_id VARCHAR(64),
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_agent_instances_owner FOREIGN KEY (owner_id) REFERENCES users(id),
    CONSTRAINT fk_agent_instances_def FOREIGN KEY (definition_id) REFERENCES agent_definitions(id)
);

CREATE TABLE teams (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    project_id VARCHAR(64),
    name VARCHAR(128) NOT NULL,
    description VARCHAR(512),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE team_members (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    team_id VARCHAR(64) NOT NULL,
    agent_instance_id VARCHAR(64) NOT NULL,
    role VARCHAR(64),
    sort_order INT NOT NULL DEFAULT 0,
    CONSTRAINT fk_team_members_team FOREIGN KEY (team_id) REFERENCES teams(id)
);

CREATE TABLE conversations (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    owner_id VARCHAR(64) NOT NULL,
    project_id VARCHAR(64),
    title VARCHAR(128) NOT NULL,
    type VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
CREATE INDEX idx_conversations_owner ON conversations(owner_id);
CREATE INDEX idx_conversations_proj ON conversations(project_id);

CREATE TABLE conversation_participants (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    conversation_id VARCHAR(64) NOT NULL,
    agent_id VARCHAR(64) NOT NULL,
    joined_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_participants_conv FOREIGN KEY (conversation_id) REFERENCES conversations(id)
);
CREATE INDEX idx_conv_participants ON conversation_participants(conversation_id, agent_id);

CREATE TABLE messages (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    conversation_id VARCHAR(64) NOT NULL,
    sender_id VARCHAR(64) NOT NULL,
    sender_type VARCHAR(32) NOT NULL,
    content TEXT,
    card_payload_json TEXT,
    mentions VARCHAR(256),
    schema_version VARCHAR(16) NOT NULL DEFAULT 'v1',
    sequence_num BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_messages_conv FOREIGN KEY (conversation_id) REFERENCES conversations(id)
);
CREATE INDEX idx_messages_conv_seq ON messages(conversation_id, sequence_num);
CREATE INDEX idx_messages_created ON messages(conversation_id, created_at);

CREATE TABLE workflow_definitions (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    version VARCHAR(32) NOT NULL,
    schema_version VARCHAR(16) NOT NULL DEFAULT 'v1',
    dsl_json TEXT NOT NULL,
    checksum VARCHAR(64),
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE workflow_runs (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    project_id VARCHAR(64) NOT NULL,
    definition_id VARCHAR(64),
    status VARCHAR(32) NOT NULL,
    idempotency_key VARCHAR(128),
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
CREATE INDEX idx_wf_runs_proj ON workflow_runs(project_id);
CREATE INDEX idx_wf_runs_idemp ON workflow_runs(idempotency_key);

CREATE TABLE step_runs (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    run_id VARCHAR(64) NOT NULL,
    node_id VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    attempt INT NOT NULL DEFAULT 1,
    input_ref VARCHAR(256),
    output_ref VARCHAR(256),
    error_message TEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_step_runs_run FOREIGN KEY (run_id) REFERENCES workflow_runs(id)
);
CREATE INDEX idx_step_runs_run_node ON step_runs(run_id, node_id);

CREATE TABLE run_events (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    run_id VARCHAR(64) NOT NULL,
    sequence_num BIGINT NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    payload TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_run_events_run FOREIGN KEY (run_id) REFERENCES workflow_runs(id)
);
CREATE INDEX idx_run_events_run_seq ON run_events(run_id, sequence_num);

CREATE TABLE artifacts (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    run_id VARCHAR(64) NOT NULL,
    step_run_id VARCHAR(64),
    artifact_type VARCHAR(64) NOT NULL,
    path_or_ref VARCHAR(512) NOT NULL,
    checksum VARCHAR(64),
    metadata_json TEXT,
    created_at TIMESTAMP NOT NULL
);
CREATE INDEX idx_artifacts_run ON artifacts(run_id);

CREATE TABLE approvals (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    run_id VARCHAR(64) NOT NULL,
    step_run_id VARCHAR(64),
    status VARCHAR(32) NOT NULL,
    requested_by VARCHAR(64),
    reviewed_by VARCHAR(64),
    decision_reason VARCHAR(512),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
CREATE INDEX idx_approvals_run ON approvals(run_id);

CREATE TABLE deployments (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    project_id VARCHAR(64) NOT NULL,
    artifact_id VARCHAR(64),
    status VARCHAR(32) NOT NULL,
    target VARCHAR(64) NOT NULL,
    url VARCHAR(256),
    started_at TIMESTAMP NOT NULL,
    finished_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL
);
CREATE INDEX idx_deployments_proj ON deployments(project_id);
