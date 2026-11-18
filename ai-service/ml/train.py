"""Train the deployment-risk model and benchmark it against the rule baseline.

    python -m ml.synthetic --out ml/data/synthetic.jsonl          # or ml.mine_github for real data
    python -m ml.train --data ml/data/synthetic.jsonl

Model: L2-regularized logistic regression fit with IRLS (Newton's method).
Chosen deliberately over gradient-boosted trees: 14 features, need for calibrated
probabilities, per-feature explanations at serving time, and a dependency-free
serving path. Regularization strength is selected on a validation split by log loss.
Split is ordered (first 70% train, next 15% validation, last 15% test) so mined,
time-ordered data is never trained on the future.
"""

import argparse
import json
from datetime import UTC, datetime
from pathlib import Path

import numpy as np

from app.ml_features import FEATURE_NAMES, extract_features
from app.schemas import RiskScoreRequest
from app.services.risk_rules import rule_score
from ml.metrics import summarize

LAMBDAS = (0.0, 0.01, 0.1, 1.0, 10.0)


def load(path: Path) -> tuple[np.ndarray, np.ndarray, np.ndarray, str]:
    xs, ys, baseline, sources = [], [], [], set()
    for line in path.read_text().splitlines():
        row = json.loads(line)
        request = RiskScoreRequest.model_validate(row["request"])
        xs.append(extract_features(request))
        ys.append(int(row["label"]))
        baseline.append(rule_score(request)[0] / 100)
        sources.add(row.get("source", "unknown"))
    return np.array(xs), np.array(ys, dtype=float), np.array(baseline), ",".join(sorted(sources))


def fit_logreg(x: np.ndarray, y: np.ndarray, lam: float, iters: int = 50) -> np.ndarray:
    xb = np.c_[np.ones(len(x)), x]
    w = np.zeros(xb.shape[1])
    reg = lam * np.eye(xb.shape[1])
    reg[0, 0] = 0.0  # do not regularize the intercept
    for _ in range(iters):
        p = 1 / (1 + np.exp(-xb @ w))
        grad = xb.T @ (p - y) + reg @ w
        hess = (xb * (p * (1 - p))[:, None]).T @ xb + reg + 1e-9 * np.eye(len(w))
        step = np.linalg.solve(hess, grad)
        w -= step
        if np.max(np.abs(step)) < 1e-8:
            break
    return w


def predict(x: np.ndarray, w: np.ndarray) -> np.ndarray:
    return 1 / (1 + np.exp(-(w[0] + x @ w[1:])))


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--data", type=Path, required=True)
    parser.add_argument("--artifact", type=Path, default=Path("ml_artifacts/risk_model.json"))
    parser.add_argument("--report", type=Path, default=Path("ml/reports/risk_model_report.md"))
    parser.add_argument("--version", default="logreg-v1")
    args = parser.parse_args()

    x, y, baseline, source = load(args.data)
    n = len(y)
    tr, va = int(n * 0.70), int(n * 0.85)
    mean = x[:tr].mean(axis=0)
    std = x[:tr].std(axis=0)
    std[std == 0] = 1.0
    z = (x - mean) / std

    best = None
    for lam in LAMBDAS:
        w = fit_logreg(z[:tr], y[:tr], lam)
        val = summarize(y[tr:va], predict(z[tr:va], w))
        if best is None or val["logLoss"] < best[1]["logLoss"]:
            best = (lam, val, w)
    lam, _val_metrics, _ = best
    # Refit on train+validation with the selected lambda, evaluate once on test.
    w = fit_logreg(z[:va], y[:va], lam)
    test_model = summarize(y[va:], predict(z[va:], w))
    test_rules = summarize(y[va:], baseline[va:])

    artifact = {
        "version": args.version,
        "featureNames": list(FEATURE_NAMES),
        "mean": mean.round(6).tolist(),
        "std": std.round(6).tolist(),
        "intercept": round(float(w[0]), 6),
        "coef": w[1:].round(6).tolist(),
        "l2Lambda": lam,
        "trainedAt": datetime.now(UTC).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "dataSource": source,
        "rows": {"train": tr, "validation": va - tr, "test": n - va, "positiveRate": round(float(y.mean()), 4)},
        "testMetrics": {"model": test_model, "rulesBaseline": test_rules},
    }
    args.artifact.parent.mkdir(parents=True, exist_ok=True)
    args.artifact.write_text(json.dumps(artifact, indent=2) + "\n")

    synthetic = "synthetic" in source
    lines = [
        f"# Risk model report: {args.version}",
        "",
        f"- Data source: `{source}`" + (" — **SYNTHETIC DATA. These numbers validate the pipeline, not real-world performance.**" if synthetic else ""),
        f"- Rows: train {tr}, validation {va - tr}, test {n - va}; positive rate {y.mean():.3f}",
        f"- Model: L2 logistic regression (IRLS), lambda={lam} selected on validation log loss",
        "",
        "## Held-out test set",
        "",
        "| Metric | Rule baseline (deterministic-v2) | Model |",
        "| --- | ---: | ---: |",
    ]
    for key in test_model:
        lines.append(f"| {key} | {test_rules[key]} | {test_model[key]} |")
    lines += [
        "",
        "Rule-baseline probabilities are `score / 100`; its Brier/ECE/log loss reflect that it was never calibrated.",
        "ROC-AUC and PR-AUC are rank-based and compare the two fairly.",
        "",
        "## Coefficients (standardized features)",
        "",
        "| Feature | Coefficient |",
        "| --- | ---: |",
    ]
    for name, coef in sorted(zip(FEATURE_NAMES, w[1:]), key=lambda item: -abs(item[1])):
        lines.append(f"| {name} | {coef:+.3f} |")
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text("\n".join(lines) + "\n")
    print(json.dumps(artifact["testMetrics"], indent=2))


if __name__ == "__main__":
    main()
