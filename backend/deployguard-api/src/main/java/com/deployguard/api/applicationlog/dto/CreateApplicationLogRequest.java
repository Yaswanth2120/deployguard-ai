package com.deployguard.api.applicationlog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CreateApplicationLogRequest(
        @NotNull
        UUID projectId,

        UUID deploymentId,

        @NotBlank
        @Size(max = 120)
        String serviceName,

        @NotBlank
        @Size(max = 30)
        String level,

        @NotBlank
        String message,

        @NotNull
        OffsetDateTime timestamp
) {
}
