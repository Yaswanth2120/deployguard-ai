package com.deployguard.api.cirun.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateCiRunRequest(
        @NotNull
        UUID projectId,

        @NotBlank
        @Size(max = 120)
        String commitSha,

        @NotBlank
        @Size(max = 80)
        String provider,

        @NotBlank
        @Size(max = 50)
        String status,

        @NotNull
        @Min(0)
        Integer durationSeconds,

        @NotNull
        @Min(0)
        Integer failedTests
) {
}
