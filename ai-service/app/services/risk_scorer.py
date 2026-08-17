from app.schemas import RiskFactor, RiskScoreRequest, RiskScoreResponse


def calculate_risk_score(request: RiskScoreRequest) -> RiskScoreResponse:
    factors: list[RiskFactor] = []
    if "hotfix" in request.pullRequest.sourceBranch.lower():
        factors.append(RiskFactor(name="hotfix_branch", contribution=10, detail="Source branch is a hotfix."))
    if any(run.status.upper() == "FAILED" for run in request.ciRuns):
        factors.append(RiskFactor(name="failed_ci_run", contribution=30, detail="At least one CI run failed."))
    failed_tests = sum(run.failedTests for run in request.ciRuns)
    if failed_tests:
        factors.append(RiskFactor(name="failed_tests", contribution=20, detail=f"{failed_tests} failed test(s) reported."))
    error_logs = sum(1 for log in request.logs if log.severity.upper() in {"ERROR", "FATAL"})
    if error_logs:
        factors.append(RiskFactor(name="error_logs", contribution=30, detail=f"{error_logs} error log(s) reported."))
    score = min(100, sum(factor.contribution for factor in factors))
    risk_level = "HIGH" if score > 70 else "MEDIUM" if score > 30 else "LOW"
    rationale = "No risk signals were detected." if not factors else "Deterministic correlation found risk signals in PR, CI, or logs."
    return RiskScoreResponse(score=score, riskLevel=risk_level, rationale=rationale, factors=factors, modelVersion="deterministic-v1")
