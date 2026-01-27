package com.vibeteacher.security;

import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

@Service
public class TokenService {
    private static final long DEFAULT_TTL_SECONDS = 600;
    private final byte[] secret;

    public TokenService() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        this.secret = key;
    }

    public Token issueToken() {
        long exp = Instant.now().plusSeconds(DEFAULT_TTL_SECONDS).getEpochSecond();
        String header = base64Url("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
        String payload = base64Url("{\"exp\":" + exp + "}");
        String signature = sign(header + "." + payload);
        return new Token(header + "." + payload + "." + signature, exp);
    }

    public boolean validateToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            return false;
        }
        String signingInput = parts[0] + "." + parts[1];
        String expectedSig = sign(signingInput);
        if (!constantTimeEquals(parts[2], expectedSig)) {
            return false;
        }
        String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        int expIndex = payloadJson.indexOf("\"exp\":");
        if (expIndex < 0) {
            return false;
        }
        String expPart = payloadJson.substring(expIndex + 6).replaceAll("[^0-9]", "");
        if (expPart.isEmpty()) {
            return false;
        }
        long exp = Long.parseLong(expPart);
        return Instant.now().getEpochSecond() < exp;
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
