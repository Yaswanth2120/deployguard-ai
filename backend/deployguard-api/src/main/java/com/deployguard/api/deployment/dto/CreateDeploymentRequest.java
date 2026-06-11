package com.deployguard.api.deployment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CreateDeploymentRequest(
        @NotNull
        UUID projectId,

        @NotBlank
        @Size(max = 120)
        String commitSha,

        @NotBlank
        @Size(max = 120)
        String branch,

        @NotBlank
        @Size(max = 50)
        String environment,

        @NotBlank
        @Size(max = 50)
        String status,

        @NotBlank
        @Size(max = 120)
        String deployedBy,

        @NotNull
        OffsetDateTime deployedAt
) {
}
