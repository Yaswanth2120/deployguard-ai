# Testing strategy

- **Backend unit and web-slice tests** (`mvn verify`):
  - service rules
  - risk-rule contract tests against `shared/risk-rules.json`
  - webhook signature verification (including GitHub's documented test vector) and event mapping
  - retry-policy classification and the dead-letter recoverer
  - AI provenance persistence
- **AI-service tests** (`PYTHONPATH=. pytest`):
  - redaction and bounded context building
  - BM25 retrieval and grounding
  - every fallback reason
  - the agent tool loop, including budget exhaustion
  - risk-rule contract and model monotonicity
  - ML metric correctness
  - the **eval regression gate**: the heuristic analyzer must meet `evals/thresholds.json` on the golden set
- **LLM evaluation** (`python -m evals.run --mode ...`): offline for `heuristic`, needs `OPENROUTER_API_KEY` for LLM modes. Runs in CI through the manual `workflow_dispatch` job.
- **Frontend checks:** `npm run lint && npm run build`.
- **End-to-end:** verified manually against local PostgreSQL 16 and RabbitMQ:
  - Flyway V1–V9
  - signed webhooks creating and re-scoring deployments
  - sync and async analysis with persisted provenance
  - retry exhaustion landing in the DLQ

  This is not yet automated in CI.
