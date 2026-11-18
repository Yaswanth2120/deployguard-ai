"""Redaction of secrets and personal data before anything reaches a model provider.

Incident data is sensitive: logs routinely contain tokens, connection strings,
emails and IPs. Every string in the analysis request is passed through
``redact_value`` before prompt construction, and the redacted form is also what
grounding checks compare against, so a model can never "prove" evidence by
quoting a secret back.
"""

import re
from dataclasses import dataclass, field
from typing import Any

# Order matters: more specific patterns run first so a JWT is not half-eaten by
# the generic key=value rule.
_PATTERNS: list[tuple[str, re.Pattern[str]]] = [
    ("PRIVATE_KEY", re.compile(r"-----BEGIN [A-Z ]*PRIVATE KEY-----.*?-----END [A-Z ]*PRIVATE KEY-----", re.DOTALL)),
    ("JWT", re.compile(r"\beyJ[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}\b")),
    ("GITHUB_TOKEN", re.compile(r"\b(?:gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{40,})\b")),
    ("AWS_ACCESS_KEY", re.compile(r"\b(?:AKIA|ASIA)[0-9A-Z]{16}\b")),
    ("SLACK_TOKEN", re.compile(r"\bxox[abprs]-[A-Za-z0-9-]{10,}\b")),
    ("API_KEY", re.compile(r"\bsk-[A-Za-z0-9_-]{20,}\b")),
    ("URL_CREDENTIALS", re.compile(r"(?P<scheme>\b[a-z][a-z0-9+.-]*://)[^\s:/@]+:[^\s@/]+@")),
    ("BEARER", re.compile(r"(?i)\bbearer\s+[A-Za-z0-9._~+/=-]{8,}")),
    (
        "SECRET_ASSIGNMENT",
        re.compile(
            r"(?i)\b(?P<key>password|passwd|pwd|secret|client_secret|token|access_token|api[_-]?key|private[_-]?key)"
            r"(?P<sep>\s*[=:]\s*)(?P<val>\"[^\"]*\"|'[^']*'|[^\s,;]+)"
        ),
    ),
    ("EMAIL", re.compile(r"\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}\b")),
    ("IPV4", re.compile(r"\b(?:25[0-5]|2[0-4]\d|1?\d?\d)(?:\.(?:25[0-5]|2[0-4]\d|1?\d?\d)){3}\b")),
    ("CARD_NUMBER", re.compile(r"\b(?:\d[ -]?){13,16}\b")),
]


@dataclass
class RedactionResult:
    value: Any
    count: int = 0
    kinds: dict[str, int] = field(default_factory=dict)


def _replace(kind: str, match: re.Match[str]) -> str:
    if kind == "URL_CREDENTIALS":
        return f"{match.group('scheme')}[REDACTED_CREDENTIALS]@"
    if kind == "SECRET_ASSIGNMENT":
        return f"{match.group('key')}{match.group('sep')}[REDACTED_SECRET]"
    if kind == "BEARER":
        return "Bearer [REDACTED_TOKEN]"
    return f"[REDACTED_{kind}]"


def redact_text(text: str) -> RedactionResult:
    kinds: dict[str, int] = {}
    for kind, pattern in _PATTERNS:
        def _sub(match: re.Match[str], _kind: str = kind) -> str:
            kinds[_kind] = kinds.get(_kind, 0) + 1
            return _replace(_kind, match)

        text = pattern.sub(_sub, text)
    return RedactionResult(value=text, count=sum(kinds.values()), kinds=kinds)


def redact_value(value: Any) -> RedactionResult:
    """Recursively redact every string inside dicts/lists. Keys are not redacted."""
    if isinstance(value, str):
        return redact_text(value)
    if isinstance(value, dict):
        total = RedactionResult(value={})
        for key, item in value.items():
            child = redact_value(item)
            total.value[key] = child.value
            _merge(total, child)
        return total
    if isinstance(value, list):
        total = RedactionResult(value=[])
        for item in value:
            child = redact_value(item)
            total.value.append(child.value)
            _merge(total, child)
        return total
    return RedactionResult(value=value)


def _merge(total: RedactionResult, child: RedactionResult) -> None:
    total.count += child.count
    for kind, n in child.kinds.items():
        total.kinds[kind] = total.kinds.get(kind, 0) + n
