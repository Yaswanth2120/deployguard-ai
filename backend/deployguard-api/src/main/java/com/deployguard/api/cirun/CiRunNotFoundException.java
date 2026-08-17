package com.deployguard.api.cirun;

import java.util.UUID;

public class CiRunNotFoundException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public CiRunNotFoundException(UUID id) {
        super("CI run not found: " + id);
    }
}
