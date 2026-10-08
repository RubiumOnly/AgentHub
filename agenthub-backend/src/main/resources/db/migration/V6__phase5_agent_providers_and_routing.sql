-- ==============================================================================
-- V6__phase5_agent_providers_and_routing.sql: Phase 5 Agent Providers, Dynamic Routing & Token Usages
-- Compatible with MySQL 8.x and H2 (MySQL Mode)
-- ==============================================================================

-- 1. Extend providers table with routing priority, capabilities, pricing, circuit breaker status
ALTER TABLE providers ADD COLUMN priority INT NOT NULL DEFAULT 100;
ALTER TABLE providers ADD COLUMN weight INT NOT NULL DEFAULT 1;
ALTER TABLE providers ADD COLUMN capabilities VARCHAR(256);
ALTER TABLE providers ADD COLUMN cost_per_million_input DOUBLE DEFAULT 0.0;
ALTER TABLE providers ADD COLUMN cost_per_million_output DOUBLE DEFAULT 0.0;
ALTER TABLE providers ADD COLUMN circuit_status VARCHAR(32) NOT NULL DEFAULT 'CLOSED';
ALTER TABLE providers ADD COLUMN consecutive_failures INT NOT NULL DEFAULT 0;
ALTER TABLE providers ADD COLUMN last_failure_at TIMESTAMP;
ALTER TABLE providers ADD COLUMN last_success_at TIMESTAMP;
ALTER TABLE providers ADD COLUMN avg_latency_ms BIGINT DEFAULT 0;

-- 2. Extend agent_definitions table with preferred provider, model, capabilities, fallback options
ALTER TABLE agent_definitions ADD COLUMN preferred_provider_type VARCHAR(64);
ALTER TABLE agent_definitions ADD COLUMN preferred_model VARCHAR(128);
ALTER TABLE agent_definitions ADD COLUMN required_capabilities VARCHAR(256);
ALTER TABLE agent_definitions ADD COLUMN fallback_enabled BOOLEAN DEFAULT TRUE;

-- 3. Create token_usages audit table for granular token accounting and cost audit
CREATE TABLE token_usages (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    run_id VARCHAR(64),
    step_run_id VARCHAR(64),
    provider_id VARCHAR(64),
    provider_type VARCHAR(64) NOT NULL,
    model VARCHAR(128) NOT NULL,
    prompt_tokens INT NOT NULL DEFAULT 0,
    completion_tokens INT NOT NULL DEFAULT 0,
    total_tokens INT NOT NULL DEFAULT 0,
    latency_ms BIGINT NOT NULL DEFAULT 0,
    estimated_cost DOUBLE NOT NULL DEFAULT 0.0,
    status VARCHAR(32) NOT NULL DEFAULT 'SUCCESS',
    error_message VARCHAR(512),
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_token_usages_provider FOREIGN KEY (provider_id) REFERENCES providers(id)
);

-- 4. Create high-efficiency indices for routing, audit, and time-series queries
CREATE INDEX idx_token_usages_run ON token_usages(run_id);
CREATE INDEX idx_token_usages_step_run ON token_usages(step_run_id);
CREATE INDEX idx_token_usages_provider ON token_usages(provider_id);
CREATE INDEX idx_token_usages_created ON token_usages(created_at);
CREATE INDEX idx_providers_status_priority ON providers(status, priority);

-- 5. Seed default baseline multi-provider ecosystem records
INSERT INTO providers (id, owner_id, provider_type, base_url, secret_ref, model, status, priority, weight, capabilities, cost_per_million_input, cost_per_million_output, circuit_status, consecutive_failures, created_at, updated_at)
VALUES
('prov-openai', 'user-1', 'OPENAI', 'https://api.openai.com/v1', 'env:OPENAI_API_KEY', 'gpt-4o', 'ACTIVE', 100, 1, 'code,general,reasoning,fast', 5.0, 15.0, 'CLOSED', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('prov-deepseek', 'user-1', 'DEEPSEEK', 'https://api.deepseek.com', 'env:DEEPSEEK_API_KEY', 'deepseek-chat', 'ACTIVE', 95, 1, 'code,general,fast,reasoning', 0.14, 0.28, 'CLOSED', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('prov-anthropic', 'user-1', 'ANTHROPIC', 'https://api.anthropic.com/v1', 'env:ANTHROPIC_API_KEY', 'claude-3-5-sonnet-20241022', 'ACTIVE', 90, 1, 'code,general,reasoning', 3.0, 15.0, 'CLOSED', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('prov-gemini', 'user-1', 'GEMINI', 'https://generativelanguage.googleapis.com', 'env:GEMINI_API_KEY', 'gemini-1.5-flash', 'ACTIVE', 85, 1, 'general,fast,long_context', 0.075, 0.30, 'CLOSED', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('prov-ollama', 'user-1', 'OLLAMA', 'http://localhost:11434', 'none', 'llama3.2:3b', 'ACTIVE', 50, 1, 'code,general,fast,local', 0.0, 0.0, 'CLOSED', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('prov-mock', 'user-1', 'MOCK', 'mock://local', 'none', 'mock-v1', 'ACTIVE', 10, 1, 'code,general,fast,reasoning,local', 0.0, 0.0, 'CLOSED', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
