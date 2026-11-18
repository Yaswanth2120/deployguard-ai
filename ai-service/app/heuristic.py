"""Deterministic heuristic analyzer.

Serves two roles:
1. Fallback when the model is unavailable or returns unusable output. Unlike a
   canned message, it cites real log/CI lines as evidence.
2. The baseline every LLM mode is compared against in the evaluation harness.
"""

import re

from app.context_builder import IncidentContext
from app.schemas import ModelIncidentOutput

_LEXICON: dict[str, list[str]] = {
    "BAD_MIGRATION": [
        r"flyway", r"liquibase", r"alembic", r"migration", r"relation .* does not exist",
        r"column .* does not exist", r"lock timeout", r"deadlock detected", r"schema validation",
        r"duplicate column", r"alter table",
    ],
    "CONFIG_ERROR": [
        r"missing required (environment )?variable", r"could not resolve placeholder", r"invalid value for",
        r"config(uration)? (error|invalid|missing)", r"env(ironment)? var", r"feature flag",
        r"no such file.*\.(ya?ml|json|properties|conf)", r"unknown property",
    ],
    "DEPENDENCY_REGRESSION": [
        r"nosuchmethoderror", r"classnotfoundexception", r"noclassdeffounderror", r"modulenotfounderror",
        r"importerror", r"incompatible (version|types)", r"dependency", r"version mismatch", r"unsupported class file",
    ],
    "RESOURCE_EXHAUSTION": [
        r"outofmemoryerror", r"oomkilled", r"out of memory", r"gc overhead", r"heap space",
        r"no space left on device", r"pool (is )?exhausted", r"connection is not available",
        r"too many open files", r"thread pool", r"memory usage",
    ],
    "DOWNSTREAM_TIMEOUT": [
        r"timed? ?out", r"timeout", r"504", r"503", r"circuit breaker", r"connection refused",
        r"upstream", r"downstream", r"429", r"too many requests",
    ],
    "AUTH_FAILURE": [
        r"\b401\b", r"\b403\b", r"unauthori[sz]ed", r"forbidden", r"invalid_grant", r"token expired",
        r"certificate (has )?expired", r"pkix", r"ssl handshake", r"jwt", r"signature does not match",
        r"invalid credentials", r"authentication failed",
    ],
    "CODE_DEFECT": [
        r"nullpointerexception", r"typeerror", r"keyerror", r"indexerror", r"indexoutofbounds",
        r"illegalstateexception", r"illegalargumentexception", r"attributeerror", r"undefined is not",
        r"cannot read propert", r"unhandled exception", r"\b500\b", r"internal server error", r"stack ?trace",
    ],
    "INFRASTRUCTURE": [
        r"imagepullbackoff", r"crashloopbackoff", r"node .*notready", r"unknownhostexception", r"nxdomain",
        r"dns", r"network (is )?unreachable", r"no route to host", r"health ?check(s)? fail", r"evicted",
    ],
}
_COMPILED = {cat: [re.compile(p, re.IGNORECASE) for p in pats] for cat, pats in _LEXICON.items()}
_INJECTION = re.compile(
    r"ignore (all |any )?(previous|prior|above) instructions|disregard .*instructions|you are now|"
    r"system prompt|set (the )?severity to|respond with|output the following",
    re.IGNORECASE,
)
_LEVEL_WEIGHT = {"FATAL": 3.0, "CRITICAL": 3.0, "ERROR": 2.0, "WARN": 0.5, "WARNING": 0.5}

_ACTIONS = {
    "BAD_MIGRATION": ["Halt the rollout and inspect the migration history table.",
                      "Restore a backward-compatible schema or roll back the application release."],
    "CONFIG_ERROR": ["Diff configuration and secrets between the previous and current release.",
                     "Restore the missing or invalid setting and add startup validation for it."],
    "DEPENDENCY_REGRESSION": ["Review the dependency/lockfile diff in this release.",
                              "Pin the previous dependency version or roll back."],
    "RESOURCE_EXHAUSTION": ["Roll back and capture a heap dump or resource profile.",
                            "Compare memory/pool limits and look for unbounded caches or leaked connections."],
    "DOWNSTREAM_TIMEOUT": ["Check health of the affected downstream dependency.",
                           "Compare client timeout and retry settings with the previous release; add backoff with jitter."],
    "AUTH_FAILURE": ["Verify secret, token and certificate versions across the affected services.",
                     "Roll back the credential or auth configuration change."],
    "CODE_DEFECT": ["Identify first-seen exceptions in this release and correlate with changed files.",
                    "Roll back or disable the feature flag, then add a regression test."],
    "TEST_FAILURE": ["Block promotion until failing tests are fixed.",
                     "Inspect the failing tests and re-run the pipeline."],
    "INFRASTRUCTURE": ["Check cluster, registry and DNS health; this is likely not caused by the code change.",
                       "Escalate to the platform team."],
    "NO_INCIDENT": ["Continue monitoring normal deployment metrics."],
    "INSUFFICIENT_EVIDENCE": ["Collect logs and CI results for this deployment before drawing conclusions."],
}

_LEVELS = ["LOW", "MEDIUM", "HIGH"]


def _max_level(a: str, b: str) -> str:
    return a if _LEVELS.index(a) >= _LEVELS.index(b) else b


def analyze(context: IncidentContext) -> ModelIncidentOutput:
    scores: dict[str, float] = {cat: 0.0 for cat in _COMPILED}
    hits: dict[str, list[str]] = {cat: [] for cat in _COMPILED}
    suspicious: list[str] = []
    error_lines = 0
    fatal = False

    for line in context.logs:
        weight = _LEVEL_WEIGHT.get(line.level, 0.0)
        if line.level in {"ERROR", "FATAL", "CRITICAL"}:
            error_lines += line.occurrences
        if line.level in {"FATAL", "CRITICAL"}:
            fatal = True
        if _INJECTION.search(line.message):
            suspicious.append(f"[{line.id}] {line.level}: {line.message}")
            continue
        if weight == 0:
            continue
        for cat, patterns in _COMPILED.items():
            matched = sum(1 for p in patterns if p.search(line.message))
            if matched:
                scores[cat] += weight * (1 + 0.25 * (matched - 1)) * (1 + 0.1 * min(line.occurrences - 1, 10))
                hits[cat].append(f"[{line.id}] {line.level}: {line.message}")

    failed_ci = context.failed_ci_runs()
    ci_evidence = [
        f"[{run.get('ref')}] CI {run.get('provider', 'ci')} status {run.get('status')} "
        f"with {run.get('failedTests', 0)} failed tests"
        for run in failed_ci
    ]

    best_cat, best_score = max(scores.items(), key=lambda kv: kv[1])
    has_data = bool(context.logs or context.ciRuns)

    if best_score > 0:
        category = best_cat
        evidence = hits[best_cat][:4] + ci_evidence[:1]
    elif failed_ci:
        category = "TEST_FAILURE"
        evidence = ci_evidence[:3]
    elif has_data and error_lines == 0:
        category = "NO_INCIDENT"
        evidence = []
    else:
        category = "INSUFFICIENT_EVIDENCE"
        evidence = []
    evidence += suspicious[:2]

    if category == "NO_INCIDENT":
        severity = "LOW"
    else:
        severity = context.riskLevel if context.riskLevel in _LEVELS else "LOW"
        if fatal or error_lines >= 10 or category == "RESOURCE_EXHAUSTION":
            severity = _max_level(severity, "HIGH")
        elif error_lines > 0 or failed_ci:
            severity = _max_level(severity, "MEDIUM")

    runner_up = sorted(scores.values(), reverse=True)[1]
    if category in {"INSUFFICIENT_EVIDENCE"}:
        confidence = "LOW"
    elif category == "NO_INCIDENT" or best_score >= 4 and best_score >= 2 * runner_up:
        confidence = "MEDIUM"
    else:
        confidence = "LOW"

    readable = category.replace("_", " ").lower()
    if category == "NO_INCIDENT":
        summary = "No incident indicators: CI passed and no ERROR/FATAL logs were found for this deployment."
        root = "No failure pattern detected in the provided context."
    elif category == "INSUFFICIENT_EVIDENCE":
        summary = "Not enough deployment context to determine a root cause."
        root = "Insufficient evidence."
    else:
        summary = (
            f"Heuristic triage points to a {readable} issue: {error_lines} error/fatal log line(s) and "
            f"{len(failed_ci)} failing CI run(s) in this deployment."
        )
        root = f"Signals in logs/CI match the {readable} pattern (deterministic heuristic, not model output)."
    actions = list(_ACTIONS[category])
    if suspicious:
        actions.append("Investigate log lines containing instruction-like text; they may indicate log injection.")

    return ModelIncidentOutput(
        summary=summary,
        likelyRootCause=root,
        rootCauseCategory=category,
        evidence=evidence,
        recommendedActions=actions,
        severity=severity,
        confidence=confidence,
    )
