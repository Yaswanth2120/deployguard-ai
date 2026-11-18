"""Tool-using incident triage agent.

The model gets a compact overview and investigates with tools instead of
receiving every log line up front. Tools run locally over the already-redacted
context, so tool output never reintroduces secrets.
"""

import json
from dataclasses import dataclass, field
from typing import Any

from app.context_builder import IncidentContext
from app.llm_client import ChatResult, LlmConfig, LlmError, chat
from app.prompts import AGENT_SYSTEM_PROMPT, build_agent_user_prompt
from app.retrieval import default_index, tokenize

MAX_STEPS = 6
MAX_TOOL_RESULT_CHARS = 4_000

TOOLS: list[dict[str, Any]] = [
    {
        "type": "function",
        "function": {
            "name": "search_logs",
            "description": "Search this deployment's log lines (redacted). Returns matching lines with reference ids.",
            "parameters": {
                "type": "object",
                "properties": {
                    "query": {"type": "string", "description": "Keywords, e.g. 'timeout payment'."},
                    "level": {"type": "string", "enum": ["FATAL", "ERROR", "WARN", "INFO", "DEBUG"]},
                    "limit": {"type": "integer", "minimum": 1, "maximum": 25},
                },
                "required": ["query"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "get_log_stats",
            "description": "Counts of log lines by level and the most frequent distinct messages.",
            "parameters": {"type": "object", "properties": {}},
        },
    },
    {
        "type": "function",
        "function": {
            "name": "get_ci_failures",
            "description": "Failed CI runs for the deployed commit, with failed test counts.",
            "parameters": {"type": "object", "properties": {}},
        },
    },
    {
        "type": "function",
        "function": {
            "name": "search_knowledge_base",
            "description": "Search past incidents and runbooks. Use for context and remediation, never as evidence.",
            "parameters": {
                "type": "object",
                "properties": {"query": {"type": "string"}},
                "required": ["query"],
            },
        },
    },
]


@dataclass
class AgentRun:
    final: ChatResult
    tool_calls: int = 0
    prompt_tokens: int = 0
    completion_tokens: int = 0
    retrieved: list[str] = field(default_factory=list)
    trace: list[dict[str, Any]] = field(default_factory=list)


class AgentTools:
    def __init__(self, context: IncidentContext) -> None:
        self.context = context
        self.retrieved: list[str] = []

    def search_logs(self, query: str, level: str | None = None, limit: int = 10) -> dict[str, Any]:
        terms = set(tokenize(query))
        scored = []
        for line in self.context.logs:
            if level and line.level != level.upper():
                continue
            overlap = len(terms & set(tokenize(line.message)))
            if overlap or not terms:
                scored.append((overlap, line))
        scored.sort(key=lambda item: (-item[0], item[1].id))
        return {"matches": [
            {"ref": line.id, "level": line.level, "occurrences": line.occurrences, "message": line.message}
            for _, line in scored[: max(1, min(limit, 25))]
        ]}

    def get_log_stats(self) -> dict[str, Any]:
        by_level: dict[str, int] = {}
        for line in self.context.logs:
            by_level[line.level] = by_level.get(line.level, 0) + line.occurrences
        top = sorted(self.context.logs, key=lambda line: -line.occurrences)[:5]
        return {
            "totalLines": self.context.totalLogLines,
            "byLevel": by_level,
            "topMessages": [{"ref": line.id, "level": line.level, "occurrences": line.occurrences,
                             "message": line.message} for line in top],
        }

    def get_ci_failures(self) -> dict[str, Any]:
        return {"failedRuns": self.context.failed_ci_runs(), "totalRuns": len(self.context.ciRuns)}

    def search_knowledge_base(self, query: str) -> dict[str, Any]:
        docs = default_index().search(query, k=3)
        for doc in docs:
            if doc["id"] not in self.retrieved:
                self.retrieved.append(doc["id"])
        return {"documents": [{"id": d["id"], "title": d["title"], "text": d["text"]} for d in docs]}

    def dispatch(self, name: str, arguments: str) -> str:
        try:
            args = json.loads(arguments or "{}")
            if not isinstance(args, dict):
                raise TypeError("arguments must be an object")
            handler = {
                "search_logs": self.search_logs,
                "get_log_stats": self.get_log_stats,
                "get_ci_failures": self.get_ci_failures,
                "search_knowledge_base": self.search_knowledge_base,
            }.get(name)
            if handler is None:
                return json.dumps({"error": f"unknown tool {name}"})
            result = handler(**args)
        except (TypeError, ValueError) as ex:
            return json.dumps({"error": f"invalid arguments: {ex}"})
        return json.dumps(result)[:MAX_TOOL_RESULT_CHARS]


async def run_agent(config: LlmConfig, context: IncidentContext) -> AgentRun:
    tools = AgentTools(context)
    messages: list[dict[str, Any]] = [
        {"role": "system", "content": AGENT_SYSTEM_PROMPT},
        {"role": "user", "content": build_agent_user_prompt(context)},
    ]
    run = AgentRun(final=ChatResult(content=None))

    for _ in range(MAX_STEPS):
        result = await chat(config, messages, tools=TOOLS)
        run.prompt_tokens += result.prompt_tokens
        run.completion_tokens += result.completion_tokens
        if not result.tool_calls:
            run.final = result
            run.retrieved = tools.retrieved
            return run
        messages.append({"role": "assistant", "content": result.content or "", "tool_calls": result.tool_calls})
        for call in result.tool_calls:
            fn = call.get("function", {})
            output = tools.dispatch(fn.get("name", ""), fn.get("arguments", "{}"))
            run.tool_calls += 1
            run.trace.append({"tool": fn.get("name"), "chars": len(output)})
            messages.append({"role": "tool", "tool_call_id": call.get("id", ""), "content": output})

    messages.append({"role": "user", "content": "Tool budget exhausted. Return the final JSON object now."})
    result = await chat(config, messages, json_mode=True)
    run.prompt_tokens += result.prompt_tokens
    run.completion_tokens += result.completion_tokens
    if not result.content:
        raise LlmError("agent_no_answer", "Agent produced no final answer.")
    run.final = result
    run.retrieved = tools.retrieved
    return run
