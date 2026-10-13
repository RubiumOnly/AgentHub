package com.agenthub.shared.contract;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Authoritative HTTP header constants and ETag/version helpers for AgentHub API v1.
 */
public final class ApiContractHeaders {

    public static final String API_VERSION = "v1";
    public static final String API_VERSION_V1 = API_VERSION;
    public static final String SERVER_TIMEZONE = "UTC";
    public static final String SERVER_TIMEZONE_UTC = SERVER_TIMEZONE;

    public static final String HEADER_API_VERSION = "X-API-Version";
    public static final String HEADER_SERVER_TIMEZONE = "X-Server-Timezone";
    public static final String HEADER_IDEMPOTENCY_KEY = "Idempotency-Key";
    public static final String HEADER_ETAG = "ETag";
    public static final String HEADER_IF_NONE_MATCH = "If-None-Match";
    public static final String HEADER_LAST_EVENT_ID = "Last-Event-ID";
    public static final String HEADER_STREAM_CURSOR = "X-Stream-Cursor";

    public static final String HEADER_DEPRECATION = "Deprecation";
    public static final String HEADER_DEPRECATED_ENDPOINT = "X-Deprecated-Endpoint";
    public static final String HEADER_MIGRATION_TARGET = "X-Deprecated-Migration-Target";
    public static final String HEADER_DEPRECATED_MIGRATION_TARGET = HEADER_MIGRATION_TARGET;
    public static final String HEADER_SUNSET = "Sunset";
    public static final String HEADER_LINK = "Link";
    public static final String HEADER_ENFORCE_LEGACY_GONE = "X-Enforce-Legacy-Gone";

    private ApiContractHeaders() {
    }

    public static String computeWeakEtag(String resourceKey, Object versionOrTimestamp) {
        String raw = (resourceKey != null ? resourceKey : "resource") + ":"
                + (versionOrTimestamp != null ? String.valueOf(versionOrTimestamp) : "0");
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            String hex = HexFormat.of().formatHex(sha256.digest(raw.getBytes(StandardCharsets.UTF_8)));
            return "W/\"v1-" + hex.substring(0, 16) + "\"";
        } catch (Exception e) {
            return "W/\"v1-" + Integer.toHexString(raw.hashCode()) + "\"";
        }
    }

    public static boolean matchesEtag(String ifNoneMatchHeader, String currentEtag) {
        if (ifNoneMatchHeader == null || ifNoneMatchHeader.isBlank() || currentEtag == null || currentEtag.isBlank()) {
            return false;
        }
        String candidate = ifNoneMatchHeader.trim();
        if ("*".equals(candidate) || candidate.equals(currentEtag)) {
            return true;
        }
        for (String token : candidate.split(",")) {
            String trimmed = token.trim();
            if ("*".equals(trimmed) || trimmed.equals(currentEtag)) {
                return true;
            }
        }
        return false;
    }
}
