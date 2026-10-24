package com.agenthub.fixture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phase 10 R3 演示基线测试夹具：V13 已将全部演示种子从生产迁移路径归零，
 * 本夹具仅在 demo/test profile 下按需重灌测试与演示所依赖的基线行
 * （users、agent_definitions、proj-default/ws-default、providers、team-dev-swarm），
 * 幂等可重入，生产 profile 永不加载。
 */
@Component
@Profile({"demo", "test"})
@Order(0)
public class DemoBaselineSeedFixture implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoBaselineSeedFixture.class);

    private final JdbcTemplate jdbcTemplate;

    public DemoBaselineSeedFixture(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedUsers();
        seedAgentDefinitions();
        seedProjectAndWorkspace();
        seedProviders();
        seedTeam();
        log.info("Demo baseline seed fixture ensured (demo/test profile only).");
    }

    private void seedUsers() {
        insertIfAbsent("users", "id", "user-1",
                "INSERT INTO users (id, email, password_hash, display_name, status, created_at, updated_at) " +
                        "VALUES ('user-1', 'admin@agenthub.local', '$2a$10$7EqJtq98hPqEX7fNZaFWoOhiI1F78cZq3yE6lJ11c7K11G0VwQ0kO', 'Administrator', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
        insertIfAbsent("users", "id", "user-dev",
                "INSERT INTO users (id, email, password_hash, display_name, status, created_at, updated_at) " +
                        "VALUES ('user-dev', 'dev@agenthub.local', '$2a$10$7EqJtq98hPqEX7fNZaFWoOhiI1F78cZq3yE6lJ11c7K11G0VwQ0kO', 'Developer', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
    }

    private void seedAgentDefinitions() {
        String sql = "INSERT INTO agent_definitions (id, def_key, name, role, manifest, version, created_at, updated_at) " +
                "VALUES ('%s', '%s', '%s', '%s', '%s', '1.0.0', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)";
        insertIfAbsent("agent_definitions", "id", "def-orch",
                String.format(sql, "def-orch", "Orchestrator", "Orchestrator", "Task Coordinator",
                        "{\"capabilities\":[\"task_decomposition\",\"workflow_planning\"]}"));
        insertIfAbsent("agent_definitions", "id", "def-backend",
                String.format(sql, "def-backend", "BackendArchitect", "Backend Architect", "Backend Engineer",
                        "{\"capabilities\":[\"spring_boot\",\"ddd\",\"sql\"]}"));
        insertIfAbsent("agent_definitions", "id", "def-frontend",
                String.format(sql, "def-frontend", "FrontendEngineer", "Frontend Engineer", "Frontend Engineer",
                        "{\"capabilities\":[\"react\",\"nextjs\",\"tailwind\"]}"));
        insertIfAbsent("agent_definitions", "id", "def-qa",
                String.format(sql, "def-qa", "QAAuditor", "QA Auditor", "QA Engineer",
                        "{\"capabilities\":[\"unit_test\",\"integration_test\",\"code_review\"]}"));
    }

    private void seedProjectAndWorkspace() {
        insertIfAbsent("projects", "id", "proj-default",
                "INSERT INTO projects (id, owner_id, name, description, status, workspace_id, created_at, updated_at) " +
                        "VALUES ('proj-default', 'user-1', 'Default Workspace Project', 'Primary default project workspace', 'ACTIVE', 'ws-default', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
        insertIfAbsent("workspaces", "id", "ws-default",
                "INSERT INTO workspaces (id, project_id, relative_root, git_baseline_commit, created_at, updated_at) " +
                        "VALUES ('ws-default', 'proj-default', 'default', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
    }

    private void seedProviders() {
        String sql = "INSERT INTO providers (id, owner_id, provider_type, base_url, secret_ref, model, status, priority, weight, capabilities, cost_per_million_input, cost_per_million_output, circuit_status, consecutive_failures, created_at, updated_at) " +
                "VALUES ('%s', 'user-1', '%s', '%s', '%s', '%s', 'ACTIVE', %d, 1, '%s', %s, %s, 'CLOSED', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)";
        insertIfAbsent("providers", "id", "prov-openai", String.format(sql,
                "prov-openai", "OPENAI", "https://api.openai.com/v1", "env:OPENAI_API_KEY", "gpt-4o", 100,
                "code,general,reasoning,fast", "5.0", "15.0"));
        insertIfAbsent("providers", "id", "prov-deepseek", String.format(sql,
                "prov-deepseek", "DEEPSEEK", "https://api.deepseek.com", "env:DEEPSEEK_API_KEY", "deepseek-chat", 95,
                "code,general,fast,reasoning", "0.14", "0.28"));
        insertIfAbsent("providers", "id", "prov-anthropic", String.format(sql,
                "prov-anthropic", "ANTHROPIC", "https://api.anthropic.com", "env:ANTHROPIC_API_KEY", "claude-3-5-sonnet-20241022", 90,
                "code,general,reasoning", "3.0", "15.0"));
        insertIfAbsent("providers", "id", "prov-gemini", String.format(sql,
                "prov-gemini", "GEMINI", "https://generativelanguage.googleapis.com", "env:GEMINI_API_KEY", "gemini-1.5-flash", 85,
                "general,fast,long_context", "0.075", "0.30"));
        insertIfAbsent("providers", "id", "prov-ollama", String.format(sql,
                "prov-ollama", "OLLAMA", "http://localhost:11434", "none", "llama3.2:3b", 50,
                "code,general,fast,local", "0.0", "0.0"));
        insertIfAbsent("providers", "id", "prov-mock", String.format(sql,
                "prov-mock", "MOCK", "mock://local", "none", "mock-v1", 10,
                "code,general,fast,reasoning,local", "0.0", "0.0"));
    }

    private void seedTeam() {
        insertIfAbsent("teams", "id", "team-dev-swarm",
                "INSERT INTO teams (id, project_id, name, description, topology, leader_agent_id, max_turns, status, created_at, updated_at) " +
                        "VALUES ('team-dev-swarm', 'proj-default', 'DevDeliverySwarm', 'Full-stack delivery swarm featuring Orchestrator leader with Architect, Coder and Reviewer', 'HIERARCHICAL', 'def-orch', 10, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
        String sql = "INSERT INTO team_members (id, team_id, agent_instance_id, role, role_type, sort_order, responsibilities, can_delegate) " +
                "VALUES ('%s', 'team-dev-swarm', '%s', '%s', '%s', %d, '%s', %s)";
        insertIfAbsent("team_members", "id", "tm-orch", String.format(sql,
                "tm-orch", "def-orch", "Orchestrator", "ORCHESTRATOR", 0,
                "Team coordination, task decomposition, and workflow synthesis", "TRUE"));
        insertIfAbsent("team_members", "id", "tm-backend", String.format(sql,
                "tm-backend", "def-backend", "BackendArchitect", "ARCHITECT", 1,
                "Architecture design, Spring Boot 3 services, and database schema", "TRUE"));
        insertIfAbsent("team_members", "id", "tm-frontend", String.format(sql,
                "tm-frontend", "def-frontend", "FrontendEngineer", "CODER", 2,
                "UI components, Next.js frontend, and user interaction", "TRUE"));
        insertIfAbsent("team_members", "id", "tm-qa", String.format(sql,
                "tm-qa", "def-qa", "QAAuditor", "REVIEWER", 3,
                "Code review, test verification, and quality audit", "FALSE"));
    }

    private void insertIfAbsent(String table, String keyColumn, String keyValue, String insertSql) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE " + keyColumn + " = ?", Integer.class, keyValue);
        if (count == null || count == 0) {
            jdbcTemplate.execute(insertSql);
        }
    }
}
