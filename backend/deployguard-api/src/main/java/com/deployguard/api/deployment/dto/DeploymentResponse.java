package com.deployguard.api.deployment.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DeploymentResponse(
        UUID id,
        UUID projectId,
        String commitSha,
        String branch,
        String environment,
        String status,
        String deployedBy,
        OffsetDateTime deployedAt,
        Integer riskScore,
        String riskLevel,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
