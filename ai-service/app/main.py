from fastapi import FastAPI

from app.schemas import HealthResponse, IncidentAnalysisRequest, IncidentAnalysisResponse
from app.services.incident_analyzer import analyze_incident

app = FastAPI(title="DeployGuard AI Service")


@app.get("/health", response_model=HealthResponse)
def health() -> HealthResponse:
    return HealthResponse(status="UP", service="deployguard-ai-service")


@app.post("/analyze-incident", response_model=IncidentAnalysisResponse)
def analyze_incident_endpoint(request: IncidentAnalysisRequest) -> IncidentAnalysisResponse:
    return analyze_incident(request)
