package com.deployguard.api.cirun;

import java.util.UUID;

public class CiRunNotFoundException extends RuntimeException {

    public CiRunNotFoundException(UUID id) {
        super("CI run not found: " + id);
    }
}
