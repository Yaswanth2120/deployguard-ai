"""Build a REAL labeled dataset from a public GitHub repository.

Label definition (documented proxy for "this change caused an incident"):
    label = 1 if the merged PR was reverted within --revert-window-days,
    detected by a later merged PR titled 'Revert "<original title>"' or whose body
    contains 'Reverts <owner>/<repo>#<number>' (GitHub's revert-button format).

Features are taken from the PR (size, files, migrations/config/tests touched,
author history, merge time) and its head-commit check runs. Output rows have the
same shape as /v1/risk-scores requests, so ml/train.py consumes them unchanged.

Usage (needs a token with public read access; never commit it):
    GITHUB_TOKEN=... python -m ml.mine_github --repo owner/name --max-prs 2000 --out ml/data/owner_name.jsonl

Known limitations: reverts are a lower bound on bad changes (hotfix-forward fixes
are missed); check runs may be pruned on old commits; API rate limits apply.
"""

import argparse
import json
import os
import re
import sys
import time
from datetime import datetime, timedelta
from pathlib import Path

import httpx

API = "https://api.github.com"
MIGRATION_PATTERN = re.compile(r"(^|/)(migrations?|db/migrate|alembic|flyway)(/|$)|\.sql$", re.IGNORECASE)
CONFIG_PATTERN = re.compile(r"\.(ya?ml|toml|ini|properties|conf|env\.example)$|(^|/)(config|deploy|helm|k8s|terraform)(/|$)|Dockerfile", re.IGNORECASE)
TEST_PATTERN = re.compile(r"(^|/)(tests?|__tests__|spec)(/|$)|(_test|\.test|\.spec|Test)\.[a-z]+$", re.IGNORECASE)
REVERT_TITLE = re.compile(r'^Revert "(.+)"$')
REVERTS_BODY = re.compile(r"Reverts [\w.-]+/[\w.-]+#(\d+)")


def classify_files(filenames: list[str]) -> dict:
    return {
        "touchesMigrations": any(MIGRATION_PATTERN.search(f) for f in filenames),
        "touchesConfig": any(CONFIG_PATTERN.search(f) for f in filenames),
        "testFilesChanged": sum(1 for f in filenames if TEST_PATTERN.search(f)),
    }


def find_reverted(prs: list[dict], window_days: int) -> set[int]:
    """Return numbers of merged PRs reverted by a later merged PR within the window."""
    by_title = {pr["title"]: pr for pr in prs}
    by_number = {pr["number"]: pr for pr in prs}
    reverted: set[int] = set()
    for pr in prs:
        target = None
        match = REVERTS_BODY.search(pr.get("body") or "")
        if match:
            target = by_number.get(int(match.group(1)))
        else:
            title_match = REVERT_TITLE.match(pr["title"])
            if title_match:
                target = by_title.get(title_match.group(1))
        if not target or not target.get("merged_at") or not pr.get("merged_at"):
            continue
        delta = _ts(pr["merged_at"]) - _ts(target["merged_at"])
        if timedelta(0) <= delta <= timedelta(days=window_days):
            reverted.add(target["number"])
    return reverted


def _ts(value: str) -> datetime:
    return datetime.fromisoformat(value)


class GitHub:
    def __init__(self, token: str) -> None:
        self.client = httpx.Client(
            base_url=API,
            timeout=30,
            headers={"Authorization": f"Bearer {token}", "Accept": "application/vnd.github+json",
                     "User-Agent": "deployguard-dataset-miner"},
        )

    def get(self, path: str, **params) -> httpx.Response:
        for _ in range(5):
            response = self.client.get(path, params=params)
            if response.status_code == 403 and response.headers.get("x-ratelimit-remaining") == "0":
                reset = int(response.headers.get("x-ratelimit-reset", time.time() + 60))
                time.sleep(max(1, reset - time.time()))
                continue
            response.raise_for_status()
            return response
        raise RuntimeError(f"rate limited on {path}")

    def paginate(self, path: str, limit: int, **params) -> list[dict]:
        items: list[dict] = []
        page = 1
        while len(items) < limit:
            batch = self.get(path, per_page=100, page=page, **params).json()
            if not batch:
                break
            items.extend(batch)
            page += 1
        return items[:limit]


def build_rows(gh: GitHub, repo: str, max_prs: int, window_days: int) -> list[dict]:
    # Most recent PRs first (CI data on old commits is often pruned), then process in merge order
    # so author history only counts earlier PRs and the ordered train/test split never sees the future.
    prs = [pr for pr in gh.paginate(f"/repos/{repo}/pulls", max_prs, state="closed", sort="created", direction="desc")
           if pr.get("merged_at")]
    prs.sort(key=lambda pr: pr["merged_at"])
    reverted = find_reverted(prs, window_days)
    author_counts: dict[str, int] = {}
    rows = []
    for pr in prs:
        if REVERT_TITLE.match(pr["title"]):
            continue  # revert PRs themselves are not training examples
        detail = gh.get(f"/repos/{repo}/pulls/{pr['number']}").json()
        files = [f["filename"] for f in gh.paginate(f"/repos/{repo}/pulls/{pr['number']}/files", 300)]
        checks = gh.get(f"/repos/{repo}/commits/{pr['head']['sha']}/check-runs", per_page=100).json().get("check_runs", [])
        author = (pr.get("user") or {}).get("login", "")
        merged = _ts(pr["merged_at"])
        ci_runs = [{
            "provider": "github-checks",
            "status": "FAILED" if c.get("conclusion") in {"failure", "timed_out"} else "SUCCESS",
            "failedTests": 0,
            "durationSeconds": max(0, int((_ts(c["completed_at"]) - _ts(c["started_at"])).total_seconds()))
            if c.get("completed_at") and c.get("started_at") else 0,
        } for c in checks]
        rows.append({
            "request": {
                "pullRequest": {
                    "repositoryId": repo, "number": pr["number"], "commitSha": pr["merge_commit_sha"] or pr["head"]["sha"],
                    "sourceBranch": pr["head"]["ref"], "targetBranch": pr["base"]["ref"], "title": "",
                    "additions": detail.get("additions", 0), "deletions": detail.get("deletions", 0),
                    "changedFiles": detail.get("changed_files", len(files)), **classify_files(files),
                    "authorPriorMergedPrs": author_counts.get(author, 0),
                    "targetEnvironment": "production" if pr["base"]["ref"] in {"main", "master"} else None,
                    "mergedAtHourUtc": merged.hour, "mergedOnWeekend": merged.weekday() >= 5,
                },
                "ciRuns": ci_runs,
                "logs": [],
            },
            "label": int(pr["number"] in reverted),
            "source": f"github:{repo}",
        })
        author_counts[author] = author_counts.get(author, 0) + 1
    return rows


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--repo", required=True)
    parser.add_argument("--max-prs", type=int, default=1000)
    parser.add_argument("--revert-window-days", type=int, default=14)
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()
    token = os.getenv("GITHUB_TOKEN")
    if not token:
        sys.exit("GITHUB_TOKEN is required.")
    rows = build_rows(GitHub(token), args.repo, args.max_prs, args.revert_window_days)
    args.out.parent.mkdir(parents=True, exist_ok=True)
    with args.out.open("w") as fh:
        for row in rows:
            fh.write(json.dumps(row) + "\n")
    positives = sum(r["label"] for r in rows)
    print(f"wrote {len(rows)} rows ({positives} reverted) to {args.out}")


if __name__ == "__main__":
    main()
