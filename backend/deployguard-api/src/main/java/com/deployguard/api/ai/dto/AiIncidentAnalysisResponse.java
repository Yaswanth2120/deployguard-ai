package com.deployguard.api.ai.dto;

import java.util.List;

public record AiIncidentAnalysisResponse(
        String summary,
        String likelyRootCause,
        List<String> evidence,
        List<String> recommendedActions,
        String severity,
        String confidence) {}
