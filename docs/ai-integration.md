# AI risk-correlation design

The API accepts signed provider events, validates and redacts them, persists normalized entities, and correlates signals by project and commit SHA. A risk request contains the PR context plus CI runs and bounded, redacted log excerpts. Deterministic factors (failed CI/tests, error logs, production target, hotfix branch) produce a baseline score and evidence.

The FastAPI `/v1/risk-scores` endpoint currently returns that deterministic baseline. The Nemotron 3 Ultra integration point is immediately after factor construction: the service will send a schema-constrained prompt containing only redacted context and request a JSON rationale/factor review. The response must be validated against the API schema and fall back to deterministic results on timeout, invalid output, or provider error. Provider keys remain server-side only.
