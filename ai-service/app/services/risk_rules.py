"""Deterministic rule baseline. Constants must match shared/risk-rules.json; the
contract test (tests/test_risk_rules_contract.py) enforces it, and the Java backend
has the same test against the same file."""

from app.schemas import RiskFactor, RiskScoreRequest

RULES_VERSION = "deterministic-v2"
ERROR_LOG_LEVELS = frozenset({"ERROR", "FATAL"})
PRODUCTION_ENVIRONMENTS = frozenset({"production", "prod"})
WEIGHTS = {
    "failed_ci_run": 30,
    "failed_tests": 20,
    "error_logs": 30,
    "hotfix_branch": 10,
    "production_environment": 10,
}
MAX_SCORE = 100
LOW_MAX = 30
MEDIUM_MAX = 70


def risk_level(score: int) -> str:
    if score <= LOW_MAX:
        return "LOW"
    if score <= MEDIUM_MAX:
        return "MEDIUM"
    return "HIGH"


def rule_factors(request: RiskScoreRequest) -> list[RiskFactor]:
    factors: list[RiskFactor] = []
    pr = request.pullRequest
    if any(run.status.upper() == "FAILED" for run in request.ciRuns):
        factors.append(RiskFactor(name="failed_ci_run", contribution=WEIGHTS["failed_ci_run"], detail="At least one CI run failed."))
    failed_tests = sum(run.failedTests for run in request.ciRuns)
    if failed_tests:
        factors.append(RiskFactor(name="failed_tests", contribution=WEIGHTS["failed_tests"], detail=f"{failed_tests} failed test(s) reported."))
    error_logs = sum(1 for log in request.logs if log.severity.upper() in ERROR_LOG_LEVELS)
    if error_logs:
        factors.append(RiskFactor(name="error_logs", contribution=WEIGHTS["error_logs"], detail=f"{error_logs} error/fatal log(s) reported."))
    if "hotfix" in pr.sourceBranch.lower():
        factors.append(RiskFactor(name="hotfix_branch", contribution=WEIGHTS["hotfix_branch"], detail="Source branch is a hotfix."))
    if pr.targetEnvironment and pr.targetEnvironment.lower() in PRODUCTION_ENVIRONMENTS:
        factors.append(RiskFactor(name="production_environment", contribution=WEIGHTS["production_environment"], detail="Targets production."))
    return factors


def rule_score(request: RiskScoreRequest) -> tuple[int, list[RiskFactor]]:
    factors = rule_factors(request)
    return min(MAX_SCORE, int(sum(f.contribution for f in factors))), factors
