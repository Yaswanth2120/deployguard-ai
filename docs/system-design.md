# System Design

DeployGuard AI is a deployment risk and incident analysis platform. The system combines a Next.js dashboard, Spring Boot API, PostgreSQL database, RabbitMQ queue, FastAPI AI service, and OpenRouter-backed NVIDIA Nemotron analysis with deterministic fallback behavior. The portfolio demo is hosted with Vercel for the frontend and Railway for the backend, AI service, PostgreSQL, and RabbitMQ.

## High-Level Architecture

```mermaid
flowchart LR
    user["Developer / Operator"] --> frontend["Next.js frontend<br/>Vercel"]
    frontend --> backend["Spring Boot backend<br/>Railway"]
    apiClient["API client"] --> backend

    backend --> db[("PostgreSQL<br/>Railway")]
    backend --> rabbit["RabbitMQ<br/>Railway"]
    rabbit --> worker["AI analysis worker<br/>(Spring Boot consumer)"]
    worker --> backendService["Backend AI analysis service layer"]
    backendService --> db
    backendService --> aiService["FastAPI AI service<br/>Railway"]
    backend --> aiService
    aiService --> openrouter["OpenRouter"]
    openrouter --> nemotron["NVIDIA Nemotron"]

    db --> backend
    backend --> frontend
```

## Synchronous AI Analysis

The synchronous endpoint is useful when a caller wants the AI summary immediately and can tolerate waiting for the model call or fallback response.

```mermaid
sequenceDiagram
    participant Client as Frontend or API client
    participant Backend as Spring Boot backend
    participant DB as PostgreSQL
    participant AI as FastAPI AI service
    participant Model as OpenRouter / NVIDIA Nemotron

    Client->>Backend: POST /api/deployments/{id}/ai-analysis
    Backend->>DB: Load deployment and project
    Backend->>DB: Load related CI runs and logs
    Backend->>AI: POST /analyze-incident
    AI->>Model: Chat completion request
    alt Model succeeds with valid JSON
        Model-->>AI: Incident analysis JSON
    else Missing key, timeout, provider error, or invalid JSON
        AI-->>AI: Build deterministic fallback response
    end
    AI-->>Backend: Incident analysis response
    Backend->>DB: Store ai_incident_summaries row
    Backend-->>Client: Saved AI summary
```

## Asynchronous AI Analysis

The asynchronous path returns a job immediately and processes model analysis through RabbitMQ. It is better for longer-running analysis and future scaling.

```mermaid
sequenceDiagram
    participant Frontend as Frontend
    participant Backend as Spring Boot backend
    participant Jobs as ai_analysis_jobs table
    participant Rabbit as RabbitMQ
    participant Worker as AI analysis worker
    participant DB as PostgreSQL
    participant AI as FastAPI AI service
    participant Model as OpenRouter / NVIDIA Nemotron

    Frontend->>Backend: POST /api/deployments/{id}/ai-analysis/jobs
    Backend->>DB: Validate deployment exists
    Backend->>Jobs: Insert job with status PENDING
    Backend->>Rabbit: Publish AiAnalysisJobMessage
    Backend-->>Frontend: Job response

    Rabbit-->>Worker: Deliver job message
    Worker->>Jobs: Mark job PROCESSING
    Worker->>DB: Load deployment, project, CI runs, and logs
    Worker->>AI: POST /analyze-incident
    AI->>Model: Chat completion request
    alt Model succeeds with valid JSON
        Model-->>AI: Incident analysis JSON
    else Missing key, timeout, provider error, or invalid JSON
        AI-->>AI: Build deterministic fallback response
    end
    AI-->>Worker: Incident analysis response
    Worker->>DB: Insert ai_incident_summaries row
    Worker->>Jobs: Mark job COMPLETED and set completed_at
    alt Worker or downstream call fails
        Worker->>Jobs: Mark job FAILED and store error_message
    end

    Frontend->>Backend: GET /api/ai-analysis/jobs/{jobId}
    Backend->>Jobs: Load job status
    Backend-->>Frontend: Job status
```

## Data Model Overview

`projects`
: Represents an application or service tracked by DeployGuard. A project stores the display name, GitHub repository URL, service name, and timestamps.

`deployments`
: Represents a deployed commit for a project. A deployment stores commit SHA, branch, environment, status, deployer, deployment time, risk score, risk level, and timestamps.

`ci_runs`
: Represents CI/CD results for a project and commit. A CI run stores provider, status, duration, failed test count, and creation time.

`application_logs`
: Represents ingested application log events. Logs are linked to a project and can optionally be linked to a deployment. They store service name, level, message, log timestamp, and creation time.

`ai_incident_summaries`
: Stores AI incident analysis results for a deployment. Evidence and recommended actions are stored as JSON text for now.

`ai_analysis_jobs`
: Tracks asynchronous AI analysis work. Jobs store deployment, status, error message, creation/update timestamps, and completion time.

## Major Components

Frontend
: The Next.js app provides the local dashboard, project list, deployment list, deployment detail pages, and UI actions for risk recalculation and AI analysis.

Backend
: The Spring Boot API owns core domain data, validation, Flyway migrations, risk scoring, synchronous AI analysis orchestration, and asynchronous job creation/worker processing.

AI service
: The FastAPI service accepts deployment, CI, log, and risk context. It calls OpenRouter when configured and returns the same response schema with fallback behavior when model calls fail.

Database
: PostgreSQL stores projects, deployments, CI runs, logs, AI summaries, and async job state. Flyway manages schema migrations.

Message broker
: RabbitMQ decouples async AI analysis job creation from worker processing.

External model provider
: OpenRouter routes model requests to the configured NVIDIA Nemotron model. Model identifiers, quotas, and free-tier availability can change.

## Technology Choices

Spring Boot
: A strong fit for the backend because it provides mature REST APIs, validation, dependency injection, JPA integration, Flyway support, and RabbitMQ integration.

FastAPI
: A good fit for the AI service because Python is common for model-facing services, and FastAPI gives lightweight request validation and simple JSON APIs.

PostgreSQL
: Provides reliable relational storage, UUID support, timestamp types, and enough structure for deployment, CI, log, job, and summary relationships.

Flyway
: Keeps database schema changes explicit, ordered, and repeatable across local and CI environments.

RabbitMQ
: Provides a straightforward queue for async AI analysis work without introducing heavier event-streaming infrastructure.

Next.js
: Provides a TypeScript frontend with routing, local development ergonomics, and a simple path toward richer dashboard screens.

OpenRouter / NVIDIA Nemotron
: OpenRouter gives a provider-facing API for model calls, while Nemotron is used for incident-analysis style summarization when configured.

## Reliability Behavior

- When `OPENROUTER_API_KEY` is missing, OpenRouter fails or times out, or the model returns invalid or off-schema JSON, the heuristic analyzer answers instead. The response records `meta.source = "heuristic"` and the reason. See [ai-engineering.md](ai-engineering.md).
- AI service failures do not crash the backend. Synchronous backend analysis returns a clear error when the AI service is unavailable.
- Transient async worker failures are retried with exponential backoff (3 attempts). Exhausted jobs are marked `FAILED` with the attempt count and dead-lettered to `deployment.ai.analysis.dlq`. Permanent failures are marked `FAILED` immediately.
- GitHub webhooks are verified by HMAC-SHA256 and deduplicated by `X-GitHub-Delivery`.
- Synchronous analysis is simpler for immediate responses, but it ties the client request to downstream AI latency.
- Asynchronous analysis returns quickly and is more scalable, but clients must poll job status and read summaries later.

## Scaling Considerations

- Multiple RabbitMQ consumers can process AI jobs concurrently when the workload grows.
- Queue depth can provide backpressure when OpenRouter or the AI service is slow.
- Database indexes will become important for common lookups such as deployments by project, CI runs by project/commit, logs by deployment, and jobs by deployment.
- Backend instances are intended to be stateless aside from database and RabbitMQ dependencies, which supports horizontal scaling.
- OpenRouter rate limits and model provider quotas should be treated as first-class capacity limits.
- Caching may help for repeated dashboard reads, repeated summary lookups, or expensive derived metrics. Redis is not currently implemented.

## Known Limitations

- GitHub OAuth (Authorization Code + PKCE) is implemented and gates write endpoints; GET endpoints stay public. Per-project role membership (viewer/operator/admin) from [auth-strategy.md](auth-strategy.md) is not enforced yet — any authenticated GitHub user can run write actions.
- No multi-tenancy is implemented.
- No distributed tracing is implemented.
- Hosted demo deployment exists, but no production-grade SaaS deployment is implemented yet.
- Webhooks use a single global secret rather than per-project secrets.
- Observability: Prometheus metrics on the AI service; no dashboards, alerts, or tracing.
- Evidence and recommended actions are stored as JSON text rather than first-class relational structures.

## Future Roadmap

- Per-project role membership enforcement (viewer/operator/admin)
- Multi-tenant organizations
- Non-GitHub CI/CD provider integrations and per-project webhook secrets
- Distributed tracing and richer metrics
- Production hardening for the hosted deployment
- Production-grade secrets management
- More robust search, filtering, and indexing for logs and deployment history
