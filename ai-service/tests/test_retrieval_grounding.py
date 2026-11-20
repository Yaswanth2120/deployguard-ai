from app.grounding import filter_grounded, grounding_score
from app.retrieval import default_index


def test_bm25_retrieves_relevant_runbook() -> None:
    hits = default_index().search("java.lang.OutOfMemoryError Java heap space container OOMKilled", k=3)
    assert hits[0]["id"] == "KB-004"
    flyway = default_index().search("FlywayValidateException migration failed validation", k=1)
    assert flyway[0]["category"] == "BAD_MIGRATION"


def test_bm25_returns_nothing_for_unrelated_query() -> None:
    assert default_index().search("banana smoothie recipe", k=3) == []


def test_grounding_accepts_quotes_and_rejects_fabrication() -> None:
    corpus = ["ERROR Read timed out calling payments-provider POST /v2/charges after 5000ms"]
    assert grounding_score("[L1] Read timed out calling payments-provider after 5000ms", corpus) >= 0.6
    kept, dropped = filter_grounded(
        ["[L1] payments-provider read timed out after 5000ms", "Database CPU spiked to 100% on replica-3"],
        corpus,
    )
    assert kept == ["[L1] payments-provider read timed out after 5000ms"]
    assert dropped == 1


def test_resolve_evidence_accepts_bracketed_and_bare_ids() -> None:
    from app.grounding import resolve_evidence

    refs = {"L1": "ERROR: disk full", "CI1": "CI gha status FAILED with 2 failed tests"}
    kept, dropped = resolve_evidence(["L1", "[CI1] tests", "L1: again", "L12", "LOL text"], refs, ["ERROR disk full"])
    assert kept == ["[L1] ERROR: disk full", "[CI1] CI gha status FAILED with 2 failed tests"]
    assert dropped == 2
