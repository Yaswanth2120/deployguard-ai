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
