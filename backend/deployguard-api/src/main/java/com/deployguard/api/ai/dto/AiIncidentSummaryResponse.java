package com.deployguard.api.ai.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AiIncidentSummaryResponse(
        UUID id,
        UUID deploymentId,
        String summary,
        String likelyRootCause,
        String evidence,
        String recommendedActions,
        String severity,
        String confidence,
        String modelName,
        OffsetDateTime createdAt) {}
