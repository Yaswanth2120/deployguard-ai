from typing import Any, Literal

from pydantic import BaseModel, ConfigDict, Field

RiskLevel = Literal["LOW", "MEDIUM", "HIGH"]
ConfidenceLevel = Literal["LOW", "MEDIUM", "HIGH"]
AnalysisSource = Literal["model", "heuristic"]
AnalysisMode = Literal["heuristic", "single_shot", "rag", "agent"]

# Closed set of root-cause categories. A closed label space makes model output
# scoreable against the golden evaluation set (see evals/).
RootCauseCategory = Literal[
    "BAD_MIGRATION",
    "CONFIG_ERROR",
    "DEPENDENCY_REGRESSION",
    "RESOURCE_EXHAUSTION",
    "DOWNSTREAM_TIMEOUT",
    "AUTH_FAILURE",
    "CODE_DEFECT",
    "TEST_FAILURE",
    "INFRASTRUCTURE",
    "NO_INCIDENT",
    "INSUFFICIENT_EVIDENCE",
]
ROOT_CAUSE_CATEGORIES: tuple[str, ...] = RootCauseCategory.__args__  # type: ignore[attr-defined]


class HealthResponse(BaseModel):
    status: str
    service: str


class IncidentAnalysisRequest(BaseModel):
    deployment: dict[str, Any] = Field(default_factory=dict)
    ciRuns: list[dict[str, Any]] = Field(default_factory=list)
    logs: list[dict[str, Any]] = Field(default_factory=list)
    riskScore: int = 0
    riskLevel: RiskLevel = "LOW"


class AnalysisMeta(BaseModel):
    """Provenance and telemetry for one analysis. Never contains prompt text or raw input."""

    source: AnalysisSource
    mode: AnalysisMode
    fallbackReason: str | None = None
    model: str | None = None
    promptVersion: str
    latencyMs: int = 0
    promptTokens: int = 0
    completionTokens: int = 0
    estimatedCostUsd: float = 0.0
    toolCalls: int = 0
    redactions: int = 0
    droppedEvidence: int = 0
    retrievedDocuments: list[str] = Field(default_factory=list)


class IncidentAnalysisResponse(BaseModel):
    summary: str
    likelyRootCause: str
    rootCauseCategory: RootCauseCategory
    evidence: list[str]
    recommendedActions: list[str]
    severity: RiskLevel
    confidence: ConfidenceLevel
    meta: AnalysisMeta | None = None


class ModelIncidentOutput(BaseModel):
    """Exact shape the LLM must return. Validated before anything is persisted."""

    model_config = ConfigDict(extra="forbid")

    summary: str = Field(min_length=1)
    likelyRootCause: str = Field(min_length=1)
    rootCauseCategory: RootCauseCategory
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
    # Change-shape features. Optional so the rule baseline still works without them.
    additions: int = Field(default=0, ge=0)
    deletions: int = Field(default=0, ge=0)
    changedFiles: int = Field(default=0, ge=0)
    touchesMigrations: bool = False
    touchesConfig: bool = False
    testFilesChanged: int = Field(default=0, ge=0)
    authorPriorMergedPrs: int = Field(default=0, ge=0)
    targetEnvironment: str | None = None
    mergedAtHourUtc: int | None = Field(default=None, ge=0, le=23)
    mergedOnWeekend: bool = False


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
    contribution: float
    detail: str


class RiskScoreResponse(BaseModel):
    score: int = Field(ge=0, le=100)
    riskLevel: RiskLevel
    rationale: str
    factors: list[RiskFactor]
    modelVersion: str
    probability: float | None = Field(default=None, ge=0.0, le=1.0)
    baselineScore: int = Field(ge=0, le=100)
    baselineRiskLevel: RiskLevel
