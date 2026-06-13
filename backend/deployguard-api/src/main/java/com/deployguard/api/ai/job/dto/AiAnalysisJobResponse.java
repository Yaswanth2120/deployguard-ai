package com.deployguard.api.ai.job.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AiAnalysisJobResponse(
        UUID id,
        UUID deploymentId,
        String status,
        String errorMessage,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime completedAt) {}
