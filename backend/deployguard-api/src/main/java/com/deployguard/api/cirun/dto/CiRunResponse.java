package com.deployguard.api.cirun.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CiRunResponse(
        UUID id,
        UUID projectId,
        String commitSha,
        String provider,
        String status,
        Integer durationSeconds,
        Integer failedTests,
        OffsetDateTime createdAt
) {
}
