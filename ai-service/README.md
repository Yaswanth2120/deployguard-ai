# DeployGuard AI Service

FastAPI service for deployment incident analysis.

Current status: OpenRouter integration for NVIDIA Nemotron 3 Ultra with a safe fallback response when the API key is missing, the model call fails, or the model returns invalid JSON.

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
pip install -r requirements.txt
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

Without an API key, or if the model call fails, the service returns a deterministic fallback response based on `riskLevel`.

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
  "evidence": [],
  "recommendedActions": [],
  "severity": "LOW",
  "confidence": "LOW"
}
```
