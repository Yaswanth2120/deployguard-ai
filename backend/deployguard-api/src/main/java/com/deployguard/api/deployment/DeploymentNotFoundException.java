package com.deployguard.api.deployment;

import java.util.UUID;

public class DeploymentNotFoundException extends RuntimeException {

    public DeploymentNotFoundException(UUID id) {
        super("Deployment not found: " + id);
    }
}
