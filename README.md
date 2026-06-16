# DeployGuard AI

DeployGuard AI is an AI-powered deployment risk and incident analysis platform. It will correlate GitHub pull requests, CI/CD results, deployment events, and application logs to help teams understand release risk and diagnose incidents faster.

Future AI-assisted incident summaries are planned to use NVIDIA Nemotron 3 Ultra.

## Quick Start

For complete local setup instructions, see [docs/local-development.md](docs/local-development.md).

For OpenRouter/NVIDIA Nemotron configuration and AI smoke testing, see [docs/openrouter-nemotron.md](docs/openrouter-nemotron.md).

The short path for an already configured machine is:

```bash
docker compose -f infra/docker-compose.yml up -d postgres rabbitmq
```

Then start the AI service, backend, and frontend in separate terminals using the commands in the local development runbook.

## Folder Structure

```text
deployguard-ai/
  backend/
    deployguard-api/
  ai-service/
  frontend/
  infra/
  docs/
  AGENTS.md
  README.md
```

## Tech Stack

- Backend API: Spring Boot
- AI service: FastAPI
- Frontend: Next.js
- Data store: PostgreSQL
- Queue support: RabbitMQ
- Future cache support: Redis
- Infrastructure: Docker Compose for local development
- Future model integration: NVIDIA Nemotron 3 Ultra

## Current Status

- Spring Boot backend includes Projects, Deployments, CI runs, application logs, risk scoring, and AI analysis job APIs.
- FastAPI AI service includes incident analysis with OpenRouter support and safe fallback behavior.
- Next.js frontend includes a local dashboard connected to backend APIs.
- Docker Compose runs PostgreSQL and RabbitMQ for local development.
- Demo data can be created with `./scripts/seed-demo.sh`.
- No API keys or secrets are included.

## Local PostgreSQL

Start PostgreSQL for local development:

```bash
docker compose -f infra/docker-compose.yml up -d postgres
```

The Postgres service uses `postgres:16` with `platform: linux/arm64` so it runs cleanly on Apple Silicon.

Verify it is running:

```bash
docker ps
nc -zv localhost 5432
```

Troubleshooting:

```bash
docker logs deployguard-postgres
```

If Docker reports an `exec format error`, confirm `infra/docker-compose.yml` uses `image: postgres:16` and `platform: linux/arm64` for the `postgres` service, then recreate the container:

```bash
docker compose -f infra/docker-compose.yml down
docker compose -f infra/docker-compose.yml up -d postgres
```

## Local Demo Data

Use the demo seed script to create realistic local data for the frontend dashboard.

Start local infrastructure from the repository root:

```bash
docker compose -f infra/docker-compose.yml up -d postgres rabbitmq
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

Start the AI service in a separate terminal. `OPENROUTER_API_KEY` is optional for local demo data because the AI service can return a fallback response.

```bash
cd ai-service
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8001
```

Start the frontend in another terminal:

```bash
cd frontend
npm install
npm run dev
```

Seed demo data from the repository root:

```bash
./scripts/seed-demo.sh
```

Override the backend URL when needed:

```bash
BACKEND_URL=http://localhost:8080 ./scripts/seed-demo.sh
```

The script creates one project, one high-risk production deployment, one failed CI run, one `ERROR` application log, recalculates risk, and queues an async AI analysis job. It prints the created `PROJECT_ID`, `DEPLOYMENT_ID`, `JOB_ID`, and frontend URLs for viewing the seeded data.
