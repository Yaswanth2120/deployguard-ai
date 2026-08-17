package com.deployguard.api.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record GithubCallbackRequest(
        @NotBlank String code,
        @NotBlank String codeVerifier,
        @NotBlank String redirectUri
) {
}
