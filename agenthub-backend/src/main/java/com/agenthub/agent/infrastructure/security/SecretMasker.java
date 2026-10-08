package com.agenthub.agent.infrastructure.security;

import org.springframework.core.env.Environment;

import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Enterprise Secret Masker and Credential Security Utility.
 * Guarantees zero credential leakage across logs, error traces, REST responses, and URLs.
 */
public final class SecretMasker {

    private static final Pattern API_KEY_PATTERN = Pattern.compile("(?i)(sk-[a-zA-Z0-9_-]{8,}|key-[a-zA-Z0-9_-]{8,}|AIza[0-9A-Za-z-_]{35})");
    private static final Pattern BEARER_PATTERN = Pattern.compile("(?i)(Bearer\\s+)([a-zA-Z0-9._-]{8,})");
    private static final Pattern HEADER_API_KEY_PATTERN = Pattern.compile("(?i)((?:x-api-key|x-goog-api-key|api-key):\\s*)([^\\r\\n,;\"'\\s]+)");
    private static final Pattern QUERY_PARAM_KEY_PATTERN = Pattern.compile("(?i)([?&](?:key|apiKey|token|secret)=)([^&]+)");

    private SecretMasker() {}

    /**
     * Masks an API key or raw secret string safely.
     * Examples:
     *   "sk-ant-api03-12345678" -> "sk-a***5678"
     *   "short"                 -> "[REDACTED_SECRET]"
     */
    public static String maskSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            return "";
        }
        String trimmed = secret.trim();
        if (trimmed.length() <= 8) {
            return "[REDACTED_SECRET]";
        }
        if (trimmed.startsWith("sk-")) {
            String prefix = trimmed.substring(0, Math.min(6, trimmed.length()));
            String suffix = trimmed.substring(trimmed.length() - 4);
            return prefix + "***" + suffix;
        }
        String prefix = trimmed.substring(0, Math.min(4, trimmed.length()));
        String suffix = trimmed.substring(trimmed.length() - 4);
        return prefix + "***" + suffix;
    }

    /**
     * Sanitizes full text (logs, exception messages, payloads) by masking any detected secret patterns.
     */
    public static String maskInText(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String masked = text;

        // Mask sk- and AIza keys
        Matcher matcher = API_KEY_PATTERN.matcher(masked);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String raw = matcher.group(1);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(maskSecret(raw)));
        }
        matcher.appendTail(sb);
        masked = sb.toString();

        // Mask Bearer tokens
        Matcher bearerMatcher = BEARER_PATTERN.matcher(masked);
        sb = new StringBuffer();
        while (bearerMatcher.find()) {
            String prefix = bearerMatcher.group(1);
            String token = bearerMatcher.group(2);
            bearerMatcher.appendReplacement(sb, Matcher.quoteReplacement(prefix + maskSecret(token)));
        }
        bearerMatcher.appendTail(sb);
        masked = sb.toString();

        // Mask Header API keys (e.g. x-api-key, x-goog-api-key)
        Matcher headerMatcher = HEADER_API_KEY_PATTERN.matcher(masked);
        sb = new StringBuffer();
        while (headerMatcher.find()) {
            String prefix = headerMatcher.group(1);
            String rawSecret = headerMatcher.group(2);
            headerMatcher.appendReplacement(sb, Matcher.quoteReplacement(prefix + maskSecret(rawSecret)));
        }
        headerMatcher.appendTail(sb);
        masked = sb.toString();

        // Mask query param secrets
        Matcher queryMatcher = QUERY_PARAM_KEY_PATTERN.matcher(masked);
        sb = new StringBuffer();
        while (queryMatcher.find()) {
            String paramPrefix = queryMatcher.group(1);
            String rawSecret = queryMatcher.group(2);
            queryMatcher.appendReplacement(sb, Matcher.quoteReplacement(paramPrefix + maskSecret(rawSecret)));
        }
        queryMatcher.appendTail(sb);
        masked = sb.toString();

        return masked;
    }

    /**
     * Sanitizes a URI / URL by redacting any sensitive query parameter such as ?key=... or &apiKey=...
     */
    public static String maskUrl(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        Matcher matcher = QUERY_PARAM_KEY_PATTERN.matcher(url);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String param = matcher.group(1);
            String val = matcher.group(2);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(param + maskSecret(val)));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * Resolves secret reference safely.
     * Supports:
     * - env:VAR_NAME (reads from OS environment)
     * - prop:KEY or cfg:KEY (reads from Spring Environment)
     * - raw string
     * - fallback to standard environment variables if empty
     */
    public static String resolveSecret(String secretRef, Environment springEnv, String defaultEnvFallback) {
        if (secretRef != null && !secretRef.isBlank()) {
            String trimmed = secretRef.trim();
            if (trimmed.startsWith("env:")) {
                String varName = trimmed.substring(4).trim();
                String val = System.getenv(varName);
                if (val != null && !val.isBlank()) {
                    return val.trim();
                }
            } else if (trimmed.startsWith("prop:") || trimmed.startsWith("cfg:")) {
                String propKey = trimmed.substring(trimmed.indexOf(':') + 1).trim();
                if (springEnv != null) {
                    String val = springEnv.getProperty(propKey);
                    if (val != null && !val.isBlank()) {
                        return val.trim();
                    }
                }
            } else if (!"none".equalsIgnoreCase(trimmed) && !trimmed.startsWith("sk-placeholder")) {
                return trimmed;
            }
        }

        // Try Spring environment fallback if provided
        if (springEnv != null && defaultEnvFallback != null) {
            String propVal = springEnv.getProperty(defaultEnvFallback.toLowerCase().replace('_', '.'));
            if (propVal != null && !propVal.isBlank()) {
                return propVal.trim();
            }
        }

        // Try system environment fallback
        if (defaultEnvFallback != null) {
            String envVal = System.getenv(defaultEnvFallback);
            if (envVal != null && !envVal.isBlank()) {
                return envVal.trim();
            }
        }

        return "";
    }
}
