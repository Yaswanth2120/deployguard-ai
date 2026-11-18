package com.deployguard.api.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class GithubSignatureVerifierTest {

    private static final byte[] BODY = "{\"zen\":\"Keep it logically awesome.\"}".getBytes(StandardCharsets.UTF_8);

    @Test
    void matchesGithubDocumentedExample() {
        // Example from GitHub's "Validating webhook deliveries" documentation.
        byte[] body = "Hello, World!".getBytes(StandardCharsets.UTF_8);
        assertThat(GithubSignatureVerifier.sign("It's a Secret to Everybody", body))
                .isEqualTo("sha256=757107ea0eb2509fc211221cce984b8a37570b6d7586c22c46f4379c8b043e17");
    }

    @Test
    void acceptsValidSignature() {
        assertThat(GithubSignatureVerifier.isValid("s3cret", BODY, GithubSignatureVerifier.sign("s3cret", BODY))).isTrue();
    }

    @Test
    void rejectsTamperedBodyWrongSecretAndMalformedHeaders() {
        String signature = GithubSignatureVerifier.sign("s3cret", BODY);
        assertThat(GithubSignatureVerifier.isValid("s3cret", "{}".getBytes(StandardCharsets.UTF_8), signature)).isFalse();
        assertThat(GithubSignatureVerifier.isValid("other", BODY, signature)).isFalse();
        assertThat(GithubSignatureVerifier.isValid("s3cret", BODY, null)).isFalse();
        assertThat(GithubSignatureVerifier.isValid("s3cret", BODY, "sha1=abc")).isFalse();
        assertThat(GithubSignatureVerifier.isValid("s3cret", BODY, "sha256=not-hex")).isFalse();
        assertThat(GithubSignatureVerifier.isValid("", BODY, signature)).isFalse();
    }
}
