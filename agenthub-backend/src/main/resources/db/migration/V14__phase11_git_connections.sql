-- ==============================================================================
-- V14__phase11_git_connections.sql: Phase 11 Git Provider Connection Lifecycle.
-- Stores per-project Git provider authorizations. Credentials are stored as
-- REFERENCES ONLY (env:/prop:/secret: scheme); resolved secrets never persist.
-- Revocation is idempotent and clears the credential reference.
-- Compatible with MySQL 8.x and H2 (MySQL Mode)
-- ==============================================================================

CREATE TABLE git_connections (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    project_id VARCHAR(64) NOT NULL,
    provider VARCHAR(32) NOT NULL,
    repo_url VARCHAR(512) NOT NULL,
    credential_ref VARCHAR(255),
    default_branch VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    authorized_by VARCHAR(64) NOT NULL,
    authorized_at TIMESTAMP NOT NULL,
    revoked_by VARCHAR(64),
    revoked_at TIMESTAMP,
    last_verified_at TIMESTAMP,
    last_verify_result VARCHAR(32),
    last_verify_detail VARCHAR(512),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_git_connections_project FOREIGN KEY (project_id) REFERENCES projects(id)
);

CREATE INDEX idx_git_connections_project_status ON git_connections(project_id, status);
