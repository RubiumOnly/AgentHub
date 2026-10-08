package com.agenthub;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ProfileIsolationTest {

    @Autowired
    private Environment environment;

    @Value("${agenthub.workspace.base-dir}")
    private String workspaceBaseDir;

    @Value("${spring.h2.console.enabled:false}")
    private boolean h2ConsoleEnabled;

    /**
     * 轻量空配置类：用于隔离测试 Spring Boot 外部化配置与 Environment 解析周期，
     * 避免加载真实数据库连接、JPA Repository 与业务 Bean。
     */
    @Configuration
    static class DynamicTestConfig {
    }

    @Test
    @DisplayName("测试环境激活正确的 test profile")
    void shouldActivateTestProfile() {
        assertThat(environment.getActiveProfiles()).contains("test");
        assertThat(workspaceBaseDir).contains("target/test-workspaces");
        assertThat(h2ConsoleEnabled).isFalse();
    }

    @Test
    @DisplayName("动态验证 prod profile 激活时环境严格隔离（仅包含 prod 且排斥 dev，ddl-auto 为 validate，H2 控制台关闭）")
    void shouldDynamicallyIsolateProdEnvironment() {
        try (ConfigurableApplicationContext ctx = new SpringApplicationBuilder(DynamicTestConfig.class)
                .web(WebApplicationType.NONE)
                .profiles("prod")
                .run()) {
            Environment env = ctx.getEnvironment();

            // 1. 验证 active profile 严格且仅包含 prod，绝不能混入 dev
            assertThat(env.getActiveProfiles())
                    .containsExactly("prod")
                    .doesNotContain("dev");

            // 2. 验证 ddl-auto 严格为 validate，不能被 dev 的 update 覆盖
            assertThat(env.getProperty("spring.jpa.hibernate.ddl-auto"))
                    .isEqualTo("validate");

            // 3. 验证 H2 console 彻底关闭（false 或 null）
            Boolean h2Console = env.getProperty("spring.h2.console.enabled", Boolean.class);
            assertThat(h2Console).satisfiesAnyOf(
                    enabled -> assertThat(enabled).isFalse(),
                    enabled -> assertThat(enabled).isNull()
            );

            // 4. 验证生产工作区路径使用生产加固默认值
            assertThat(env.getProperty("agenthub.workspace.base-dir"))
                    .isEqualTo("/var/lib/agenthub/workspaces");
        }
    }

    @Test
    @DisplayName("动态验证未显式指定 profile 时默认使用 dev 环境")
    void shouldDefaultToDevProfileWhenUnspecified() {
        try (ConfigurableApplicationContext ctx = new SpringApplicationBuilder(DynamicTestConfig.class)
                .web(WebApplicationType.NONE)
                .run()) {
            Environment env = ctx.getEnvironment();

            // 验证未激活 prod，且默认 profile 包含 dev
            assertThat(env.getActiveProfiles()).doesNotContain("prod");
            assertThat(env.getDefaultProfiles()).contains("dev");

            // 验证 dev 环境特征配置已生效（更新模式表结构与 H2 控制台开启）
            assertThat(env.getProperty("spring.jpa.hibernate.ddl-auto"))
                    .isEqualTo("update");
            assertThat(env.getProperty("spring.h2.console.enabled", Boolean.class))
                    .isTrue();
            assertThat(env.getProperty("agenthub.workspace.base-dir"))
                    .isEqualTo("./data/workspaces");
        }
    }

    @Test
    @DisplayName("通过 ApplicationContextRunner 辅助验证生产配置加载")
    void shouldVerifyProdConfigWithApplicationContextRunner() {
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues("spring.profiles.active=prod")
                .run(context -> {
                    Environment env = context.getEnvironment();
                    assertThat(env.getActiveProfiles()).contains("prod");
                    assertThat(env.getActiveProfiles()).doesNotContain("dev");
                    assertThat(env.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
                    Boolean h2Console = env.getProperty("spring.h2.console.enabled", Boolean.class);
                    assertThat(h2Console).satisfiesAnyOf(
                            enabled -> assertThat(enabled).isFalse(),
                            enabled -> assertThat(enabled).isNull()
                    );
                });
    }

    @Test
    @DisplayName("验证本地私有配置文件已被排除在构建交付物和 Classpath 之外")
    void shouldExcludeApplicationLocalConfigFileFromClasspath() {
        ClassPathResource localConfig = new ClassPathResource("application-local.yml");
        assertThat(localConfig.exists()).isFalse();
    }

    @Test
    @DisplayName("验证主配置 application.yml 基线参数与跨平台相对路径")
    @SuppressWarnings("unchecked")
    void shouldValidateApplicationYamlBaseline() throws Exception {
        Yaml yaml = new Yaml();
        try (InputStream in = new ClassPathResource("application.yml").getInputStream()) {
            Map<String, Object> data = yaml.load(in);
            Map<String, Object> spring = (Map<String, Object>) data.get("spring");
            Map<String, Object> profiles = (Map<String, Object>) spring.get("profiles");
            assertThat(profiles.get("default")).isEqualTo("dev");

            Map<String, Object> agenthub = (Map<String, Object>) data.get("agenthub");
            Map<String, Object> workspace = (Map<String, Object>) agenthub.get("workspace");
            String baseDir = workspace.get("base-dir").toString();
            assertThat(baseDir).doesNotContain("d:/work");
            assertThat(baseDir).contains("./data/workspaces");
        }
    }

    @Test
    @DisplayName("验证生产配置 application-prod.yml 的加固策略（禁止弱密码、关闭 H2 控制台、ddl-auto 为 validate）")
    @SuppressWarnings("unchecked")
    void shouldValidateProductionProfileHardening() throws Exception {
        Yaml yaml = new Yaml();
        try (InputStream in = new ClassPathResource("application-prod.yml").getInputStream()) {
            Map<String, Object> data = yaml.load(in);
            Map<String, Object> spring = (Map<String, Object>) data.get("spring");
            Map<String, Object> datasource = (Map<String, Object>) spring.get("datasource");
            String passwordConfig = datasource.get("password").toString();
            assertThat(passwordConfig).doesNotContain("password");
            assertThat(passwordConfig).contains("SPRING_DATASOURCE_PASSWORD");

            Map<String, Object> jpa = (Map<String, Object>) spring.get("jpa");
            Map<String, Object> hibernate = (Map<String, Object>) jpa.get("hibernate");
            assertThat(hibernate.get("ddl-auto")).isEqualTo("validate");
            assertThat(jpa.get("open-in-view")).isEqualTo(false);

            Map<String, Object> h2 = (Map<String, Object>) spring.get("h2");
            Map<String, Object> console = (Map<String, Object>) h2.get("console");
            assertThat(console.get("enabled")).isEqualTo(false);
        }
    }

    @Test
    @DisplayName("验证开发配置 application-dev.yml 开启 H2 控制台与 ddl-auto update")
    @SuppressWarnings("unchecked")
    void shouldValidateDevProfileSettings() throws Exception {
        Yaml yaml = new Yaml();
        try (InputStream in = new ClassPathResource("application-dev.yml").getInputStream()) {
            Map<String, Object> data = yaml.load(in);
            Map<String, Object> spring = (Map<String, Object>) data.get("spring");
            Map<String, Object> jpa = (Map<String, Object>) spring.get("jpa");
            Map<String, Object> hibernate = (Map<String, Object>) jpa.get("hibernate");
            assertThat(hibernate.get("ddl-auto")).isEqualTo("update");

            Map<String, Object> h2 = (Map<String, Object>) spring.get("h2");
            Map<String, Object> console = (Map<String, Object>) h2.get("console");
            assertThat(console.get("enabled")).isEqualTo(true);
        }
    }
}
