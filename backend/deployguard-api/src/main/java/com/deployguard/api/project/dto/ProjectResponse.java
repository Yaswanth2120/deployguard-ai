package com.deployguard.api.project.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ProjectResponse(
        UUID id,
        String name,
        String githubRepoUrl,
        String serviceName,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
