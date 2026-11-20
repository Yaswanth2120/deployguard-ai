"""Evidence grounding: every evidence item the model returns must trace back to the
(redacted) input it was shown. Ungrounded items are dropped and counted, so the
hallucinated-evidence rate is measurable in production, not only offline."""

import re

_REF = re.compile(r"^\s*\[(?:L|CI)\d+\]\s*")
_TOKEN = re.compile(r"[a-z0-9_.:/-]+")
MIN_OVERLAP = 0.6


def _tokens(text: str) -> set[str]:
    return {t.strip(".:/-") for t in _TOKEN.findall(text.lower()) if len(t.strip(".:/-")) >= 3}


def grounding_score(item: str, corpus: list[str]) -> float:
    """Max fraction of the item's content tokens that appear in a single corpus entry."""
    body = _tokens(_REF.sub("", item))
    if not body:
        return 0.0
    best = 0.0
    for source in corpus:
        overlap = len(body & _tokens(source)) / len(body)
        best = max(best, overlap)
    return best


def filter_grounded(evidence: list[str], corpus: list[str], threshold: float = MIN_OVERLAP) -> tuple[list[str], int]:
    kept = [item for item in evidence if grounding_score(item, corpus) >= threshold]
    return kept, len(evidence) - len(kept)


# "[L3] ...", "L3", "L3: ..." all count: the agent sees bare ids in tool output ("ref": "L3").
_LEADING_REF = re.compile(r"^\s*\[?((?:L|CI)\d+)\]?(?![\w])")


def resolve_evidence(evidence: list[str], references: dict[str, str], corpus: list[str]) -> tuple[list[str], int]:
    """Evidence-by-reference.

    An item that starts with a valid reference id ("[L3] ...") is replaced by the canonical
    text of that line, so a quote can never be fabricated or distorted. Items without a valid
    id fall back to the token-overlap check. Duplicate references are collapsed.
    Returns (kept items, number of items dropped as ungrounded).
    """
    kept: list[str] = []
    seen: set[str] = set()
    dropped = 0
    for item in evidence:
        match = _LEADING_REF.match(item)
        if match and match.group(1) in references:
            ref = match.group(1)
            if ref not in seen:
                seen.add(ref)
                kept.append(f"[{ref}] {references[ref]}")
            continue
        if grounding_score(item, corpus) >= MIN_OVERLAP:
            kept.append(item)
        else:
            dropped += 1
    return kept, dropped
