# OpenRouter Nemotron Configuration

DeployGuard AI can call OpenRouter for NVIDIA Nemotron incident analysis from the FastAPI AI service. The same `/analyze-incident` endpoint also works without an API key by returning a safe fallback response.

Model identifiers, provider routing, and free-tier availability can change. Confirm the current model identifier and account limits in OpenRouter before relying on a specific model in a demo or deployment.

## Environment Variables

Create a local environment file from the example:

```bash
cp ai-service/.env.example ai-service/.env
```

Do not commit `.env` or real API keys.

Example values:

```bash
OPENROUTER_API_KEY=
OPENROUTER_MODEL=nvidia/nemotron-3-ultra-550b-a55b:free
OPENROUTER_BASE_URL=https://openrouter.ai/api/v1
OPENROUTER_TIMEOUT_SECONDS=30
```

Current application defaults and behavior:

- `OPENROUTER_MODEL`: `nvidia/nemotron-3-ultra-550b-a55b:free`
- `OPENROUTER_BASE_URL`: `https://openrouter.ai/api/v1`
- request timeout: `30` seconds

The example includes `OPENROUTER_TIMEOUT_SECONDS` to document the local timeout setting. The current AI service implementation uses a 30 second timeout internally.

`OPENROUTER_API_KEY` has no default. When it is missing, the service intentionally uses fallback mode.

## Fallback Mode

Use fallback mode to verify the API response schema without calling OpenRouter.

Terminal 1:

```bash
cd ai-service
source .venv/bin/activate
unset OPENROUTER_API_KEY
python -m uvicorn app.main:app --host 127.0.0.1 --port 8001
```

Terminal 2:

```bash
./scripts/smoke-test-ai.sh
```

Expected result:

- `GET /health` succeeds.
- `POST /analyze-incident` succeeds.
- The response includes `summary`, `likelyRootCause`, `evidence`, `recommendedActions`, `severity`, and `confidence`.
- The content is a fallback analysis based on `riskLevel`.

## Real Model Mode

Use real model mode to call OpenRouter.

Terminal 1:

```bash
cd ai-service
source .venv/bin/activate
export OPENROUTER_API_KEY="your-openrouter-api-key"
export OPENROUTER_MODEL="nvidia/nemotron-3-ultra-550b-a55b:free"
export OPENROUTER_BASE_URL="https://openrouter.ai/api/v1"
python -m uvicorn app.main:app --host 127.0.0.1 --port 8001
```

Terminal 2:

```bash
./scripts/smoke-test-ai.sh
```

The smoke test validates the response shape. It does not prove which upstream model produced the text because the public response schema is intentionally unchanged.

## Custom AI Service URL

If the service is running somewhere else:

```bash
AI_SERVICE_URL=http://127.0.0.1:8001 ./scripts/smoke-test-ai.sh
```

## Troubleshooting

### Missing API Key

If `OPENROUTER_API_KEY` is unset or blank, the service returns the fallback response. This is expected for local development.

### Invalid API Key

OpenRouter may return `401` or `403`. The AI service catches the provider error and returns fallback analysis rather than crashing. Verify the key and restart the AI service with the corrected environment variable.

### Request Timeout

The AI service uses a 30 second timeout. If OpenRouter does not respond in time, the service returns fallback analysis. Try again later or verify network connectivity.

### HTTP 429 Rate Limiting

OpenRouter may return `429` when account or model limits are reached. The service returns fallback analysis. Wait for the rate limit window to reset or use a model/account with available capacity.

### Model Unavailable

If the configured model identifier is unavailable, renamed, or not enabled for the account, OpenRouter may return an error. Confirm the current model identifier in OpenRouter, update `OPENROUTER_MODEL`, and restart the AI service.

### Invalid JSON Or Model Response

The AI service asks the model for JSON only and validates the response shape. If parsing or validation fails, the service returns fallback analysis.

### Fallback Response Being Returned

Fallback is returned when:

- `OPENROUTER_API_KEY` is missing
- OpenRouter rejects the request
- the request times out
- the model returns invalid JSON
- the model response does not match the expected schema

Check the AI service environment variables and rerun:

```bash
./scripts/smoke-test-ai.sh
```
