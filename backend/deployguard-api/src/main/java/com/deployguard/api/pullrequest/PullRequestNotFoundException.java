package com.deployguard.api.pullrequest;

import java.util.UUID;

public class PullRequestNotFoundException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public PullRequestNotFoundException(UUID id) {
        super("Pull request not found: " + id);
    }
}
