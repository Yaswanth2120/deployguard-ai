"""Combine result files from separate eval runs into one comparison report.

    PYTHONPATH=. python -m evals.merge evals/reports/A.json evals/reports/B.json --out evals/reports/combined.md

Each result is labelled "<mode>" or, when files use different models, "<mode>@<model>".
If two files contain the same label, the later file wins (use it to replace a run that
failed for infrastructure reasons).
"""

import argparse
import json
from pathlib import Path

from evals.run import to_markdown


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("files", nargs="+", type=Path)
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()

    loaded = [json.loads(path.read_text()) for path in args.files]
    models = {r["model"] for doc in loaded for r in doc["results"] if r["model"]}
    merged: dict[str, dict] = {}
    for doc in loaded:
        for result in doc["results"]:
            label = result["mode"]
            if len(models) > 1 and result["model"]:
                label = f"{result['mode']}@{result['model'].split('/')[-1]}"
            merged[label] = {**result, "mode": label}
    results = list(merged.values())
    cases = len(results[0]["rows"]) // max(1, results[0]["summary"].get("runs", 1))
    sources = ", ".join(f"`{path.name}`" for path in args.files)
    markdown = to_markdown(results, {"ranAt": f"merged from {sources}", "cases": cases})
    args.out.write_text(markdown)
    print(markdown)


if __name__ == "__main__":
    main()
