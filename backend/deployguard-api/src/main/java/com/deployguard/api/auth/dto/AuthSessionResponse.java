package com.deployguard.api.auth.dto;

import java.time.Instant;

public record AuthSessionResponse(String accessToken, Instant expiresAt, AuthUserResponse user) {
}
