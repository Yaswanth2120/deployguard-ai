# Production Deployment Preparation

DeployGuard AI does not have production URLs or a selected cloud provider yet. This guide prepares the current services for container deployment while keeping secrets external and preserving the local development workflow.

## Deployment Order

Deploy services in this order:

1. Hosted PostgreSQL
2. Hosted RabbitMQ
3. FastAPI AI service
4. Spring Boot backend
5. Next.js frontend

The backend depends on PostgreSQL, RabbitMQ, and the AI service. The frontend depends on the backend URL being known at build time because `NEXT_PUBLIC_API_BASE_URL` is bundled into browser JavaScript by Next.js.

## Required Infrastructure

PostgreSQL:

- Use a hosted PostgreSQL database.
- Store the host, port, database name, username, and password in the deployment platform configuration or secret manager.
- Do not reuse local Docker Compose credentials in hosted environments.
- Flyway runs automatically when the backend starts and applies migrations from `backend/deployguard-api/src/main/resources/db/migration`.

RabbitMQ:

- Use a hosted RabbitMQ broker.
- Store the host, port, username, and password in the deployment platform configuration or secret manager.
- The current async AI analysis workflow expects RabbitMQ to be reachable before backend startup.

## Environment Variables

### Backend

Required for hosted deployment:

```bash
DB_HOST=<postgres-host>
DB_PORT=5432
DB_NAME=<postgres-database>
DB_USERNAME=<postgres-user>
DB_PASSWORD=<postgres-password>
RABBITMQ_HOST=<rabbitmq-host>
RABBITMQ_PORT=5672
RABBITMQ_USERNAME=<rabbitmq-user>
RABBITMQ_PASSWORD=<rabbitmq-password>
AI_SERVICE_BASE_URL=<ai-service-url>
FRONTEND_ALLOWED_ORIGINS=<frontend-origin>
PORT=8080
```

`SERVER_PORT` is still supported for local compatibility. If both `PORT` and `SERVER_PORT` are set, `PORT` wins.

`FRONTEND_ALLOWED_ORIGINS` is a comma-separated list, for example:

```bash
FRONTEND_ALLOWED_ORIGINS=https://frontend.example.invalid,http://localhost:3000
```

Use placeholder values until real production origins exist.

### AI Service

```bash
OPENROUTER_API_KEY=<secret-openrouter-key>
OPENROUTER_MODEL=nvidia/nemotron-3-ultra-550b-a55b:free
OPENROUTER_BASE_URL=https://openrouter.ai/api/v1
OPENROUTER_TIMEOUT_SECONDS=30
PORT=8001
```

`OPENROUTER_API_KEY` is required for real model calls. Without it, the AI service keeps the current fallback behavior. Confirm the OpenRouter model identifier before a hosted launch because provider availability can change.

### Frontend

```bash
NEXT_PUBLIC_API_BASE_URL=<backend-api-url>
PORT=3000
```

`NEXT_PUBLIC_API_BASE_URL` is a build-time public browser value. Changing it after the image is built will not update already bundled frontend JavaScript. Build one frontend image per backend API base URL, or rebuild the image when the backend URL changes.

Do not put secrets in frontend variables. Any `NEXT_PUBLIC_` variable is visible to browser users.

## Build Container Images

Run these commands from the repository root:

```bash
docker build -t deployguard-api:local backend/deployguard-api
docker build -t deployguard-ai-service:local ai-service
docker build \
  --build-arg NEXT_PUBLIC_API_BASE_URL=http://localhost:8080 \
  -t deployguard-frontend:local \
  frontend
```

For hosted deployment, replace image tags with your registry tags. No production registry or cloud provider is configured in this repository.

## Local Container Run Commands

Start local infrastructure:

```bash
docker compose -f infra/docker-compose.yml up -d postgres rabbitmq
```

Run the AI service:

```bash
docker run --rm \
  --name deployguard-ai-service \
  -p 8001:8001 \
  -e PORT=8001 \
  -e OPENROUTER_API_KEY= \
  -e OPENROUTER_MODEL=nvidia/nemotron-3-ultra-550b-a55b:free \
  -e OPENROUTER_BASE_URL=https://openrouter.ai/api/v1 \
  deployguard-ai-service:local
```

Run the backend. On macOS and Windows, `host.docker.internal` lets the backend container reach PostgreSQL, RabbitMQ, and the AI service published on the host:

```bash
docker run --rm \
  --name deployguard-api \
  -p 8080:8080 \
  -e PORT=8080 \
  -e DB_HOST=host.docker.internal \
  -e DB_PORT=5432 \
  -e DB_NAME=deployguard \
  -e DB_USERNAME=deployguard \
  -e DB_PASSWORD=deployguard \
  -e RABBITMQ_HOST=host.docker.internal \
  -e RABBITMQ_PORT=5672 \
  -e RABBITMQ_USERNAME=deployguard \
  -e RABBITMQ_PASSWORD=deployguard \
  -e AI_SERVICE_BASE_URL=http://host.docker.internal:8001 \
  -e FRONTEND_ALLOWED_ORIGINS=http://localhost:3000,http://127.0.0.1:3000 \
  deployguard-api:local
```

Run the frontend:

```bash
docker run --rm \
  --name deployguard-frontend \
  -p 3000:3000 \
  -e PORT=3000 \
  deployguard-frontend:local
```

These commands use local-only credentials from `infra/docker-compose.yml`. Do not reuse those credentials in hosted environments.

## Health Checks

Backend:

```bash
curl -fsS http://localhost:8080/api/health
```

AI service:

```bash
curl -fsS http://localhost:8001/health
```

Frontend:

```bash
curl -fsS http://localhost:3000
```

PostgreSQL:

```bash
pg_isready -h <postgres-host> -p 5432 -U <postgres-user> -d <postgres-database>
```

RabbitMQ:

```bash
rabbitmq-diagnostics -q ping
```

For hosted RabbitMQ where CLI access is unavailable, use the provider health check, management API, or TCP connectivity check required by that provider.

## Secret Management

Keep secrets out of Git:

- Do not commit `.env` files.
- Do not place database passwords, RabbitMQ passwords, OpenRouter keys, platform tokens, or private URLs in source control.
- Use the deployment platform secret manager or CI/CD secret store.
- Keep `.env.example` files limited to placeholders and local-only values.

## Troubleshooting

Backend fails on startup:

- Confirm PostgreSQL is reachable from the backend container.
- Confirm `DB_PASSWORD` is set.
- Check Flyway migration errors before changing any schema files.
- Confirm RabbitMQ is reachable and credentials are correct.
- Confirm `AI_SERVICE_BASE_URL` points to the AI service from the backend container network, not from the host browser.

Frontend cannot call backend:

- Confirm `NEXT_PUBLIC_API_BASE_URL` was set when the frontend image was built.
- Rebuild the frontend image if the backend URL changes.
- Confirm `FRONTEND_ALLOWED_ORIGINS` on the backend includes the frontend origin exactly, including scheme and port.

AI service returns fallback responses:

- Confirm `OPENROUTER_API_KEY` is set in the runtime environment.
- Confirm `OPENROUTER_MODEL` is available in OpenRouter.
- Check provider rate limits and response errors in service logs.

RabbitMQ jobs do not complete:

- Confirm the backend can connect to RabbitMQ.
- Confirm the AI service is reachable from the backend.
- Check backend logs for worker errors and failed job updates.

## Not Included

- No Kubernetes manifests are included.
- No cloud provider is selected.
- No production URL is configured.
- No credentials or provider secrets are committed.
