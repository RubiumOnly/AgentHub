package com.agenthub.sandbox.domain.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Sanitizes and isolates environment variables for sandbox executions.
 * Blocks inheritance of sensitive host credentials (API keys, secrets, passwords)
 * and prevents injection of dangerous execution variables (e.g. LD_PRELOAD, BASH_ENV).
 */
public class EnvironmentSanitizer {

    private static final Logger log = LoggerFactory.getLogger(EnvironmentSanitizer.class);

    // Whitelist of benign host environment variables permitted into sandbox
    private static final Set<String> ALLOWED_SYSTEM_ENV_VARS = Set.of(
            "PATH", "PATHEXT", "SYSTEMROOT", "COMSPEC", "TEMP", "TMP",
            "HOME", "USER", "USERNAME", "USERPROFILE",
            "LANG", "LC_ALL", "LC_CTYPE", "TZ", "OS"
    );

    // Patterns matching sensitive host secrets that MUST NEVER be leaked into sandbox
    private static final Pattern SENSITIVE_KEY_PATTERN = Pattern.compile(
            "(?i).*(KEY|SECRET|PASSWORD|TOKEN|CREDENTIAL|AUTH|PRIVATE|DATABASE|DATASOURCE|SIGNATURE|BEARER).*"
    );

    // Dangerous environment variables that could cause arbitrary library hijacking or shell hijack
    private static final Set<String> FORBIDDEN_CUSTOM_ENV_KEYS = Set.of(
            "LD_PRELOAD", "LD_LIBRARY_PATH", "DYLD_INSERT_LIBRARIES",
            "DYLD_LIBRARY_PATH", "BASH_ENV", "ENV", "SHELLOPTS", "PS4"
    );

    /**
     * Builds an isolated, sanitized environment map.
     * Inherits only essential system runtime variables and filters out any sensitive host secrets.
     */
    public Map<String, String> sanitize(Map<String, String> userEnv) {
        Map<String, String> cleanEnv = new HashMap<>();

        // 1. Inherit safe baseline system environment variables from current process
        Map<String, String> hostEnv = System.getenv();
        for (Map.Entry<String, String> entry : hostEnv.entrySet()) {
            String key = entry.getKey();
            if (ALLOWED_SYSTEM_ENV_VARS.contains(key.toUpperCase())) {
                // Double check it doesn't accidentally match sensitive keywords
                if (!SENSITIVE_KEY_PATTERN.matcher(key).matches()) {
                    cleanEnv.put(key, entry.getValue());
                }
            }
        }

        // 2. Set explicit sandbox execution markers and baseline PATH fallback
        cleanEnv.put("AGENTHUB_SANDBOX", "true");
        cleanEnv.put("CI", "true");
        if (!cleanEnv.containsKey("PATH")) {
            boolean isWin = System.getProperty("os.name", "").toLowerCase().contains("win");
            cleanEnv.put("PATH", isWin ? "C:\\Windows\\System32;C:\\Windows" : "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin");
        }

        // 3. Merge user-provided custom environment variables with safety validation
        if (userEnv != null) {
            for (Map.Entry<String, String> entry : userEnv.entrySet()) {
                String key = entry.getKey();
                String val = entry.getValue();
                if (key == null || key.isBlank() || val == null) {
                    continue;
                }

                // Disallow dynamic linker or shell hijacking
                if (FORBIDDEN_CUSTOM_ENV_KEYS.contains(key.toUpperCase())) {
                    log.warn("Blocked forbidden environment injection key: {}", key);
                    continue;
                }

                // If user env contains obvious secret injection attempt, drop or sanitize
                if (SENSITIVE_KEY_PATTERN.matcher(key).matches()) {
                    log.warn("Blocked potentially dangerous sensitive environment variable in sandbox: {}", key);
                    continue;
                }

                cleanEnv.put(key, val);
            }
        }

        return Collections.unmodifiableMap(cleanEnv);
    }
}
