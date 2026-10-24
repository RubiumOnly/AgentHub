package com.agenthub;

import com.agenthub.identity.infrastructure.security.TokenProvider;
import com.agenthub.shared.filter.LegacyEndpointCallTracker;
import com.agenthub.shared.filter.LegacyEndpointMigrationFilter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 9（可信基线、AOCI 接入与治理守卫）自动化测试套件。
 * 覆盖：
 * 1. 唯一权威长期计划（docs/AGENTHUB_PRODUCT_LONG_TERM_PLAN.md）与废弃计划/旧认知索引零残留及文档口径校准校验；
 * 2. 前端源码（agenthub-frontend/src）零 localhost、零 /api/workspace/ 旧接口调用、零宿主机绝对路径硬编码、零非演示模式静默 Mock 回退及默认 ID 冻结校验；
 * 3. 后端源码（agenthub-backend/src/main/java）默认常量（"user-1" / "proj-default" / "run-default"）按相对路径+出现次数上限双重冻结及生产环境阻断 /api/workspace/* 校验；
 * 4. 22 张 Flyway 核心数据库表、最多 18 个 @RestController 与 docs/ARCHITECTURE.md、docs/API.md、docs/CURRENT_CAPABILITY_BOUNDARIES.md 词汇及矩阵全量对齐校验；
 * 5. 受管范围（Managed Scope）与 Flyway V1-V12 迁移脚本完整性及固定 SHA-256 指纹基线校验。
 */
@SpringBootTest
@ActiveProfiles("test")
class Phase9BaselineAndGovernanceGuardTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Flyway flyway;

    private static final Map<String, Integer> FROZEN_BACKEND_USER_1_MAX_COUNTS = Map.of();

    private static final Map<String, Integer> FROZEN_BACKEND_PROJ_DEFAULT_MAX_COUNTS = Map.of();

    private static final Map<String, Integer> FROZEN_BACKEND_RUN_DEFAULT_MAX_COUNTS = Map.of();

    private static final Map<String, Integer> FROZEN_FRONTEND_PROJ_DEFAULT_MAX_COUNTS = Map.of();

    private static final List<String> EXPECTED_22_TABLES = List.of(
            "users",
            "projects",
            "workspaces",
            "workspace_locks",
            "agent_definitions",
            "agent_instances",
            "providers",
            "token_usages",
            "teams",
            "team_members",
            "conversations",
            "conversation_participants",
            "messages",
            "workflow_definitions",
            "workflow_runs",
            "step_runs",
            "run_events",
            "artifacts",
            "approvals",
            "deployments",
            "invalidated_tokens",
            "auth_audit_logs"
    );

    private static final Map<String, String> EXPECTED_12_MIGRATION_SHA256;
    static {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("V1__init_schema.sql", "4494f50553cc572083af09345b1a4abab7a099f287c0e41aaa6b9ac8535f18aa");
        map.put("V2__seed_system_baseline.sql", "41815733fe55e4226e6af3b6d46f988cd672785894732c7bebb2b76aafd620b6");
        map.put("V3__phase2_workspace_audit_artifacts.sql", "90aa1236f311fb2add5a36864c589ff8e80e9fddc8aea3e559c904a4cbc30db0");
        map.put("V4__phase3_execution_kernel_and_state_machine.sql", "117417311b8059624a8c4cbee3dee640ca3eee9981a0e6eede3f6e8e41f340c5");
        map.put("V5__phase4_workflow_dag_and_orchestration.sql", "973f0ee63cc1d584697654e4f4aee38b4a4537dc9c640ce7dd0e15e89b7c65b4");
        map.put("V6__phase5_agent_providers_and_routing.sql", "6f47d9d8d6d376432c4d2cff8d8657b92e20bc63c73e25fc11658cee74fb3c54");
        map.put("V7__phase6_multi_agent_teams_and_message_bus.sql", "65e445b0c9dc7762f026159f18f5c041b7e04384308c00f49c22e192fdc28499");
        map.put("V8__phase7_sandbox_and_deployments.sql", "5d38aacf60cd1140b3e541fec26ded5af5a9ead8725515d4d4cc5a7aa60a72b6");
        map.put("V9__phase_a_security_revocation_and_audit.sql", "bc45b7615671e4e96b8446691cea2b91280e404971be1f88353aaa1fc37f51b0");
        map.put("V10__phase_c_scheduling_leases_and_recovery.sql", "20a083d3abe365adc9f0169c01f02b7e926354b5f6885c3c7e865e8f0b5a4a79");
        map.put("V11__phase_d_sandbox_lifecycle_and_isolation.sql", "44ee1c979be0786055a0d098af33e989deda448e4a6530df1354a780de0968df");
        map.put("V12__phase_e_production_ops_and_lock_precision.sql", "fe48a123e581a88ef2fb50e276bd77f8286f6a81ed000fb0fb6f2236a4bf209b");
        EXPECTED_12_MIGRATION_SHA256 = Map.copyOf(map);
    }

    @Test
    @DisplayName("1. 唯一权威长期计划、废弃认知/计划文件零残留与全仓文档诚实口径校验")
    void shouldEnforceSingleAuthoritativePlanAndCalibratedDocs() throws IOException {
        Path repoRoot = resolveRepositoryRoot();

        // 1) 已废弃的认知索引与旧长期计划必须已物理删除，严禁复活
        assertThat(Files.exists(repoRoot.resolve("ARCHITECTURE_INDEX.md")))
                .as("Deprecated ARCHITECTURE_INDEX.md must remain deleted")
                .isFalse();
        assertThat(Files.exists(repoRoot.resolve("docs/AGENTHUB_LONG_TERM_PLAN.md")))
                .as("Deprecated docs/AGENTHUB_LONG_TERM_PLAN.md must remain deleted")
                .isFalse();
        assertThat(Files.exists(repoRoot.resolve("docs/AGENTHUB_NEXT_LONG_TERM_PLAN.md")))
                .as("Deprecated docs/AGENTHUB_NEXT_LONG_TERM_PLAN.md must remain deleted")
                .isFalse();

        // 2) 唯一权威长期计划必须存在且符合 v4.0 的当前阶段结构；历史 Phase 0 章节不再作为执行入口
        Path longTermPlan = repoRoot.resolve("docs/AGENTHUB_PRODUCT_LONG_TERM_PLAN.md");
        assertThat(Files.exists(longTermPlan)).isTrue();
        String planContent = Files.readString(longTermPlan, StandardCharsets.UTF_8);
        assertThat(planContent)
                .contains("> 版本：v4.0")
                .contains("## 6. 24～36 个月阶段路线")
                .contains("### Phase 9：可信基线、AOCI 接入与瘦身冻结")
                .contains("### Phase 10：核心内核收敛与兼容路径退场")
                .contains("### Phase 15：受控 Beta、生态边界与维护期")
                .contains("## 10. 阶段证据模板")
                .contains("AOCI 不可用时明确记录阻塞")
                .doesNotContain("9.1 Phase 0 Product Brief 正式基线")
                .doesNotContain("9.4 Phase 0 阶段验收记录");

        // 3) 所有当前 Markdown（长期计划除外）严禁残留旧计划引用或未经实测的夸大表述。
        // 动态扫描允许 Phase 9 删除重复文档，而不会因为测试仍列出旧文件而阻止瘦身。
        List<Path> activeDocs = new ArrayList<>();
        activeDocs.add(repoRoot.resolve("README.md"));
        try (Stream<Path> docs = Files.walk(repoRoot.resolve("docs"))) {
            docs.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".md"))
                    .filter(path -> !path.equals(longTermPlan))
                    .forEach(activeDocs::add);
        }

        for (Path doc : activeDocs) {
            assertThat(Files.exists(doc)).as("Active documentation must exist: " + doc).isTrue();
            String content = Files.readString(doc, StandardCharsets.UTF_8);
            assertThat(content)
                    .as("Doc %s must not reference deleted plans or uncalibrated marketing claims", doc.getFileName())
                    .doesNotContain("ARCHITECTURE_INDEX.md")
                    .doesNotContain("AGENTHUB_LONG_TERM_PLAN.md")
                    .doesNotContain("AGENTHUB_NEXT_LONG_TERM_PLAN.md")
                    .doesNotContain("生产级")
                    .doesNotContain("全部完成")
                    .doesNotContain("终局里程碑")
                    .doesNotContain("100% 圆满交付收官")
                    .doesNotContain("真实全链路")
                    .doesNotContain("10,000+ QPS");
        }

        // 4) CHANGELOG.md 作为历史变更记录，必须标明非当前能力权威来源且不含夸大营销词
        Path changelog = repoRoot.resolve("CHANGELOG.md");
        assertThat(Files.exists(changelog)).isTrue();
        String changelogContent = Files.readString(changelog, StandardCharsets.UTF_8);
        assertThat(changelogContent)
                .contains("docs/AGENTHUB_PRODUCT_LONG_TERM_PLAN.md")
                .doesNotContain("生产级")
                .doesNotContain("全部完成")
                .doesNotContain("终局里程碑")
                .doesNotContain("100% 圆满交付收官")
                .doesNotContain("真实全链路")
                .doesNotContain("10,000+ QPS");
    }

    @Test
    @DisplayName("2. 前端源码零 localhost、零 /api/workspace/ 旧接口调用、零非演示模式静默 Mock 回退与默认 ID 冻结校验")
    void shouldEnforceFrontendZeroLocalhostAndZeroLegacyWorkspaceCalls() throws IOException {
        Path repoRoot = resolveRepositoryRoot();
        Path frontendSrc = repoRoot.resolve("agenthub-frontend/src");
        assertThat(Files.isDirectory(frontendSrc)).isTrue();

        List<Path> tsFiles = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(frontendSrc)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return name.endsWith(".ts") || name.endsWith(".tsx");
                    })
                    .forEach(tsFiles::add);
        }

        assertThat(tsFiles).hasSize(39);

        List<String> unexpectedFrontendProjDefault = new ArrayList<>();
        List<String> unexpectedFrontendLegacyIm = new ArrayList<>();

        for (Path file : tsFiles) {
            String relPath = frontendSrc.relativize(file).toString().replace('\\', '/');
            String content = Files.readString(file, StandardCharsets.UTF_8);
            String lower = content.toLowerCase(Locale.ROOT);
            assertThat(content)
                    .as("Frontend file %s must not hardcode localhost, 127.0.0.1, user-1, or legacy /api/workspace/ endpoints", relPath)
                    .doesNotContain("localhost")
                    .doesNotContain("127.0.0.1")
                    .doesNotContain("/api/workspace/")
                    .doesNotContain("\"user-1\"")
                    .doesNotContain("'user-1'");
            assertThat(lower)
                    .as("Frontend file %s must not hardcode host workspace path d:/work/agenthub", relPath)
                    .doesNotContain("d:/work/agenthub")
                    .doesNotContain("d:\\work\\agenthub");

            int projDefaultCount = countOccurrences(content, "\"proj-default\"") + countOccurrences(content, "'proj-default'");
            int allowedProjDefault = FROZEN_FRONTEND_PROJ_DEFAULT_MAX_COUNTS.getOrDefault(relPath, 0);
            if (projDefaultCount > allowedProjDefault) {
                unexpectedFrontendProjDefault.add(relPath + " (count=" + projDefaultCount + ", max=" + allowedProjDefault + ")");
            }

            if (content.contains("/api/im/")) {
                unexpectedFrontendLegacyIm.add(relPath);
            }
        }

        assertThat(unexpectedFrontendProjDefault)
                .as("No frontend file may exceed Phase 0 frozen count for proj-default")
                .isEmpty();
        assertThat(unexpectedFrontendLegacyIm)
                .as("No frontend component outside services/api.ts may call legacy /api/im/ routes")
                .isEmpty();

        // 验证 WorkflowCanvas.tsx 和 services/api.ts 不存在非演示模式下的静默 Mock 兜底
        String canvasContent = Files.readString(frontendSrc.resolve("components/WorkflowCanvas.tsx"), StandardCharsets.UTF_8);
        assertThat(canvasContent)
                .doesNotContain("isDemoMode() || !projectId")
                .doesNotContain("已自动触发 JGit 工作区版本快照");
        String apiTsContent = Files.readString(frontendSrc.resolve("services/api.ts"), StandardCharsets.UTF_8);
        assertThat(apiTsContent)
                .doesNotContain("isDemoMode() || runId === MOCK_RUN.id");
    }

    @Test
    @DisplayName("3. 后端源码默认 ID（user-1 / proj-default / run-default）按相对路径+次数上限冻结及生产环境阻断 /api/workspace/* 校验")
    void shouldFreezeBackendDefaultLiteralsAndBlockLegacyWorkspaceInProd() throws IOException, jakarta.servlet.ServletException {
        Path repoRoot = resolveRepositoryRoot();
        Path backendMainJava = repoRoot.resolve("agenthub-backend/src/main/java");
        assertThat(Files.isDirectory(backendMainJava)).isTrue();

        List<String> unexpectedUser1Violations = new ArrayList<>();
        List<String> unexpectedProjDefaultViolations = new ArrayList<>();
        List<String> unexpectedRunDefaultViolations = new ArrayList<>();
        List<String> forbiddenDefaultSeedViolations = new ArrayList<>();
        List<String> hardcodedHostPathFiles = new ArrayList<>();

        try (Stream<Path> walk = Files.walk(backendMainJava)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".java"))
                    .forEach(javaFile -> {
                        try {
                            String relPath = backendMainJava.relativize(javaFile).toString().replace('\\', '/');
                            String content = Files.readString(javaFile, StandardCharsets.UTF_8);
                            String lower = content.toLowerCase(Locale.ROOT);

                            int user1Count = countOccurrences(content, "\"user-1\"");
                            int maxUser1 = FROZEN_BACKEND_USER_1_MAX_COUNTS.getOrDefault(relPath, 0);
                            if (user1Count > maxUser1) {
                                unexpectedUser1Violations.add(relPath + " (count=" + user1Count + ", max=" + maxUser1 + ")");
                            }

                            int projDefaultCount = countOccurrences(content, "\"proj-default\"");
                            int maxProjDefault = FROZEN_BACKEND_PROJ_DEFAULT_MAX_COUNTS.getOrDefault(relPath, 0);
                            if (projDefaultCount > maxProjDefault) {
                                unexpectedProjDefaultViolations.add(relPath + " (count=" + projDefaultCount + ", max=" + maxProjDefault + ")");
                            }

                            int runDefaultCount = countOccurrences(content, "\"run-default\"");
                            int maxRunDefault = FROZEN_BACKEND_RUN_DEFAULT_MAX_COUNTS.getOrDefault(relPath, 0);
                            if (runDefaultCount > maxRunDefault) {
                                unexpectedRunDefaultViolations.add(relPath + " (count=" + runDefaultCount + ", max=" + maxRunDefault + ")");
                            }

                            if (content.contains("\"ws-default\"") || content.contains("\"user-2\"") || content.contains("\"team-dev-swarm\"")) {
                                forbiddenDefaultSeedViolations.add(relPath);
                            }

                            if (lower.contains("d:/work/agenthub") || lower.contains("d:\\work\\agenthub")) {
                                hardcodedHostPathFiles.add(relPath);
                            }
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        }

        assertThat(unexpectedUser1Violations)
                .as("No backend class may introduce new \"user-1\" occurrences beyond Phase 0 per-file freeze baseline")
                .isEmpty();
        assertThat(unexpectedProjDefaultViolations)
                .as("No backend class may introduce new \"proj-default\" occurrences beyond Phase 0 per-file freeze baseline")
                .isEmpty();
        assertThat(unexpectedRunDefaultViolations)
                .as("No backend class may introduce new \"run-default\" occurrences beyond Phase 0 per-file freeze baseline")
                .isEmpty();
        assertThat(forbiddenDefaultSeedViolations)
                .as("No backend class may hardcode ws-default, user-2, or team-dev-swarm")
                .isEmpty();
        assertThat(hardcodedHostPathFiles)
                .as("No backend classes may hardcode host path d:/work/agenthub")
                .isEmpty();

        // Phase 10 兼容退场：旧 /api/workspace/*、/api/im、/api/sandbox、/api/runs 控制器已物理删除，
        // 生产边界由 LegacyEndpointMigrationFilter 承担——prod 下旧路由一律 410 Gone 并附带迁移目标。
        MockEnvironment prodEnv = new MockEnvironment();
        prodEnv.setActiveProfiles("prod");
        TokenProvider prodTokenProvider = new TokenProvider(
                "ProdHardenedSecretKeyForPhase0Verification_2026_9988776655!",
                86400L,
                prodEnv,
                null
        );
        LegacyEndpointMigrationFilter migrationFilter = new LegacyEndpointMigrationFilter(
                new LegacyEndpointCallTracker(new SimpleMeterRegistry()), prodTokenProvider);

        for (String legacyPath : List.of("/api/workspace/diff", "/api/im/conversations",
                "/api/sandbox/preview/proj-demo", "/api/runs/run-1")) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", legacyPath);
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();
            migrationFilter.doFilter(request, response, chain);
            assertThat(response.getStatus())
                    .as("Legacy route %s must be rejected with 410 Gone in production profile", legacyPath)
                    .isEqualTo(410);
            assertThat(response.getHeader("X-Deprecated-Migration-Target"))
                    .as("Legacy route %s must advertise its migration target", legacyPath)
                    .isNotBlank();
        }
    }

    @Test
    @DisplayName("4. 核心资源/事件词汇表、22 张 Flyway 表、18 个 RestController 与 API/能力边界矩阵全量对齐校验")
    void shouldVerifyVocabularyAndAll22FlywayTablesAlignWithDocumentation() throws IOException {
        Path repoRoot = resolveRepositoryRoot();
        String archDoc = Files.readString(repoRoot.resolve("docs/ARCHITECTURE.md"), StandardCharsets.UTF_8);
        String apiDoc = Files.readString(repoRoot.resolve("docs/API.md"), StandardCharsets.UTF_8);
        String capDoc = Files.readString(repoRoot.resolve("docs/CURRENT_CAPABILITY_BOUNDARIES.md"), StandardCharsets.UTF_8);

        // 1) 验证数据库中全部业务表集合与 EXPECTED_22_TABLES 完全一致，且全部登记在 docs/ARCHITECTURE.md 表矩阵中
        List<String> actualTables = jdbcTemplate.queryForList(
                "SELECT LOWER(TABLE_NAME) FROM INFORMATION_SCHEMA.TABLES " +
                        "WHERE UPPER(TABLE_SCHEMA) NOT IN ('INFORMATION_SCHEMA', 'MYSQL', 'PERFORMANCE_SCHEMA', 'SYS') " +
                        "AND LOWER(TABLE_NAME) <> 'flyway_schema_history'",
                String.class
        );
        assertThat(Set.copyOf(actualTables))
                .as("Actual business tables in database must match the 22 Flyway V1-V12 tables")
                .isEqualTo(Set.copyOf(EXPECTED_22_TABLES));

        for (String table : EXPECTED_22_TABLES) {
            assertThat(archDoc).as("docs/ARCHITECTURE.md must document table: %s", table).contains("`" + table + "`");
        }

        // 2) 验证当前架构契约要求的核心资源与事件词汇在 docs/ARCHITECTURE.md §2 全部登记
        List<String> requiredVocabulary = List.of(
                "Project", "Workspace", "WorkflowDefinition", "Run", "StepRun",
                "Event", "Artifact", "Approval", "Deployment", "Agent Policy", "Context Source"
        );
        for (String term : requiredVocabulary) {
            assertThat(archDoc).as("docs/ARCHITECTURE.md §2 must define vocabulary term: %s", term).contains(term);
        }

        // 3) 动态扫描后端 @RestController，验证每一个均在 docs/API.md 矩阵中完整登记；18 是当前基线硬上限
        Path backendMainJava = repoRoot.resolve("agenthub-backend/src/main/java");
        List<String> restControllerSimpleNames = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(backendMainJava)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith("Controller.java"))
                    .forEach(file -> {
                        try {
                            String content = Files.readString(file, StandardCharsets.UTF_8);
                            if (content.contains("@RestController")) {
                                String simpleName = file.getFileName().toString().replace(".java", "");
                                restControllerSimpleNames.add(simpleName);
                            }
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        }
        assertThat(restControllerSimpleNames).hasSizeLessThanOrEqualTo(18);
        assertThat(apiDoc).contains("REST/SSE 接口与前端调用全景矩阵");
        for (String controllerName : restControllerSimpleNames) {
            assertThat(apiDoc)
                    .as("docs/API.md matrix must document @RestController: %s", controllerName)
                    .contains("`" + controllerName + "`");
        }

        // 4) 验证 Provider/Runtime/Sandbox 能力矩阵及三种运行模式在 docs/CURRENT_CAPABILITY_BOUNDARIES.md 完整登记
        assertThat(capDoc).contains("DEMO_SIMULATED");
        assertThat(capDoc).contains("LIVE_SELF_HOSTED");
        assertThat(capDoc).contains("LIVE_TEAM");
        assertThat(capDoc).contains("Provider / Runtime / Sandbox 能力矩阵");
        for (String implName : List.of(
                "OpenAiCompatibleProvider", "AnthropicProvider", "GeminiProvider", "OllamaProvider", "MockLlmProvider",
                "ProviderDrivenAgentRuntime", "OpenAiCompatibleRuntime", "CliAgentRuntime", "MockAgentRuntime",
                "LocalProcessSandbox", "DockerSandbox"
        )) {
            assertThat(capDoc)
                    .as("docs/CURRENT_CAPABILITY_BOUNDARIES.md must document implementation: %s", implName)
                    .contains("`" + implName + "`");
        }
    }

    @Test
    @DisplayName("5. Managed Scope 受管目录、AOCI 诚实记录与 Flyway V1-V12 迁移固定 SHA-256 指纹基线校验")
    void shouldVerifyManagedScopeAndFlywayMigrationFingerprints() throws Exception {
        Path repoRoot = resolveRepositoryRoot();
        Path migrationDir = repoRoot.resolve("agenthub-backend/src/main/resources/db/migration");
        assertThat(Files.isDirectory(migrationDir)).isTrue();

        // 1) 校验迁移目录下仅包含预期的 12 个 V1-V12 脚本文件，且每个脚本 LF 归一化后的 SHA-256 指纹与基线 100% 匹配
        Set<String> actualSqlFiles;
        try (Stream<Path> stream = Files.list(migrationDir)) {
            actualSqlFiles = stream.filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .filter(name -> name.endsWith(".sql"))
                    .collect(Collectors.toSet());
        }
        assertThat(actualSqlFiles).isEqualTo(EXPECTED_12_MIGRATION_SHA256.keySet());

        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        for (Map.Entry<String, String> entry : EXPECTED_12_MIGRATION_SHA256.entrySet()) {
            Path sqlFile = migrationDir.resolve(entry.getKey());
            assertThat(Files.isRegularFile(sqlFile))
                    .as("Flyway migration file must exist: %s", entry.getKey())
                    .isTrue();
            String normalizedLf = Files.readString(sqlFile, StandardCharsets.UTF_8).replace("\r\n", "\n");
            sha256.reset();
            String digestHex = HexFormat.of().formatHex(sha256.digest(normalizedLf.getBytes(StandardCharsets.UTF_8)));
            assertThat(digestHex)
                    .as("Flyway migration %s SHA-256 fingerprint drifted from Phase 0 baseline", entry.getKey())
                    .isEqualTo(entry.getValue());
        }

        // 2) 校验 Flyway validate 通过，已成功应用全部 12 个版本，且无 pending / failed 迁移
        flyway.validate();
        MigrationInfo[] applied = flyway.info().applied();
        assertThat(applied).hasSize(12);
        assertThat(flyway.info().pending()).isEmpty();

        // 3) AOCI 正式认知与治理资产已由官方 aoci CLI init/scan 生成并随 Phase 9 提交入库；
        //    这些资产机器无关且必须随仓库存在，任何缺失都表示治理链断裂，只能用官方 CLI 重建，不得手工伪造。
        Path aociDir = repoRoot.resolve(".aoci");
        assertThat(Files.isDirectory(aociDir))
                .as("AOCI .aoci governance directory must exist as a committed official asset")
                .isTrue();
        assertThat(Files.isRegularFile(repoRoot.resolve("aoci.txt")))
                .as("AOCI Root cognition asset must exist as a committed official asset")
                .isTrue();
        assertThat(Files.isRegularFile(repoRoot.resolve("aoci.meta.txt")))
                .as("AOCI Meta cognition asset must exist as a committed official asset")
                .isTrue();
        assertThat(Files.isRegularFile(repoRoot.resolve("aoci.code.txt")))
                .as("AOCI Code cognition asset must exist as a committed official asset")
                .isTrue();
        assertThat(Files.isRegularFile(repoRoot.resolve(".aoci/config.json")))
                .as("AOCI governance config must exist as a committed official asset")
                .isTrue();
        assertThat(Files.isRegularFile(repoRoot.resolve(".aoci/baseline.json")))
                .as("AOCI baseline fingerprint must exist as a committed official asset")
                .isTrue();
    }

    private int countOccurrences(String content, String token) {
        int count = 0;
        int idx = 0;
        while ((idx = content.indexOf(token, idx)) != -1) {
            count++;
            idx += token.length();
        }
        return count;
    }

    private Path resolveRepositoryRoot() {
        Path cwd = Paths.get("").toAbsolutePath().normalize();
        if (Files.exists(cwd.resolve("docs")) && Files.exists(cwd.resolve("agenthub-backend"))) {
            return cwd;
        }
        Path parent = cwd.getParent();
        if (parent != null && Files.exists(parent.resolve("docs")) && Files.exists(parent.resolve("agenthub-backend"))) {
            return parent;
        }
        return cwd;
    }
}
