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
