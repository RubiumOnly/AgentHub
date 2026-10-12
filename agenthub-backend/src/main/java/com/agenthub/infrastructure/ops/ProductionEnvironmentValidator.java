package com.agenthub.infrastructure.ops;

import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Phase E Production Configuration, Container Spec & Runbook Security Validator.
 * Enforces strict pre-flight gates on .env.production, docker-compose.prod.yml,
 * Dockerfiles, and disaster recovery SOPs.
 */
@Component
public class ProductionEnvironmentValidator {

    private static final List<String> FORBIDDEN_PLACEHOLDERS = List.of(
            "changeme",
            "your-",
            "placeholder",
            "provide_strong_random",
            "secret-key-only-for-local",
            "phase1-2026-superstrong",
            "example"
    );

    private static final List<String> WEAK_PASSWORDS = List.of(
            "password",
            "123456",
            "12345678",
            "123456789",
            "1234567890",
            "admin",
            "admin123",
            "root",
            "root123",
            "redis",
            "mysql",
            "agenthub",
            "qwerty"
    );

    public record ValidationResult(boolean valid, List<String> violations) {
        public void assertValid() {
            if (!valid) {
                throw new IllegalStateException("Production environment validation failed: " + String.join("; ", violations));
            }
        }
    }

    public ValidationResult validateEnvFile(Path envFilePath) {
        if (envFilePath == null || !Files.exists(envFilePath)) {
            return new ValidationResult(false, List.of("Environment file does not exist: " + envFilePath));
        }
        try {
            Map<String, String> envMap = parseEnvFile(envFilePath);
            return validateEnvMap(envMap);
        } catch (IOException e) {
            return new ValidationResult(false, List.of("Failed to read environment file: " + e.getMessage()));
        }
    }

    public Map<String, String> parseEnvFile(Path envFilePath) throws IOException {
        Map<String, String> map = new LinkedHashMap<>();
        List<String> lines = Files.readAllLines(envFilePath, StandardCharsets.UTF_8);
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int eqIdx = line.indexOf('=');
            if (eqIdx <= 0) {
                continue;
            }
            String key = line.substring(0, eqIdx).trim();
            String value = line.substring(eqIdx + 1).trim();
            if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
                if (value.length() >= 2) {
                    value = value.substring(1, value.length() - 1);
                }
            }
            map.put(key, value);
        }
        return map;
    }

    public ValidationResult validateEnvMap(Map<String, String> env) {
        List<String> violations = new ArrayList<>();
        if (env == null || env.isEmpty()) {
            return new ValidationResult(false, List.of("Environment configuration map is empty"));
        }

        // 1. Validate AGENTHUB_AUTH_SECRET
        String authSecret = env.get("AGENTHUB_AUTH_SECRET");
        if (authSecret == null || authSecret.isBlank()) {
            violations.add("AGENTHUB_AUTH_SECRET is missing or empty");
        } else {
            if (authSecret.length() < 32) {
                violations.add("AGENTHUB_AUTH_SECRET must be at least 32 characters long (actual: " + authSecret.length() + ")");
            }
            if (containsForbiddenPlaceholder(authSecret) || isWeakSecret(authSecret)) {
                violations.add("AGENTHUB_AUTH_SECRET contains forbidden placeholder or weak password pattern");
            }
        }

        // 2. Validate MYSQL_ROOT_PASSWORD
        validateDatabasePassword("MYSQL_ROOT_PASSWORD", env.get("MYSQL_ROOT_PASSWORD"), violations);

        // 3. Validate MYSQL_PASSWORD
        validateDatabasePassword("MYSQL_PASSWORD", env.get("MYSQL_PASSWORD"), violations);

        // 4. Validate optional REDIS_PASSWORD (if enabled/non-empty)
        String redisPassword = env.get("REDIS_PASSWORD");
        if (redisPassword != null && !redisPassword.isBlank()) {
            if (redisPassword.length() < 12) {
                violations.add("REDIS_PASSWORD must be at least 12 characters when set");
            }
            if (containsForbiddenPlaceholder(redisPassword) || isWeakSecret(redisPassword)) {
                violations.add("REDIS_PASSWORD contains forbidden placeholder or weak password pattern");
            }
        }

        // 5. Validate optional ACTUATOR_PROMETHEUS_TOKEN (if set)
        String scrapeToken = env.get("ACTUATOR_PROMETHEUS_TOKEN");
        if (scrapeToken != null && !scrapeToken.isBlank()) {
            if (scrapeToken.length() < 16) {
                violations.add("ACTUATOR_PROMETHEUS_TOKEN must be at least 16 characters when set");
            }
            if (containsForbiddenPlaceholder(scrapeToken) || isWeakSecret(scrapeToken)) {
                violations.add("ACTUATOR_PROMETHEUS_TOKEN contains forbidden placeholder or weak value");
            }
        }

        // 6. Validate LLM API keys: may be blank if unused, but must never retain sample placeholders
        for (String keyName : List.of("DEEPSEEK_API_KEY", "ANTHROPIC_API_KEY", "OPENAI_API_KEY", "GEMINI_API_KEY")) {
            String apiKey = env.get(keyName);
            if (apiKey != null && !apiKey.isBlank() && containsForbiddenPlaceholder(apiKey)) {
                violations.add(keyName + " still contains sample placeholder value; leave blank if unused or provide real secret");
            }
        }

        return new ValidationResult(violations.isEmpty(), violations);
    }

    @SuppressWarnings("unchecked")
    public ValidationResult validateDockerComposeSpec(Path composeFilePath) {
        List<String> violations = new ArrayList<>();
        if (composeFilePath == null || !Files.exists(composeFilePath)) {
            return new ValidationResult(false, List.of("Compose file not found: " + composeFilePath));
        }

        try {
            String rawContent = Files.readString(composeFilePath, StandardCharsets.UTF_8);
            if (rawContent.contains("/var/run/docker.sock")) {
                violations.add("Compose file must not mount host /var/run/docker.sock into production services");
            }

            Yaml yaml = new Yaml();
            Map<String, Object> root = yaml.load(rawContent);
            Map<String, Object> services = (Map<String, Object>) root.get("services");
            if (services == null || services.isEmpty()) {
                return new ValidationResult(false, List.of("Compose file defines no services"));
            }

            for (String requiredService : List.of(
                    "agenthub-mysql", "agenthub-redis", "agenthub-backend", "agenthub-frontend", "agenthub-gateway", "agenthub-prometheus")) {
                if (!services.containsKey(requiredService)) {
                    violations.add("Missing required production service in compose: " + requiredService);
                    continue;
                }
                Map<String, Object> svc = (Map<String, Object>) services.get(requiredService);
                if (svc.get("restart") == null) {
                    violations.add(requiredService + " is missing restart policy");
                }
                if (svc.get("networks") == null) {
                    violations.add(requiredService + " is missing network declaration");
                }
                if (svc.get("healthcheck") == null) {
                    violations.add(requiredService + " is missing healthcheck definition");
                }
                Map<String, Object> deploy = (Map<String, Object>) svc.get("deploy");
                if (deploy == null || deploy.get("resources") == null) {
                    violations.add(requiredService + " is missing deploy.resources limits");
                }
                List<String> securityOpts = (List<String>) svc.get("security_opt");
                if (securityOpts == null || securityOpts.stream().noneMatch(s -> s.contains("no-new-privileges:true"))) {
                    violations.add(requiredService + " is missing security_opt no-new-privileges:true");
                }
            }

            // Check Redis healthcheck handles password via REDISCLI_AUTH and checks PONG
            Map<String, Object> redisSvc = (Map<String, Object>) services.get("agenthub-redis");
            if (redisSvc != null && redisSvc.get("healthcheck") != null) {
                String hcTest = String.valueOf(((Map<String, Object>) redisSvc.get("healthcheck")).get("test"));
                if (!hcTest.contains("REDISCLI_AUTH") || !hcTest.contains("PONG")) {
                    violations.add("agenthub-redis healthcheck must use REDISCLI_AUTH and verify PONG response");
                }
            }

            // Check Backend does not have a hard startup deadlock on Redis when Redis has no consumer
            Map<String, Object> backendSvc = (Map<String, Object>) services.get("agenthub-backend");
            if (backendSvc != null) {
                Map<String, Object> dependsOn = (Map<String, Object>) backendSvc.get("depends_on");
                if (dependsOn != null && dependsOn.containsKey("agenthub-redis")) {
                    violations.add("agenthub-backend should not depend on agenthub-redis in depends_on while Redis has no active consumer");
                }
                String backendImage = String.valueOf(backendSvc.get("image"));
                if (!backendImage.contains("IMAGE_TAG")) {
                    violations.add("agenthub-backend image must support commit SHA / IMAGE_TAG variable");
                }
                String backendUser = String.valueOf(backendSvc.get("user"));
                if (!"10001:10001".equals(backendUser)) {
                    violations.add("agenthub-backend must declare non-root user 10001:10001");
                }
            }

            Map<String, Object> frontendSvc = (Map<String, Object>) services.get("agenthub-frontend");
            if (frontendSvc != null) {
                String frontendImage = String.valueOf(frontendSvc.get("image"));
                if (!frontendImage.contains("IMAGE_TAG")) {
                    violations.add("agenthub-frontend image must support commit SHA / IMAGE_TAG variable");
                }
                String frontendUser = String.valueOf(frontendSvc.get("user"));
                if (!"1001:1001".equals(frontendUser)) {
                    violations.add("agenthub-frontend must declare non-root user 1001:1001");
                }
            }

        } catch (Exception e) {
            violations.add("Failed to parse compose file: " + e.getMessage());
        }

        return new ValidationResult(violations.isEmpty(), violations);
    }

    public ValidationResult validateRunbookSafety(Path runbookPath) {
        List<String> violations = new ArrayList<>();
        if (runbookPath == null || !Files.exists(runbookPath)) {
            return new ValidationResult(false, List.of("Disaster recovery runbook not found: " + runbookPath));
        }
        try {
            List<String> lines = Files.readAllLines(runbookPath, StandardCharsets.UTF_8);
            boolean insideCodeBlock = false;
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i).trim();
                if (line.startsWith("```")) {
                    insideCodeBlock = !insideCodeBlock;
                    continue;
                }
                if (insideCodeBlock && !line.startsWith("#")) {
                    if (line.contains("docker system prune") && line.contains("--volumes")) {
                        violations.add("Line " + (i + 1) + ": Runbook executable code block contains destructive 'docker system prune --volumes'");
                    }
                    if (line.contains("find ") && line.contains("-mtime") && line.contains("rm -rf")) {
                        violations.add("Line " + (i + 1) + ": Runbook executable code block contains unassociated mtime recursive rm -rf on workspaces");
                    }
                }
            }
        } catch (IOException e) {
            violations.add("Failed to read runbook: " + e.getMessage());
        }
        return new ValidationResult(violations.isEmpty(), violations);
    }

    private void validateDatabasePassword(String key, String value, List<String> violations) {
        if (value == null || value.isBlank()) {
            violations.add(key + " is missing or empty");
            return;
        }
        if (value.length() < 12) {
            violations.add(key + " must be at least 12 characters long (actual: " + value.length() + ")");
        }
        if (containsForbiddenPlaceholder(value) || isWeakSecret(value)) {
            violations.add(key + " contains forbidden placeholder or weak password pattern");
        }
    }

    private boolean containsForbiddenPlaceholder(String val) {
        String lower = val.toLowerCase(Locale.ROOT);
        for (String placeholder : FORBIDDEN_PLACEHOLDERS) {
            if (lower.contains(placeholder)) {
                return true;
            }
        }
        return false;
    }

    private boolean isWeakSecret(String val) {
        String lower = val.toLowerCase(Locale.ROOT).trim();
        for (String weak : WEAK_PASSWORDS) {
            if (lower.equals(weak)) {
                return true;
            }
        }
        return false;
    }
}
