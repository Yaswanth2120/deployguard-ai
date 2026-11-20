"""Large-incident eval set (evals/golden/large_incidents.jsonl).

Tests the one situation where a tool-using agent should beat single-shot prompting:
incidents too big for the bounded context. Each case takes a hard case whose decisive
explanation is an INFO line (e.g. "pool size changed from 40 to 4 in this release"),
then adds 150 distinct, benign WARN lines. Context building ranks by severity, so the
decisive INFO line falls outside the 60 lines single-shot and RAG see, while the agent
can still reach it with search_logs. Without that line, the symptom alone points to the
wrong category.

    PYTHONPATH=. python -m evals.build_large
"""

import json
import random
from pathlib import Path

from evals.build_golden import _case
from evals.golden_extra import HARD

OUT = Path(__file__).parent / "golden" / "large_incidents.jsonl"
SEED = 20261121
NOISE_LINES = 150
CASE_IDS = [
    "h-mig-npe", "h-mig-enum", "h-cfg-pool", "h-cfg-region", "h-cfg-ratelimit", "h-dep-jwt",
    "h-dep-date", "h-dep-timeout", "h-down-preexisting", "h-auth-scope", "h-infra-az", "h-infra-registry",
]
NOISE_TEMPLATES = [
    "Deprecated header X-Client-Version={n} sent by mobile client",
    "Feature flag cache refresh took {n}ms (threshold 50ms)",
    "Client {n} used deprecated API version v1 on /api/catalog",
    "Static asset bundle-{n}.js served without cache headers",
    "Session {n} renewed close to expiry",
]


def build() -> list[dict]:
    rng = random.Random(SEED)
    by_id = {h[0]: h for h in HARD}
    cases = []
    for case_id in CASE_IDS:
        _, cat, acceptable, signature, cite, ci_failed, _ = by_id[case_id]
        case = _case(rng, f"large-{case_id}", cat, (signature, cite, ci_failed), "HIGH")
        noise = [
            {"level": "WARN", "message": rng.choice(NOISE_TEMPLATES).format(n=1000 + i), "timestamp": "2026-11-01T10:30:00Z"}
            for i in range(NOISE_LINES)
        ]
        case["request"]["logs"] = noise + case["request"]["logs"]
        case.update(slice="large", acceptableCategories=acceptable, acceptableSeverities=["MEDIUM", "HIGH"])
        cases.append(case)
    return cases


def main() -> None:
    cases = build()
    OUT.write_text("".join(json.dumps(c, sort_keys=True) + "\n" for c in cases))
    print(f"wrote {len(cases)} cases to {OUT}")


if __name__ == "__main__":
    main()
