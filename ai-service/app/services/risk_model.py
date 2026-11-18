"""Online scoring with the trained logistic-regression risk model.

The artifact (ml/artifacts/risk_model.json) holds standardization stats and
coefficients only, so serving needs no numpy/sklearn. Per-feature contributions
(coef * standardized value) make every score explainable.
"""

import json
import math
import os
from functools import lru_cache
from pathlib import Path

from app.ml_features import FEATURE_NAMES, extract_features
from app.schemas import RiskFactor, RiskScoreRequest

DEFAULT_ARTIFACT = Path(__file__).resolve().parents[2] / "ml_artifacts" / "risk_model.json"


class RiskModel:
    def __init__(self, artifact: dict) -> None:
        if tuple(artifact["featureNames"]) != FEATURE_NAMES:
            raise ValueError("Risk model artifact feature order does not match FEATURE_NAMES.")
        self.version: str = artifact["version"]
        self.mean: list[float] = artifact["mean"]
        self.std: list[float] = artifact["std"]
        self.coef: list[float] = artifact["coef"]
        self.intercept: float = artifact["intercept"]

    def predict(self, request: RiskScoreRequest) -> tuple[float, list[RiskFactor]]:
        x = extract_features(request)
        z = [(xi - m) / s if s else 0.0 for xi, m, s in zip(x, self.mean, self.std)]
        contributions = [c * zi for c, zi in zip(self.coef, z)]
        logit = self.intercept + sum(contributions)
        probability = 1 / (1 + math.exp(-logit))
        factors = [
            RiskFactor(name=name, contribution=round(contrib, 3), detail=f"value={raw:.3g}")
            for name, contrib, raw in zip(FEATURE_NAMES, contributions, x)
            if abs(contrib) >= 0.05
        ]
        factors.sort(key=lambda f: -abs(f.contribution))
        return probability, factors


@lru_cache(maxsize=1)
def load_model() -> RiskModel | None:
    path = Path(os.getenv("RISK_MODEL_PATH", str(DEFAULT_ARTIFACT)))
    if not path.exists():
        return None
    return RiskModel(json.loads(path.read_text()))
