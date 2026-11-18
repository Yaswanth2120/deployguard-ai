# DeployGuard AI

**AI-powered deployment risk detection and incident analysis for modern DevOps teams.**

DeployGuard AI correlates deployments, CI/CD runs, and application logs to detect risky releases before they become incidents. It combines a deterministic risk-scoring engine with an AI incident-analysis service, a RabbitMQ async workflow, and a Next.js dashboard.

## Live Demo

> **Backend and AI service are currently paused** (Railway free-tier credits ran out). The frontend link still loads, but API calls will fail until the Railway services are redeployed under a paid plan or migrated to another host. In the meantime, see [Local Setup](#local-setup) to run the full stack yourself — that path is unaffected.

| Service | URL |
| --- | --- |
| Frontend dashboard | https://deployguard-ai-coral.vercel.app/ |
| Backend API | https://deployguard-api-production.up.railway.app |
| AI service | https://deployguard-ai-production.up.railway.app |

> The hosted demo is a portfolio deployment, not a production SaaS environment. GET endpoints are intentionally public so recruiters can browse without signing in; write endpoints (ingestion, risk recalculation, AI analysis) require GitHub sign-in. Multi-tenancy, per-project role enforcement, and distributed tracing are not implemented yet.

## Recruiter Quick Links

- [Interview guide](docs/interview-guide.md) — short walkthroughs, design rationale, trade-offs, and likely questions.
- [AI engineering](docs/ai-engineering.md) — eval harness and results, RAG/agent modes, redaction, grounding, risk model.
- [Resume bullets](docs/resume-bullets.md) — concise and detailed resume-ready project bullets.
- [System design](docs/system-design.md) — architecture diagrams, data model, reliability behavior, and scaling notes.
- [Deployment guide](docs/deployment.md) — Railway/Vercel deployment configuration and container build notes.
- [E2E demo validation](docs/e2e-demo-validation.md) — local validation script and expected checks.
- [Data model](docs/data-model.md), [API contract](docs/deployguard-api.openapi.yaml), and [AI contract](docs/ai-service-contract.yaml) — planned v1 integration surface.
- [Auth strategy](docs/auth-strategy.md), [runtime/configuration](docs/runtime-and-configuration.md), and [testing strategy](docs/testing-strategy.md) — operational design decisions.

## Problem

Deployment incidents are hard to triage quickly because the useful signals are scattered:

- CI failures live in one system.
- Deployment metadata lives somewhere else.
- Error logs are separate from the deployed commit.
- Incident summaries often require manual investigation under pressure.

DeployGuard AI creates a single workflow for answering: **what changed, how risky is it, what evidence supports that risk, and what should the team do next?**

## Solution Overview

DeployGuard AI models projects, deployments, CI runs, and application logs in PostgreSQL. The Spring Boot backend computes an explainable deployment risk score from deterministic rules, then sends the same deployment context to a FastAPI AI service for structured incident analysis. AI analysis can run synchronously or asynchronously through RabbitMQ jobs.

The AI service integrates with OpenRouter/NVIDIA Nemotron in four modes: `heuristic`, `single_shot`, `rag` (BM25 retrieval over past incidents and runbooks), and `agent` (a tool loop over logs, CI failures, and the knowledge base). Inputs are redacted for secrets and PII before any model call. Output must match a strict schema with a closed root-cause label set. Evidence the input cannot ground is dropped. On any model failure, a deterministic heuristic analyzer answers instead, and the response records `meta.source = "heuristic"` with the reason. Quality is measured on a 60-case golden eval set in CI. See [docs/ai-engineering.md](docs/ai-engineering.md).

## Key Features

- **GitHub OAuth authentication:** Authorization Code + PKCE sign-in against GitHub; the API issues a short-lived JWT that gates write endpoints while reads stay public.
- **Deployment risk scoring:** deterministic rules (one spec, `shared/risk-rules.json`, contract-tested in Java and Python) plus a logistic-regression risk model with per-feature explanations, benchmarked against the rules (currently trained on synthetic data; a GitHub revert-based dataset miner is included).
- **Evaluation harness:** 60-case golden incident set with core, hard, ambiguous, prompt-injection, and secret-leak slices; accuracy, macro-F1, grounding, injection and leak metrics; optional LLM-as-judge; CI regression gate.
- **GitHub webhook ingestion:** signed (HMAC-SHA256), idempotent `workflow_run`, `pull_request`, and `deployment_status` events feed CI runs, PRs, and scored deployments.
- **Incident context model:** projects, deployments, CI/CD runs, application logs, AI summaries, and async jobs stored in PostgreSQL.
- **Synchronous AI analysis:** immediate incident summary with root cause, evidence, recommended actions, severity, and confidence.
- **Asynchronous AI jobs:** RabbitMQ-backed queue with `PENDING`, `PROCESSING`, `COMPLETED`, and `FAILED` states, retry with exponential backoff for transient failures, and a dead-letter queue.
- **Explicit fallback provenance:** a heuristic analyzer that cites real log lines answers when the model is unavailable; every summary records whether a model or the heuristic produced it, the fallback reason, the prompt version, tokens, and latency.
- **Observability:** Prometheus `/metrics` on the AI service (analyses by source and reason, latency histogram, tokens, cost, ungrounded evidence, redactions).
- **Next.js dashboard:** project list, deployment list, deployment detail view, risk badges, AI summaries, and job status.
- **Demo automation:** seed script and validation script create and verify realistic demo data.
- **Hosted portfolio deployment:** frontend on Vercel; backend, AI service, PostgreSQL, and RabbitMQ on Railway.

## Architecture Overview

```mermaid
flowchart LR
    user["Developer / Recruiter"] --> frontend["Next.js Dashboard<br/>Vercel"]
    frontend --> backend["Spring Boot API<br/>Railway"]
    backend --> postgres[("PostgreSQL<br/>Railway")]
    backend --> rabbit["RabbitMQ<br/>Railway"]
    rabbit --> worker["AI Analysis Worker<br/>Spring Boot Consumer"]
    worker --> ai["FastAPI AI Service<br/>Railway"]
    backend --> ai
    ai --> openrouter["OpenRouter"]
    openrouter --> nemotron["NVIDIA Nemotron"]
```

Detailed diagrams are in [docs/system-design.md](docs/system-design.md).

## Tech Stack

| Layer | Technology | Purpose |
| --- | --- | --- |
| Frontend | Next.js 15, React 19, TypeScript, Tailwind CSS | Dashboard and deployment detail UI |
| Backend | Spring Boot 3.5, Java 21, Maven | REST APIs, risk scoring, orchestration, async worker |
| AI service | FastAPI, Python 3.12, Pydantic, httpx | OpenRouter/Nemotron call and fallback incident analysis |
| Database | PostgreSQL 16 | Projects, deployments, CI runs, logs, summaries, jobs |
| Migrations | Flyway | Versioned database schema changes |
| Queue | RabbitMQ | Async AI analysis job processing |
| Local infra | Docker Compose | PostgreSQL and RabbitMQ for local development |
| Hosting | Railway, Vercel | Hosted demo deployment |

## System Workflow

1. A project is created with a repository URL and service name.
2. A deployment is recorded with commit SHA, branch, environment, status, and deployer.
3. CI/CD run data is ingested for the project and commit.
4. Application logs are ingested and optionally linked to the deployment.
5. The backend recalculates risk from deterministic rules.
6. The frontend displays the deployment, risk score, CI signals, logs, AI summaries, and job history.

## AI Analysis Workflow

### Synchronous

1. Client calls `POST /api/deployments/{deploymentId}/ai-analysis`.
2. Backend loads deployment, project, CI runs, and logs.
3. Backend calls the FastAPI AI service.
4. AI service calls OpenRouter/Nemotron or returns fallback.
5. Backend stores the AI incident summary and returns it.

### Asynchronous

1. Client calls `POST /api/deployments/{deploymentId}/ai-analysis/jobs`.
2. Backend creates an `ai_analysis_jobs` row with `PENDING` status.
3. Backend publishes a RabbitMQ message.
4. Spring Boot worker marks the job `PROCESSING`.
5. Worker calls the same AI analysis flow.
6. Worker stores the summary and marks the job `COMPLETED`, or stores an error and marks it `FAILED`.
7. Client polls `GET /api/ai-analysis/jobs/{jobId}`.

## Authentication

GET endpoints stay public so the dashboard is browsable without signing in. Write endpoints (ingestion, risk recalculation, sync/async AI analysis) require a signed-in GitHub session:

1. The frontend generates a PKCE `code_verifier`/`code_challenge` pair and redirects to GitHub's OAuth authorize endpoint.
2. GitHub redirects back to `/auth/callback` with an authorization code.
3. The frontend posts the code and verifier to `POST /api/auth/github/callback`.
4. The backend exchanges the code with GitHub, fetches the GitHub user, and issues a short-lived JWT (`Authorization: Bearer` token).
5. The frontend attaches that token to write requests; the backend's Spring Security filter chain validates it and rejects unauthenticated writes with `401`.

This is a real, working OAuth flow, not a stub — see [docs/auth-strategy.md](docs/auth-strategy.md) for the full design and its current scope (per-project role membership is not enforced yet; any authenticated GitHub user can run write actions).

## Screenshots

Screenshots are expected under `docs/screenshots/`. Add captured images there when preparing the final portfolio walkthrough.

![Dashboard Screenshot](docs/screenshots/dashboard.png)

![Deployment Detail Screenshot](docs/screenshots/deployment-detail.png)

![AI Analysis Screenshot](docs/screenshots/ai-analysis.png)

See [docs/screenshot-checklist.md](docs/screenshot-checklist.md) for the recommended capture list.

## Local Setup

### Prerequisites

- Java 21
- Maven
- Python 3.12
- Node.js and npm
- Docker Desktop
- curl

### 1. Start infrastructure

```bash
docker compose -f infra/docker-compose.yml up -d postgres rabbitmq
```

### 2. Start the AI service

```bash
cd ai-service
python3.12 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
python -m uvicorn app.main:app --host 127.0.0.1 --port 8001
```

### 3. Start the backend

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

### 4. Start the frontend

```bash
cd frontend
npm install
NEXT_PUBLIC_API_BASE_URL=http://localhost:8080 npm run dev
```

Open http://localhost:3000.

Full setup and troubleshooting: [docs/local-development.md](docs/local-development.md).

## Environment Variables

| Service | Variable | Local example | Notes |
| --- | --- | --- | --- |
| Backend | `DB_HOST` | `localhost` | PostgreSQL host |
| Backend | `DB_PORT` | `5432` | PostgreSQL port |
| Backend | `DB_NAME` | `deployguard` | Database name |
| Backend | `DB_USERNAME` | `deployguard` | Local Docker username |
| Backend | `DB_PASSWORD` | `deployguard` | Local-only password; use hosted secrets outside local dev |
| Backend | `AI_SERVICE_BASE_URL` | `http://localhost:8001` | FastAPI service URL |
| Backend | `RABBITMQ_HOST` | `localhost` | RabbitMQ host |
| Backend | `RABBITMQ_PORT` | `5672` | RabbitMQ AMQP port |
| Backend | `RABBITMQ_USERNAME` | `deployguard` | Local Docker username |
| Backend | `RABBITMQ_PASSWORD` | `deployguard` | Local-only password; use hosted secrets outside local dev |
| Backend | `FRONTEND_ALLOWED_ORIGINS` | `http://localhost:3000` | CORS allow-list |
| Backend | `GITHUB_CLIENT_ID` | your GitHub OAuth App client ID | Public, but still set per-environment |
| Backend | `GITHUB_CLIENT_SECRET` | your GitHub OAuth App client secret | Secret; never commit a real value |
| Backend | `JWT_SIGNING_KEY` | long random string | Secret; must differ per environment, never reuse the local default |
| Backend | `JWT_EXPIRATION_MINUTES` | `60` | Session token lifetime |
| AI service | `OPENROUTER_API_KEY` | empty | Secret; never commit a real key |
| AI service | `OPENROUTER_MODEL` | `nvidia/nemotron-3-ultra-550b-a55b:free` | Confirm availability in OpenRouter |
| AI service | `OPENROUTER_BASE_URL` | `https://openrouter.ai/api/v1` | OpenRouter API base URL |
| AI service | `OPENROUTER_TIMEOUT_SECONDS` | `30` | Model request timeout |
| Frontend | `NEXT_PUBLIC_API_BASE_URL` | `http://localhost:8080` | Public browser value; not a secret |
| Frontend | `NEXT_PUBLIC_GITHUB_CLIENT_ID` | your GitHub OAuth App client ID | Public browser value; must match the backend's `GITHUB_CLIENT_ID` |

Creating the GitHub OAuth App: GitHub Settings → Developer settings → OAuth Apps → New OAuth App. Set **Authorization callback URL** to `<frontend-origin>/auth/callback` (e.g. `http://localhost:3000/auth/callback` locally, or your Vercel URL in production). Copy the client ID into both `GITHUB_CLIENT_ID` (backend) and `NEXT_PUBLIC_GITHUB_CLIENT_ID` (frontend), and the client secret into `GITHUB_CLIENT_SECRET` (backend only).

More detail: [docs/configuration.md](docs/configuration.md).

## Seed Demo Data

After the backend, AI service, frontend, PostgreSQL, and RabbitMQ are running:

```bash
# Copy the short-lived bearer token from a signed-in browser session.
AUTH_TOKEN=<bearer-token> ./scripts/seed-demo.sh
```

The script creates:

- one project
- one high-risk production deployment
- one failed CI run
- one `ERROR` application log
- a recalculated risk score
- one queued async AI analysis job

Override the backend URL if needed:

```bash
BACKEND_URL=http://localhost:8080 ./scripts/seed-demo.sh
```

## Validation and Smoke Tests

Run the complete local demo validation:

```bash
./scripts/validate-demo.sh
```

Run the AI service smoke test:

```bash
./scripts/smoke-test-ai.sh
```

Run component checks:

```bash
# Backend
cd backend/deployguard-api
mvn test

# AI service
cd ai-service
python -m pytest

# Frontend
cd frontend
npm run build
```

## Deployment Architecture

- **Vercel:** hosts the Next.js frontend.
- **Railway:** hosts the Spring Boot backend, FastAPI AI service, PostgreSQL, and RabbitMQ.
- **OpenRouter:** provides model access for NVIDIA Nemotron incident summaries.
- **Fallback mode:** keeps AI analysis operational when a model key is missing or a model call fails.

Deployment configuration details are in [docs/deployment.md](docs/deployment.md). Do not commit platform tokens, database credentials, RabbitMQ credentials, or OpenRouter keys.

## System Design Concepts Demonstrated

- Monorepo organization across backend, AI service, frontend, infra, scripts, and docs
- REST API design with validation and structured error responses
- Relational data modeling for deployment, CI, log, AI summary, and job entities
- Flyway schema migration discipline
- Deterministic scoring separate from probabilistic AI output
- Async job processing with RabbitMQ and a tracked job lifecycle
- Graceful degradation when external AI providers fail
- CORS configuration for hosted frontend/backend separation
- Dockerized services and local reproducibility
- Deployment split across Vercel and Railway

## Resume Bullet Examples

See [docs/resume-bullets.md](docs/resume-bullets.md). It lists which numbers are measured and which still need a run with an API key.

## Repository Structure

```text
deployguard-ai/
  backend/deployguard-api/   Spring Boot API, risk engine, AI orchestration
  ai-service/                FastAPI analysis service (RAG/agent modes, redaction, grounding)
    evals/                   Golden incident set, scoring, eval runner, CI thresholds
    ml/                      Risk-model training, synthetic generator, GitHub dataset miner
  shared/risk-rules.json     Single source of truth for deterministic risk rules
  frontend/                  Next.js dashboard
  infra/                     Docker Compose for local infrastructure
  scripts/                   Demo seed, AI smoke test, E2E validation
  docs/                      Architecture, deployment, runbooks, recruiter docs
```

## Known Limitations

- Authentication covers sign-in and endpoint gating (GitHub OAuth + PKCE, backend-issued short-lived JWT); it does not yet enforce per-project role membership (viewer/operator/admin) — any authenticated GitHub user can run write actions
- No multi-tenancy yet
- Hosted demo exists, but there is no production-grade SaaS deployment
- No distributed tracing yet; metrics exist on the AI service only, with no dashboards or alerts
- LLM-mode eval numbers are not yet recorded (needs an OpenRouter key; the heuristic baseline is measured)
- The risk model is trained on synthetic data until a real dataset is mined with `ml/mine_github.py`
- Webhooks use one global secret, not per-project secrets
- Redis caching is planned but not implemented

## Security Notes

- No API keys or secrets are committed.
- `.env` files are ignored and should stay local.
- Local Docker Compose credentials are development-only.
- OpenRouter keys belong in a local `.env` file or hosted secret manager.

See [docs/security-notes.md](docs/security-notes.md).
