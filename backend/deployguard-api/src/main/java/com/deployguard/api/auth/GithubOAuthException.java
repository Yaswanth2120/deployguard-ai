package com.deployguard.api.auth;

public class GithubOAuthException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public GithubOAuthException(String message) {
        super(message);
    }
}
