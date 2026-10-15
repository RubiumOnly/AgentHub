package com.agenthub.shared.contract.webhook;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Standard HMAC-SHA256 signature generator and verifier for AgentHub webhooks.
 * Implements anti-tampering and anti-replay defense via timestamped signatures:
 * X-AgentHub-Signature: t={timestampSeconds},v1={hexEncodedHmac}
 */
public final class WebhookSignatureVerifier {

    public static final String SIGNATURE_HEADER = "X-AgentHub-Signature";
    public static final String HMAC_ALGORITHM = "HmacSHA256";
    public static final long DEFAULT_TOLERANCE_SECONDS = 300L; // 5 minutes

    private WebhookSignatureVerifier() {
    }

    /**
     * Generate standard timestamped signature header value.
     */
    public static String generateSignatureHeader(String payloadJson, String secret, long timestampSeconds) {
        if (payloadJson == null || secret == null) {
            throw new IllegalArgumentException("payloadJson and secret must not be null");
        }
        String signedPayload = timestampSeconds + "." + payloadJson;
        String hmacHex = computeHmacSha256(signedPayload, secret);
        return "t=" + timestampSeconds + ",v1=" + hmacHex;
    }

    /**
     * Overload using current system epoch second.
     */
    public static String generateSignatureHeader(String payloadJson, String secret) {
        return generateSignatureHeader(payloadJson, secret, Instant.now().getEpochSecond());
    }

    /**
     * Verify timestamped HMAC-SHA256 signature against replay and payload tampering.
     *
     * @param signatureHeader      raw header content from X-AgentHub-Signature
     * @param payloadJson          raw request body JSON
     * @param secret               pre-shared webhook secret
     * @param maxToleranceSeconds  maximum permitted clock skew / replay window in seconds
     * @return true if signature is valid and within tolerance, false otherwise
     */
    public static boolean verifySignature(String signatureHeader,
                                           String payloadJson,
                                           String secret,
                                           long maxToleranceSeconds) {
        if (signatureHeader == null || payloadJson == null || secret == null
                || signatureHeader.isBlank() || secret.isBlank() || maxToleranceSeconds < 0) {
            return false;
        }

        Long timestamp = null;
        String providedSignature = null;

        String[] parts = signatureHeader.split(",");
        for (String part : parts) {
            String[] kv = part.split("=", 2);
            if (kv.length == 2) {
                String key = kv[0].trim();
                String val = kv[1].trim();
                if ("t".equalsIgnoreCase(key)) {
                    try {
                        timestamp = Long.parseLong(val);
                    } catch (NumberFormatException e) {
                        return false;
                    }
                } else if ("v1".equalsIgnoreCase(key)) {
                    providedSignature = val;
                }
            }
        }

        if (timestamp == null || providedSignature == null || providedSignature.isBlank()) {
            return false;
        }

        // Anti-replay check: ensure timestamp is within tolerance window (past or future skew)
        long nowSeconds = Instant.now().getEpochSecond();
        if (Math.abs(nowSeconds - timestamp) > maxToleranceSeconds) {
            return false;
        }

        String signedPayload = timestamp + "." + payloadJson;
        String expectedSignature = computeHmacSha256(signedPayload, secret);

        // Constant-time comparison normalized to lowercase ASCII to protect against timing attacks & case sensitivity
        byte[] expectedBytes = expectedSignature.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8);
        byte[] providedBytes = providedSignature.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expectedBytes, providedBytes);
    }

    /**
     * Compute raw HMAC-SHA256 hex string.
     */
    public static String computeHmacSha256(String data, String secret) {
        if (data == null || secret == null) {
            throw new IllegalArgumentException("data and secret must not be null");
        }
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
            mac.init(secretKeySpec);
            byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(rawHmac);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Failed to compute HMAC-SHA256 signature", e);
        }
    }
}
