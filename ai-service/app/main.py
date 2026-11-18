import logging
from typing import Annotated

from fastapi import FastAPI, Query
from fastapi.responses import PlainTextResponse

from app.schemas import (
    AnalysisMode,
    HealthResponse,
    IncidentAnalysisRequest,
    IncidentAnalysisResponse,
    RiskScoreRequest,
    RiskScoreResponse,
)
from app.services.incident_analyzer import analyze_incident
from app.services.risk_scorer import calculate_risk_score
from app.telemetry import metrics

logging.basicConfig(level=logging.INFO, format="%(message)s")

app = FastAPI(title="DeployGuard AI Service")


@app.get("/health", response_model=HealthResponse)
def health() -> HealthResponse:
    return HealthResponse(status="UP", service="deployguard-ai-service")


@app.post("/analyze-incident", response_model=IncidentAnalysisResponse)
async def analyze_incident_endpoint(
    request: IncidentAnalysisRequest,
    mode: Annotated[AnalysisMode | None, Query(description="Override ANALYSIS_MODE for this request.")] = None,
) -> IncidentAnalysisResponse:
    return await analyze_incident(request, mode=mode)


@app.post("/v1/risk-scores", response_model=RiskScoreResponse)
def risk_score_endpoint(request: RiskScoreRequest) -> RiskScoreResponse:
    return calculate_risk_score(request)


@app.get("/metrics", response_class=PlainTextResponse)
def metrics_endpoint() -> str:
    return metrics.render()
