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
from datetime import datetime, timezone
from pathlib import Path

from app.llm_client import LlmConfig
from app.prompts import PROMPT_VERSION
from app.schemas import IncidentAnalysisRequest
from app.services.incident_analyzer import analyze_incident
from evals.scoring import aggregate, score_case

GOLDEN = Path(__file__).parent / "golden" / "incidents.jsonl"
REPORTS = Path(__file__).parent / "reports"


def load_cases(limit: int | None = None) -> list[dict]:
    cases = [json.loads(line) for line in GOLDEN.read_text().splitlines() if line.strip()]
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
        f"- Golden set: {meta['cases']} cases (`evals/golden/incidents.jsonl`, synthetic, hand-authored signatures)",
        f"- Prompt version: `{PROMPT_VERSION}`",
        f"- Models: {', '.join(f'{r['mode']}={r['model'] or 'n/a'}' for r in results)}",
        "",
        header,
        "| --- |" + " ---: |" * len(results),
    ]
    for key in keys:
        if all(key not in r["summary"] for r in results):
            continue
        lines.append(f"| {key} | " + " | ".join(str(r["summary"].get(key, "")) for r in results) + " |")
    lines += ["", "## Accuracy by slice", "", header, "| --- |" + " ---: |" * len(results)]
    slices = sorted({s for r in results for s in r["summary"]["sliceAccuracy"]})
    for name in slices:
        lines.append(f"| {name} | " + " | ".join(str(r["summary"]["sliceAccuracy"].get(name, "")) for r in results) + " |")
    lines += ["", "## Misclassified cases", ""]
    for r in results:
        misses = [f"`{row['id']}` ({row['expected']} -> {row['predicted']})" for row in r["rows"] if not row["categoryCorrect"]]
        lines.append(f"- **{r['mode']}**: " + (", ".join(misses) if misses else "none"))
    return "\n".join(lines) + "\n"


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--mode", action="append", choices=["heuristic", "single_shot", "rag", "agent"])
    parser.add_argument("--limit", type=int)
    parser.add_argument("--judge", action="store_true")
    parser.add_argument("--concurrency", type=int, default=4)
    args = parser.parse_args()
    modes = args.mode or ["heuristic"]
    cases = load_cases(args.limit)

    results = [asyncio.run(run_mode(mode, cases, args.judge, args.concurrency)) for mode in modes]
    ran_at = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H-%M-%SZ")
    REPORTS.mkdir(parents=True, exist_ok=True)
    (REPORTS / f"{ran_at}.json").write_text(json.dumps({"promptVersion": PROMPT_VERSION, "results": results}, indent=2))
    markdown = to_markdown(results, {"ranAt": ran_at, "cases": len(cases)})
    (REPORTS / "latest.md").write_text(markdown)
    print(markdown)


if __name__ == "__main__":
    main()
