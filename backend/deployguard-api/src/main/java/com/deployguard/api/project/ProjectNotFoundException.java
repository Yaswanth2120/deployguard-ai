package com.deployguard.api.project;

import java.util.UUID;

public class ProjectNotFoundException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public ProjectNotFoundException(UUID id) {
        super("Project not found: " + id);
    }
}
