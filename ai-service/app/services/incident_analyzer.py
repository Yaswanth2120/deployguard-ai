import json
import os
from typing import Any

import httpx
from dotenv import load_dotenv
from pydantic import ValidationError

from app.schemas import IncidentAnalysisRequest, IncidentAnalysisResponse

load_dotenv()

DEFAULT_MODEL = "nvidia/nemotron-3-ultra-550b-a55b:free"
DEFAULT_BASE_URL = "https://openrouter.ai/api/v1"
OPENROUTER_TIMEOUT_SECONDS = 30.0
EXPECTED_RESPONSE_KEYS = {
    "summary",
    "likelyRootCause",
    "evidence",
    "recommendedActions",
    "severity",
    "confidence",
}


async def analyze_incident(request: IncidentAnalysisRequest) -> IncidentAnalysisResponse:
    api_key = os.getenv("OPENROUTER_API_KEY")

    if not api_key:
        return build_fallback_response(request)

    try:
        raw_response = await call_openrouter(request, api_key)
        return parse_model_response(raw_response)
    except (httpx.HTTPError, AttributeError, KeyError, TypeError, ValueError, ValidationError):
        return build_fallback_response(request)


async def call_openrouter(request: IncidentAnalysisRequest, api_key: str) -> str:
    base_url = os.getenv("OPENROUTER_BASE_URL", DEFAULT_BASE_URL).rstrip("/")
    model = os.getenv("OPENROUTER_MODEL", DEFAULT_MODEL)

    payload = {
        "model": model,
        "messages": [
            {
                "role": "system",
                "content": build_system_prompt(),
            },
            {
                "role": "user",
                "content": build_user_prompt(request),
            },
        ],
        "response_format": {"type": "json_object"},
    }

    headers = {
        "Authorization": f"Bearer {api_key}",
        "Content-Type": "application/json",
        "X-Title": "DeployGuard AI",
    }

    async with httpx.AsyncClient(timeout=OPENROUTER_TIMEOUT_SECONDS) as client:
        response = await client.post(f"{base_url}/chat/completions", headers=headers, json=payload)
        response.raise_for_status()
        data = response.json()

    return data["choices"][0]["message"]["content"]


def build_system_prompt() -> str:
    return (
        "You are a senior site reliability engineer analyzing deployment incidents. "
        "Use only the deployment, CI/CD, log, riskScore, and riskLevel data provided. "
        "Do not invent facts, services, timelines, metrics, users, or failures. "
        "If evidence is insufficient, say so clearly. "
        "Return JSON only with exactly these keys: summary, likelyRootCause, evidence, "
        "recommendedActions, severity, confidence. "
        "severity and confidence must each be one of LOW, MEDIUM, HIGH. "
        "evidence and recommendedActions must be arrays of strings."
    )


def build_user_prompt(request: IncidentAnalysisRequest) -> str:
    context = request.model_dump(mode="json")
    return (
        "Analyze this DeployGuard incident context and return JSON only.\n\n"
        f"{json.dumps(context, indent=2, sort_keys=True)}"
    )


def parse_model_response(raw_response: str) -> IncidentAnalysisResponse:
    parsed = json.loads(extract_json(raw_response))
    validate_response_shape(parsed)
    return IncidentAnalysisResponse.model_validate(parsed)


def validate_response_shape(parsed: Any) -> None:
    if not isinstance(parsed, dict):
        raise ValueError("Model response must be a JSON object.")

    response_keys = set(parsed.keys())
    if response_keys != EXPECTED_RESPONSE_KEYS:
        missing = EXPECTED_RESPONSE_KEYS - response_keys
        extra = response_keys - EXPECTED_RESPONSE_KEYS
        raise ValueError(f"Model response keys mismatch. Missing: {missing}. Extra: {extra}.")


def extract_json(raw_response: str) -> str:
    text = raw_response.strip()

    if text.startswith("```"):
        lines = text.splitlines()
        if lines and lines[0].startswith("```"):
            lines = lines[1:]
        if lines and lines[-1].startswith("```"):
            lines = lines[:-1]
        text = "\n".join(lines).strip()

    if text.startswith("{") and text.endswith("}"):
        return text

    start = text.find("{")
    end = text.rfind("}")
    if start == -1 or end == -1 or end <= start:
        raise ValueError("Model response did not contain a JSON object.")

    return text[start : end + 1]


def build_fallback_response(request: IncidentAnalysisRequest) -> IncidentAnalysisResponse:
    risk_level = request.riskLevel

    if risk_level == "HIGH":
        return IncidentAnalysisResponse(
            summary="High deployment risk detected. The deployment should be reviewed before further rollout.",
            likelyRootCause="Mock analysis indicates severe risk signals from CI/CD results, logs, or deployment context.",
            evidence=[],
            recommendedActions=[
                "Review failed CI runs and recent error logs.",
                "Pause or roll back the deployment if user impact is confirmed.",
            ],
            severity="HIGH",
            confidence="MEDIUM",
        )

    if risk_level == "MEDIUM":
        return IncidentAnalysisResponse(
            summary="Medium deployment risk detected. The deployment has warning signals that need investigation.",
            likelyRootCause="Mock analysis indicates moderate risk signals from CI/CD results, logs, or deployment context.",
            evidence=[],
            recommendedActions=[
                "Inspect related CI runs and deployment logs.",
                "Monitor service health before expanding the rollout.",
            ],
            severity="MEDIUM",
            confidence="MEDIUM",
        )

    return IncidentAnalysisResponse(
        summary="Low deployment risk detected. No severe incident indicators were found in the provided context.",
        likelyRootCause="Mock analysis indicates no strong failure pattern in the deployment context.",
        evidence=[],
        recommendedActions=[
            "Continue monitoring normal deployment metrics.",
        ],
        severity="LOW",
        confidence="LOW",
    )
