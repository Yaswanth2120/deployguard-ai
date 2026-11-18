package com.deployguard.api.ai.dto;

import java.util.List;

public record AiIncidentAnalysisResponse(
        String summary,
        String likelyRootCause,
        String rootCauseCategory,
        List<String> evidence,
        List<String> recommendedActions,
        String severity,
        String confidence,
        AiAnalysisMeta meta) {}
