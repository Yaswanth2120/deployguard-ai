"""Synthetic deployment-outcome generator.

THIS IS NOT REAL DATA. It exists so the training/evaluation pipeline is runnable
end to end without access to a company's incident history. Labels come from a
hidden generative process that includes effects the rule baseline ignores
(change size, migrations, test coverage, author experience, timing) and an
interaction term the linear model cannot represent exactly, plus label noise.
Metrics on this data demonstrate the pipeline, not real-world performance;
use ml/mine_github.py to build a real dataset.
"""

import json
import math
import random
from pathlib import Path

GENERATOR_VERSION = "synthetic-v1"


def _sample(rng: random.Random) -> tuple[dict, int]:
    changed_files = max(1, int(rng.lognormvariate(1.6, 0.9)))
    lines = max(1, int(changed_files * rng.lognormvariate(3.0, 0.8)))
    additions = int(lines * rng.uniform(0.4, 0.9))
    deletions = lines - additions
    migrations = rng.random() < 0.08
    config = rng.random() < 0.15
    tests_changed = sum(1 for _ in range(changed_files) if rng.random() < 0.3)
    hotfix = rng.random() < 0.07
    env = rng.choices(["production", "staging", "dev"], weights=[0.45, 0.4, 0.15])[0]
    hour = rng.randrange(24)
    weekend = rng.random() < 0.12
    author_prs = int(rng.expovariate(1 / 40))

    # CI outcome depends weakly on change size and tests.
    p_ci_fail = 0.06 + 0.04 * math.log1p(lines) / 5 - 0.03 * (tests_changed > 0)
    ci_failed = rng.random() < max(0.01, p_ci_fail)
    failed_tests = rng.randint(1, 8) if ci_failed and rng.random() < 0.8 else 0
    ci_duration = int(rng.uniform(120, 1500))

    test_ratio = tests_changed / changed_files
    prod = env == "production"
    off_hours = hour < 7 or hour >= 20

    # Pre-merge error logs (staging/canary) are partly caused by latent bugginess.
    bug_propensity = rng.gauss(0, 1)
    error_logs = int(max(0, rng.gauss(0.5 + 1.2 * max(0, bug_propensity), 1.0))) if rng.random() < 0.35 else 0

    logit = (
        -4.1
        + 0.42 * math.log1p(lines)
        + 1.0 * migrations
        + 0.6 * config
        - 1.3 * test_ratio
        + 0.5 * hotfix
        + 0.35 * prod
        + 1.5 * ci_failed
        + 0.3 * math.log1p(failed_tests)
        + 0.7 * math.log1p(error_logs)
        + 0.55 * off_hours
        + 0.3 * weekend
        - 0.3 * math.log1p(author_prs)
        + 0.9 * (migrations and prod)          # interaction: not linearly representable
        + 0.6 * bug_propensity                  # latent, unobserved by any feature
    )
    label = int(rng.random() < 1 / (1 + math.exp(-logit)))

    request = {
        "pullRequest": {
            "repositoryId": "synthetic/repo",
            "number": rng.randint(1, 99999),
            "commitSha": f"{rng.getrandbits(40):010x}",
            "sourceBranch": ("hotfix/" if hotfix else "feature/") + "change",
            "targetBranch": "main",
            "additions": additions,
            "deletions": deletions,
            "changedFiles": changed_files,
            "touchesMigrations": migrations,
            "touchesConfig": config,
            "testFilesChanged": tests_changed,
            "authorPriorMergedPrs": author_prs,
            "targetEnvironment": env,
            "mergedAtHourUtc": hour,
            "mergedOnWeekend": weekend,
        },
        "ciRuns": [{
            "provider": "github-actions",
            "status": "FAILED" if ci_failed else "SUCCESS",
            "failedTests": failed_tests,
            "durationSeconds": ci_duration,
        }],
        "logs": [
            {"severity": "ERROR", "message": "synthetic staging error", "timestamp": "2026-01-01T00:00:00Z"}
            for _ in range(error_logs)
        ],
    }
    return request, label


def generate(n: int, seed: int) -> list[dict]:
    rng = random.Random(seed)
    rows = []
    for _ in range(n):
        request, label = _sample(rng)
        rows.append({"request": request, "label": label, "source": GENERATOR_VERSION})
    return rows


def main() -> None:
    import argparse

    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--n", type=int, default=20000)
    parser.add_argument("--seed", type=int, default=7)
    parser.add_argument("--out", type=Path, default=Path("ml/data/synthetic.jsonl"))
    args = parser.parse_args()
    args.out.parent.mkdir(parents=True, exist_ok=True)
    with args.out.open("w") as fh:
        for row in generate(args.n, args.seed):
            fh.write(json.dumps(row) + "\n")
    print(f"wrote {args.n} rows to {args.out}")


if __name__ == "__main__":
    main()
