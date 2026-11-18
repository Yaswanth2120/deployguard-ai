from app.schemas import RiskScoreRequest, RiskScoreResponse
from app.services.risk_model import load_model
from app.services.risk_rules import RULES_VERSION, risk_level, rule_score


def calculate_risk_score(request: RiskScoreRequest) -> RiskScoreResponse:
    baseline, rule_factors = rule_score(request)
    baseline_level = risk_level(baseline)
    model = load_model()

    if model is None:
        rationale = "No risk signals were detected." if not rule_factors else "Deterministic rules found risk signals in PR, CI, or logs."
        return RiskScoreResponse(
            score=baseline, riskLevel=baseline_level, rationale=rationale, factors=rule_factors,
            modelVersion=RULES_VERSION, probability=None, baselineScore=baseline, baselineRiskLevel=baseline_level,
        )

    probability, factors = model.predict(request)
    score = max(0, min(100, round(probability * 100)))
    top = ", ".join(f.name for f in factors[:3] if f.contribution > 0) or "no strong risk drivers"
    return RiskScoreResponse(
        score=score,
        riskLevel=risk_level(score),
        rationale=f"Predicted incident probability {probability:.2f}; top drivers: {top}.",
        factors=factors,
        modelVersion=model.version,
        probability=round(probability, 4),
        baselineScore=baseline,
        baselineRiskLevel=baseline_level,
    )
