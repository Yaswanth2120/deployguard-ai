# DeployGuard AI Service

FastAPI service for deployment incident analysis.

Current status: mock incident analysis only. This service does not call NVIDIA Nemotron, OpenRouter, or any external AI provider yet.

## Requirements

- Python 3.11+

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

```bash
curl -i -X POST http://localhost:8001/analyze-incident \
  -H "Content-Type: application/json" \
  -d '{
    "deployment": {
      "id": "deployment-id",
      "environment": "prod",
      "commitSha": "abc123"
    },
    "ciRuns": [],
    "logs": [],
    "riskScore": 85,
    "riskLevel": "HIGH"
  }'
```

The response is currently mocked from `riskLevel`:

- `LOW`: low severity summary
- `MEDIUM`: medium severity summary
- `HIGH`: high severity summary
