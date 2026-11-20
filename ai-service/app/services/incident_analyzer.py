"""Incident analysis orchestrator.

Pipeline: redact -> bound context -> (retrieve) -> model or agent -> strict schema
validation -> evidence grounding -> provenance metadata -> telemetry.

Any model failure falls back to the deterministic heuristic analyzer. The response
always says which path produced it (``meta.source``) and why (``meta.fallbackReason``),
so fallback output can never be mistaken for model output.
"""

import json
import os
import time
from typing import Any

from pydantic import ValidationError

from app import heuristic
from app.agent import run_agent
from app.context_builder import IncidentContext, build_context
from app.grounding import resolve_evidence
from app.llm_client import LlmConfig, LlmError, chat
from app.prompts import PROMPT_VERSION, SYSTEM_PROMPT, build_user_prompt
from app.retrieval import default_index
from app.schemas import (
    AnalysisMeta,
    IncidentAnalysisRequest,
    IncidentAnalysisResponse,
    ModelIncidentOutput,
)
from app.telemetry import record_analysis

DEFAULT_MODE = "rag"
VALID_MODES = {"heuristic", "single_shot", "rag", "agent"}
EXPECTED_RESPONSE_KEYS = set(ModelIncidentOutput.model_fields.keys())
OUTPUT_SCHEMA = ModelIncidentOutput.model_json_schema()
AGENT_MAX_LOG_LINES = 500
AGENT_CHAR_BUDGET = 200_000


def resolve_mode(mode: str | None) -> str:
    chosen = (mode or os.getenv("ANALYSIS_MODE") or DEFAULT_MODE).strip().lower()
    return chosen if chosen in VALID_MODES else DEFAULT_MODE


async def analyze_incident(
    request: IncidentAnalysisRequest,
    mode: str | None = None,
    config: LlmConfig | None = None,
) -> IncidentAnalysisResponse:
    started = time.perf_counter()
    mode = resolve_mode(mode)
    config = config if config is not None else LlmConfig.from_env()

    if mode == "agent":
        context = build_context(request, max_log_lines=AGENT_MAX_LOG_LINES, char_budget=AGENT_CHAR_BUDGET)
    else:
        context = build_context(request)

    meta: dict[str, Any] = {
        "mode": mode,
        "promptVersion": PROMPT_VERSION,
        "redactions": context.redactions,
    }

    if mode == "heuristic":
        return _finish_heuristic(context, meta, "heuristic_mode", started)
    if config is None:
        return _finish_heuristic(context, meta, "no_api_key", started)

    try:
        if mode == "agent":
            run = await run_agent(config, context, json_schema=OUTPUT_SCHEMA, is_valid_answer=_is_valid)
            raw = run.final.content or ""
            meta.update(
                toolCalls=run.tool_calls,
                promptTokens=run.prompt_tokens,
                completionTokens=run.completion_tokens,
                retrievedDocuments=run.retrieved,
            )
        else:
            docs = _retrieve(context) if mode == "rag" else []
            messages = [
                {"role": "system", "content": SYSTEM_PROMPT},
                {"role": "user", "content": build_user_prompt(context, docs)},
            ]
            result = await chat(config, messages, json_schema=OUTPUT_SCHEMA)
            raw = result.content or ""
            meta.update(
                promptTokens=result.prompt_tokens,
                completionTokens=result.completion_tokens,
                retrievedDocuments=[d["id"] for d in docs],
            )
        output = parse_model_response(raw)
    except LlmError as ex:
        return _finish_heuristic(context, meta, ex.reason, started)
    except json.JSONDecodeError:
        return _finish_heuristic(context, meta, "invalid_json", started)
    except (ValidationError, ValueError, TypeError):
        return _finish_heuristic(context, meta, "schema_mismatch", started)

    grounded, dropped = resolve_evidence(output.evidence, context.references(), context.grounding_corpus())
    confidence = output.confidence
    if dropped and not grounded and output.rootCauseCategory not in {"NO_INCIDENT", "INSUFFICIENT_EVIDENCE"}:
        confidence = "LOW"

    meta.update(
        source="model",
        model=config.model,
        droppedEvidence=dropped,
        estimatedCostUsd=config.cost(meta.get("promptTokens", 0), meta.get("completionTokens", 0)),
    )
    return _finish(output.model_copy(update={"evidence": grounded, "confidence": confidence}), meta, started)


def _retrieve(context: IncidentContext) -> list[dict]:
    query_lines = [line.message for line in context.logs if line.level in {"FATAL", "CRITICAL", "ERROR", "WARN"}][:15]
    if not query_lines:
        query_lines = [line.message for line in context.logs[:5]]
    if context.failed_ci_runs():
        query_lines.append("ci tests failed")
    query = " ".join(query_lines)
    return default_index().search(query, k=3) if query.strip() else []


def _finish_heuristic(context: IncidentContext, meta: dict[str, Any], reason: str, started: float) -> IncidentAnalysisResponse:
    meta.update(source="heuristic", fallbackReason=reason, model=None)
    return _finish(heuristic.analyze(context), meta, started)


def _finish(output: ModelIncidentOutput, meta: dict[str, Any], started: float) -> IncidentAnalysisResponse:
    meta["latencyMs"] = int((time.perf_counter() - started) * 1000)
    analysis_meta = AnalysisMeta(**meta)
    record_analysis(analysis_meta.model_dump())
    return IncidentAnalysisResponse(**output.model_dump(), meta=analysis_meta)


def _is_valid(raw_response: str) -> bool:
    try:
        parse_model_response(raw_response)
    except (json.JSONDecodeError, ValidationError, ValueError, TypeError):
        return False
    return True


def parse_model_response(raw_response: str) -> ModelIncidentOutput:
    parsed = json.loads(extract_json(raw_response))
    validate_response_shape(parsed)
    return ModelIncidentOutput.model_validate(parsed)


def validate_response_shape(parsed: Any) -> None:
    if not isinstance(parsed, dict):
        raise TypeError("Model response must be a JSON object.")
    keys = set(parsed.keys())
    if keys != EXPECTED_RESPONSE_KEYS:
        raise ValueError(
            f"Model response keys mismatch. Missing: {EXPECTED_RESPONSE_KEYS - keys}. Extra: {keys - EXPECTED_RESPONSE_KEYS}."
        )


def extract_json(raw_response: str) -> str:
    text = raw_response.strip()
    if text.startswith("```"):
        lines = text.splitlines()[1:]
        if lines and lines[-1].startswith("```"):
            lines = lines[:-1]
        text = "\n".join(lines).strip()
    if text.startswith("{") and text.endswith("}"):
        return text
    start, end = text.find("{"), text.rfind("}")
    if start == -1 or end <= start:
        raise json.JSONDecodeError("Model response did not contain a JSON object.", text, 0)
    return text[start : end + 1]
