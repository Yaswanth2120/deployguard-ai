package com.deployguard.api.ai.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AiIncidentSummaryResponse(
        UUID id,
        UUID deploymentId,
        String summary,
        String likelyRootCause,
        String rootCauseCategory,
        String evidence,
        String recommendedActions,
        String severity,
        String confidence,
        String modelName,
        String source,
        String fallbackReason,
        String analysisMode,
        String promptVersion,
        Integer promptTokens,
        Integer completionTokens,
        Integer latencyMs,
        Integer droppedEvidence,
        OffsetDateTime createdAt) {}
