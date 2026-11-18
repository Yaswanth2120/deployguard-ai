"""BM25 retrieval over the incident knowledge base (past incidents + runbooks).

Pure Python so the service has no vector-DB dependency. The corpus is small and
domain-specific (error strings, component names), where lexical BM25 is a strong
baseline; swapping in an embedding retriever only requires another ``search``
implementation with the same signature.
"""

import json
import math
import re
from collections import Counter
from functools import lru_cache
from pathlib import Path

KB_PATH = Path(__file__).parent / "knowledge" / "kb.json"
_TOKEN = re.compile(r"[a-z0-9_]+")
_STOP = {
    "the", "a", "an", "and", "or", "of", "to", "in", "on", "for", "is", "was", "with", "after",
    "at", "by", "from", "be", "it", "this", "that", "as", "are", "not", "no", "error", "info", "warn",
}


def tokenize(text: str) -> list[str]:
    return [t for t in _TOKEN.findall(text.lower()) if t not in _STOP and len(t) > 1]


class Bm25Index:
    def __init__(self, docs: list[dict], k1: float = 1.4, b: float = 0.75) -> None:
        self.docs = docs
        self.k1 = k1
        self.b = b
        self.doc_tokens = [tokenize(f"{d['title']} {d['title']} {' '.join(d.get('tags', []))} {d['text']}") for d in docs]
        self.tf = [Counter(tokens) for tokens in self.doc_tokens]
        self.avgdl = sum(len(t) for t in self.doc_tokens) / max(1, len(docs))
        df: Counter[str] = Counter()
        for tokens in self.doc_tokens:
            df.update(set(tokens))
        n = len(docs)
        self.idf = {term: math.log(1 + (n - freq + 0.5) / (freq + 0.5)) for term, freq in df.items()}

    def search(self, query: str, k: int = 3, min_score: float = 1.0) -> list[dict]:
        q = tokenize(query)
        scored: list[tuple[float, int]] = []
        for i, tf in enumerate(self.tf):
            dl = len(self.doc_tokens[i])
            score = 0.0
            for term in q:
                if term not in tf:
                    continue
                freq = tf[term]
                score += self.idf[term] * freq * (self.k1 + 1) / (freq + self.k1 * (1 - self.b + self.b * dl / self.avgdl))
            if score >= min_score:
                scored.append((score, i))
        scored.sort(reverse=True)
        return [{**self.docs[i], "score": round(s, 3)} for s, i in scored[:k]]


@lru_cache(maxsize=1)
def default_index() -> Bm25Index:
    return Bm25Index(json.loads(KB_PATH.read_text()))
