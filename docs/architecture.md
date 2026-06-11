# Architecture Overview

DeployGuard AI will connect deployment activity with source control, CI/CD, and application runtime signals.

## Planned Components

- Frontend: Web interface for deployment risk views, incident timelines, and AI-generated summaries.
- Backend API: Core API for ingesting and querying pull requests, build results, deployments, and incidents.
- AI service: Dedicated service for incident analysis and future NVIDIA Nemotron 3 Ultra integration.
- PostgreSQL: Primary relational data store.
- Redis: Future caching and lightweight coordination.
- RabbitMQ: Future asynchronous event processing.
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

This repository currently contains structure and documentation only. Service implementations, API contracts, schemas, workers, and integrations will be added in later tasks.
