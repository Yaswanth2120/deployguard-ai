package com.deployguard.api.auth;

public class InvalidTokenException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public InvalidTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}
