package com.deployguard.api.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GithubTokenResponse(
        @JsonProperty("access_token") String accessToken,
        String error,
        @JsonProperty("error_description") String errorDescription
) {
}
