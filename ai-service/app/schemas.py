from typing import Any, Literal

from pydantic import BaseModel, Field

RiskLevel = Literal["LOW", "MEDIUM", "HIGH"]
ConfidenceLevel = Literal["LOW", "MEDIUM", "HIGH"]


class HealthResponse(BaseModel):
    status: str
    service: str


class IncidentAnalysisRequest(BaseModel):
    deployment: dict[str, Any] = Field(default_factory=dict)
    ciRuns: list[dict[str, Any]] = Field(default_factory=list)
    logs: list[dict[str, Any]] = Field(default_factory=list)
    riskScore: int = 0
    riskLevel: RiskLevel = "LOW"


class IncidentAnalysisResponse(BaseModel):
    summary: str
    likelyRootCause: str
    evidence: list[str]
    recommendedActions: list[str]
    severity: RiskLevel
    confidence: ConfidenceLevel
