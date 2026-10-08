-- ==============================================================================
-- V2__seed_system_baseline.sql: Initial System Seed Data
-- ==============================================================================

-- Default System Users (password: admin123, dev123)
-- BCrypt hash: $2a$10$7EqJtq98hPqEX7fNZaFWoOhiI1F78cZq3yE6lJ11c7K11G0VwQ0kO
INSERT INTO users (id, email, password_hash, display_name, status, created_at, updated_at)
VALUES 
('user-1', 'admin@agenthub.local', '$2a$10$7EqJtq98hPqEX7fNZaFWoOhiI1F78cZq3yE6lJ11c7K11G0VwQ0kO', 'Administrator', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('user-dev', 'dev@agenthub.local', '$2a$10$7EqJtq98hPqEX7fNZaFWoOhiI1F78cZq3yE6lJ11c7K11G0VwQ0kO', 'Developer', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Default Agent Definitions
INSERT INTO agent_definitions (id, def_key, name, role, manifest, version, created_at, updated_at)
VALUES
('def-orch', 'Orchestrator', 'Orchestrator', 'Task Coordinator', '{"capabilities":["task_decomposition","workflow_planning"]}', '1.0.0', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('def-backend', 'BackendArchitect', 'Backend Architect', 'Backend Engineer', '{"capabilities":["spring_boot","ddd","sql"]}', '1.0.0', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('def-frontend', 'FrontendEngineer', 'Frontend Engineer', 'Frontend Engineer', '{"capabilities":["react","nextjs","tailwind"]}', '1.0.0', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('def-qa', 'QAAuditor', 'QA Auditor', 'QA Engineer', '{"capabilities":["unit_test","integration_test","code_review"]}', '1.0.0', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Default Project and Workspace
INSERT INTO projects (id, owner_id, name, description, status, workspace_id, created_at, updated_at)
VALUES
('proj-default', 'user-1', 'Default Workspace Project', 'Primary default project workspace', 'ACTIVE', 'ws-default', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO workspaces (id, project_id, relative_root, git_baseline_commit, created_at, updated_at)
VALUES
('ws-default', 'proj-default', 'default', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
