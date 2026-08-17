package com.deployguard.api.auth.dto;

public record AuthUserResponse(String githubId, String login, String name, String avatarUrl, String role) {
}
