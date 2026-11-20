"""Builds a bounded, redacted, prioritized incident context for the model.

Production incidents can carry thousands of log lines. Dumping them all blows
the context window, raises cost and dilutes the signal. This module:

1. redacts secrets/PII (see redaction.py),
2. normalizes log level fields (``level`` or ``severity``),
3. deduplicates identical messages with an occurrence count,
4. ranks lines by severity, then frequency,
5. truncates each message and caps total characters to a token budget.
"""

import json
from dataclasses import dataclass, field
from typing import Any

from app.redaction import redact_value
from app.schemas import IncidentAnalysisRequest

SEVERITY_RANK = {"FATAL": 0, "CRITICAL": 0, "ERROR": 1, "WARN": 2, "WARNING": 2, "INFO": 3, "DEBUG": 4}
MAX_MESSAGE_CHARS = 400
DEFAULT_MAX_LOG_LINES = 60
DEFAULT_CONTEXT_CHAR_BUDGET = 24_000  # ~6k tokens at ~4 chars/token


@dataclass
class LogLine:
    id: str
    level: str
    message: str
    occurrences: int
    firstTimestamp: str | None


@dataclass
class IncidentContext:
    deployment: dict[str, Any]
    ciRuns: list[dict[str, Any]]
    logs: list[LogLine]
    riskScore: int
    riskLevel: str
    redactions: int = 0
    droppedLogLines: int = 0
    totalLogLines: int = 0
    redaction_kinds: dict[str, int] = field(default_factory=dict)

    def failed_ci_runs(self) -> list[dict[str, Any]]:
        return [
            run for run in self.ciRuns
            if str(run.get("status", "")).upper() in {"FAILED", "FAILURE", "ERROR", "CANCELLED", "TIMED_OUT"}
            or int(run.get("failedTests") or 0) > 0
        ]

    def grounding_corpus(self) -> list[str]:
        """Every redacted string the model was shown; evidence must trace back to one of these."""
        corpus = [f"{line.level} {line.message}" for line in self.logs]
        for run in self.ciRuns:
            corpus.append(" ".join(f"{k} {v}" for k, v in run.items() if v is not None))
        corpus.append(" ".join(f"{k} {v}" for k, v in self.deployment.items() if v is not None))
        corpus.append(f"riskScore {self.riskScore} riskLevel {self.riskLevel}")
        return corpus

    def references(self) -> dict[str, str]:
        """Reference id -> canonical quoted text, for every log line and CI run shown to the model."""
        refs = {line.id: f"{line.level}: {line.message}" for line in self.logs}
        for run in self.ciRuns:
            text = (f"CI {run.get('provider', 'ci')} status {run.get('status')} "
                    f"with {run.get('failedTests', 0)} failed tests")
            if run.get("summary"):
                text += f": {run['summary']}"
            refs[str(run.get("ref"))] = text
        return refs

    def to_prompt_dict(self) -> dict[str, Any]:
        return {
            "deployment": self.deployment,
            "ciRuns": self.ciRuns,
            "riskScore": self.riskScore,
            "riskLevel": self.riskLevel,
            "logSummary": {
                "totalLines": self.totalLogLines,
                "shownDistinctLines": len(self.logs),
                "omittedDistinctLines": self.droppedLogLines,
            },
        }


def _level(entry: dict[str, Any]) -> str:
    return str(entry.get("level") or entry.get("severity") or "INFO").upper()


def build_context(
    request: IncidentAnalysisRequest,
    max_log_lines: int = DEFAULT_MAX_LOG_LINES,
    char_budget: int = DEFAULT_CONTEXT_CHAR_BUDGET,
) -> IncidentContext:
    redacted = redact_value(request.model_dump(mode="json"))
    data = redacted.value

    grouped: dict[tuple[str, str], LogLine] = {}
    order: list[tuple[str, str]] = []
    for entry in data.get("logs", []):
        if not isinstance(entry, dict):
            continue
        level = _level(entry)
        message = str(entry.get("message", "")).strip()[:MAX_MESSAGE_CHARS]
        key = (level, message)
        if key in grouped:
            grouped[key].occurrences += 1
            continue
        grouped[key] = LogLine(
            id="",
            level=level,
            message=message,
            occurrences=1,
            firstTimestamp=entry.get("timestamp"),
        )
        order.append(key)

    ranked = sorted(
        (grouped[key] for key in order),
        key=lambda line: (SEVERITY_RANK.get(line.level, 3), -line.occurrences),
    )

    kept: list[LogLine] = []
    used = len(json.dumps(data.get("deployment", {}))) + len(json.dumps(data.get("ciRuns", [])))
    for line in ranked:
        cost = len(line.message) + 40
        if len(kept) >= max_log_lines or used + cost > char_budget:
            break
        used += cost
        kept.append(line)
    for index, line in enumerate(kept, start=1):
        line.id = f"L{index}"

    ci_runs = [run for run in data.get("ciRuns", []) if isinstance(run, dict)]
    for index, run in enumerate(ci_runs, start=1):
        run.setdefault("ref", f"CI{index}")

    return IncidentContext(
        deployment=data.get("deployment", {}) or {},
        ciRuns=ci_runs,
        logs=kept,
        riskScore=int(data.get("riskScore", 0)),
        riskLevel=str(data.get("riskLevel", "LOW")),
        redactions=redacted.count,
        redaction_kinds=redacted.kinds,
        droppedLogLines=len(ranked) - len(kept),
        totalLogLines=len(data.get("logs", [])),
    )
