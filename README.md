# DeployGuard AI

DeployGuard AI is a deployment risk and incident-analysis platform. It correlates deployment events, CI/CD results, and application logs to produce a deterministic risk score for each deployment, and it generates AI-assisted incident summaries through a dedicated analysis service with safe fallback behavior.

The project is built as a small, inspectable monorepo: a Spring Boot backend, a FastAPI AI service, a Next.js dashboard, PostgreSQL for storage, and RabbitMQ for asynchronous AI work.

> Status: This is a working local-first project. There is no hosted production deployment, no authentication, and no multi-tenancy yet. See [Known Limitations](#known-limitations) for the honest scope.

## For Recruiters and Engineers

If you are evaluating this project, start here:

- **[docs/interview-guide.md](docs/interview-guide.md)** — 30-second, two-minute, and five-minute walkthroughs, design rationale, trade-offs, and likely interview questions with truthful answers.
- **[docs/resume-bullets.md](docs/resume-bullets.md)** — resume bullets, LinkedIn and GitHub descriptions, and STAR-style examples.
- **[docs/system-design.md](docs/system-design.md)** — architecture diagrams, component responsibilities, reliability behavior, and scaling notes.
- **[docs/screenshot-checklist.md](docs/screenshot-checklist.md)** — what to capture for a portfolio walkthrough.

## The Problem

**Business problem.** When a deployment causes an incident, teams lose time figuring out *which* change is risky and *why* a service is failing. The signal is spread across CI results, deployment history, and application logs, and is usually pieced together by hand under pressure.

**Technical problem.** Those signals live in different systems and formats. There is no single, consistent view that ties a specific deployed commit to its CI outcome, its error logs, and a structured assessment of how risky it is.

**Target users.** Developers and operators who ship application services and want a faster read on release risk and a quicker first-pass explanation when something breaks.

## The Solution

DeployGuard AI brings deployment, CI, and log signals into one data model and layers two capabilities on top:

1. A **deterministic risk engine** that scores each deployment from concrete signals, so the score is explainable and repeatable.
2. An **AI incident-analysis service** that produces a structured summary (root cause, evidence, recommended actions, severity, confidence) and falls back to a deterministic response when the model is unavailable.

## Major Features

- **Deterministic risk engine.** Each deployment is scored from explicit, additive rules (see [Risk Scoring](#risk-scoring)). The same inputs always produce the same score, capped at 100 and mapped to `LOW` / `MEDIUM` / `HIGH`. No model is involved in the score itself.
- **Synchronous AI analysis.** `POST /api/deployments/{id}/ai-analysis` loads the deployment, project, related CI runs, and logs, calls the AI service inline, stores the summary, and returns it. Best when the caller wants an answer immediately and can tolerate the model latency.
- **Asynchronous AI analysis via RabbitMQ.** `POST /api/deployments/{id}/ai-analysis/jobs` inserts a job row, publishes a message to RabbitMQ, and returns immediately. A Spring Boot consumer processes the job, writes the summary, and updates job status. Clients poll `GET /api/ai-analysis/jobs/{jobId}`. Better for longer-running analysis and future scaling.
- **OpenRouter / NVIDIA Nemotron integration.** The FastAPI service calls OpenRouter (configured for an NVIDIA Nemotron model) for incident analysis, asking for JSON only and validating the response shape before saving.
- **Fallback behavior.** The AI service returns a deterministic fallback summary keyed to the deployment's risk level whenever `OPENROUTER_API_KEY` is missing, OpenRouter errors, the request times out, or the model returns JSON that does not match the expected schema. The platform stays usable with no API key.
- **Frontend dashboard.** A Next.js dashboard with a project list, deployment list, deployment detail pages, risk badges, and actions to recalculate risk and trigger AI analysis.

## Risk Scoring

The risk engine ([RiskScoringService.java](backend/deployguard-api/src/main/java/com/deployguard/api/risk/RiskScoringService.java)) adds points for each risk signal tied to a deployment's commit and environment:

| Signal | Points |
| --- | --- |
| A related CI run has status `FAILED` | +30 |
| A related CI run has more than zero failed tests | +20 |
| An `ERROR`-level application log is linked to the deployment | +30 |
| The deployment branch name contains `hotfix` | +10 |
| The deployment targets `production` / `prod` | +10 |

The score is capped at 100. Risk level is `LOW` for scores ≤ 30, `MEDIUM` for 31–70, and `HIGH` above 70.

## Technology Stack

- **Backend API:** Spring Boot 3.5 (Java 21) — REST, validation, JPA, Flyway, RabbitMQ integration
- **AI service:** FastAPI (Python 3.12) with `httpx` and `pydantic`
- **Frontend:** Next.js 15 / React 19 / TypeScript 5
- **Database:** PostgreSQL 16, schema managed by Flyway migrations (`V1`–`V7`)
- **Message broker:** RabbitMQ 3 (management image)
- **Model provider:** OpenRouter, configured for an NVIDIA Nemotron model
- **Local infrastructure:** Docker Compose
- **Planned, not implemented:** Redis caching

## Architecture

For system diagrams (high-level architecture, synchronous and asynchronous AI flows), component responsibilities, the data model, reliability behavior, scaling notes, and roadmap, see [docs/system-design.md](docs/system-design.md). A shorter overview is in [docs/architecture.md](docs/architecture.md).

## Quick Start

Full setup is in the [local development runbook](docs/local-development.md). The short version:

```bash
# 1. Start infrastructure
docker compose -f infra/docker-compose.yml up -d postgres rabbitmq
```

Then, in separate terminals, start the AI service, backend, and frontend using the commands in [docs/local-development.md](docs/local-development.md). `OPENROUTER_API_KEY` is optional — without it, the AI service runs in fallback mode.

Seed realistic demo data from the repository root:

```bash
./scripts/seed-demo.sh
```

See:

- [docs/local-development.md](docs/local-development.md) — complete local stack runbook
- [docs/configuration.md](docs/configuration.md) — environment variables and safe-vs-secret guidance
- [docs/openrouter-nemotron.md](docs/openrouter-nemotron.md) — OpenRouter/Nemotron configuration and AI smoke testing
- [docs/security-notes.md](docs/security-notes.md) — secret-handling notes

## End-to-End Validation

<!-- PLACEHOLDER: An automated end-to-end validation walkthrough/script is not part of this branch yet.
     When the E2E validation work is merged, link the validation runbook and sample output here. -->

_An end-to-end validation runbook and sample output are not available yet. This section will link the validation steps and expected output once that work is merged._ For now, the manual local verification path is the test-and-checks step in the [local development runbook](docs/local-development.md#6-run-tests-and-checks), plus the AI smoke test in [docs/openrouter-nemotron.md](docs/openrouter-nemotron.md).

## Hosted Deployment

<!-- PLACEHOLDER: There is no hosted deployment yet. When a demo environment is deployed,
     add the frontend, backend, and AI service URLs here. Do not invent URLs. -->

_There is no hosted demo environment yet, so no deployment URLs are published._ The project runs locally via Docker Compose and the [local development runbook](docs/local-development.md). Hosted deployment is on the [roadmap](docs/system-design.md#future-roadmap).

## Known Limitations

This project is intentionally scoped and the limitations are documented honestly:

- No authentication or authorization
- No multi-tenancy
- No hosted production deployment
- No distributed tracing
- No retry or dead-letter queue behavior for failed async jobs
- Observability is limited to local logs and basic health checks
- Redis caching is planned but not implemented

A fuller list and the roadmap are in [docs/system-design.md](docs/system-design.md#known-limitations).

## Folder Structure

```text
deployguard-ai/
  backend/
    deployguard-api/   Spring Boot API (risk engine, AI orchestration, async jobs)
  ai-service/          FastAPI incident-analysis service (OpenRouter + fallback)
  frontend/            Next.js dashboard
  infra/               Docker Compose configuration
  scripts/             Local development and demo scripts
  docs/                Architecture, runbooks, and recruiter documentation
  AGENTS.md
  README.md
```

## Current Status

- Spring Boot backend with Projects, Deployments, CI runs, application logs, risk scoring, and synchronous + asynchronous AI analysis job APIs.
- FastAPI AI service with incident analysis, OpenRouter support, and deterministic fallback behavior.
- Next.js frontend with a local dashboard connected to the backend APIs.
- Docker Compose runs PostgreSQL and RabbitMQ for local development.
- Demo data can be created with `./scripts/seed-demo.sh`.
- No API keys or secrets are included in the repository.

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
