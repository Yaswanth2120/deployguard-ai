"""Feature extraction shared by offline training (ml/train.py) and online scoring.

One function, one feature order: training/serving skew is impossible by construction.
"""

import math

from app.schemas import RiskScoreRequest
from app.services.risk_rules import ERROR_LOG_LEVELS, PRODUCTION_ENVIRONMENTS

FEATURE_NAMES: tuple[str, ...] = (
    "log_lines_changed",
    "log_files_changed",
    "touches_migrations",
    "touches_config",
    "test_ratio",
    "hotfix_branch",
    "targets_production",
    "failed_ci",
    "log_failed_tests",
    "log_error_logs",
    "log_ci_duration_min",
    "off_hours",
    "weekend",
    "log_author_experience",
)


def extract_features(request: RiskScoreRequest) -> list[float]:
    pr = request.pullRequest
    lines = pr.additions + pr.deletions
    error_logs = sum(1 for log in request.logs if log.severity.upper() in ERROR_LOG_LEVELS)
    failed_tests = sum(run.failedTests for run in request.ciRuns)
    duration_min = max((run.durationSeconds for run in request.ciRuns), default=0) / 60
    hour = pr.mergedAtHourUtc
    off_hours = 1.0 if hour is not None and (hour < 7 or hour >= 20) else 0.0
    return [
        math.log1p(lines),
        math.log1p(pr.changedFiles),
        float(pr.touchesMigrations),
        float(pr.touchesConfig),
        min(1.0, pr.testFilesChanged / pr.changedFiles) if pr.changedFiles else 0.0,
        float("hotfix" in pr.sourceBranch.lower()),
        float(bool(pr.targetEnvironment) and pr.targetEnvironment.lower() in PRODUCTION_ENVIRONMENTS),
        float(any(run.status.upper() == "FAILED" for run in request.ciRuns)),
        math.log1p(failed_tests),
        math.log1p(error_logs),
        math.log1p(duration_min),
        off_hours,
        float(pr.mergedOnWeekend),
        math.log1p(pr.authorPriorMergedPrs),
    ]
