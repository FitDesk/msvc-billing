package com.msvcbilling.utils;

import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class WebhookSignatureValidator {

    private static final String HMAC_SHA256 = "HmacSHA256";

    private WebhookSignatureValidator() {
    }

    public static boolean isValidSignature(String secret, String payloadBody, String headerSignature) {
        if (!StringUtils.hasText(secret) || !StringUtils.hasText(headerSignature) || payloadBody == null) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
            mac.init(keySpec);
            byte[] rawHmac = mac.doFinal(payloadBody.getBytes(StandardCharsets.UTF_8));

            String hex = bytesToHex(rawHmac);
            String base64 = Base64.getEncoder().encodeToString(rawHmac);

            // header could contain "sha256=" prefix (like GitHub) — normalize:
            String sig = headerSignature.trim();
            if (sig.startsWith("sha256=")) {
                sig = sig.substring(7);
            }

            return secureEquals(sig, hex) || secureEquals(sig, base64);
        } catch (
                Exception e) {
            return false;
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    // Constant-time comparison
    private static boolean secureEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length())
            return false;
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}