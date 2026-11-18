"""Dependency-free metrics registry with Prometheus text exposition, plus structured
analysis logs. Labels are low-cardinality by construction (no IDs, no free text)."""

import json
import logging
import threading
from collections import defaultdict

logger = logging.getLogger("deployguard.analysis")

LATENCY_BUCKETS = (0.1, 0.25, 0.5, 1, 2, 5, 10, 20, 30, 60)


class _Registry:
    def __init__(self) -> None:
        self._lock = threading.Lock()
        self._counters: dict[str, dict[tuple, float]] = defaultdict(lambda: defaultdict(float))
        self._hist: dict[str, dict[tuple, list[float]]] = defaultdict(dict)
        self._help: dict[str, tuple[str, str]] = {}

    def inc(self, name: str, help_text: str, value: float = 1.0, **labels: str) -> None:
        key = tuple(sorted(labels.items()))
        with self._lock:
            self._help.setdefault(name, ("counter", help_text))
            self._counters[name][key] += value

    def observe(self, name: str, help_text: str, value: float, **labels: str) -> None:
        key = tuple(sorted(labels.items()))
        with self._lock:
            self._help.setdefault(name, ("histogram", help_text))
            # bucket counts..., +Inf count, sum
            state = self._hist[name].setdefault(key, [0.0] * (len(LATENCY_BUCKETS) + 2))
            for i, bound in enumerate(LATENCY_BUCKETS):
                if value <= bound:
                    state[i] += 1
            state[len(LATENCY_BUCKETS)] += 1
            state[len(LATENCY_BUCKETS) + 1] += value

    def reset(self) -> None:
        with self._lock:
            self._counters.clear()
            self._hist.clear()
            self._help.clear()

    def value(self, name: str, **labels: str) -> float:
        return self._counters.get(name, {}).get(tuple(sorted(labels.items())), 0.0)

    def render(self) -> str:
        out: list[str] = []
        with self._lock:
            for name, (kind, help_text) in sorted(self._help.items()):
                out.append(f"# HELP {name} {help_text}")
                out.append(f"# TYPE {name} {kind}")
                if kind == "counter":
                    for key, val in sorted(self._counters[name].items()):
                        out.append(f"{name}{_labels(key)} {val:g}")
                else:
                    for key, state in sorted(self._hist[name].items()):
                        for i, bound in enumerate(LATENCY_BUCKETS):
                            out.append(f"{name}_bucket{_labels(key + (('le', f'{bound:g}'),))} {state[i]:g}")
                        out.append(f"{name}_bucket{_labels(key + (('le', '+Inf'),))} {state[len(LATENCY_BUCKETS)]:g}")
                        out.append(f"{name}_count{_labels(key)} {state[len(LATENCY_BUCKETS)]:g}")
                        out.append(f"{name}_sum{_labels(key)} {state[len(LATENCY_BUCKETS) + 1]:g}")
        return "\n".join(out) + "\n"


def _labels(key: tuple) -> str:
    if not key:
        return ""
    return "{" + ",".join(f'{k}="{v}"' for k, v in key) + "}"


metrics = _Registry()


def record_analysis(meta: dict) -> None:
    """Emit metrics and one structured log line for a completed analysis."""
    source = meta["source"]
    mode = meta["mode"]
    reason = meta.get("fallbackReason") or "none"
    metrics.inc("deployguard_analysis_total", "Incident analyses by source, mode and fallback reason.",
                source=source, mode=mode, reason=reason)
    metrics.observe("deployguard_analysis_latency_seconds", "End-to-end analysis latency.",
                    meta.get("latencyMs", 0) / 1000.0, source=source, mode=mode)
    if meta.get("promptTokens"):
        metrics.inc("deployguard_llm_tokens_total", "LLM tokens consumed.", meta["promptTokens"], type="prompt")
    if meta.get("completionTokens"):
        metrics.inc("deployguard_llm_tokens_total", "LLM tokens consumed.", meta["completionTokens"], type="completion")
    if meta.get("estimatedCostUsd"):
        metrics.inc("deployguard_llm_cost_usd_total", "Estimated LLM spend in USD.", meta["estimatedCostUsd"])
    if meta.get("toolCalls"):
        metrics.inc("deployguard_agent_tool_calls_total", "Agent tool invocations.", meta["toolCalls"])
    if meta.get("droppedEvidence"):
        metrics.inc("deployguard_ungrounded_evidence_total",
                    "Evidence items dropped because they were not grounded in the input.", meta["droppedEvidence"])
    if meta.get("redactions"):
        metrics.inc("deployguard_redactions_total", "Sensitive values redacted before model calls.", meta["redactions"])

    logger.info(json.dumps({"event": "incident_analysis", **{k: v for k, v in meta.items() if k != "retrievedDocuments"},
                            "retrievedDocuments": meta.get("retrievedDocuments", [])}))
