package com.deployguard.api.auth;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class GithubOAuthClient {

    private static final String TOKEN_URL = "https://github.com/login/oauth/access_token";
    private static final String USER_URL = "https://api.github.com/user";

    private final AuthProperties properties;
    private final RestClient restClient;

    public GithubOAuthClient(AuthProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.create();
    }

    public GithubUser exchangeCodeForUser(String code, String codeVerifier, String redirectUri) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", properties.github().clientId());
        form.add("client_secret", properties.github().clientSecret());
        form.add("code", code);
        form.add("redirect_uri", redirectUri);
        form.add("code_verifier", codeVerifier);

        GithubTokenResponse tokenResponse;
        try {
            tokenResponse = restClient.post()
                    .uri(TOKEN_URL)
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(GithubTokenResponse.class);
        } catch (RestClientException exception) {
            throw new GithubOAuthException("GitHub token exchange failed: " + exception.getMessage());
        }

        if (tokenResponse == null || tokenResponse.accessToken() == null) {
            String reason = tokenResponse != null ? tokenResponse.errorDescription() : "empty response";
            throw new GithubOAuthException("GitHub token exchange failed: " + reason);
        }

        GithubUser user;
        try {
            user = restClient.get()
                    .uri(USER_URL)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenResponse.accessToken())
                    .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                    .retrieve()
                    .body(GithubUser.class);
        } catch (RestClientException exception) {
            throw new GithubOAuthException("GitHub user lookup failed: " + exception.getMessage());
        }

        if (user == null) {
            throw new GithubOAuthException("GitHub user lookup returned no data");
        }

        return user;
    }
}
