package com.deployguard.api.pullrequest.dto;
import java.time.OffsetDateTime;
import java.util.List;
public record RiskScoreResponse(int score, String riskLevel, String rationale, List<RiskFactorResponse> factors, OffsetDateTime calculatedAt) { }
