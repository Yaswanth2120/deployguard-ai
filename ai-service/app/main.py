from fastapi import FastAPI

from app.schemas import (
    HealthResponse,
    IncidentAnalysisRequest,
    IncidentAnalysisResponse,
    RiskScoreRequest,
    RiskScoreResponse,
)
from app.services.incident_analyzer import analyze_incident
from app.services.risk_scorer import calculate_risk_score

app = FastAPI(title="DeployGuard AI Service")


@app.get("/health", response_model=HealthResponse)
def health() -> HealthResponse:
    return HealthResponse(status="UP", service="deployguard-ai-service")


@app.post("/analyze-incident", response_model=IncidentAnalysisResponse)
async def analyze_incident_endpoint(request: IncidentAnalysisRequest) -> IncidentAnalysisResponse:
    return await analyze_incident(request)


@app.post("/v1/risk-scores", response_model=RiskScoreResponse)
def risk_score_endpoint(request: RiskScoreRequest) -> RiskScoreResponse:
    return calculate_risk_score(request)
