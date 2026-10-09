-- ==============================================================================
-- V9__phase_a_security_revocation_and_audit.sql: Persistent Token Revocation & Auth Audit
-- Compatible with MySQL 8.x and H2 (MySQL Mode)
-- ==============================================================================

CREATE TABLE invalidated_tokens (
    token_id VARCHAR(128) NOT NULL PRIMARY KEY,
    user_id VARCHAR(64),
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE auth_audit_logs (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    event_type VARCHAR(64) NOT NULL,
    user_id VARCHAR(64),
    email VARCHAR(128),
    ip_address VARCHAR(64),
    details VARCHAR(512),
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_invalidated_tokens_expires ON invalidated_tokens(expires_at);
CREATE INDEX idx_auth_audit_logs_user ON auth_audit_logs(user_id);
CREATE INDEX idx_auth_audit_logs_event ON auth_audit_logs(event_type);
