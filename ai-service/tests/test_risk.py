import json
from pathlib import Path

from fastapi.testclient import TestClient

from app.main import app
from app.ml_features import FEATURE_NAMES
from app.schemas import CiRunContext, LogContext, PullRequestContext, RiskScoreRequest
from app.services import risk_rules
from app.services.risk_model import load_model
from app.services.risk_scorer import calculate_risk_score

RULES = json.loads((Path(__file__).resolve().parents[2] / "shared" / "risk-rules.json").read_text())


def _request(branch="main", env=None, ci=(), failed_tests=0, levels=(), **pr) -> RiskScoreRequest:
    return RiskScoreRequest(
        pullRequest=PullRequestContext(repositoryId="acme/api", number=1, commitSha="abc", sourceBranch=branch,
                                       targetBranch="main", targetEnvironment=env, **pr),
        ciRuns=[CiRunContext(provider="gha", status=s, failedTests=failed_tests if i == 0 else 0) for i, s in enumerate(ci)],
        logs=[LogContext(severity=lvl, message="m", timestamp="t") for lvl in levels],
    )


def test_rule_constants_match_shared_contract() -> None:
    assert risk_rules.RULES_VERSION == RULES["version"]
    assert risk_rules.WEIGHTS == RULES["weights"]
    assert set(risk_rules.ERROR_LOG_LEVELS) == set(RULES["errorLogLevels"])
    assert set(risk_rules.PRODUCTION_ENVIRONMENTS) == set(RULES["productionEnvironments"])
    assert (risk_rules.LOW_MAX, risk_rules.MEDIUM_MAX, risk_rules.MAX_SCORE) == (
        RULES["thresholds"]["lowMax"], RULES["thresholds"]["mediumMax"], RULES["maxScore"])


def test_rule_test_vectors() -> None:
    for vector in RULES["testVectors"]:
        request = _request(vector["branch"], vector["environment"], vector["ciStatuses"], vector["failedTests"], vector["logLevels"])
        score, _ = risk_rules.rule_score(request)
        assert score == vector["expectedScore"], vector["name"]
        assert risk_rules.risk_level(score) == vector["expectedLevel"], vector["name"]


def test_model_artifact_matches_feature_contract() -> None:
    model = load_model()
    assert model is not None
    assert len(model.coef) == len(FEATURE_NAMES)


def test_model_is_monotonic_in_key_risk_signals() -> None:
    model = load_model()
    base = _request(additions=50, deletions=10, changedFiles=3, testFilesChanged=1, authorPriorMergedPrs=30, ci=["SUCCESS"])
    p0, _ = model.predict(base)
    for risky in (
        _request(additions=50, deletions=10, changedFiles=3, testFilesChanged=1, authorPriorMergedPrs=30, ci=["FAILED"], failed_tests=3),
        _request(additions=50, deletions=10, changedFiles=3, testFilesChanged=1, authorPriorMergedPrs=30, ci=["SUCCESS"], touchesMigrations=True),
        _request(additions=5000, deletions=900, changedFiles=3, testFilesChanged=1, authorPriorMergedPrs=30, ci=["SUCCESS"]),
    ):
        assert model.predict(risky)[0] > p0


def test_scorer_returns_model_and_baseline() -> None:
    response = calculate_risk_score(_request("hotfix/x", "prod", ["FAILED"], 2, ["ERROR"], touchesMigrations=True, additions=900))
    assert response.modelVersion.startswith("logreg")
    assert 0 < response.probability < 1
    assert response.baselineScore == 100
    assert response.baselineRiskLevel == "HIGH"
    assert response.factors and response.factors[0].contribution != 0


def test_scorer_falls_back_to_rules_without_artifact(monkeypatch, tmp_path) -> None:
    monkeypatch.setenv("RISK_MODEL_PATH", str(tmp_path / "missing.json"))
    load_model.cache_clear()
    try:
        response = calculate_risk_score(_request("hotfix/x", ci=["FAILED"], failed_tests=1))
        assert response.modelVersion == RULES["version"]
        assert response.score == response.baselineScore == 60
        assert response.probability is None
    finally:
        load_model.cache_clear()


def test_risk_endpoint_contract() -> None:
    response = TestClient(app).post("/v1/risk-scores", json={
        "pullRequest": {"repositoryId": "acme/api", "number": 1, "commitSha": "abc", "sourceBranch": "main", "targetBranch": "main"},
        "ciRuns": [], "logs": [],
    })
    assert response.status_code == 200
    body = response.json()
    assert body["baselineRiskLevel"] == "LOW"
    assert body["riskLevel"] in {"LOW", "MEDIUM", "HIGH"}
