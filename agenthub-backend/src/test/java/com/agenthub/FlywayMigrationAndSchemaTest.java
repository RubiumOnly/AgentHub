package com.agenthub;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class FlywayMigrationAndSchemaTest {

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("测试 Flyway 迁移成功执行且包含初始架构与种子基线脚本")
    void shouldSuccessfullyApplyFlywayMigrations() {
        MigrationInfo[] applied = flyway.info().applied();
        assertThat(applied).isNotEmpty();
        assertThat(applied.length).isGreaterThanOrEqualTo(2);

        assertThat(applied[0].getVersion().getVersion()).isEqualTo("1");
        assertThat(applied[0].getDescription()).contains("init schema");

        assertThat(applied[1].getVersion().getVersion()).isEqualTo("2");
        assertThat(applied[1].getDescription()).contains("seed system baseline");
    }

    @Test
    @DisplayName("测试所有 18 张核心领域表在数据库中均已成功创建")
    void shouldVerifyAll18FoundationTablesExist() {
        List<String> expectedTables = List.of(
                "USERS",
                "PROJECTS",
                "WORKSPACES",
                "AGENT_DEFINITIONS",
                "PROVIDERS",
                "AGENT_INSTANCES",
                "TEAMS",
                "TEAM_MEMBERS",
                "CONVERSATIONS",
                "CONVERSATION_PARTICIPANTS",
                "MESSAGES",
                "WORKFLOW_DEFINITIONS",
                "WORKFLOW_RUNS",
                "STEP_RUNS",
                "RUN_EVENTS",
                "ARTIFACTS",
                "APPROVALS",
                "DEPLOYMENTS"
        );

        for (String table : expectedTables) {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE UPPER(TABLE_NAME) = ? AND UPPER(TABLE_SCHEMA) = 'PUBLIC'",
                    Integer.class,
                    table
            );
            assertThat(count).as("Table %s should exist in database schema", table).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("测试种子基线数据正确加载默认用户、Agent定义与默认工作区")
    void shouldVerifyBaselineSeedDataLoaded() {
        Integer userCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Integer.class);
        assertThat(userCount).isGreaterThanOrEqualTo(2);

        Integer agentDefCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM agent_definitions", Integer.class);
        assertThat(agentDefCount).isGreaterThanOrEqualTo(4);

        Integer projectCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM projects WHERE id = 'proj-default'", Integer.class);
        assertThat(projectCount).isEqualTo(1);

        Integer workspaceCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM workspaces WHERE id = 'ws-default'", Integer.class);
        assertThat(workspaceCount).isEqualTo(1);
    }
}
