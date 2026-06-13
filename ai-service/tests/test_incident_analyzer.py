import asyncio

import httpx

from app.schemas import IncidentAnalysisRequest
from app.services import incident_analyzer


def run(coro):
    return asyncio.run(coro)


def test_missing_api_key_returns_fallback(monkeypatch):
    monkeypatch.delenv("OPENROUTER_API_KEY", raising=False)

    async def fail_if_called(request, api_key):
        raise AssertionError("OpenRouter should not be called without an API key.")

    monkeypatch.setattr(incident_analyzer, "call_openrouter", fail_if_called)

    response = run(incident_analyzer.analyze_incident(IncidentAnalysisRequest(riskLevel="LOW")))

    assert response.severity == "LOW"
    assert response.confidence == "LOW"
    assert response.evidence == []


def test_invalid_json_returns_fallback(monkeypatch):
    monkeypatch.setenv("OPENROUTER_API_KEY", "test-key")

    async def invalid_json_response(request, api_key):
        return "this is not json"

    monkeypatch.setattr(incident_analyzer, "call_openrouter", invalid_json_response)

    response = run(incident_analyzer.analyze_incident(IncidentAnalysisRequest(riskLevel="HIGH")))

    assert response.severity == "HIGH"
    assert response.confidence == "MEDIUM"
    assert response.evidence == []


def test_timeout_returns_fallback(monkeypatch):
    monkeypatch.setenv("OPENROUTER_API_KEY", "test-key")

    async def timeout_response(request, api_key):
        raise httpx.TimeoutException("request timed out")

    monkeypatch.setattr(incident_analyzer, "call_openrouter", timeout_response)

    response = run(incident_analyzer.analyze_incident(IncidentAnalysisRequest(riskLevel="MEDIUM")))

    assert response.severity == "MEDIUM"
    assert response.confidence == "MEDIUM"
    assert response.recommendedActions


def test_invalid_response_shape_returns_fallback(monkeypatch):
    monkeypatch.setenv("OPENROUTER_API_KEY", "test-key")

    async def wrong_shape_response(request, api_key):
        return '{"summary": "missing required fields"}'

    monkeypatch.setattr(incident_analyzer, "call_openrouter", wrong_shape_response)

    response = run(incident_analyzer.analyze_incident(IncidentAnalysisRequest(riskLevel="LOW")))

    assert response.severity == "LOW"
    assert response.likelyRootCause.startswith("Mock analysis")
