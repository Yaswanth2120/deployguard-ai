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


class PullRequestContext(BaseModel):
    repositoryId: str
    number: int = Field(gt=0)
    commitSha: str
    sourceBranch: str
    targetBranch: str
    title: str = ""


class CiRunContext(BaseModel):
    provider: str
    status: str
    failedTests: int = Field(default=0, ge=0)
    durationSeconds: int = Field(default=0, ge=0)


class LogContext(BaseModel):
    severity: str
    message: str
    timestamp: str


class RiskScoreRequest(BaseModel):
    pullRequest: PullRequestContext
    ciRuns: list[CiRunContext]
    logs: list[LogContext]


class RiskFactor(BaseModel):
    name: str
    contribution: int
    detail: str


class RiskScoreResponse(BaseModel):
    score: int = Field(ge=0, le=100)
    riskLevel: RiskLevel
    rationale: str
    factors: list[RiskFactor]
    modelVersion: str
