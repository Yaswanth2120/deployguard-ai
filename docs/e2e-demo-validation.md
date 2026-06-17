# End-to-End Demo Validation

Use `scripts/validate-demo.sh` to validate the complete local DeployGuard AI demo workflow from infrastructure checks through seeded data, risk scoring, synchronous AI analysis, asynchronous AI analysis, and stored incident summaries.

The validator does not require a real OpenRouter key. The AI service fallback response is valid for this workflow.

## Prerequisites

Install the same tools required by the local development stack:

- Docker Desktop
- curl
- Java 21
- Maven
- Python or Python 3
- Node.js
- npm

The script checks for these commands before it touches the running services.

## Required Running Services

Start PostgreSQL and RabbitMQ from the repository root:

```bash
docker compose -f infra/docker-compose.yml up -d postgres rabbitmq
```

Start the AI service:

```bash
cd ai-service
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
python -m uvicorn app.main:app --host 127.0.0.1 --port 8001
```

Start the backend:

```bash
cd backend/deployguard-api
DB_HOST=localhost \
DB_PORT=5432 \
DB_NAME=deployguard \
DB_USERNAME=deployguard \
DB_PASSWORD=deployguard \
AI_SERVICE_BASE_URL=http://localhost:8001 \
RABBITMQ_HOST=localhost \
RABBITMQ_PORT=5672 \
RABBITMQ_USERNAME=deployguard \
RABBITMQ_PASSWORD=deployguard \
mvn spring-boot:run
```

Start the frontend:

```bash
cd frontend
npm install
NEXT_PUBLIC_API_BASE_URL=http://localhost:8080 npm run dev
```

## Run Validation

From the repository root:

```bash
./scripts/validate-demo.sh
```

The script runs `scripts/seed-demo.sh`, captures `PROJECT_ID`, `DEPLOYMENT_ID`, and `JOB_ID`, then validates the seeded workflow.

## Environment Overrides

Use these variables when services are running on non-default URLs or when async processing needs a different polling window:

```bash
BACKEND_URL=http://localhost:8080 \
AI_SERVICE_URL=http://127.0.0.1:8001 \
FRONTEND_URL=http://localhost:3000 \
POLL_INTERVAL_SECONDS=2 \
POLL_TIMEOUT_SECONDS=60 \
./scripts/validate-demo.sh
```

Defaults:

- `BACKEND_URL`: `http://localhost:8080`
- `AI_SERVICE_URL`: `http://127.0.0.1:8001`
- `FRONTEND_URL`: `http://localhost:3000`
- `POLL_INTERVAL_SECONDS`: `2`
- `POLL_TIMEOUT_SECONDS`: `60`

If `AI_SERVICE_URL` is changed, start the backend with a matching `AI_SERVICE_BASE_URL` value so backend-initiated analysis calls reach the same AI service.

## Expected Output

Successful runs print readable checks with `PASS` lines and a final summary:

```text
DeployGuard AI end-to-end demo validation
Backend:  http://localhost:8080
AI:       http://127.0.0.1:8001
Frontend: http://localhost:3000

== Prerequisites ==
PASS command available: docker
PASS command available: curl

...

== Summary ==
PASS checks: 24
FAIL checks: 0

DeployGuard AI demo validation passed.
```

Failures print `FAIL` next to the broken service or workflow step, list failures in the final summary, and exit nonzero.

## What It Validates

The validator checks:

- required local commands
- `deployguard-postgres` and `deployguard-rabbitmq` containers are running
- local PostgreSQL port `5432` and RabbitMQ port `5672` are reachable
- AI service `/health`, backend `/api/health`, and the frontend root URL are reachable
- `scripts/seed-demo.sh` completes
- `PROJECT_ID`, `DEPLOYMENT_ID`, and `JOB_ID` are captured
- seeded deployment exists
- seeded deployment has `HIGH` risk and a risk score
- synchronous AI analysis returns the expected response schema
- asynchronous AI analysis job exists
- asynchronous job reaches `COMPLETED`
- at least one AI incident summary is stored

## Common Failures

### Missing Command

Install the missing prerequisite and rerun the script.

### PostgreSQL Or RabbitMQ Container Is Not Running

Start local infrastructure:

```bash
docker compose -f infra/docker-compose.yml up -d postgres rabbitmq
```

Check logs:

```bash
docker logs deployguard-postgres
docker logs deployguard-rabbitmq
```

### Port 5432 Or 5672 Is Not Reachable

Confirm Docker published the ports:

```bash
docker ps
```

If another local process owns the port, stop it or adjust the local setup before running the validator.

### AI Service Health Fails

Start the AI service on the expected URL, or pass `AI_SERVICE_URL`:

```bash
AI_SERVICE_URL=http://127.0.0.1:8001 ./scripts/validate-demo.sh
```

No OpenRouter key is required for the demo validation. The fallback AI response should still pass.

### Backend Health Fails

Start the backend and confirm it can reach PostgreSQL, RabbitMQ, and the AI service. If the AI service URL is not the default, start the backend with a matching `AI_SERVICE_BASE_URL`.

### Frontend Check Fails

Start the frontend with:

```bash
cd frontend
NEXT_PUBLIC_API_BASE_URL=http://localhost:8080 npm run dev
```

### Async Job Fails

The validator prints the backend job error message when available. Check the backend logs first, then confirm RabbitMQ is running and the backend was started with the expected RabbitMQ settings.

### Async Job Times Out

Increase the polling timeout for slower machines:

```bash
POLL_TIMEOUT_SECONDS=120 ./scripts/validate-demo.sh
```

If the job stays `PENDING` or `PROCESSING`, check backend logs and RabbitMQ connectivity.

## Rerunning

The validator is safe to rerun for local demo purposes. Each run creates a new timestamped demo project and deployment through the existing seed script.
