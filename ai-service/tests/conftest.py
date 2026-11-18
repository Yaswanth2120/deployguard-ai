import pytest

from app.telemetry import metrics


@pytest.fixture(autouse=True)
def _isolate(monkeypatch):
    monkeypatch.delenv("OPENROUTER_API_KEY", raising=False)
    monkeypatch.delenv("ANALYSIS_MODE", raising=False)
    metrics.reset()
    yield
