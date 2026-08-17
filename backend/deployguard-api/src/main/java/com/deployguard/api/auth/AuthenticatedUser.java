package com.deployguard.api.auth;

public record AuthenticatedUser(String githubId, String login, String name, String avatarUrl, String role) {
}
