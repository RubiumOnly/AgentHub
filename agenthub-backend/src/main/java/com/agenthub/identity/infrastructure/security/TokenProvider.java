package com.agenthub.identity.infrastructure.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;

@Component
public class TokenProvider {

    private final String secretKey;
    private final long tokenValiditySeconds;

    public TokenProvider(
            @Value("${agenthub.auth.secret:AgentHub-Secure-JWT-Secret-Key-Phase1-2026-SuperStrong}") String secretKey,
            @Value("${agenthub.auth.validity-seconds:86400}") long tokenValiditySeconds) {
        this.secretKey = secretKey;
        this.tokenValiditySeconds = tokenValiditySeconds;
    }

    public static class TokenClaims {
        private final String userId;
        private final String email;
        private final long expiresAt;

        public TokenClaims(String userId, String email, long expiresAt) {
            this.userId = userId;
            this.email = email;
            this.expiresAt = expiresAt;
        }

        public String getUserId() { return userId; }
        public String getEmail() { return email; }
        public long getExpiresAt() { return expiresAt; }
        public boolean isExpired() { return Instant.now().getEpochSecond() > expiresAt; }
    }

    public String generateToken(String userId, String email) {
        long expiresAt = Instant.now().getEpochSecond() + tokenValiditySeconds;
        String payload = userId + ":" + email + ":" + expiresAt;
        String signature = sign(payload);
        String rawToken = payload + ":" + signature;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(rawToken.getBytes(StandardCharsets.UTF_8));
    }

    public TokenClaims parseAndValidateToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }

        // Support transparent dev tokens
        if (token.startsWith("dev-token-")) {
            String userId = token.substring("dev-token-".length());
            return new TokenClaims(userId, userId + "@agenthub.local", Instant.now().getEpochSecond() + 86400);
        }

        try {
            byte[] decoded = Base64.getUrlDecoder().decode(token);
            String raw = new String(decoded, StandardCharsets.UTF_8);
            String[] parts = raw.split(":");
            if (parts.length != 4) {
                return null;
            }

            String userId = parts[0];
            String email = parts[1];
            long expiresAt = Long.parseLong(parts[2]);
            String providedSignature = parts[3];

            String expectedSignature = sign(userId + ":" + email + ":" + expiresAt);
            if (!MessageDigest.isEqual(providedSignature.getBytes(StandardCharsets.UTF_8), expectedSignature.getBytes(StandardCharsets.UTF_8))) {
                return null;
            }

            TokenClaims claims = new TokenClaims(userId, email, expiresAt);
            if (claims.isExpired()) {
                return null;
            }
            return claims;
        } catch (Exception e) {
            return null;
        }
    }

    private String sign(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to calculate HMAC signature", e);
        }
    }

    public long getTokenValiditySeconds() {
        return tokenValiditySeconds;
    }
}
