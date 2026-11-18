# AI integration

Superseded by [ai-engineering.md](ai-engineering.md). That document covers:

- analysis modes (heuristic, single-shot, RAG, agent)
- redaction, injection handling and evidence grounding
- the eval harness and measured results
- the deployment risk model
- metrics

`/v1/risk-scores` returns the logistic-regression model score next to the deterministic rule baseline (`shared/risk-rules.json`). If the model artifact is missing, it falls back to the rules.
