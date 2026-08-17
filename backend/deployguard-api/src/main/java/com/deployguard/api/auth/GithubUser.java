package com.deployguard.api.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GithubUser(
        long id,
        String login,
        String name,
        @JsonProperty("avatar_url") String avatarUrl
) {
}
