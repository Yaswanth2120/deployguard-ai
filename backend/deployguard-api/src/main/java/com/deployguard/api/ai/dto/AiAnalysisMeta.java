package com.deployguard.api.ai.dto;

import java.util.List;

public record AiAnalysisMeta(
        String source,
        String mode,
        String fallbackReason,
        String model,
        String promptVersion,
        Integer latencyMs,
        Integer promptTokens,
        Integer completionTokens,
        Double estimatedCostUsd,
        Integer toolCalls,
        Integer redactions,
        Integer droppedEvidence,
        List<String> retrievedDocuments) {}
