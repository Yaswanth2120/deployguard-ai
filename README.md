# DeployGuard AI

DeployGuard AI is an AI-powered deployment risk and incident analysis platform. It will correlate GitHub pull requests, CI/CD results, deployment events, and application logs to help teams understand release risk and diagnose incidents faster.

Future AI-assisted incident summaries are planned to use NVIDIA Nemotron 3 Ultra.

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

Planned stack:

- Backend API: Spring Boot
- AI service: FastAPI
- Frontend: Next.js
- Data store: PostgreSQL
- Cache / queue support: Redis and RabbitMQ
- Infrastructure: Docker Compose for local development
- Future model integration: NVIDIA Nemotron 3 Ultra

No application framework code has been generated yet.

## Current Status

Initial monorepo structure only.

- Required folders have been created.
- Documentation placeholders have been added.
- Docker Compose placeholder exists for future services.
- No business logic has been implemented.
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
