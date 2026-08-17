from fastapi.testclient import TestClient

from app.main import app
from app.schemas import CiRunContext, LogContext, PullRequestContext, RiskScoreRequest
from app.services.risk_scorer import calculate_risk_score


def test_risk_scorer_correlates_pr_ci_and_logs() -> None:
    response = calculate_risk_score(RiskScoreRequest(
        pullRequest=PullRequestContext(repositoryId="acme/api", number=12, commitSha="abc", sourceBranch="hotfix/login", targetBranch="main"),
        ciRuns=[CiRunContext(provider="github-actions", status="FAILED", failedTests=2)],
        logs=[LogContext(severity="ERROR", message="sanitized", timestamp="2026-01-01T00:00:00Z")],
    ))
    assert response.score == 90
    assert response.riskLevel == "HIGH"
    assert len(response.factors) == 4


def test_risk_score_endpoint_returns_contract_response() -> None:
    response = TestClient(app).post("/v1/risk-scores", json={
        "pullRequest": {"repositoryId": "acme/api", "number": 1, "commitSha": "abc", "sourceBranch": "main", "targetBranch": "main"},
        "ciRuns": [], "logs": [],
    })
    assert response.status_code == 200
    assert response.json()["riskLevel"] == "LOW"
