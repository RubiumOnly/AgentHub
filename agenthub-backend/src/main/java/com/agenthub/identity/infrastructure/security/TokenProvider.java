package com.agenthub.identity.infrastructure.security;

import com.agenthub.identity.infrastructure.entity.InvalidatedTokenEntity;
import com.agenthub.identity.infrastructure.repository.InvalidatedTokenRepository;
import com.agenthub.shared.exception.BusinessException;
import com.agenthub.shared.exception.ErrorCode;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TokenProvider {

    private static final Logger log = LoggerFactory.getLogger(TokenProvider.class);

    private final String secretKey;
    private final long tokenValiditySeconds;
    private final Environment environment;
    private final InvalidatedTokenRepository invalidatedTokenRepository;

    private final Set<String> invalidatedTokens = ConcurrentHashMap.newKeySet();
    private final Map<String, StreamTicket> streamTickets = new ConcurrentHashMap<>();

    public TokenProvider(
            @Value("${agenthub.auth.secret:AgentHub-Secure-JWT-Secret-Key-Phase1-2026-SuperStrong}") String secretKey,
            @Value("${agenthub.auth.validity-seconds:86400}") long tokenValiditySeconds,
            Environment environment,
            @Autowired(required = false) InvalidatedTokenRepository invalidatedTokenRepository) {
        this.secretKey = secretKey;
        this.tokenValiditySeconds = tokenValiditySeconds;
        this.environment = environment;
        this.invalidatedTokenRepository = invalidatedTokenRepository;
    }

    @PostConstruct
    public void init() {
        validateConfigurationForProfile();
        loadPersistedRevocations();
    }

    public boolean isProd() {
        if (environment == null || environment.getActiveProfiles() == null) {
            return false;
        }
        return Arrays.asList(environment.getActiveProfiles()).contains("prod");
    }

    public void validateConfigurationForProfile() {
        if (isProd()) {
            if (secretKey == null || secretKey.isBlank()) {
                throw new IllegalStateException("FATAL: agenthub.auth.secret (or AGENTHUB_AUTH_SECRET) is required in production environment!");
            }
            if (secretKey.length() < 32) {
                throw new IllegalStateException("FATAL: agenthub.auth.secret must be at least 32 characters in production!");
            }
            String lower = secretKey.toLowerCase();
            if (lower.contains("phase1-2026-superstrong")
                    || lower.contains("changeme")
                    || lower.contains("your-")
                    || lower.contains("placeholder")
                    || lower.contains("secret-key-only-for-local")) {
                throw new IllegalStateException("FATAL: agenthub.auth.secret must not use sample, placeholder or default values in production!");
            }
            log.info("TokenProvider initialized for PRODUCTION with external hardened secret.");
        }
    }

    private void loadPersistedRevocations() {
        if (invalidatedTokenRepository != null) {
            try {
                List<InvalidatedTokenEntity> activeRevocations =
                        invalidatedTokenRepository.findByExpiresAtAfter(LocalDateTime.now());
                for (InvalidatedTokenEntity entity : activeRevocations) {
                    invalidatedTokens.add(entity.getTokenId());
                }
                log.info("Loaded {} active invalidated tokens from persistent store", activeRevocations.size());
            } catch (Exception e) {
                log.warn("Could not load persisted token revocations on startup: {}", e.getMessage());
            }
        }
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

    public static class StreamTicket {
        private final String ticket;
        private final String userId;
        private final String email;
        private final long expiresAtEpoch;

        public StreamTicket(String ticket, String userId, String email, long expiresAtEpoch) {
            this.ticket = ticket;
            this.userId = userId;
            this.email = email;
            this.expiresAtEpoch = expiresAtEpoch;
        }

        public String getTicket() { return ticket; }
        public String getUserId() { return userId; }
        public String getEmail() { return email; }
        public long getExpiresAtEpoch() { return expiresAtEpoch; }
        public boolean isExpired() { return Instant.now().getEpochSecond() > expiresAtEpoch; }
    }

    public String generateToken(String userId, String email) {
        String tokenId = java.util.UUID.randomUUID().toString().replace("-", "");
        long expiresAt = Instant.now().getEpochSecond() + tokenValiditySeconds;
        String payload = tokenId + ":" + userId + ":" + email + ":" + expiresAt;
        String signature = sign(payload);
        String rawToken = payload + ":" + signature;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(rawToken.getBytes(StandardCharsets.UTF_8));
    }

    public void invalidateToken(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        invalidatedTokens.add(token);

        try {
            byte[] decoded = Base64.getUrlDecoder().decode(token);
            String raw = new String(decoded, StandardCharsets.UTF_8);
            String[] parts = raw.split(":");
            if (parts.length >= 4) {
                String tokenId = parts.length == 5 ? parts[0] : hashString(token);
                String userId = parts.length == 5 ? parts[1] : parts[0];
                long expiresAtEpoch = Long.parseLong(parts.length == 5 ? parts[3] : parts[2]);

                invalidatedTokens.add(tokenId);

                if (invalidatedTokenRepository != null) {
                    LocalDateTime expiresAt = LocalDateTime.ofInstant(
                            Instant.ofEpochSecond(expiresAtEpoch), ZoneId.systemDefault());
                    invalidatedTokenRepository.save(new InvalidatedTokenEntity(tokenId, userId, expiresAt));
                }
            }
        } catch (Exception e) {
            // Raw token fallback
            if (invalidatedTokenRepository != null) {
                String tokenHash = hashString(token);
                invalidatedTokens.add(tokenHash);
                LocalDateTime defaultExpiry = LocalDateTime.now().plusSeconds(tokenValiditySeconds);
                invalidatedTokenRepository.save(new InvalidatedTokenEntity(tokenHash, "unknown", defaultExpiry));
            }
        }
    }

    public String refreshToken(String oldToken) {
        TokenClaims claims = parseAndValidateToken(oldToken);
        if (claims == null) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_INVALID, "Invalid or expired token for refresh");
        }
        invalidateToken(oldToken);
        return generateToken(claims.getUserId(), claims.getEmail());
    }

    public TokenClaims parseAndValidateToken(String token) {
        if (token == null || token.isBlank() || invalidatedTokens.contains(token) || invalidatedTokens.contains(hashString(token))) {
            return null;
        }

        // Support transparent dev tokens only in non-production environments
        if (token.startsWith("dev-token-")) {
            if (isProd()) {
                log.warn("Blocked dev-token attempt in production profile: [{}]", token);
                return null;
            }
            String userId = token.substring("dev-token-".length());
            return new TokenClaims(userId, userId + "@agenthub.local", Instant.now().getEpochSecond() + 86400);
        }

        try {
            byte[] decoded = Base64.getUrlDecoder().decode(token);
            String raw = new String(decoded, StandardCharsets.UTF_8);
            String[] parts = raw.split(":");
            if (parts.length == 5) {
                String tokenId = parts[0];
                if (invalidatedTokens.contains(tokenId)) {
                    return null;
                }

                String userId = parts[1];
                String email = parts[2];
                long expiresAt = Long.parseLong(parts[3]);
                String providedSignature = parts[4];

                String expectedSignature = sign(tokenId + ":" + userId + ":" + email + ":" + expiresAt);
                if (!MessageDigest.isEqual(providedSignature.getBytes(StandardCharsets.UTF_8), expectedSignature.getBytes(StandardCharsets.UTF_8))) {
                    return null;
                }

                TokenClaims claims = new TokenClaims(userId, email, expiresAt);
                if (claims.isExpired()) {
                    return null;
                }
                return claims;
            } else if (parts.length == 4) {
                // Backward-compatibility for legacy 4-part tokens
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
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Issues a short-lived (60s), single-use stream ticket for SSE connections without exposing bearer tokens in URLs.
     */
    public String createStreamTicket(String userId, String email) {
        if (userId == null || userId.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Authentication required to generate stream ticket");
        }
        String ticket = "st-" + UUID.randomUUID().toString().replace("-", "");
        long expiresAt = Instant.now().getEpochSecond() + 60; // 60s validity
        streamTickets.put(ticket, new StreamTicket(ticket, userId, email, expiresAt));
        return ticket;
    }

    /**
     * Consumes and validates a stream ticket. Consumed ticket is immediately invalidated (single-use).
     */
    public TokenClaims validateAndConsumeStreamTicket(String ticket) {
        if (ticket == null || ticket.isBlank()) {
            return null;
        }
        StreamTicket st = streamTickets.remove(ticket);
        if (st == null || st.isExpired()) {
            return null;
        }
        return new TokenClaims(st.getUserId(), st.getEmail(), st.getExpiresAtEpoch());
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

    private String hashString(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception e) {
            return input;
        }
    }

    public long getTokenValiditySeconds() {
        return tokenValiditySeconds;
    }
}
