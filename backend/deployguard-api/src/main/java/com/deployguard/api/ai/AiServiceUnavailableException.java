package com.deployguard.api.ai;

public class AiServiceUnavailableException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public AiServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
