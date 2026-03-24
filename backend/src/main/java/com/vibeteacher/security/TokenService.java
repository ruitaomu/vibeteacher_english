package com.vibeteacher.security;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class TokenService {
    private static final Logger logger = LoggerFactory.getLogger(TokenService.class);
    private static final long DEFAULT_TTL_SECONDS = 3600;
    private final byte[] secret;

    public TokenService(@Value("${token.secret:}") String configuredSecret) {
        if (configuredSecret != null && !configuredSecret.isBlank()) {
            this.secret = configuredSecret.getBytes(StandardCharsets.UTF_8);
            logger.info("TokenService: using configured stable secret.");
        } else {
            byte[] key = new byte[32];
            new SecureRandom().nextBytes(key);
            this.secret = key;
            logger.warn("TokenService: no SESSION_TOKEN_SECRET configured - using random secret.");
        }
    }

    public Token issueToken() {
        return issueToken(null);
    }

    public Token issueToken(String userId) {
        long exp = Instant.now().plusSeconds(DEFAULT_TTL_SECONDS).getEpochSecond();
        String header = base64Url("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
        String payloadJson;
        if (userId == null || userId.isBlank()) {
            payloadJson = "{\"exp\":" + exp + "}";
        } else {
            payloadJson = "{\"exp\":" + exp + ",\"uid\":\"" + escapeJson(userId.trim()) + "\"}";
        }
        String payload = base64Url(payloadJson);
        String signature = sign(header + "." + payload);
        return new Token(header + "." + payload + "." + signature, exp);
    }

    public boolean validateToken(String token) {
        String payloadJson = extractAndValidatePayloadJson(token);
        if (payloadJson == null) {
            return false;
        }
        Long exp = extractLongField(payloadJson, "exp");
        if (exp == null) {
            return false;
        }
        return Instant.now().getEpochSecond() < exp;
    }

    public String extractUserId(String token) {
        String payloadJson = extractAndValidatePayloadJson(token);
        if (payloadJson == null) {
            return null;
        }
        int uidIndex = payloadJson.indexOf("\"uid\":\"");
        if (uidIndex < 0) {
            return null;
        }
        int start = uidIndex + 7;
        int end = payloadJson.indexOf('"', start);
        if (end <= start) {
            return null;
        }
        String userId = payloadJson.substring(start, end).trim();
        return userId.isBlank() ? null : userId;
    }

    private String extractAndValidatePayloadJson(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            return null;
        }
        String signingInput = parts[0] + "." + parts[1];
        String expectedSig = sign(signingInput);
        if (!constantTimeEquals(parts[2], expectedSig)) {
            return null;
        }
        return new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
    }

    private Long extractLongField(String payloadJson, String fieldName) {
        String marker = "\"" + fieldName + "\":";
        int fieldIndex = payloadJson.indexOf(marker);
        if (fieldIndex < 0) {
            return null;
        }

        int valueStart = fieldIndex + marker.length();
        while (valueStart < payloadJson.length() && Character.isWhitespace(payloadJson.charAt(valueStart))) {
            valueStart++;
        }

        int valueEnd = valueStart;
        while (valueEnd < payloadJson.length() && Character.isDigit(payloadJson.charAt(valueEnd))) {
            valueEnd++;
        }

        if (valueEnd == valueStart) {
            return null;
        }

        try {
            return Long.parseLong(payloadJson, valueStart, valueEnd, 10);
        } catch (NumberFormatException ex) {
            logger.warn("TokenService: failed to parse numeric field {} from payload.", fieldName, ex);
            return null;
        }
    }

    private String sign(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] sig = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(sig);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign token", e);
        }
    }

    private String base64Url(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public static final class Token {
        private final String value;
        private final long expiresAt;

        public Token(String value, long expiresAt) {
            this.value = value;
            this.expiresAt = expiresAt;
        }

        public String value() {
            return value;
        }

        public long expiresAt() {
            return expiresAt;
        }
    }
}
