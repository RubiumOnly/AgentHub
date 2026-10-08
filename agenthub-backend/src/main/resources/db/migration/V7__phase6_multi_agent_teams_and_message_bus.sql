-- ==============================================================================
-- V7__phase6_multi_agent_teams_and_message_bus.sql: Multi-Agent Teams & Conversation Message Bus
-- Compatible with MySQL 8.x and H2 (MySQL Mode)
-- ==============================================================================

-- 1. Extend teams table with topology, leader agent, max turns, status, and config
ALTER TABLE teams ADD COLUMN topology VARCHAR(32) NOT NULL DEFAULT 'HIERARCHICAL';
ALTER TABLE teams ADD COLUMN leader_agent_id VARCHAR(64);
ALTER TABLE teams ADD COLUMN max_turns INT NOT NULL DEFAULT 10;
ALTER TABLE teams ADD COLUMN config_json TEXT;
ALTER TABLE teams ADD COLUMN status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE';

-- 2. Extend team_members table with role_type, responsibilities, system prompt override, and delegation
ALTER TABLE team_members ADD COLUMN role_type VARCHAR(64) DEFAULT 'CUSTOM';
ALTER TABLE team_members ADD COLUMN responsibilities VARCHAR(512);
ALTER TABLE team_members ADD COLUMN system_prompt_override TEXT;
ALTER TABLE team_members ADD COLUMN can_delegate BOOLEAN DEFAULT TRUE;

-- 3. Extend conversations table with team_id, last_sequence_num, summary, token_count, and status
ALTER TABLE conversations ADD COLUMN team_id VARCHAR(64);
ALTER TABLE conversations ADD COLUMN last_sequence_num BIGINT NOT NULL DEFAULT 0;
ALTER TABLE conversations ADD COLUMN summary TEXT;
ALTER TABLE conversations ADD COLUMN token_count INT NOT NULL DEFAULT 0;
ALTER TABLE conversations ADD COLUMN status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE';

-- 4. Extend messages table with recipient_id, message_type, protocol_type, token_count, and in_reply_to_id
ALTER TABLE messages ADD COLUMN recipient_id VARCHAR(64);
ALTER TABLE messages ADD COLUMN message_type VARCHAR(32) NOT NULL DEFAULT 'BROADCAST';
ALTER TABLE messages ADD COLUMN protocol_type VARCHAR(32) NOT NULL DEFAULT 'NORMAL';
ALTER TABLE messages ADD COLUMN token_count INT NOT NULL DEFAULT 0;
ALTER TABLE messages ADD COLUMN in_reply_to_id VARCHAR(64);

-- 5. High-efficiency indices for routing, visibility, team lookup, and message ordering
CREATE INDEX idx_teams_project ON teams(project_id);
CREATE INDEX idx_conversations_team ON conversations(team_id);
CREATE INDEX idx_messages_conv_recipient ON messages(conversation_id, recipient_id);
CREATE INDEX idx_messages_conv_type ON messages(conversation_id, message_type);

-- 6. Seed default baseline multi-agent team and members
INSERT INTO teams (id, project_id, name, description, topology, leader_agent_id, max_turns, status, created_at, updated_at)
VALUES
('team-dev-swarm', 'proj-default', 'DevDeliverySwarm', 'Full-stack delivery swarm featuring Orchestrator leader with Architect, Coder and Reviewer', 'HIERARCHICAL', 'def-orch', 10, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO team_members (id, team_id, agent_instance_id, role, role_type, sort_order, responsibilities, can_delegate)
VALUES
('tm-orch', 'team-dev-swarm', 'def-orch', 'Orchestrator', 'ORCHESTRATOR', 0, 'Team coordination, task decomposition, and workflow synthesis', TRUE),
('tm-backend', 'team-dev-swarm', 'def-backend', 'BackendArchitect', 'ARCHITECT', 1, 'Architecture design, Spring Boot 3 services, and database schema', TRUE),
('tm-frontend', 'team-dev-swarm', 'def-frontend', 'FrontendEngineer', 'CODER', 2, 'UI components, Next.js frontend, and user interaction', TRUE),
('tm-qa', 'team-dev-swarm', 'def-qa', 'QAAuditor', 'REVIEWER', 3, 'Code review, test verification, and quality audit', FALSE);
