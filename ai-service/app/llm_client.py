"""Thin OpenRouter (OpenAI-compatible) chat client that returns content, tool calls and usage."""

import os
from dataclasses import dataclass, field
from typing import Any

import httpx

DEFAULT_MODEL = "nvidia/nemotron-3-ultra-550b-a55b:free"
DEFAULT_BASE_URL = "https://openrouter.ai/api/v1"


class LlmError(RuntimeError):
    def __init__(self, reason: str, message: str = "") -> None:
        super().__init__(message or reason)
        self.reason = reason


@dataclass
class ChatResult:
    content: str | None
    tool_calls: list[dict[str, Any]] = field(default_factory=list)
    prompt_tokens: int = 0
    completion_tokens: int = 0
    raw_message: dict[str, Any] = field(default_factory=dict)


@dataclass
class LlmConfig:
    api_key: str
    model: str
    base_url: str
    timeout_seconds: float
    price_per_1k_prompt: float
    price_per_1k_completion: float
    structured_output: str = "json_schema"

    @classmethod
    def from_env(cls) -> "LlmConfig | None":
        api_key = os.getenv("OPENROUTER_API_KEY")
        if not api_key:
            return None
        return cls(
            api_key=api_key,
            model=os.getenv("OPENROUTER_MODEL", DEFAULT_MODEL),
            base_url=os.getenv("OPENROUTER_BASE_URL", DEFAULT_BASE_URL).rstrip("/"),
            timeout_seconds=float(os.getenv("OPENROUTER_TIMEOUT_SECONDS", "30")),
            price_per_1k_prompt=float(os.getenv("LLM_PRICE_PER_1K_PROMPT_USD", "0")),
            price_per_1k_completion=float(os.getenv("LLM_PRICE_PER_1K_COMPLETION_USD", "0")),
            # json_schema (constrained decoding) or json_object for providers without schema support.
            structured_output=os.getenv("LLM_STRUCTURED_OUTPUT", "json_schema"),
        )

    def cost(self, prompt_tokens: int, completion_tokens: int) -> float:
        return round(
            prompt_tokens / 1000 * self.price_per_1k_prompt
            + completion_tokens / 1000 * self.price_per_1k_completion,
            6,
        )


async def chat(
    config: LlmConfig,
    messages: list[dict[str, Any]],
    tools: list[dict[str, Any]] | None = None,
    json_mode: bool = True,
    temperature: float = 0.0,
    json_schema: dict[str, Any] | None = None,
    tool_choice: str = "auto",
) -> ChatResult:
    payload: dict[str, Any] = {
        "model": config.model,
        "messages": messages,
        "temperature": temperature,
    }
    if tools:
        payload["tools"] = tools
        payload["tool_choice"] = tool_choice
    elif json_schema is not None and config.structured_output == "json_schema":
        # Constrained decoding: the server can only emit tokens that keep the output valid
        # against the schema. Supported by vLLM and by some hosted providers.
        payload["response_format"] = {
            "type": "json_schema",
            "json_schema": {"name": "incident_analysis", "schema": json_schema, "strict": True},
        }
    elif json_mode:
        payload["response_format"] = {"type": "json_object"}

    headers = {
        "Authorization": f"Bearer {config.api_key}",
        "Content-Type": "application/json",
        "X-Title": "DeployGuard AI",
    }

    try:
        async with httpx.AsyncClient(timeout=config.timeout_seconds) as client:
            response = await client.post(f"{config.base_url}/chat/completions", headers=headers, json=payload)
    except httpx.TimeoutException as ex:
        raise LlmError("timeout", str(ex)) from ex
    except httpx.HTTPError as ex:
        raise LlmError("network_error", str(ex)) from ex

    if response.status_code == 429:
        raise LlmError("rate_limited", "Provider rate limit.")
    if response.status_code >= 400:
        raise LlmError("provider_error", f"Provider returned HTTP {response.status_code}.")

    try:
        data = response.json()
        message = data["choices"][0]["message"]
    except (ValueError, KeyError, IndexError, TypeError) as ex:
        raise LlmError("malformed_provider_response", str(ex)) from ex

    usage = data.get("usage") or {}
    return ChatResult(
        content=message.get("content"),
        tool_calls=message.get("tool_calls") or [],
        prompt_tokens=int(usage.get("prompt_tokens") or 0),
        completion_tokens=int(usage.get("completion_tokens") or 0),
        raw_message=message,
    )
