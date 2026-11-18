from app.context_builder import build_context
from app.schemas import IncidentAnalysisRequest


def _req(logs):
    return IncidentAnalysisRequest(logs=logs, riskLevel="HIGH", riskScore=80)


def test_dedups_and_ranks_by_severity_then_frequency() -> None:
    logs = (
        [{"level": "INFO", "message": "ok"}]
        + [{"level": "ERROR", "message": "timeout"}] * 3
        + [{"severity": "FATAL", "message": "oom"}]
        + [{"level": "ERROR", "message": "npe"}]
    )
    ctx = build_context(_req(logs))
    assert [(line.level, line.message, line.occurrences) for line in ctx.logs] == [
        ("FATAL", "oom", 1), ("ERROR", "timeout", 3), ("ERROR", "npe", 1), ("INFO", "ok", 1),
    ]
    assert [line.id for line in ctx.logs] == ["L1", "L2", "L3", "L4"]
    assert ctx.totalLogLines == 6


def test_caps_lines_and_reports_dropped() -> None:
    logs = [{"level": "ERROR", "message": f"error {i}"} for i in range(200)]
    ctx = build_context(_req(logs), max_log_lines=10)
    assert len(ctx.logs) == 10
    assert ctx.droppedLogLines == 190


def test_respects_char_budget_and_truncates_messages() -> None:
    logs = [{"level": "ERROR", "message": "x" * 5000 + str(i)} for i in range(50)]
    ctx = build_context(_req(logs), char_budget=3000)
    assert all(len(line.message) <= 400 for line in ctx.logs)
    assert len(ctx.logs) < 10


def test_context_is_redacted() -> None:
    ctx = build_context(_req([{"level": "ERROR", "message": "login failed password=hunter2"}]))
    assert "hunter2" not in ctx.logs[0].message
    assert ctx.redactions == 1
