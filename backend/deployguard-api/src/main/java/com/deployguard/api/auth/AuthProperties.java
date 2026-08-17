package com.deployguard.api.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "deployguard.auth")
public record AuthProperties(Github github, Jwt jwt) {

    public record Github(String clientId, String clientSecret) {
    }

    public record Jwt(String signingKey, long expirationMinutes) {
    }
}
