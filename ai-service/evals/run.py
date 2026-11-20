"""Run the incident-analysis evaluation.

    python -m evals.run --mode heuristic                     # offline, no key needed
    OPENROUTER_API_KEY=... python -m evals.run --mode heuristic --mode single_shot --mode rag --mode agent
    OPENROUTER_API_KEY=... python -m evals.run --mode rag --judge

Writes evals/reports/<timestamp>.json (per-case rows + aggregates) and a markdown
comparison table to evals/reports/latest.md.
"""

import argparse
import asyncio
import json
import math
import statistics
from datetime import UTC, datetime
from pathlib import Path

from app.llm_client import LlmConfig
from app.prompts import PROMPT_VERSION
from app.schemas import IncidentAnalysisRequest
from app.services.incident_analyzer import analyze_incident
from evals.scoring import aggregate, score_case

GOLDEN = Path(__file__).parent / "golden" / "incidents.jsonl"
CI_KEYS = ("categoryAccuracy", "categoryMacroF1", "severityAccuracy", "hallucinatedEvidenceRate", "modelAnswerRate",
           "latencyMsP50", "latencyMsP95", "meanPromptTokens", "meanCompletionTokens", "meanToolCalls")


def wilson(p: float, n: int, z: float = 1.96) -> tuple[float, float]:
    """95% Wilson score interval for a proportion: uncertainty from the finite case set."""
    if n == 0:
        return (0.0, 0.0)
    denom = 1 + z * z / n
    centre = (p + z * z / (2 * n)) / denom
    half = z * math.sqrt(p * (1 - p) / n + z * z / (4 * n * n)) / denom
    return (round(centre - half, 3), round(centre + half, 3))


def combine_runs(runs: list[dict]) -> dict:
    """Mean over repeated runs plus run-to-run std; Wilson CIs on accuracy and hard-slice accuracy."""
    first = runs[0]
    summary = dict(first["summary"])
    spread: dict[str, float] = {}
    for key in CI_KEYS:
        values = [r["summary"][key] for r in runs if isinstance(r["summary"].get(key), (int, float))]
        if values:
            summary[key] = round(statistics.fmean(values), 4)
            spread[key] = round(statistics.pstdev(values), 4) if len(values) > 1 else 0.0
    slices = first["summary"]["sliceAccuracy"]
    summary["sliceAccuracy"] = {
        name: round(statistics.fmean(r["summary"]["sliceAccuracy"][name] for r in runs), 4) for name in slices
    }
    n_all = len(first["rows"])
    n_hard = sum(1 for row in first["rows"] if row["slice"] == "hard")
    summary["categoryAccuracyCI95"] = wilson(summary["categoryAccuracy"], n_all)
    if n_hard:
        summary["hardAccuracyCI95"] = wilson(summary["sliceAccuracy"]["hard"], n_hard)
    summary["runStd"] = spread
    summary["runs"] = len(runs)
    return {**first, "summary": summary, "rows": [row for r in runs for row in r["rows"]]}
REPORTS = Path(__file__).parent / "reports"


def load_cases(limit: int | None = None, path: Path = GOLDEN) -> list[dict]:
    cases = [json.loads(line) for line in path.read_text().splitlines() if line.strip()]
    return cases[:limit] if limit else cases


async def run_mode(mode: str, cases: list[dict], use_judge: bool = False, concurrency: int = 4) -> dict:
    config = LlmConfig.from_env()
    if mode != "heuristic" and config is None:
        raise SystemExit(f"mode '{mode}' needs OPENROUTER_API_KEY; only 'heuristic' runs offline.")
    semaphore = asyncio.Semaphore(concurrency)

    async def one(case: dict) -> dict:
        async with semaphore:
            response = await analyze_incident(IncidentAnalysisRequest.model_validate(case["request"]), mode=mode, config=config)
            row = score_case(case, response)
            if use_judge and config is not None:
                from evals.judge import judge
                row["judge"] = await judge(config, case, response.model_dump())
            return row

    rows = await asyncio.gather(*(one(c) for c in cases))
    summary = aggregate(list(rows))
    if use_judge:
        scores = [r["judge"]["score"] for r in rows if r.get("judge", {}).get("score") is not None]
        summary["judgeMeanScore"] = round(sum(scores) / len(scores), 3) if scores else None
    return {"mode": mode, "model": config.model if (config and mode != "heuristic") else None,
            "summary": summary, "rows": list(rows)}


def _cell(summary: dict, key: str) -> str:
    value = summary.get(key, "")
    std = summary.get("runStd", {}).get(key)
    if std and summary.get("runs", 1) > 1:
        return f"{value} ± {std}"
    return str(value)


def to_markdown(results: list[dict], meta: dict) -> str:
    keys = ["categoryAccuracy", "categoryMacroF1", "severityAccuracy", "evidenceCitationRate",
            "hallucinatedEvidenceRate", "injectionFollowRate", "secretLeakRate", "modelAnswerRate",
            "latencyMsP50", "latencyMsP95", "meanPromptTokens", "meanCompletionTokens", "totalCostUsd",
            "meanToolCalls", "judgeMeanScore"]
    header = "| Metric | " + " | ".join(r["mode"] for r in results) + " |"
    lines = [
        "# Incident analysis eval",
        "",
        f"- Run at: {meta['ranAt']}",
        f"- Eval set: {meta['cases']} cases (`{meta.get('golden', 'evals/golden/incidents.jsonl')}`, synthetic)",
        f"- Prompt version: `{PROMPT_VERSION}`",
        f"- Models: {', '.join(f'{r['mode']}={r['model'] or 'n/a'}' for r in results)}",
        "",
        header,
        "| --- |" + " ---: |" * len(results),
    ]
    for key in keys:
        if all(key not in r["summary"] for r in results):
            continue
        lines.append(f"| {key} | " + " | ".join(_cell(r["summary"], key) for r in results) + " |")
    for key in ("categoryAccuracyCI95", "hardAccuracyCI95", "runs"):
        if any(key in r["summary"] for r in results):
            lines.append(f"| {key} | " + " | ".join(str(r["summary"].get(key, "")) for r in results) + " |")
    lines += ["", "## Accuracy by slice", "", header, "| --- |" + " ---: |" * len(results)]
    slices = sorted({s for r in results for s in r["summary"]["sliceAccuracy"]})
    for name in slices:
        lines.append(f"| {name} | " + " | ".join(str(r["summary"]["sliceAccuracy"].get(name, "")) for r in results) + " |")
    lines += ["", "## Misclassified cases", "", "Count after the id = number of runs in which it was wrong.", ""]
    for r in results:
        misses: dict[str, int] = {}
        labels: dict[str, str] = {}
        for row in r["rows"]:
            if not row["categoryCorrect"]:
                misses[row["id"]] = misses.get(row["id"], 0) + 1
                labels[row["id"]] = f"{row['expected']} -> {row['predicted']}"
        text = ", ".join(f"`{cid}` ({labels[cid]}) x{n}" for cid, n in misses.items())
        lines.append(f"- **{r['mode']}**: " + (text or "none"))
    return "\n".join(lines) + "\n"


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--mode", action="append", choices=["heuristic", "single_shot", "rag", "agent"])
    parser.add_argument("--limit", type=int)
    parser.add_argument("--golden", type=Path, default=GOLDEN, help="Eval set to run (default: main golden set).")
    parser.add_argument("--judge", action="store_true")
    parser.add_argument("--concurrency", type=int, default=4)
    parser.add_argument("--runs", type=int, default=1, help="Repeat each mode N times and report mean, std and CIs.")
    args = parser.parse_args()
    modes = args.mode or ["heuristic"]
    cases = load_cases(args.limit, args.golden)

    results = []
    for mode in modes:
        runs = 1 if mode == "heuristic" else args.runs
        attempts = [asyncio.run(run_mode(mode, cases, args.judge, args.concurrency)) for _ in range(runs)]
        results.append(combine_runs(attempts))
    ran_at = datetime.now(UTC).strftime("%Y-%m-%dT%H-%M-%SZ")
    REPORTS.mkdir(parents=True, exist_ok=True)
    (REPORTS / f"{ran_at}.json").write_text(json.dumps({"promptVersion": PROMPT_VERSION, "results": results}, indent=2))
    markdown = to_markdown(results, {"ranAt": ran_at, "cases": len(cases), "golden": f"evals/golden/{args.golden.name}"})
    (REPORTS / "latest.md").write_text(markdown)
    print(markdown)


if __name__ == "__main__":
    main()
