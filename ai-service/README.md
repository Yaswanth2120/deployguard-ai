# DeployGuard AI Service

FastAPI service for deployment incident analysis.

Current status: incident analysis in four modes (`heuristic`, `single_shot`, `rag`, `agent`) with redaction, evidence grounding, a strict output schema, provenance metadata, and Prometheus metrics. `/v1/risk-scores` serves a logistic-regression risk model next to the deterministic rule baseline. Design and measured results are in [../docs/ai-engineering.md](../docs/ai-engineering.md).

## Layout

```text
app/
  services/incident_analyzer.py  orchestration: redact -> context -> mode -> validate -> ground -> meta
  agent.py                       tool-calling agent loop
  retrieval.py, knowledge/       BM25 over past incidents and runbooks
  redaction.py, grounding.py     safety controls
  heuristic.py                   deterministic analyzer (fallback and eval baseline)
  prompts.py                     versioned prompts (PROMPT_VERSION)
  telemetry.py                   metrics registry, GET /metrics
  ml_features.py                 shared train/serve feature extraction
  services/risk_rules.py         rule baseline (matches ../shared/risk-rules.json)
  services/risk_model.py         dependency-free model serving
evals/                           golden set, scoring, runner, CI thresholds
ml/                              synthetic generator, GitHub miner, trainer, metrics
ml_artifacts/risk_model.json     trained model artifact
```

## Requirements

- Python 3.11+

## Environment Variables

```bash
export OPENROUTER_API_KEY="your-openrouter-api-key"
export OPENROUTER_MODEL="nvidia/nemotron-3-ultra-550b-a55b:free"
export OPENROUTER_BASE_URL="https://openrouter.ai/api/v1"
```

Only `OPENROUTER_API_KEY` is required for real AI analysis. The model and base URL have defaults.

You can also create a local `.env` file for development. Do not commit `.env` files or API keys.

## Setup

```bash
cd deployguard-ai/ai-service
python3.11 -m venv .venv
source .venv/bin/activate
pip install -r requirements-dev.txt   # runtime: requirements.txt
```

## Test, lint, evaluate

```bash
ruff check app ml evals tests
PYTHONPATH=. pytest
PYTHONPATH=. python -m evals.run --mode heuristic                     # offline
OPENROUTER_API_KEY=... PYTHONPATH=. python -m evals.run --mode rag --mode agent
```

## Train the risk model

```bash
PYTHONPATH=. python -m ml.synthetic --out ml/data/synthetic.jsonl
PYTHONPATH=. python -m ml.train --data ml/data/synthetic.jsonl
```

If `python3.11` is not available, use any Python 3.11+ interpreter installed on your machine.

## Run

```bash
uvicorn app.main:app --reload --port 8001
```

## Health Check

```bash
curl -i http://localhost:8001/health
```

Expected response:

```json
{
  "status": "UP",
  "service": "deployguard-ai-service"
}
```

## Analyze Incident

With `OPENROUTER_API_KEY` set, this endpoint calls OpenRouter chat completions using `nvidia/nemotron-3-ultra-550b-a55b:free`.

Without an API key, or if the model call fails, the heuristic analyzer answers and `meta.source` is `"heuristic"` with a `fallbackReason`. Override the mode per request with `?mode=heuristic|single_shot|rag|agent`.

```bash
curl -i -X POST http://localhost:8001/analyze-incident \
  -H "Content-Type: application/json" \
  -d '{
    "deployment": {
      "id": "deployment-id",
      "environment": "prod",
      "commitSha": "abc123"
    },
    "ciRuns": [
      {
        "provider": "github-actions",
        "status": "FAILED",
        "failedTests": 3
      }
    ],
    "logs": [
      {
        "level": "ERROR",
        "message": "Payment API timeout after deployment"
      }
    ],
    "riskScore": 85,
    "riskLevel": "HIGH"
  }'
```

Response shape:

```json
{
  "summary": "...",
  "likelyRootCause": "...",
  "rootCauseCategory": "DOWNSTREAM_TIMEOUT",
  "evidence": ["[L1] ERROR: Payment API timeout after deployment"],
  "recommendedActions": ["..."],
  "severity": "HIGH",
  "confidence": "MEDIUM",
  "meta": {
    "source": "model",
    "mode": "rag",
    "fallbackReason": null,
    "model": "nvidia/nemotron-3-ultra-550b-a55b:free",
    "promptVersion": "incident-v2.0",
    "latencyMs": 0,
    "promptTokens": 0,
    "completionTokens": 0,
    "estimatedCostUsd": 0.0,
    "toolCalls": 0,
    "redactions": 0,
    "droppedEvidence": 0,
    "retrievedDocuments": ["KB-005"]
  }
}
```

## Metrics

```bash
curl http://localhost:8001/metrics
```
