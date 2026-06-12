package com.deployguard.api.applicationlog.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ApplicationLogResponse(
        UUID id,
        UUID projectId,
        UUID deploymentId,
        String serviceName,
        String level,
        String message,
        OffsetDateTime timestamp,
        OffsetDateTime createdAt
) {
}
