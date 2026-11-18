"""CI regression gate: the offline heuristic analyzer must keep meeting its thresholds
on the golden set, and safety metrics (injection, leaks, hallucinated evidence) must stay at zero."""

import asyncio
import json
from pathlib import Path

from evals.run import load_cases, run_mode

THRESHOLDS = json.loads((Path(__file__).resolve().parents[1] / "evals" / "thresholds.json").read_text())["heuristic"]


def test_heuristic_meets_eval_thresholds() -> None:
    summary = asyncio.run(run_mode("heuristic", load_cases()))["summary"]
    for key, bound in THRESHOLDS.items():
        if key.endswith("_max"):
            assert summary[key[:-4]] <= bound, (key, summary[key[:-4]])
        else:
            assert summary[key] >= bound, (key, summary[key])


def test_golden_set_has_all_slices() -> None:
    slices = {case["slice"] for case in load_cases()}
    assert {"core", "hard", "ambiguous", "injection", "secrets"} <= slices
