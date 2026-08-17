package com.deployguard.api.deployment;

import java.util.UUID;

public class DeploymentNotFoundException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public DeploymentNotFoundException(UUID id) {
        super("Deployment not found: " + id);
    }
}
