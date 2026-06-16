# Architecture Overview

DeployGuard AI will connect deployment activity with source control, CI/CD, and application runtime signals.

For the current detailed architecture, Mermaid diagrams, component responsibilities, reliability behavior, scaling notes, and roadmap, see [system-design.md](system-design.md).

## Components

- Frontend: Web interface for deployment risk views, incident timelines, and AI-generated summaries.
- Backend API: Core API for ingesting and querying pull requests, build results, deployments, and incidents.
- AI service: Dedicated service for incident analysis with OpenRouter/NVIDIA Nemotron support and fallback behavior.
- PostgreSQL: Primary relational data store.
- Redis: Future caching and lightweight coordination.
- RabbitMQ: Asynchronous AI analysis job processing.
- External systems: GitHub, CI/CD providers, deployment systems, and application log sources.

## High-Level Flow

```text
GitHub / CI/CD / Deployments / Logs
              |
              v
        Backend API
              |
      +-------+--------+
      |                |
      v                v
  PostgreSQL       RabbitMQ
                       |
                       v
                  AI Service
                       |
                       v
          Incident summaries and analysis
```

## Current Scope

The repository currently includes the Spring Boot backend, FastAPI AI service, Next.js frontend, PostgreSQL, RabbitMQ, risk scoring, AI analysis endpoints, and local demo scripts. Authentication, multi-tenancy, production deployment, distributed tracing, and Redis are not implemented.
