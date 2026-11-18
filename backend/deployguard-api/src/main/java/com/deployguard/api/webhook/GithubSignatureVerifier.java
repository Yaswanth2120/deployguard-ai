package com.deployguard.api.webhook;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Verifies GitHub's X-Hub-Signature-256 header (HMAC-SHA256 of the raw body) in constant time. */
public final class GithubSignatureVerifier {

    private static final String PREFIX = "sha256=";

    private GithubSignatureVerifier() {
    }

    public static boolean isValid(String secret, byte[] body, String signatureHeader) {
        if (secret == null || secret.isBlank() || signatureHeader == null || !signatureHeader.startsWith(PREFIX)) {
            return false;
        }
        byte[] expected = hmac(secret, body);
        byte[] provided;
        try {
            provided = HexFormat.of().parseHex(signatureHeader.substring(PREFIX.length()));
        } catch (IllegalArgumentException ex) {
            return false;
        }
        return MessageDigest.isEqual(expected, provided);
    }

    public static String sign(String secret, byte[] body) {
        return PREFIX + HexFormat.of().formatHex(hmac(secret, body));
    }

    private static byte[] hmac(String secret, byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(body);
        } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
            throw new IllegalStateException("HmacSHA256 unavailable", ex);
        }
    }
}
